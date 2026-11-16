# Enforced Probe Budgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the operator of a JVM set a per-probe time-to-live and hit-rate ceiling that the BTrace agent enforces by removing the probe and saying why.

**Architecture:** Limits are resolved once at agent load from system properties and agent arguments. `HandlerRepositoryImpl.resolveHandler` composes a per-probe counter into each handler handle when a rate ceiling is set. A daemon governor thread in the agent checks every probe once a second and, on a breach, no-ops the probe's call sites and runs the probe's exit.

**Tech Stack:** Java 8 source level, `java.lang.invoke` method handles, JUnit 5, Gradle, JMH (`benchmarks/agent-benchmark`).

**Spec:** `internal/specs/2026-11-14-probe-budgets-design.md` — read it first; this plan argues from it.

## Global Constraints

- Main code targets Java 8. No fully qualified type names in source (CI: `scripts/guardrails/check_fqn.py`). Spotless / Google Java Format.
- No wire-protocol or public-API change. New agent options are additive.
- With no limit configured: no counting step in any handler handle, no governor thread.
- Never fail open: a handler whose counting step cannot be built is disabled (`HandlerRepositoryImpl.failClosed`).
- No retransformation or I/O on an application thread; the hot path only increments.
- Do not change the connected-probe exit path. Read `internal/specs/2026-09-27-issue-932-exit-path-wedge-fix-design.md` and `RemoteClient.completeTerminalExit` before Task 3.
- Gradle: `GRADLE_USER_HOME=$(pwd)/.gradle-user`, redirect output to a file and filter it. Integration tests run only with `-Pintegration`. Read test counts, not exit status.
- Every new test must be seen to fail against code without the behavior it asserts.
- **Do not commit.** At each "Checkpoint" step, stop and report; the maintainer decides when to commit.
- Exact names fixed by this plan:
  - agent arguments `probeTtl` (seconds), `probeMaxRate` (hits per second);
  - system properties `io.btrace.probeTtl`, `io.btrace.probeMaxRate`;
  - budget exit code `5`;
  - governor thread name `BTrace Budget Governor`, tick 1000 ms.

## Review Focus

1. **Governor delayed by a GC pause or safepoint.** A tick that arrives 3 s late must not read three seconds of hits as one second. Rate is `delta / elapsed`, not `delta`. (Task 3)
2. **One probe throws during its check or removal.** The governor must keep checking the other probes and keep ticking; a dead governor is a silent fail-open. (Task 3)
3. **Several probes on one JVM.** A hot probe is removed; a quiet probe deployed next to it keeps running and keeps its own count. (Tasks 2, 5)
4. **Redeploy after a trip.** Deploying a probe with the same class name after it was budget-removed must start clean: not tripped, counter at zero. (Task 2)
5. **Budget exit racing a client exit or JVM shutdown.** If the user exits, or the shutdown hook fires, while the governor is removing the same probe, removal happens once and nothing hangs. (Task 3)

## File Structure

| File | Responsibility |
| --- | --- |
| Create `btrace-agent/src/main/java/io/btrace/instr/ProbeBudgets.java` | Process-wide budget state: whether a rate ceiling is on, per-probe counters, tripped marks |
| Modify `btrace-agent/src/main/java/io/btrace/instr/HandlerRepositoryImpl.java` | Compose the counting step; return a no-op for tripped probes; clear state on unregister |
| Create `btrace-agent/src/main/java/io/btrace/agent/BudgetLimits.java` | Parse and resolve the two limits; tighten-only precedence; validation |
| Create `btrace-agent/src/main/java/io/btrace/agent/BudgetGovernor.java` | The tick: TTL and rate checks, trip sequence |
| Modify `btrace-agent/src/main/java/io/btrace/agent/Client.java`, `RemoteClient.java` | Deploy timestamp; `budgetExit` for connected and detached probes; governor's view of live clients |
| Modify `btrace-agent/src/main/java/io/btrace/agent/Main.java`, `btrace-core/src/main/java/io/btrace/core/Args.java`, `btrace-core/src/main/resources/io/btrace/core/messages.properties` | Option constants, snapshot-before-parse, `$` rejection, governor start, usage text |
| Create `integration-tests/src/test/java/tests/ProbeBudgetIntegrationTest.java` and probes under `integration-tests/src/test/btrace/` | End-to-end proof |
| Modify `benchmarks/agent-benchmark`, `docs/GettingStarted.md`, `docs/Troubleshooting.md`, `docs/tutorials/12-production-safe-attach.md` | Cost measurement and user docs |

