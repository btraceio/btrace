# Implementation plan — stall watchdog for integration tests

Design: `docs/superpowers/specs/2026-07-29-issue-932-client-close-deadlock-design.md`.
Branch: `agent/issue-932-client-close-deadlock`, worktree `.worktrees/issue-932-client-close`.
Base: `origin/develop` @ `840564e9`. Independent of PR #934.

Revision 2. Adversarial review of revision 1 found that the SIGQUIT fallback would have written a
thread dump into the target stdout buffer that test validators assert on (`RuntimeTest.java:858` →
`ManifestLibsTests.java:81`), turning a diagnostic into a test failure. That is removed, along with
the echo flag that existed only to serve it. Other corrections are noted where they apply.

Every command below runs from this worktree, prefixed `GRADLE_USER_HOME=$(pwd)/.gradle-user`, with
output redirected to a log before filtering, per `AGENTS.md`.

## Constraints

- **Java 8 source level** for `integration-tests` tests (`build.gradle:15-18`). No `var`, no
  `List.of`, no direct `Process.pid()`. Note this is enforced by convention only, and CI actually
  runs the harness on Java 24 — the matrix JDK is the *target* JVM via `TEST_JAVA_HOME`.
- **One worker JVM for all ten test classes.** No `forkEvery`, `maxParallelForks`, or
  `junit-platform.properties` exists today. Static state is shared for the whole run, and a system
  property set by one test leaks into every class after it.
- Test knobs arrive via `-P` (`build.gradle:274` forwards project properties beginning with
  `btrace.`).
- The watchdog must never become the hang it diagnoses, and must never perturb the system under
  test. Every wait is bounded; every internal failure is recorded as text, not thrown.

## Task 1 — `TargetRegistry`

New: `integration-tests/src/test/java/tests/harness/TargetRegistry.java`

- `static Handle register(Process process, String label, String jcmdPath)` — called at launch,
  before a pid is known. **`jcmdPath` is passed in at registration**, not resolved centrally:
  `resolveTestJavaHome()` is `protected static` in package `tests` (`RuntimeTest.java:128`) and is
  unreachable from `tests.harness`, and targets do not all come from the same JDK —
  `Issue888RuntimeHardeningIntegrationTest.java:172` launches with the *build* JDK while the
  `RuntimeTest` paths use `resolveTestJavaHome()`.
- `Handle.setPid(String)` — called where a launch site parses `ready:`.
- `static List<Snapshot> liveTargets()` — entries where `process.isAlive()`. Registering at launch
  and filtering on liveness at capture removes any need for deregistration hooks, and makes the
  never-stopped `TestApp` at `BTraceFunctionalTests.java:535` harmless.
- Pid resolution when `ready:` never arrived — the startup-stall case, which is exactly when the
  registry must still be useful: fall back to `Process.pid()` **via reflection**, cached, guarded,
  returning null on failure. Reflection keeps the Java 8 source level while working on the Java 24
  worker CI actually uses.
- `Snapshot` is an immutable `(label, pid, process, jcmdPath)`.
- Backed by `CopyOnWriteArrayList`.

## Task 2 — `StallCapture`

New: `integration-tests/src/test/java/tests/harness/StallCapture.java`

`static void capture(Appendable sink, String label, long elapsedMs, int frame, List<Snapshot> targets, long budgetMs)`
— writes the report and never throws.

- Sections in order: banner (label, elapsed, frame index); test-JVM threads; then each live target.
- **The sink is flushed after every section.** Revision 1 built the whole report as a `String` and
  wrote it at the end, so a hang in any section produced zero bytes — the precise failure being
  designed against.
- The test-JVM dump is `ThreadMXBean.dumpAllThreads(true, true)` (lock owners and monitors, which
  `Thread.getAllStackTraces()` omits) computed **on a throwaway thread with a bounded join**. It
  requires a safepoint, and a JVM that cannot reach one is a live possibility here. On overrun the
  section reads `test-JVM dump timed out`.
