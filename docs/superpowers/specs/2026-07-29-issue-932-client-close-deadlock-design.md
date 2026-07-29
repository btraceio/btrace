# Issue 932 — make an integration-test stall self-diagnosing

Design document.

## Symptom

`PreparedModeAuthenticationFunctionalTest` intermittently stalls on its V2/V2 parameterisations,
consumes the full 30-minute CI budget, reports no assertion failure and no stack trace, and leaves
several orphaned `java` processes behind. Last line before the silence is
`Successfully started BTrace probe`, with no `PASSED` line for the parameterisation.

## Why this design is not a root-cause fix

An earlier revision argued the stall was `Client.close()` deadlocking against its own parked reader,
fixable with `sock.shutdownInput()`. **That was refuted by measurement** and is recorded here so it
is not proposed a third time.

`NioSocketImpl.close()` does not wait for an in-flight reader. It pre-closes the descriptor and
*signals* the blocked thread; the deferred close completes on the reader's own thread. Measured on
macOS across JDK 11/17/24/26, descriptor in both blocking and non-blocking mode:

| action, with a reader parked in an unbounded `read()` | duration | reader observes |
|---|---|---|
| `socket.close()` | 0–1 ms | `SocketException: Socket closed` |
| `socket.getOutputStream().close()` | 0 ms | `SocketException: Socket closed` |
| `socket.shutdownInput()` | 0 ms | `read()` returns `-1` |

`Client.close()` is bounded and cannot be a 25-minute stall.

The supporting evidence was also misread. `log.info("Successfully started BTrace probe")` is emitted
at `Client.java:1284`, inside `submit`'s wrapping listener, *before* `listener.onCommand(cmd)` at
`Client.java:1292` reaches the test's `handleStatus`. It proves only that the worker reached line
1284 — not that `started.get(20, SECONDS)` returned. Every elimination argument rested on that
misreading.

What the evidence does support: `started.get(20, SECONDS)` may have thrown `TimeoutException`, and
that exception was then *lost*, because the enclosing `finally` runs `TestApp.stop()` →
`process.waitFor()` with no bound (`RuntimeTest.java:1006`). A pending exception whose `finally`
never returns is never delivered to JUnit. That reproduces the observed shape exactly — no assertion
failure, no `PASSED` line, orphaned JVMs — and means the 25 minutes were spent waiting on a target
JVM that never exited.

The remaining product-side defect is whatever wedged the **target** JVM. Nothing in the available
evidence identifies it; two mechanisms have now been proposed and refuted, both by reasoning
backwards from a log shape that several mechanisms explain equally well. The next attempt starts
from a thread dump, which is what this design delivers.

## What PR #934 does and does not provide

Verified against `git show 44f2c25d`: the branch bounds `TestApp.stop()`, makes the submit executors
daemon threads, and adds a speculative `shutdownInput()` in `Client.close()`. Its commit message also
describes a five-minute per-test timeout in `SEPARATE_THREAD` mode — **that annotation is not in the
diff**, and the branch carries no `junit-platform.properties`. This design therefore assumes no
per-test timeout exists, and must not rely on one to bound the capture.

## Goal

> When an integration test stalls, the CI artifacts and (where available) the job log name the
> blocked thread in the test JVM and in every live target JVM, without anyone re-running anything.

## Design

A **stall watchdog** in the integration-test harness. Evidence only: it does not kill anything and
does not fail a test.

**1. `StallWatchdog`, a JUnit 5 extension.** Registered with `@ExtendWith` on the abstract
`RuntimeTest`; JUnit searches the superclass hierarchy, so all eight subclasses inherit it with no
boilerplate. `BeforeAllCallback` arms first (so a stall in `classSetup()` is covered), `beforeEach`
re-arms per invocation, `afterEach`/`afterAll` disarm. Disarm uses `ScheduledFuture.cancel(false)`
plus an `AtomicBoolean` so an in-flight capture is never attributed to the next test and `afterEach`
never blocks waiting for one.

**2. The deadline is derived, not hard-coded.** `RuntimeTest` already allows a target up to
`startupTimeoutMs = timeout * 4` (`RuntimeTest.java:784`), i.e. 240 s at the default
`btrace.test.timeoutMs = 60000`. A four-minute watchdog would fire on legitimately slow green tests.
The deadline is `btrace.test.timeoutMs * 6` (six minutes by default), overridable with
`-Pbtrace.test.stallTimeoutMs`. **`-P`, not `-D`**: `integration-tests/build.gradle:274` forwards
only Gradle project properties beginning with `btrace.` into the test JVM.

**3. Two captures, thirty seconds apart, then stop.** A single frame cannot distinguish "wedged"
from "slow" — a thread parked in `SocketInputStream.read` looks identical either way. The diff
between two frames is the evidence. Capturing twice and stopping bounds the log cost on a job that
is already lost.