---

### Task 1: Limit resolution (`BudgetLimits`)

**Files:**
- Create: `btrace-agent/src/main/java/io/btrace/agent/BudgetLimits.java`
- Modify: `btrace-core/src/main/java/io/btrace/core/Args.java` (add `PROBE_TTL = "probeTtl"`, `PROBE_MAX_RATE = "probeMaxRate"`)
- Test: `btrace-agent/src/test/java/io/btrace/agent/BudgetLimitsTest.java`

**Interfaces:**
- Produces:
  ```java
  final class BudgetLimits {
    static final String TTL_PROPERTY = "io.btrace.probeTtl";
    static final String MAX_RATE_PROPERTY = "io.btrace.probeMaxRate";
    static final BudgetLimits NONE;                 // both unset
    long ttlSeconds();                              // 0 = no limit
    long maxRatePerSecond();                        // 0 = no limit
    boolean any();
    /** Reads the two operator properties. Call before agent arguments are parsed. */
    static Snapshot snapshot(Properties systemProperties);
    /**
     * @param premain true when agent arguments were written by the operator
     * @throws InvalidAgentOptionException for an invalid operator value
     *     (any system property; any agent argument when premain)
     */
    static BudgetLimits resolve(Snapshot operator, ArgsMap args, boolean premain, Consumer<String> report);
    static final class Snapshot { /* raw ttl and rate strings, may be null */ }
  }
  ```
  Invalid attach-mode arguments are passed to `report` as one line naming the option and are ignored.

- [ ] **Step 1: Write the failing tests** in `BudgetLimitsTest`:
  - `unsetMeansNoLimit` — empty properties, empty args → `any()` is false.
  - `propertyAloneIsTheCeiling` — property rate `1000` → `maxRatePerSecond() == 1000`.
  - `argumentTightens` — property `1000`, arg `probeMaxRate=100` → `100`.
  - `argumentCannotLoosen` — property `1000`, arg `probeMaxRate=5000` → `1000`.
  - `argumentAloneApplies` — no property, arg `probeTtl=600` → `ttlSeconds() == 600`.
  - `invalidPropertyThrows` — property `probeTtl` = `abc`, `0`, `-1` → `InvalidAgentOptionException` whose message names `io.btrace.probeTtl`.
  - `invalidArgumentInPremainThrows` — arg `probeMaxRate=abc`, `premain=true` → throws, message names `probeMaxRate=abc`.
  - `invalidArgumentUnderAttachIsReportedAndIgnored` — property `1000`, arg `probeMaxRate=abc`, `premain=false` → returns `1000`, `report` received one line containing `probeMaxRate=abc`.
- [ ] **Step 2: Run and see them fail** — `./gradlew :btrace-agent:test --tests '*BudgetLimitsTest'` → compilation failure (class missing).
- [ ] **Step 3: Implement `BudgetLimits`** to the interface above. Values are positive decimal `long`s; anything else is invalid.
- [ ] **Step 4: Run** the same command → 8 passed, 0 failed.
- [ ] **Step 5: Checkpoint** — `./gradlew :btrace-agent:spotlessCheck`; report; do not commit.

---

### Task 2: Counting in the handler handle (`ProbeBudgets`, `resolveHandler`)