- Per target: `jcmd <pid> Thread.print -l` using the snapshot's own `jcmdPath`, with output
  redirected to a temp file — **never a pipe**. `Thread.print -l` on an instrumented target exceeds
  the 64 KB pipe buffer, and a jcmd blocked on a full pipe would be killed by the bound having
  produced nothing.
- `budgetMs` spans all targets together, checked before each; a skipped target says so.
- **No SIGQUIT fallback.** A HotSpot SIGQUIT dump goes to the target's stdout, and for
  `testStartup` targets that stream is accumulated by `OutputPump` (`OutputPump.java:67`) into the
  buffer `v.validate(stdout.toString(), …)` asserts on (`RuntimeTest.java:858`). It would corrupt
  the system under test. A jcmd that fails or overruns is recorded as text and that is the end of it.
- Every line is prefixed `[stall-dump] ` so a capture interleaved with the `[traced app]` and
  `[btrace out]` reader threads is recoverable with one `grep`.

## Task 3 — `StallWatchdog` and `@StallTimeout`

New: `integration-tests/src/test/java/tests/harness/StallWatchdog.java`,
`integration-tests/src/test/java/tests/harness/StallTimeout.java`

`BeforeAllCallback` + `BeforeEachCallback` + `AfterEachCallback` + `AfterAllCallback`.

- Arms on `beforeAll` — JUnit fires extension `beforeAll` before the class's own `@BeforeAll`, and
  every subclass calls `classSetup()` from one, so that path is covered — and re-arms per
  `beforeEach`.
- Deadline: `@StallTimeout(millis=…)` on method or class, else `-Pbtrace.test.stallTimeoutMs`, else
  `btrace.test.timeoutMs * 6` (six minutes at the default). The annotation exists because a system
  property cannot be scoped to a single test in a single-JVM run. A shorter default is not safe:
  `RuntimeTest.java:784` already allows a target `timeout * 4` = 240 s for startup alone.
- **Targets are snapshotted once, at fire time**, and both frames capture those exact `Process`
  objects. Revision 1 re-read the live registry for frame two, so a test that unwedged during the
  gap would have had the *next* test's target captured.
- Frame two is a **separately scheduled task** at +30 s, not a sleep inside frame one, and it
  re-checks the disarm flag and skips if the test has finished. Two frames because one cannot
  distinguish "wedged" from "slow"; the diff is the evidence. Scheduler pool size 2 so one fire
  cannot defer another test's deadline.
- Disarm: `ScheduledFuture.cancel(false)` plus an `AtomicBoolean`; `afterEach` never blocks on an
  in-flight capture.
- Output dir comes from the absolute path in `btrace.test.stallDumpDir` (task 4). A relative path
  would resolve against `Test.workingDir`, which defaults to the *project* directory, yielding
  `integration-tests/integration-tests/build/...` — outside the upload glob.
- Filenames: sanitised JUnit unique id truncated to 120 chars plus a short hash of the full id.
  The full id of a parameterised invocation exceeds the 255-byte filename limit, and the motivating
  test is a `@ParameterizedTest` whose four rows share one method name.
- Sink order is **file first, stdout second**. `if: always()` upload steps do run after a
  `timeout-minutes` cancellation, whereas a cancelled job tears down the worker→daemon→console
  pipeline, and the release path redirects all test output into a file it only summarises after the
  command exits (`scripts/run-release-gate.sh:52`). Stdout is a shared synchronised `PrintStream`
  that a stalled pipeline can itself block on.
- Stale dumps from earlier runs are cleared once per JVM on first arm; `cleanTest` does not cover
  this directory.

## Task 4 — build wiring

Modify: `integration-tests/build.gradle` — in the `test` block (`:163`), add

```
systemProperty 'btrace.test.stallDumpDir',
    layout.buildDirectory.dir('reports/stall-dumps').get().asFile.absolutePath
```

