# Issue #888: runtime pre-release hardening implementation plan

> Implements the clean design contract in
> `internal/specs/2026-07-16-issue-888-runtime-hardening.md`. All production source remains
> Java-8 compatible; Java-9 and Java-11 source-set changes use only APIs available to their
> respective source levels.

## Boundaries and prerequisites

- Work only in this issue worktree. Preserve unrelated files and do not commit.
- Read the existing `BTraceRuntimeImplBase` command-thread lifecycle and the current profiler
  state machine before changing either; their ordering is part of the defect.
- Treat the registration token as internal runtime metadata. The descriptor field name must be a
  private, documented constant in `BTraceMBean`, not a public script API.
- Do not promise an atomic JMX compare-and-unregister operation: JMX does not provide one. The
  implementation must make the immediately observed foreign/replaced registration a no-op.
- Every wait introduced in a test uses a bounded timeout and releases latches/processes in
  `finally`. Production record entry/exit must contain no snapshot-duration wait or spin loop.

## Progress gate 1 — make speculative detach and handler execution fail-safe

### Files

- Modify `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeImplBase.java`.
- Modify `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeAccessImpl.java`.
- Add `btrace-runtime/src/test/java/io/btrace/runtime/BTraceRuntimeImplBaseTest.java`.
- Add `btrace-runtime/src/test/java/io/btrace/runtime/BTraceRuntimeAccessImplTest.java`.

### Implementation

1. Refactor the private `SpeculativeQueueManager` so its queue map and thread-local references are
   constructed once and never nulled. Protect the complete speculative operation and `clear()` with
   one lifecycle read/write barrier (for example a `ReentrantReadWriteLock`): every
   `speculation`, `speculate`, `send`, `commit`, and `discard` acquires the read side, checks the
   closed state, performs its entire map/thread-local/queue mutation, then releases it;
   `clear()` acquires the write side, publishes `closed=true`, clears the speculative map and the
   passed main `CommandQueue`, and releases it.
   The successful read-side closed-state check is an operation's linearization point; publishing
   `closed=true` while holding the write side is clear's linearization point. Thus clear waits for
   a pre-existing operation, all operations that begin after clear linearizes are no-ops, and no
   operation can create or mutate an orphaned buffer after clear returns.
2. Move the outer runtime's normal command-queue fallback into that same read-side critical
   section (for example replace the current boolean `send` result with a manager dispatch method
   that either buffers, enqueues to the main queue, or drops after close). A check-then-enqueue
   split in `BTraceRuntimeImplBase.send(Command)` is forbidden because clear could otherwise
   linearize between the check and main-queue mutation. Pre-clear `commit` may transfer output
   before clear linearizes; post-clear calls, including `EXIT`, must not enqueue/restore a
   speculative buffer. Preserve the pre-close capacity, overflow message, commit, and discard
   behavior. Replace the command-thread's separate `queue.clear(); specQueueManager.clear()`
   sequence with this single close-and-clear operation, so no fallback command can arrive after the
   main queue was cleared. Keep that command-thread `finally` as the sole normal caller.
3. Make `RTWrapper.escape` save the exact previous `rt`, set it to `null` for the callback, and
   restore that saved value unconditionally in `finally`, including when it was `null`. Catch
   handler failures at this boundary, log an error with the throwable through the runtime logger,
   and return normally so target code is isolated. Do not catch VM-fatal errors or convert an
   error-handler failure into a target exception.
4. Keep both `handleEvent` and every `handleException` `@OnError` invocation routed through
   `doWithCurrent`/`escape`. Make any call-site adjustment needed to ensure reflection failures
   reach the same diagnostic boundary without changing handler ordering or the ordinary
   `ErrorCommand` path when there is no `@OnError` handler.
5. Keep test access package-private and minimal: expose only a deterministic operation hook that
   pauses after the read lock and closed-state check but before a selected mutation, plus observable
   queue/map state. Do not make the queue manager or lifecycle controls public.