**Files:**
- Create: `btrace-agent/src/main/java/io/btrace/instr/ProbeBudgets.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/HandlerRepositoryImpl.java` (`resolveHandler` near line 261-330, `unregisterProbe` near line 86)
- Test: `btrace-agent/src/test/java/io/btrace/instr/HandlerRepositoryImplTest.java` (extend; it already has a stub probe and a `ping()` handler), `btrace-agent/src/test/java/io/btrace/instr/ProbeBudgetsTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class ProbeBudgets {
    /** Called once at agent start. false = no counting step is ever composed. */
    public static void setRateLimited(boolean on);
    public static boolean isRateLimited();
    /** Counter for a probe, by internal class name; created on first use. */
    public static LongAdder counter(String probeInternalName);
    public static void markTripped(String probeInternalName);
    public static boolean isTripped(String probeInternalName);
    /** Drops counter and tripped mark; called from unregisterProbe. */
    public static void forget(String probeInternalName);
    static void resetForTesting();
  }
  ```
- `resolveHandler` behavior after this task:
  1. If `ProbeBudgets.isTripped(probeName)` → return a no-op handle of `handlerType` (use the existing `createNoopMH`), without caching.
  2. Resolve the raw handle as today.
  3. If `ProbeBudgets.isRateLimited()` → compose the increment of `ProbeBudgets.counter(probeName)` in front of the raw handle (`MethodHandles.foldArguments` with a zero-argument void combiner bound to the adder). On any `Throwable` while composing: log at error and use `failClosed(handlerType)`.
  4. Apply `applyLevelGuard` to the result, so a level-rejected call is not counted.
  5. Cache and return, as today.
- `unregisterProbe` additionally calls `ProbeBudgets.forget(probeName)`.

- [ ] **Step 1: Write the failing tests.**
  In `ProbeBudgetsTest`: `counterIsPerProbe` (two names, increments do not mix); `forgetClearsCounterAndTrippedMark`.
  In `HandlerRepositoryImplTest`:
  - `rateLimitedHandlerCountsEachInvocation` — `setRateLimited(true)`, resolve the stub handler, invoke 3 times → `counter(name).sum() == 3` and the stub's `ping` ran 3 times.
  - `noCountingStepWhenNotRateLimited` — `setRateLimited(false)`, resolve, invoke 3 times → `counter(name).sum() == 0`, `ping` ran 3 times.
  - `trippedProbeResolvesToNoop` — `markTripped(name)`, resolve, invoke → `ping` did not run; also true for a handler resolved and cached *before* the mark, when resolved again.
  - `redeployAfterTripStartsClean` — mark tripped, increment the counter, `unregisterProbe`, `registerProbe` again → not tripped, `sum() == 0`, handler runs. *(Review Focus 4)*
  - `twoProbesCountSeparately` — two stub probes, invoke one 5 times → the other's counter is 0. *(Review Focus 3)*
  - `levelRejectedCallIsNotCounted` — a stub handler with a level requirement the current level does not meet → invoke → `sum() == 0`.
- [ ] **Step 2: Run and see them fail** — `./gradlew :btrace-agent:test --tests '*ProbeBudgetsTest' --tests '*HandlerRepositoryImplTest'` → compilation failure.
- [ ] **Step 3: Implement** `ProbeBudgets` (two `ConcurrentHashMap`s keyed by internal name) and the `resolveHandler` / `unregisterProbe` changes above. Note for `trippedProbeResolvesToNoop`: the tripped check must come before the cache lookup.
- [ ] **Step 4: Run** the same command → all pass. Then confirm the fail-closed branch by temporarily making the compose step throw and observing the handler is disabled; restore.
- [ ] **Step 5: Checkpoint** — `./gradlew :btrace-agent:test :btrace-agent:spotlessCheck`; report counts; do not commit.

---

### Task 3: The governor and the budget exit

**Files:**
- Create: `btrace-agent/src/main/java/io/btrace/agent/BudgetGovernor.java`
- Modify: `btrace-agent/src/main/java/io/btrace/agent/Client.java`, `btrace-agent/src/main/java/io/btrace/agent/RemoteClient.java`
- Test: `btrace-agent/src/test/java/io/btrace/agent/BudgetGovernorTest.java`; extend `RemoteClientTerminalExitTest.java` for the detached case