Its own line, because the `-P` forwarding loop at `:274` forwards project properties only.
`build/reports/**/*` is what both `if: always()` upload steps already collect
(`continuous.yml:171`, `release.yml:377`), so no workflow change is needed. Confirm that during
task 8 rather than assuming it.

`options.release = 8` is **not** attempted: `Issue884PublishedFatAgentE2ETest` already calls
`Files.writeString` (JDK 11) at seven sites (`:173, :187, :239, :245, :279, :285, :289`), so the
module cannot compile at release 8 today. Recorded as a pre-existing violation, not fixed here.

## Task 5 — a per-test timeout, because nothing else provides one

New: `integration-tests/src/test/resources/junit-platform.properties`

```
junit.jupiter.execution.timeout.testable.method.default = 8m
junit.jupiter.execution.timeout.thread.mode.default = SEPARATE_THREAD
```

PR #934's commit message describes exactly this and **its diff contains neither** — verified with
`git show 44f2c25d`. Without it the watchdog's outcome is "30 minutes still burned, now with
evidence". With it, a stall becomes a failing test at 8 minutes.

Eight minutes, not five: a passing `test()` invocation runs `testDynamic` then `testStartup` in one
method, and `startupTimeoutMs` alone is 240 s, so five minutes risks failing green tests on slow
lanes. Eight sits above that and below `release.yml`'s 15-minute integration job (`:271`).
`SEPARATE_THREAD` is required because the default mode interrupts the test thread, and a thread
blocked in socket I/O ignores an interrupt.

Ordering: watchdog captures at 6:00 and 6:30, timeout fails the test at 8:00.

This mode change affects every integration test, so task 8's full-suite run is what qualifies it.

## Task 6 — wire the registry into the launch sites

Modify: `integration-tests/src/test/java/tests/RuntimeTest.java`

- `@ExtendWith(StallWatchdog.class)` on the class; `@ExtendWith` is `@Inherited` and all eight
  subclasses pick it up.
- Register at launch, set the pid where `ready:` is parsed. Corrected line numbers, all verified:

  | site | launch | pid parsed |
  |---|---|---|
  | `testDynamic` raw path | `:348` | `:367` |
  | `testDynamicOneliner` raw path | `:540` | `:559` |
  | `testStartup` raw path | `:745` | `:765` (in the `readyAwareCompletion` decorator, not a reader thread) |
  | `TestApp` | constructor, `:1067` caller | `:937` |

  `TestApp.pid` is a private `int` whose only accessor `getPid()` (`:1007`) blocks up to 30 s, so the
  hook goes at `:937` directly.
- No echo-flag changes anywhere: the SIGQUIT fallback that needed them is gone.

Modify: `integration-tests/src/test/java/tests/Issue888RuntimeHardeningIntegrationTest.java` —
register the target built at `:172-189`; the pid is parsed by the *caller* at `:83-85`, so
`startTarget()` must return the handle alongside the process rather than a bare `Process`.

Client-JVM spawns (`RuntimeTest.java:1109, :1255, :1380, :1493`) are registered too where a handle
can be threaded without restructuring; where it cannot, the omission is recorded in the commit
message rather than left silent. `:254` (`hasJaxbProbeDescriptorSupport`) stays unregistered — it is
bounded at 10 s and cannot stall a job.

## Task 7 — tests

New: `integration-tests/src/test/java/tests/harness/StallCaptureTest.java`,
`integration-tests/src/test/java/tests/harness/StallWatchdogTest.java`

Driven through the in-memory seam, scoped with `@StallTimeout`, output under `@TempDir`.

1. **Captures the test JVM.** Park a distinctively named thread; assert banner, label, thread name
   and stack all appear.