### Tests and evidence

1. In `BTraceRuntimeImplBaseTest`, use the package-local hook to test four deterministic
   interleavings—not timing races—for `send`, `speculation/speculate`, `commit`, and `discard`.
   For each operation, pause it while it owns the read side, start `clear`, assert clear cannot
   finish, release the operation, then assert clear finishes and leaves no buffer/map entry or
   main-queue command behind. For the pre-existing `commit` case, assert its transfer happens
   before clear's linearization and is then cleared according to teardown semantics. After clear
   returns, invoke every operation again and assert no map/thread-local/main-queue mutation. Run
   the sequence repeatedly to catch publication mistakes, but use latches/barriers for the proof;
   do not rely on a sleep. Keep an independent normal pre-close case that buffers then commits the
   expected command sequence.
2. In `BTraceRuntimeAccessImplTest`, exercise `RTWrapper.escape` with a throwing callback twice:
   once with a runtime already installed and once with no prior runtime. Assert the original
   wrapper value (including `null`) is restored and the exception does not escape. Capture the
   repository's SLF4J test logger/appender and assert one error-level record contains the thrown
   cause. Test `handleEvent`/`handleException` through the package-local runtime fixture where
   practical; the real protocol test in gate 6 is mandatory coverage for both.
3. Run and filter the log:

   ```bash
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:test --tests io.btrace.runtime.BTraceRuntimeImplBaseTest --tests io.btrace.runtime.BTraceRuntimeAccessImplTest > /tmp/issue-888-gate1.log 2>&1
   rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|BTraceRuntimeImplBaseTest|BTraceRuntimeAccessImplTest" /tmp/issue-888-gate1.log
   ```

**Stop condition:** any post-clear map, thread-local, speculative, or main-queue mutation; an
orphaned queue; an NPE; changed normal speculative semantics; a handler exception reaching the
caller; or a wrapper that remains cleared returns this gate to implementation.

## Progress gate 2 — own probe MBeans and make runtime exit single-shot

### Files

- Modify `btrace-runtime/src/main/java/io/btrace/runtime/BTraceMBean.java`.
- Modify `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeImplBase.java`.
- Add `btrace-runtime/src/test/java/io/btrace/runtime/BTraceMBeanTest.java`.
- Extend `btrace-runtime/src/test/java/io/btrace/runtime/BTraceRuntimeImplBaseTest.java`.

### Implementation

1. Change `BTraceMBean.registerMBean(Class<?>)` from fire-and-forget to return an immutable,
   probe-owned, idempotently closable registration handle (or an explicit no-op handle). The handle
   contains only the canonical `ObjectName` and a fresh unguessable token; it must not retain the
   probe class, bean, or defining loader after registration.
2. Derive the canonical object name in one shared helper from the `@BTrace` name/class name. On a
   no-property probe, return no owned registration. On an existing name, log the collision and
   return no owned registration—never unregister or replace it.
3. Give each constructed `BTraceMBean` its token and include that token in the top-level
   `MBeanInfo` descriptor using the reserved constant. Preserve existing attribute descriptors,
   descriptions, and open types. Before a handle unregisters, re-read `getMBeanInfo(objectName)`,
   compare the reserved descriptor value exactly, and unregister only on a match. Missing bean,
   foreign/invalid descriptor, or a repeated close is a logged/best-effort no-op.
4. Store the returned handle on `BTraceRuntimeImplBase` during `init`. Replace independent
   `handleExit(int)` and `shutdownCmdLine()` terminal paths with one idempotent runtime-owned
   shutdown state machine. Its atomic first transition captures the requested exit code; every
   later runtime, shutdown-hook, or agent request is a no-op and cannot replace that code. The
   first transition runs failure-resilient MBean/extension/runtime cleanup and records a stable
   terminal `MessageCommand` marker containing the cleanup/MBean outcome, but does **not** set
   `exitting`, clear queues, or enqueue an `ExitCommand` yet.