**Interfaces:**
- Consumes: `BudgetLimits` (Task 1); `ProbeBudgets.counter / markTripped` (Task 2); `IndyDispatcher.invalidateProbe(String)`.
- Produces:
  ```java
  // Client.java
  static final int BUDGET_EXIT_CODE = 5;
  static Collection<Client> liveClients();          // snapshot of CLIENTS.values()
  long deployedAtNanos();                           // set when the probe is loaded; 0 before
  String probeInternalName();                       // null before the probe is loaded
  boolean budgetExempt();                           // false here; FileClient overrides → true
  /** Sends the reason line, logs it at WARN, then exits the probe with BUDGET_EXIT_CODE. */
  void budgetExit(String reason);

  // BudgetGovernor.java
  final class BudgetGovernor {
    BudgetGovernor(BudgetLimits limits, LongSupplier nanoTime, Supplier<Collection<Client>> clients);
    void tick();                                    // one pass; never throws
    static void start(BudgetLimits limits);         // daemon thread "BTrace Budget Governor", 1000 ms
  }
  ```
- `tick()` per non-exempt client with a loaded probe that is not shutting down:
  - TTL: `now - deployedAtNanos() > ttlSeconds` → trip with reason
    `probe <name> removed: lifetime exceeded probeTtl=<ttl>s`.
  - Rate: `delta = counter.sum() - lastSum`, `elapsed = now - lastTickNanos`; `rate = delta * 1e9 / elapsed`; `rate > maxRatePerSecond` → trip with reason
    `probe <name> removed: <rate> hits/s exceeded probeMaxRate=<max>`.
    The first tick after a probe appears only records the baseline.
  - Trip = `ProbeBudgets.markTripped(name)`, `IndyDispatcher.invalidateProbe(name)`, `client.budgetExit(reason)` — in that order.
  - Each client is handled inside its own `try/catch (Throwable)`; a failure is logged at error and the loop continues.
  - `<name>` is the probe's user-visible class name (`getClassName()`), and the full line sent is prefixed `BTrace: `.
- `budgetExit`:
  - **Connected** client (`Client` default, and `RemoteClient` with a transport): send `MessageCommand.line(reason)`, then `runtime.handleExit(BUDGET_EXIT_CODE)` — the same call the shutdown hook at `Client.java:374` makes. Nothing else.
  - **Detached** `RemoteClient` (`isDisconnected()`): `runtime.handleExit(BUDGET_EXIT_CODE)` to tear the runtime down, then `super.onExit(BUDGET_EXIT_CODE)` directly, because `onCommand` drops the urgent `ExitCommand` when no transport is attached (`RemoteClient.java:421-427`). `Client.onExit` is already idempotent through `shuttingDown`.

- [ ] **Step 1: Write the failing tests** in `BudgetGovernorTest`, using a hand-advanced `LongSupplier` clock and fake `Client` subclasses that record `budgetExit` calls:
  - `noTripBelowCeiling` — ceiling 1000, 500 hits in 1 s → no exit.
  - `tripsAboveCeiling` — ceiling 1000, 5000 hits in 1 s → one `budgetExit`, reason contains `exceeded probeMaxRate=1000`, probe is marked tripped.
  - `lateTickDoesNotInflateRate` — ceiling 1000, 2400 hits, clock advanced 3 s → no exit. *(Review Focus 1)*
  - `firstTickOnlyBaselines` — 1,000,000 hits before the first tick → no exit on that tick.
  - `ttlTrips` — ttl 10, clock at deploy + 11 s → exit, reason contains `probeTtl=10s`.
  - `ttlDoesNotTripEarly` — clock at deploy + 9 s → no exit.
  - `exemptClientIsSkipped` — `budgetExempt()` true, way over both limits → no exit.
  - `oneFailingClientDoesNotStopTheOthers` — first client's `budgetExit` throws, second is over the ceiling → second still exits; a following `tick()` does not throw. *(Review Focus 2)*
  - `tripHappensOnce` — over the ceiling on two consecutive ticks → `budgetExit` called once. *(Review Focus 5)*
  - `hotProbeTripsQuietProbeStays` — two clients, one over → only that one exits. *(Review Focus 3)*
  In `RemoteClientTerminalExitTest`:
  - `forcedRemovalRemovesDetachedProbe` — detached client, `budgetExit(...)` → client no longer in `Client.listProbes()`, its transformer was cleaned up.
  - `budgetExitAfterClientExitIsHarmless` — exit the client normally, then `budgetExit(...)` → no exception, finalization ran once. *(Review Focus 5)*