2. **Captures a target JVM.** Launch a JVM that blocks until told to exit (not "short-lived" — a
   target that exits mid-attach makes jcmd nondeterministic), launched from the same JDK whose
   `jcmd` is used. Assert a `Thread.print` section for its pid appears. Degrade with an assumption
   rather than a failure if attach is unavailable in the environment.
3. **Degrades rather than hangs.** A bogus pid: capture returns inside its budget, records the
   failure inline, still contains the test-JVM dump.
4. **Budget is shared.** Several unreachable targets: capture returns in roughly the budget, not
   budget × N, and names what it skipped.
5. **Silent when green.** A fast test with the watchdog armed writes nothing into the `@TempDir`
   output dir.
6. **Fires end to end, with a target.** `@StallTimeout(millis=1500)` on a test that registers a real
   blocking target and then sleeps ~3 s; assert the dump names both the sleeping thread and the
   target's `Thread.print` section. This replaces revision 1's throwaway "add a test, run it, delete
   it" verification step, which produced evidence nobody could re-verify.
7. **Two frames.** A test that stays parked past the 30 s gap produces two dump files whose thread
   sections differ in elapsed time — the frame-two mechanism is otherwise never exercised.
   Uses a shortened inter-frame gap via the same annotation so it runs in seconds.

## Task 8 — verification

1. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessApply`, commit any formatting change.
2. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:compileTestJava`.
3. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test --tests 'tests.harness.*'`
   — note this also runs the pre-existing `CompletionTest` and `OutputPumpTest`, and still triggers
   the full dist/probe build chain (`build.gradle:167-172`), so it is not a cheap check.
4. Full suite as CI runs it:
   `GRADLE_USER_HOME=$(pwd)/.gradle-user TEST_JAVA_HOME=<jdk> ./gradlew -Pintegration -PCI :integration-tests:test`.
   This is the gate for task 5's `SEPARATE_THREAD` change. Record the duration and confirm no
   watchdog file is produced. Docker-tagged tests are included whenever a Docker host is reachable
   (`build.gradle:176-241`), so hold Docker state constant between baseline and after.
5. Confirm `build/reports/stall-dumps/` is inside the upload glob by listing what a run leaves in
   `integration-tests/build/reports/`.

## Order

1 → 2 → 3 → 7 (tests for 1–3) → 6 → 4 → 5 → 8.


## Revision 3 — corrections found by adversarial review of the implementation

Two defects would have shipped silently, and both were confirmed by direct measurement rather than
argument:

1. **The per-test timeout was never committed.** `.gitignore`'s bare `junit*` pattern matches
   `integration-tests/src/test/resources/junit-platform.properties` at any depth, so the file existed
   locally, passed every local run, and was absent from the commit. `.gitignore` now carries a
   negation, placed *after* the pattern it overrides, since the last matching pattern wins.
2. **Test-JVM stacks were truncated to eight frames.** `ThreadInfo.toString()` caps output at
   `MAX_FRAMES = 8`; a 44-frame stack rendered as 8. The dump is now rendered frame by frame with
   locked monitors and synchronizers, and a test asserts the rendered depth exceeds eight.

Also corrected: the stdout echo was the one unbounded wait in a class that bounds everything else,
and it ran on one of only two scheduler threads — it now runs on a throwaway thread with a bounded
join. The registry is pruned on disarm, so a late stall does not spend its capture budget on
`jcmd` calls against processes from earlier classes. Dump filenames are truncated from the tail, not
the head, because a parameterised unique id is identical up to its invocation index, and carry a
sequence number so two teardown stalls in one class cannot collide on one path. The derived deadline
is clamped below the per-test timeout, since `btrace.test.timeoutMs` is an operator knob that could
otherwise push the watchdog past it. The four btrace client JVM launch sites are registered — the
client is the side the surviving issue-932 hypothesis implicates. `budgetIsSharedAcrossTargets`
passed against a per-target budget and now does not; `capturesTargetJvmThreads` would have failed a
build rather than skipped when `jcmd` cannot attach on a loaded runner.