5. Extend `ConsumerWrapper` with a terminal-marker acknowledgement that fires only after the
   command listener successfully dispatches that exact marker. That acknowledgement alone queues
   exactly one runtime `ExitCommand` carrying the captured first code. Consumption of this queued
   Exit is the only transition that sets `exitting`; the command thread then performs its ordinary
   queue-clear `finally` path. This establishes the required order: marker dispatch → one Exit
   with the first code → queue cleanup. Do not let agent code manufacture an exit echo.
6. An off-command-thread request may wait only for a bounded marker-ack interval; a timeout returns
   without forcing an early Exit, while the command thread remains responsible for eventual marker
   acknowledgement and Exit enqueue. A command-thread caller must neither wait for acknowledgement
   nor join itself. Preserve interruption, make cleanup failures diagnosable, and keep every later
   cleanup operation running even if an earlier one fails.

### Tests and evidence

1. `BTraceMBeanTest` declares small annotated nested probe fixtures (property, no-property, and
   canonical-name collision). It verifies a successful registration's exact `ObjectName`, token
   descriptor field, attribute behavior, idempotent close, and no registration for a no-property
   fixture.
2. Pre-register a foreign `DynamicMBean` under the same name and prove registration returns no
   owned handle, leaves the foreign MBean registered, and never replaces it. In a separate
   sequential-replacement test, register BTrace, remove it from the test, install a foreign bean,
   then close the saved BTrace handle; assert the foreign bean remains. Each test removes only the
   bean it created in `finally`.
3. The base-runtime fixture initializes a property probe and records listener dispatches. For both
   direct `handleExit(code)` and `shutdownCmdLine()` it proves the exact terminal marker is
   dispatched before one—and only one—runtime Exit with the first requested code; neither
   `exitting` nor queue clearing occurs before marker acknowledgement. Repeated/concurrent terminal
   requests prove first-code-wins. A command-listener/cmdThread test invokes terminal shutdown from
   the command thread and proves it neither waits for marker acknowledgement nor self-joins. An
   off-thread latch test holds marker dispatch beyond the bounded wait, proves the caller returns
   without early Exit, releases the listener, then observes marker followed by the sole Exit.
4. The same fixture calls terminal shutdown twice and proves its own matching MBean disappears
   exactly once. Repeat with a deliberately throwing extension/exit-cleanup fixture and prove the
   registration is still removed. A non-terminal `DisconnectCommand` fixture must leave the probe
   and registration alive. Do not write a misleading test for the impossible concurrent
   third-party check/unregister race.
5. Run the gate's focused tests with the command below; inspect only the filtered log:

   ```bash
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:test --tests io.btrace.runtime.BTraceMBeanTest --tests io.btrace.runtime.BTraceRuntimeImplBaseTest > /tmp/issue-888-runtime-terminal.log 2>&1
   rg -n "BTraceMBeanTest|BTraceRuntimeImplBaseTest|BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-runtime-terminal.log
   ```

**Stop condition:** replacement of a pre-existing MBean, unregistering a descriptor/token
mismatch, a retained probe reference in the handle, cleanup skipped after an earlier failure, a
marker-less/duplicate Exit, a changed first exit code, a queue clear or `exitting` transition before
marker dispatch, an off-thread timeout that drops terminal completion, or command-thread self-wait.

## Progress gate 2b — retain the remote transport through the terminal handshake

### Files

- Modify `btrace-agent/src/main/java/io/btrace/agent/RemoteClient.java`.
- Modify `btrace-agent/src/main/java/io/btrace/agent/Client.java`.
- Add `btrace-agent/src/test/java/io/btrace/agent/RemoteClientTerminalExitTest.java`.
- Extend `integration-tests/src/test/java/tests/Issue888RuntimeHardeningIntegrationTest.java` in
  gate 6 for the full client-agent-target assertion.