- [ ] **Step 2: Run and see them fail** — `./gradlew :btrace-agent:test --tests '*BudgetGovernorTest' --tests '*RemoteClientTerminalExitTest'` → compilation failure.
- [ ] **Step 3: Implement** the `Client` / `RemoteClient` additions and `BudgetGovernor` to the interfaces above. Set `deployedAtNanos` in `Client.loadClass` once the probe is loaded. `FileClient` overrides `budgetExempt()` to return `true`.
- [ ] **Step 4: Run** the same command → all pass; existing `RemoteClient*Test` classes still pass.
- [ ] **Step 5: Checkpoint** — `./gradlew :btrace-agent:test :btrace-agent:spotlessCheck`; report counts; do not commit.

---

### Task 4: Wire into agent startup

**Files:**
- Modify: `btrace-agent/src/main/java/io/btrace/agent/Main.java` (`mainLocked` near line 286-340, `parseArgs` default branch near line 1211-1217)
- Modify: `btrace-core/src/main/resources/io/btrace/core/messages.properties` (usage text near line 144)
- Test: `btrace-agent/src/test/java/io/btrace/agent/MainTest.java`; check `AgentArgsPropertyTest.java` for a list of known options and add the two new ones if it has one

**Interfaces:**
- Consumes: `BudgetLimits.snapshot / resolve` (Task 1), `ProbeBudgets.setRateLimited` (Task 2), `BudgetGovernor.start` (Task 3).

- [ ] **Step 1: Write the failing tests** in `MainTest` (use the existing arg-parsing test seams, e.g. `parseAgentArgs`):
  - `dollarBudgetPropertyArgumentIsRejected` — agent args `$io.btrace.probeMaxRate=999999` → the system property is not set, and one line naming the argument is reported.
  - `otherDollarArgumentsStillSetProperties` — `$some.other=1` → property set, as today.
- [ ] **Step 2: Run and see them fail** — `./gradlew :btrace-agent:test --tests '*MainTest'`.
- [ ] **Step 3: Implement.**
  - In `mainLocked`, take `BudgetLimits.snapshot(System.getProperties())` before `loadArgs(args)`.
  - In `parseArgs`' `$key` branch, skip and report keys equal to `io.btrace.probeTtl` or `io.btrace.probeMaxRate`.
  - After arguments are parsed, call `BudgetLimits.resolve(snapshot, argMap, "premain".equals(agentMode), reporter)`. An `InvalidAgentOptionException` is handled exactly like the one from `ControlServer.open` (`Main.java:329-336`): one line on stderr and in the log, command server not started, startup scripts still run.
  - If `limits.any()`: `ProbeBudgets.setRateLimited(limits.maxRatePerSecond() > 0)` and `BudgetGovernor.start(limits)`, both before the server accepts clients.
  - Add two usage lines to `messages.properties`:
    `probeTtl         maximum lifetime of a probe in seconds; the probe is removed when it is exceeded`
    `probeMaxRate     maximum handler executions per second for one probe; the probe is removed when it is exceeded`
- [ ] **Step 4: Run** `./gradlew :btrace-agent:test :btrace-core:test` → all pass.
- [ ] **Step 5: Checkpoint** — `./gradlew build` (redirected, filtered); report the result line and test counts; do not commit.

---

### Task 5: End-to-end coverage

**Files:**
- Create: `integration-tests/src/test/java/tests/ProbeBudgetIntegrationTest.java` (extends `RuntimeTest`; model the launch helpers on `AgentInvalidOptionsIntegrationTest` — `launchTestAppWithAgent`, `TestApp.capturedOutput()` — and the probe-deploy flow on `BTraceFunctionalTests.testDynamic`)
- Create: `integration-tests/src/test/btrace/BudgetHotProbe.java` (empty `@OnMethod` handler on a method the test app calls in a loop), `integration-tests/src/test/btrace/BudgetQuietProbe.java` (handler on a method called rarely)
- Possibly create a small looping target under `integration-tests/src/main/java/resources/` if no existing test app calls a method fast enough to exceed `probeMaxRate=100`