**4. Each capture contains:** a banner with the test's unique id and elapsed time; the test JVM's
threads via `ThreadMXBean.dumpAllThreads(true, true)` (lock-owner and monitor information, which
`Thread.getAllStackTraces()` omits and a deadlock diagnosis needs); and for every live registered
target, `jcmd <pid> Thread.print -l`.

**5. File first, stdout second.** The file is the reliable sink: `if: always()` upload steps do run
after a `timeout-minutes` cancellation, whereas a cancelled job tears down the worker→daemon→console
pipeline, and the release path redirects all test output into a file it only summarises *after* the
command exits (`scripts/run-release-gate.sh:52`). Dumps are written to
`integration-tests/build/reports/stall-dumps/`, which the existing `if: always()` glob
`integration-tests/build/reports/**/*` (`continuous.yml:171`) already uploads — no workflow change.
The stdout copy is best-effort and written second, because `System.out` is a shared synchronised
`PrintStream` that a stalled pipeline can itself block on. Every line carries a `[stall-dump] `
prefix so one `grep` recovers a capture interleaved with the `[traced app]` reader threads.
Filenames derive from the sanitised JUnit unique id, not `<class>.<method>` — the motivating test is
a `@ParameterizedTest` whose four rows share one method name.

**6. `jcmd` is treated as untrustworthy.** Its output goes to a file via `redirectOutput`, never to a
pipe: `Thread.print -l` on an instrumented target exceeds the 64 KB pipe buffer, and a blocked jcmd
would then be killed by the bound having produced nothing — losing precisely the dump we want. The
call is bounded and `destroyForcibly`-ed on overrun. If it fails or times out — the jammed
Attach Listener case, which is plausible here — the watchdog falls back to `kill -QUIT`, which the
VM's Signal Dispatcher handles independently of the attach mechanism. That dump goes to the target's
stdout, which `RuntimeTest` echoes only when `debug` is set (`RuntimeTest.java:940`), so requesting a
capture also flips a flag that makes the reader threads echo unconditionally.

**7. Target registry.** A static registry holds the `Process` plus a lazily-resolved pid, registered
at launch rather than at `ready:`, so a target that dies before reporting a pid is still known.
Entries with `!process.isAlive()` are skipped at capture time, which removes the need for
deregistration hooks and covers the leak at `BTraceFunctionalTests.java:536`, where a `TestApp` is
launched and never stopped. Four launch sites register: `TestApp`, the three raw `ProcessBuilder`
paths in `RuntimeTest` (`:348`, `:540`, `:745`), and
`Issue888RuntimeHardeningIntegrationTest.java:184`. The whole capture shares one overall time budget
rather than 10 s per target, so accumulated stale entries cannot stretch it.

**Java 8 source level.** `integration-tests` compiles tests with `sourceCompatibility = 8`, but
without `options.release`, so javac 24 happily compiles JDK 9+ APIs that then fail on a JDK 8
worker. No `var`, no `List.of`, no `ProcessHandle`/`Process.pid()`. Adding
`compileTestJava { options.release = 8 }` to enforce this is attempted; if existing test code
already violates it the change is dropped and the violation reported rather than fixed here.

## Testing

The extension's own tests drive it through an in-memory seam rather than scraping stdout, and take
their deadline from a `@StallTimeout` annotation read from the `ExtensionContext` rather than a
system property — with no `forkEvery` configured anywhere in the build, one worker JVM runs all ten
test classes, so a system property set by a test leaks into every class that follows it.

1. **Captures the test JVM.** A parked thread with a recognisable name; assert the capture contains
   the banner, the unique id, and that thread's stack.
2. **Captures a target JVM.** Launch a target, assert the capture contains a `Thread.print` section
   for its pid and a thread name only the target has.
3. **Degrades rather than hangs.** With a bogus registered pid, the capture completes within its
   budget, reports the failure inline, and still contains the test-JVM dump.
4. **Stays silent when green.** A fast test writes no dump; the assertion targets a `@TempDir`-rooted
   output directory so a file left by an earlier Gradle invocation cannot make it pass or fail
   spuriously.

## Out of scope

- Product code. Nothing in `btrace-client`, `btrace-core`, `btrace-agent` changes.
- `JBangAttachDockerTest` and `Issue884PublishedFatAgentE2ETest` do not extend `RuntimeTest` and are
  not covered. They must not be covered by registering the extension globally: their targets run
  inside containers, and a container pid handed to a host `jcmd` would attach to an unrelated host
  process.
- Bounding `TestApp.stop()`, daemon submit executors, and the missing per-test timeout — PR #934's
  territory, whatever that PR ends up containing.
- Killing or failing a stalled test.