### Implementation

1. In the inbound reader, treat `DisconnectCommand` as transport-only and non-terminal. For an
   inbound `ExitCommand`, do not echo it and do not call final agent `Client.onExit` first; invoke
   the runtime's code-carrying shared terminal-shutdown bridge while retaining the output protocol.
2. Add one `TerminalHandshake` owned by `RemoteClient`, with an idempotent completion latch/state.
   The reader's generic `finally` recognizes a terminal handshake and must not close the socket or
   protocol merely because the inbound reader returns. A bounded reader wait may time out, but it
   leaves output retained and completion owned by the eventual runtime dispatch; it never falls
   back to generic-finally close.
3. When the runtime dispatches the stable terminal marker, write/flush it normally. When it
   dispatches its sole generated Exit, write/flush that exact Exit, count down the handshake latch,
   and invoke one idempotent `completeTerminalExit`. Only that completion performs final agent
   `Client.onExit` cleanup and I/O close. Change `Client.onExit` to finalization-only: it neither
   sends another `ExitCommand` nor invokes the runtime shutdown bridge again.
4. Preserve this ordering for both inbound-client exits and runtime-originated exits: marker → one
   generated Exit using the first runtime code → `completeTerminalExit`. Failure/timeout paths must
   retain the diagnostic-before-termination ownership rather than emitting an echo or closing early.

### Tests and evidence

1. `RemoteClientTerminalExitTest` creates an authenticated loopback V1/V2 session with a
   package-local runtime/dispatch seam. It injects an inbound nonzero `ExitCommand`, proves it is
   not echoed immediately, allows the reader to return, and proves the output protocol/socket are
   still usable while `TerminalHandshake` is pending. It then dispatches the runtime marker and
   generated Exit and records the exact wire order: marker, exactly one Exit with the inbound first
   code, then final close. Assert the latch is counted down only after a successful Exit write.
2. Add a delayed-marker test: hold marker dispatch past the bounded reader wait, prove no
   generic-finally close and no early exit echo, release it, and assert the same wire sequence.
   Add a runtime-originated exit case and a non-terminal Disconnect case. Use latches/timeouts and
   `finally` cleanup; do not depend on sleeps.
3. Spy or fixture the base agent `Client` finalizer to assert `onExit` runs once after generated
   Exit dispatch and never sends an Exit/re-enters runtime shutdown. Keep existing authentication
   tests green.
4. Run and inspect:

   ```bash
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:spotlessCheck > /tmp/issue-888-agent-spotless.log 2>&1
   rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-agent-spotless.log

   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.agent.RemoteClientTerminalExitTest --tests io.btrace.agent.RemoteClientAuthenticationTest > /tmp/issue-888-remote-terminal.log 2>&1
   rg -n "RemoteClientTerminalExitTest|RemoteClientAuthenticationTest|BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-remote-terminal.log
   ```

**Stop condition:** an inbound Exit is echoed, a pending handshake permits generic-finally close,
the marker is not delivered before Exit, more than one Exit is written, code differs from the first
request, `Client.onExit` sends/re-enters terminal shutdown, or final I/O close precedes successful
generated-Exit dispatch.

## Progress gate 3 — make class-definition failures uniform across tiers

### Files

- Add `btrace-runtime/src/main/java/io/btrace/runtime/DefineClassSupport.java`.
- Modify `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeImpl_8.java`.
- Modify `btrace-runtime/src/main/java9/io/btrace/runtime/BTraceRuntimeImpl_9.java`.
- Modify `btrace-runtime/src/main/java11/io/btrace/runtime/BTraceRuntimeImpl_11.java`.
- Modify `btrace-runtime/build.gradle` to wire multi-release outputs into runtime tests.
- Add `btrace-runtime/src/test/java/io/btrace/runtime/DefineClassSupportTest.java`.
- Add `btrace-runtime/src/test/java/io/btrace/runtime/RuntimeDefineClassTierTest.java`.