- [ ] **Step 1: Write the tests.**
  - `hotProbeIsRemovedWithReasonAndExitCode` — target started with `port=0,probeMaxRate=100`; deploy `BudgetHotProbe` → client output contains `removed:` and `exceeded probeMaxRate=100`; client exit code is `5`; the target is still running; a probe listing shows nothing left.
  - `quietProbeSurvivesNextToHotProbe` — same target, deploy `BudgetQuietProbe` and `BudgetHotProbe` → the hot one is removed, the quiet one still produces output afterwards. *(Review Focus 3)*
  - `detachedProbeIsRemovedByTtl` — target started with `port=0,probeTtl=5`; deploy, detach the client; within 15 s the probe listing is empty and the target's captured output contains `lifetime exceeded probeTtl=5s`.
  - `launchArgumentCannotRaiseOperatorCeiling` — target launched with `-Dio.btrace.probeMaxRate=100`, agent loaded with `probeMaxRate=100000000` → hot probe is still removed at 100.
  - `invalidLimitAtLaunchKeepsTargetRunningWithoutServer` — `port=0,probeMaxRate=abc` → output names `probeMaxRate=abc`, no stack trace, target runs (same assertions as `AgentInvalidOptionsIntegrationTest.assertRejectedWithoutCrash`).
  - `noLimitsBehavesAsBefore` — no budget options; deploy `BudgetHotProbe`; after 5 s it is still attached and exits `0` when asked.
- [ ] **Step 2: Prove they can fail.** Temporarily make `BudgetGovernor.start` a no-op, run
  `./gradlew -Pintegration :integration-tests:test --tests '*ProbeBudgetIntegrationTest'` → the first four fail for the expected reason (probe never removed); restore.
- [ ] **Step 3: Run for real** — same command → 6 passed, 0 failed, 0 skipped. Read the counts from the XML/HTML report.
- [ ] **Step 4: Run on a second JDK tier** — `TEST_JAVA_HOME=<JDK 8 path>` and again with the newest installed JDK; report each as `command → counts`.
- [ ] **Step 5: Checkpoint** — report; do not commit.

---

### Task 6: Cost measurement and documentation

**Files:**
- Modify: `benchmarks/agent-benchmark/build.gradle` (line 58 builds the agent string), `benchmarks/agent-benchmark/src/jmh/java/benchmark/BTraceBench.java` only if a new variant needs it
- Modify: `docs/GettingStarted.md`, `docs/Troubleshooting.md`, `docs/tutorials/12-production-safe-attach.md`

- [ ] **Step 1: Benchmark.** Add a way to run `BTraceBench` with `probeMaxRate=<very large>` appended to the agent string (a Gradle property, e.g. `-Pbudget`). The benchmark deploys with `noServer=true,script=…`, so the probe is exempt: counted, never tripped. Run without and with the property and record the per-call difference for an instrumented method with an empty handler.
- [ ] **Step 2: Decide the counter type.** If the `LongAdder` delta is within noise of the unbudgeted run, keep it. If it is clearly measurable, report the numbers and stop for a decision; do not switch to a racy counter without approval (the spec's default is `LongAdder` because undercounting fails toward not tripping).
- [ ] **Step 3: Document.**
  - `GettingStarted.md`: the two options, both spellings, the tighten-only rule, and that limits are fixed at agent load.
  - `Troubleshooting.md`: the two removal messages verbatim, exit code `5`, and that a detached probe's removal is recorded only in the target's log.
  - Tutorial 12: a short step that restarts the hot-loop demo with `probeMaxRate` and shows the removal line and the throughput recovering. Use real output from a run; do not invent numbers.
  - No internal iteration labels in any of it.
- [ ] **Step 4: Full gate.** `./gradlew build`, `./gradlew -Pintegration :integration-tests:test`, `./gradlew spotlessCheck` → report each as `command → key result line`.
- [ ] **Step 5: Checkpoint** — final report listing every gate, what was not verified and why (for example `classFileApiBackendTest` without JDK 28), and the files changed; do not commit.