### Implementation

1. Add the Java-8-compatible package-private `DefineClassSupport` normalizer. It unwraps one
   `InvocationTargetException`, immediately rethrows the exact `VirtualMachineError` or
   `ThreadDeath`, and otherwise creates an `IllegalStateException` whose message identifies probe
   class definition and runtime tier and whose cause is the unwrapped root. Do not silently return
   `null`, catch only a curated reflection list, or lose linkage/verification failures.
2. Wrap the complete definition-and-initialization transaction in every tier with that normalizer:
   `_8` covers missing `Unsafe`, isolated loader definition, and `ensureClassInitialized`; `_9`
   covers stack-walker validation, anchor creation, lookup definition, and constructor
   initialization; `_11` covers the 11--14 anchor branch and all reflective 15+ hidden-class
   lookup/invocation operations. Successful return remains a `Class<?>` with the same isolation
   behavior as before.
3. Introduce only package-private injectable definition seams necessary to force each tier's
   boundary to throw in a unit test. They must be unavailable to scripts/public callers and invoke
   the identical production failure normalizer; this tests `_8`, `_9`, `_11` regardless of the
   JDK that runs the suite when the test JVM can load those classfile versions. Keep test source
   Java-8 compatible by looking up tier classes/seams reflectively rather than statically
   referencing Java-9/11 types.
4. In `btrace-runtime/build.gradle`, make this wiring mandatory, not conditional: configure the
   existing `test` task to depend on `compileJava9Java` and `compileJava11Java`, and append
   `sourceSets.java9.output.classesDirs` and `sourceSets.java11.output.classesDirs` to its runtime
   classpath. Use the concrete Groovy shape below (adapting only syntax required by the existing
   Gradle version):

   ```groovy
   tasks.named('test') {
       dependsOn tasks.named('compileJava9Java'), tasks.named('compileJava11Java')
       classpath = classpath.plus(files(
           sourceSets.java9.output.classesDirs,
           sourceSets.java11.output.classesDirs))
   }
   ```

   This makes `RuntimeDefineClassTierTest` load all three implementations on a Java 11+ test JVM;
   a Java 8 runner still executes the `_8` natural path but cannot load higher-version bytecode.
5. Inspect direct consumers of `defineClass` and remove/guard any assumption that a failed result
   may be `null`; preserve the normalized cause in propagation/logging.

### Tier matrix and tests

| Test tier | Deterministic seam on any supported test JVM | Natural runtime matrix when available |
| --- | --- | --- |
| `_8` | inject missing-unsafe/verification/linkage failure and assert normalized cause | JDK 8 selects `_8` |
| `_9` | inject lookup/constructor/reflection-wrapper failure and assert normalized cause | JDK 9--10 selects `_9` |
| `_11` anchor | inject anchor/lookup/initialization failure and assert normalized cause | JDK 11--14 selects anchor path |
| `_11` hidden | inject reflective hidden-class failure and assert normalized cause | JDK 15+ selects hidden-class path |

`DefineClassSupportTest` directly covers verification and linkage errors, an
`InvocationTargetException` wrapper, `VirtualMachineError`, and `ThreadDeath` identity. The
tier test uses invalid bytes through the selected factory to prove a real definition failure is an
`IllegalStateException` with its cause; it also executes each injectable tier seam. Existing
`HiddenClassDefineRegressionTest` remains green and successful definitions remain non-null.

Run `:btrace-runtime:test` through a redirected log, filter for the new tests and
`HiddenClassDefineRegressionTest`, and first confirm the Gradle task ran `compileJava9Java` and
`compileJava11Java`. Use a Java 11+ test launcher for the mandatory all-tier seam run; execute the natural rows
on available JDK 8, 9/10, 11--14, and 15+ runners; absence of an installed historical JDK is not a
reason to omit the deterministic seam tests.

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:test --tests io.btrace.runtime.DefineClassSupportTest --tests io.btrace.runtime.RuntimeDefineClassTierTest > /tmp/issue-888-define-class.log 2>&1
rg -n "compileJava9Java|compileJava11Java|DefineClassSupportTest|RuntimeDefineClassTierTest|BUILD SUCCESSFUL|BUILD FAILED|FAILED" /tmp/issue-888-define-class.log
```

**Stop condition:** any recoverable definition/initialization failure returns `null`, any tier
bypasses the common boundary, the root cause is replaced, or VM-fatal/`ThreadDeath` is wrapped.

## Progress gate 4 — replace profiler contention with bounded FIFO deferral

### Files

- Modify `btrace-runtime/src/main/java/io/btrace/runtime/profiling/MethodInvocationRecorder.java`.
- Modify `btrace-runtime/src/main/java/io/btrace/runtime/profiling/MethodInvocationProfiler.java`.
- Extend `btrace-runtime/src/test/java/io/btrace/runtime/profiling/MethodInvocationRecorderTest.java`.
- Add `btrace-runtime/src/test/java/io/btrace/runtime/profiling/MethodInvocationProfilerTest.java`.

### Implementation

1. Replace the state-protected `LinkedList` with a FIFO `ConcurrentLinkedQueue<DelayedRecord>`;
   make `DelayedRecord` immutable. Preserve the writer state meanings, but eliminate all loops in
   application `recordEntry`/`recordExit` that wait for state 2 or 3.
2. Define one shared operation path: an owner that can atomically claim `0 -> 3` drains the queue
   fully in FIFO order, then processes its own direct work under the correct `0 -> 1` or snapshot
   transition. If it cannot claim an immediately available write state, it appends exactly one
   delayed record and returns. Recheck and drain already queued records before later direct work so
   a queued record cannot be overtaken. Queue insertion is the deferred-work linearization point;
   the successful state claim is the direct-work linearization point.
3. In `getRecords`, track whether state 2 was actually acquired and release only that acquired
   state in `finally`. A setup/drain failure before acquisition must return or throw promptly, not
   loop waiting to change 2 to 0. Keep snapshot-plus-reset atomic and keep existing live-stack
   preservation behavior.
4. While traversing profiler recorder weak references in both `snapshot` and `reset`, remove
   references whose referent is `null`. Hold live referents only for the individual operation;
   retain concurrent recorder creation safety.
5. Add a narrowly scoped package-local test hook (latch/observable queue size/state acquisition),
   not a production public API, so tests can hold state 2 and force pre-acquisition failure without
   sleeps or reflection.

### Tests and evidence

1. Hold snapshot state 2 with a latch. From an application thread invoke both entry and exit;
   each must finish inside a bounded timeout before the snapshot latch is released. Assert the
   queue contains the two operations in FIFO order, then release the snapshot, force the next
   drain/snapshot, and verify valid stack state and aggregate result.
2. Force a failure before `getRecords` owns state 2 (and separately a normal contention case),
   assert completion/throw inside its timeout, then prove a subsequent record/snapshot still works.
3. Create live and cleared-recorder fixtures via the package-local cleanup seam. After each of
   `snapshot(false)` and `reset()`, assert cleared wrappers are removed and live recorder data is
   still aggregated/reset correctly. Do not rely on probabilistic GC for correctness.
4. Retain and run the existing recursion, reset-with-live-frame, interval-delta, and accumulation
   tests; they establish the unchanged profiler data model.

**Stop condition:** an application entry/exit waits for a held snapshot, a delayed entry/exit is
lost or overtaken, `getRecords` releases a state it did not own, or a cleared weak wrapper survives
both traversals.

## Progress gate 5 — format and run all runtime-component checks

From the issue worktree, redirect every Gradle run to a log, filter it, and inspect the filtered
file rather than consuming raw Gradle output. Add the IPv4 `JAVA_TOOL_OPTIONS` setting only if the
restricted environment needs it.

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:spotlessCheck > /tmp/issue-888-runtime-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-runtime-spotless.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:test > /tmp/issue-888-runtime-test.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/issue-888-runtime-test.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/issue-888-dist.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|btraceJar" /tmp/issue-888-dist.log
```

**Stop condition:** Spotless failure, a failing focused/runtime test, or a failed masked
distribution build. Fix and repeat the affected gate before integration work.

## Progress gate 6 — verify the real client, agent, target, and protocol lifecycle

### Files

- Add `integration-tests/src/test/java/tests/Issue888RuntimeHardeningIntegrationTest.java`.
- Add `integration-tests/src/test/java/resources/Issue888RuntimeHardeningTarget.java`.
- Add `integration-tests/src/test/btrace/Issue888RuntimeHardeningTest.java`.
- Extend `btrace-compiler/src/test/java/io/btrace/compiler/BTraceDslVerifierTest.java` with the
  verifier fixture for the handler-fault trigger.
- Modify `integration-tests/src/test/java/tests/RuntimeTest.java` only if a small protected helper
  is needed to expose its existing staged masked JAR path, event classpath, target launch, or V2
  protocol setup. Do not duplicate unrelated harness logic.

### E2E topology and assertions

1. The test reserves a free port with `ServerSocket(0)`, closes the reservation, and starts the
   target JVM from the staged integration-test classes with the same dynamic-attach flags used by
   `RuntimeTest`: `AllowRedefinitionToAddDeleteMethods`, `IgnoreUnrecognizedVMOptions`,
   `EnableDynamicAgentLoading`, `UnlockDiagnosticVMOptions`, `-OmitStackTraceInFastThrow`,
   and `-Dbtrace.test`. Do **not** pass `-Dbtrace.port` to this initially uninstrumented target:
   `Client.attach` interprets that property as an already-running agent and skips
   `VirtualMachine.loadAgent`; the later unavailable-socket branch in `Client.submit` calls
   `System.exit(1)`, terminating the Gradle test JVM. Instead construct the client with the
   reserved port and let its attach/load-agent flow supply that port to the newly loaded agent; the
   agent then publishes `btrace.port` on the target. The target removes inherited
   `JAVA_TOOL_OPTIONS`, prints a PID-ready marker, and keeps collecting stdout/stderr via bounded
   pumps.
2. `Issue888RuntimeHardeningTarget` has an explicit stdin command (for example `mbean-status`) to
   inspect its own platform MBean server. It reports the probe's exact canonical name and whether
   the reserved descriptor/token is present; after detach it reports that the same name is absent.
   This avoids incorrectly querying the test JVM's MBean server.
3. The test sets forced V2 system properties and uses the existing `Client` direct-API pattern:
   construct it with the staged masked `btrace.jar` override, attach to the target PID, compile the
   new probe against `getEventsClassPath`, and call `submit` on an executor. Its command listener
   completes a status future, records normal probe output, and never hides startup failure.
4. The probe contains an `@Property` (creating the MBean), an `@OnEvent`, an instrumented
   `@OnMethod`, and an `@OnError`. The named-event and instrumented handlers each write a unique
   marker and then call the exact verifier-legal failure trigger
   `BTraceUtils.substr("x", 2)`. `BTraceUtils` is a verifier-allowed BTrace class and its
   `Strings.substr` implementation delegates to `String.substring(2)`; for the one-character
   literal this deterministically throws `StringIndexOutOfBoundsException` without a forbidden
   source-level `throw`. The `@OnError` handler writes its own marker and calls the same trigger,
   exercising the error-handler failure route. The script includes a normal timer/method output
   marker. Once status is successful, the test sends the named `EventCommand` through the same
   client. It waits for both original handler markers, the `@OnError` marker, and a later normal
   output over V2, proving the target survived fault-isolated handlers. Assert target runtime
   stderr/log capture has an error-level diagnostic and throwable for the named-event failure and
   the failed `@OnError` invocation.
5. Before accepting the E2E fixture, add a `BTraceDslVerifierTest` source-string fixture containing
   `@OnEvent`, `@OnMethod`, `@OnError`, and `BTraceUtils.substr("x", 2)` and assert
   `Compiler.compile(...)` produces one class. This is the verifier acceptance proof; the
   integration module's existing `compileTestProbes` task then recompiles the exact on-disk probe.
   Do not substitute a Java `throw`, unchecked cast, direct JDK method call, or a trigger whose
   failure depends on target state.
6. Query `mbean-status` while attached and require the canonical MBean plus a nonblank ownership
   descriptor token. Send a real nonzero `ExitCommand` through the same forced-V2 client path, not
   `DisconnectCommand`. Record all received command types and require the terminal-cleanup marker
   (including its MBean-close diagnostic) before exactly one final ExitCommand with that same
   nonzero code; the protocol reader must receive both after it submitted the inbound Exit. Only
   after that generated Exit may the client close/finalize. Query `mbean-status` again and require
   the MBean absent, then send `done` to the target and require clean target and executor shutdown.
   The test also proves a `DisconnectCommand` is non-terminal by checking it does not invoke the
   unload/MBean-close path; it must not use Disconnect for the unload assertion. Every failure path
   closes the client, releases the executor, and destroys a stuck target in `finally`.

This is intentionally not replaced with a unit test: it exercises the built masked distribution,
real attach, target instrumentation, client command transport, forced V2 negotiation, handler
dispatch, terminal handshake, and target-owned MBean visibility.

### E2E verification

Run the focused scenario only after gate 5's distribution build, then run all integration tests:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/issue-888-integration-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-integration-spotless.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-compiler:test --tests io.btrace.compiler.BTraceDslVerifierTest > /tmp/issue-888-verifier.log 2>&1
rg -n "BTraceDslVerifierTest|BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-verifier.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test --tests tests.Issue888RuntimeHardeningIntegrationTest > /tmp/issue-888-focused-integration.log 2>&1
rg -n "Issue888RuntimeHardeningIntegrationTest|BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR" /tmp/issue-888-focused-integration.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test > /tmp/issue-888-integration.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|tests" /tmp/issue-888-integration.log
```

**Stop condition:** an event/error handler failure reaches the target application, lacks an error
diagnostic, V2 is not forced/observed, normal output cannot follow the faults, the terminal marker
is not followed by exactly one Exit carrying the submitted nonzero code, the client cannot receive
both terminal commands after sending inbound Exit, the target MBean survives matching terminal exit,
or Disconnect unloads the probe. Also stop if the initially uninstrumented target is launched with `-Dbtrace.port`
(causing `Client.attach` to skip agent loading and `Client.submit` to take its unavailable-socket
`System.exit(1)` branch, terminating the Gradle test JVM), or any process/executor cannot be shut
down within its bound.

## Final completion gate

1. Run `git diff --check` and inspect the complete diff against all F1--F7 acceptance criteria.
2. Confirm no public API, masked-jar layout, release metadata, or user documentation change was
   introduced unless implementation evidence requires one.
3. Confirm the final evidence includes the focused runtime terminal-state and RemoteClient
   handshake suites, the runtime full suite, masked distribution build, focused real attach
   lifecycle test, and full integration suite. If any matrix JDK was not installed, record that
   the deterministic per-tier seam passed and exactly which natural rows were executed.
4. Do not commit, open a PR, or close #888 without separate authorization.

Completion means every production gate, all deterministic concurrency/tier tests, the real
client-agent-target V2 lifecycle test, full integration suite, formatting, distribution build, and
diff review are green; no valid design requirement remains unimplemented.
