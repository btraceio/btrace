# Issue #932 — implementation plan: catch the target-JVM wedge, fix the product defect

Issue: https://github.com/btraceio/btrace/issues/932
Branch: `fix/932` (currently @ `c8063580`, based **before** the two diagnostics PRs)
Plan date: 2026-09-26

## Situation

`PreparedModeAuthenticationFunctionalTest` intermittently hangs on its V2/V2 parameterizations:
silent (no assertion, no `PASSED` line), consumes the 30-minute job budget, leaves ~7 orphaned
JVMs. Observed only on Linux CI (`test (17.0.19-tem)`); 14 local macOS attempts found nothing.

**Already merged on `origin/develop`** (NOT in this branch — it is based on a pre-merge commit):

- **#934** (`fba8612c`): bounded `TestApp.stop()` (30 s + `destroyForcibly()` + 10 s), daemon +
  named probe-submission threads, `sock.shutdownInput()` in `Client.close()`.
- **#935** (`5112dad8`): `StallWatchdog` (arms via `@ExtendWith` on `RuntimeTest`; at 6 min writes
  two frames 30 s apart — test-JVM stacks + `jcmd Thread.print -l` of every live target — to
  `integration-tests/build/reports/stall-dumps/`, then closes the stalled test's registered
  targets), 8-min per-test timeout (`junit-platform.properties`, `SEPARATE_THREAD`), `.gitignore`
  negation `!**/junit-platform.properties` after the bare `junit*` pattern that silently swallowed
  it in #934.

**Refuted hypotheses** (do not revisit):
1. Non-daemon executor threads holding the worker JVM — refuted: CI log has no `PASSED` line, so
   the test method never returned.
2. `Client.close()` deadlocking against its blocked reader via `NioSocketImpl` — refuted by
   measurement (JDK 11/17/24/26, blocking + non-blocking descriptor): `close()` returns in 0–1 ms;
   the closer never waits on the reader.

**Surviving chain** (issue's conclusion):
target JVM wedges after probe submission → `started.get(20, SECONDS)` (or `submission.get(10s)`)
times out → the pending `TimeoutException` is lost because the enclosing `finally` runs
`app.stop()` → (pre-#934 unbounded) `process.waitFor()` → silence + orphans.

**Open item — the product-side defect**: *what* wedges the target JVM is unidentified. The issue
explicitly rules out a third blind hypothesis: the dump is the arbitrator. Recent develop CI runs
(~9–10 min each) show no visible 30-min hang since the watchdog landed, but that needs a proper
sweep — and note that post-#934/#935 a wedge now surfaces as a *loud* failure (8-min timeout
naming the test, or `TestApp.stop()`'s forced destroy + test failure) whose reports would carry
the watchdog frames. Either shape counts as a recurrence.

## Phase 1 findings (2026-09-26 sweep — COMPLETE)

Swept: 231 pre-merge suspect runs (cancelled/failure, June 1–July 30), 162 post-merge runs
(1,228 jobs, 434 test-matrix jobs, July 30–Sep 26), via `gh api` job-level durations and log
grep. Results materially narrow the issue:

1. **Five genuine silent stalls, not two** — July 14 (develop push, the #895 merge day itself),
   July 19, July 20, July 28 ×2. All reproduce the exact signature: last output
   `Successfully started BTrace probe: OnTimerArgTest.java`, then silence to the 30-min cancel,
   5–7 orphaned JVMs. Logs verified job-by-job.
2. **All five inside `PreparedModeAuthenticationFunctionalTest`** — three distinct methods
   (V2/V2 supplied-token, V2/V2 no-token, incorrect-token round trip). The class landed 2026-07-14
   (#895); every stall is within its first two weeks. The issue's "V2/V2 parameterization" framing
   is real but incomplete — the incorrect-token test stalls too.
3. **All five on the `test (17.0.19-tem)` tier.** Other tiers ran the same class in the same
   window and never stalled. 17.0.19-tem has been in the matrix since 2026-05-03 (#832), so it is
   not the introduction of the tier but a real, unexplained tier correlation.
4. **The July 12 near-miss is a different bug**: `ExternalTypeAdapterIntegrationTest` passed;
   `JBangAttachDockerTest` hung waiting on Docker (30-min cancel, 7 orphans). Same failure shape,
   different mechanism — and it still fails loudly post-merge (seen 2026-08-03,
   `ConditionTimeoutException` at `JBangAttachDockerTest.java:61`). Do not conflate the two.
5. **Zero recurrences post-merge**: 60 runs of the 17.0.19-tem tier since July 30, max duration
   344 s, median 288 s — same profile as the other tiers, no `+30s per wedged target` inflation
   (pre-merge events had 5–7 orphans each, i.e. multiple wedged targets per run). The wedge is not
   recurring at its pre-merge rate (~6% of the tier's runs, 5/~85 in the window).
6. **NEW: the post-#934 machinery would not catch a resurrected exit-path wedge.** Code walk:
   if the target wedges *while exiting* (agent shutdown hook `Client.java:352-370` →
   `runtime.handleExit(0)` → `onExit` → `cleanupTransformers`/`retransformLoaded`,
   `Client.java:291`), the probe round trip has already completed, the test method's own waits
   return normally, and the enclosing `finally { app.stop(); }` hits the wedged target:
   `destroyForcibly()` at 30 s **bypasses JVM shutdown hooks entirely**
   (`RuntimeTest.java:473` comment) — the test passes at +30 s, the watchdog (6 min) and the
   JUnit timeout (8 min) never fire because the method finished. The pre-merge stall became
   visible only because `stop()` then waited forever; post-#934 the same wedge is **silently
   absorbed**, not reported. "Zero recurrences" therefore cannot fully separate *gone* from
   *masked* — only the duration-fingerprint argument (finding 5) leans toward gone.

Implication: the harness needs one more sentinel — capture the wedged target's stack *at the
moment of forced destroy* — which is also the exact evidence Phase 3's signature 4 requires.

## Principles

- No speculative product-code change without a captured dump. No third guess.
- Dump analysis reads evidence against current code (`file:line` refs below); fixes land at the
  layer the dump names.
- All waits in new harness code bounded; new harness code is Java 8 source level (worker JDK 24).
- No commits until changes are fully tested (AGENTS.md).

## Phase 0 — sync and verify the diagnosis machinery (local, ~1 h)

1. Rebase `fix/932` onto `origin/develop` (`1d4f4e1a`). After rebase, verify:
   - `integration-tests/src/test/resources/junit-platform.properties` is committed
     (`git ls-files | grep junit-platform`) — the #934 trap.
   - `.gitignore` keeps `!**/junit-platform.properties` **after** the `junit*` line (last match wins).
   - `tests/harness/StallWatchdog|StallCapture|StallTimeout` present, with their tests.
   - `TestApp.stop()` bounded: `SHUTDOWN_TIMEOUT_SECONDS = 30`, forced + 10 s
     (`RuntimeTest.java:962-1061` on develop).
2. Run the harness tests:
   `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests "tests.harness.*"`
   (redirect Gradle output to a log and filter — do not consume raw).
3. Optional local-only provocation (do not commit): a temporary test parking in a socket read to
   observe the 6-min dump + 8-min timeout + target reaping end-to-end.

## Phase 1 — CI evidence sweep since 2026-07-30 (cheap; forks the plan)

The watchdog has been armed for ~2 months. Sweep all runs after `fba8612c`:

- `continuous.yml` `test (*)` matrix jobs, all branches/PRs: grep job logs for
  `[stall-dump]`, `stall-dumps`, `TimeoutException` naming
  `PreparedModeAuthenticationFunctionalTest`, `Terminating test timed out`, and
  `TestApp`/`process.waitFor` forced-shutdown failures; download
  `integration-test-reports-*` artifacts (upload is `if: always()`, path
  `integration-tests/build/reports/**/*` — covers `stall-dumps/`).
- `release.yml` release-smoke / finalize-tag runs since 2026-07-30 (the issue's release-path
  exposure: a stall there leaves artifacts on Central with no tag).
- `v2-protocol-tests.yml` weekly runs (Sunday 02:00 UTC) — same grep.

Decision:
- **A. any stall artifact or loud timeout on the class found** → Phase 3 immediately (the dumps
  are the diagnosis); skip Phase 2.
- **B. nothing found** → the wedge is rare (or, weakly, mitigated). Proceed to Phase 2, keep CI
  armed, and record the negative result in the issue (frequency estimate: how many runs since
  July 30 — the flake was 2-of-3 on one day, so absence over N runs is real information).

## Phase 2 — exit-wedge observability + targeted reproduction (revised 2026-09-26)

**2a. Exit-wedge sentinel in `TestApp.stop()`** (harness-only change, evidence-first — replaces
the blind overnight loop as the primary step):

- In `RuntimeTest.TestApp.stop()`, when `process.waitFor(30, SECONDS)` is about to
  `destroyForcibly()`, first capture the wedged target: `jcmd <pid> Thread.print -l` (bounded,
  best-effort, output to a temp file then appended to `build/reports/stall-dumps/` with a loud
  `[exit-wedge]` prefix + a stdout marker line). Reuse `StallCapture`'s jcmd plumbing and the
  `TargetRegistry` snapshots; every wait bounded; internal failure → text, never an exception.
- Rationale: this is the *only* observable moment where a target-JVM exit wedge still shows up
  post-#934. The captured frame — expected to show the `Shutdown`/shutdown-hook thread inside
  `handleExit` → `retransformLoaded` (Phase 3, signature 4) — is the dump the issue asked for.
  `destroyForcibly()` still proceeds right after, so the sentinel adds bounded overhead only on
  the already-slow path.
- Harness test: provoke the sentinel with a target that ignores `done` (sleep-loop app), assert
  the marker + dump file appear and teardown stays bounded. Java 8 source level.
- CI: zero workflow change — dumps land under the already-uploaded `build/reports/**` glob.

**2b. Targeted reproduction attempt (secondary, cheap to run alongside)**: pre-merge rate was
~6% of the 17.0.19-tem tier's runs — if the trigger is intrinsic to the class + tier, a loop has
real odds; if it was July's runner-pool environment, it will not fire — either result is
informative.

- Docker `ubuntu-24.04`, 2 CPUs / 7 GB (GitHub linux-runner parity), worker JDK 24,
  `TEST_JAVA_HOME` = Temurin 17.0.19, `./gradlew clean :btrace-dist:build` first.
- Loop `--tests "tests.PreparedModeAuthenticationFunctionalTest"` with a per-iteration
  wall-clock kill at 20 min, preserve `build/` on any non-green iteration, overnight (~50–100
  iterations).
- If single-class loops stay clean, loop the full suite instead (class ordering and pipe/IO
  pressure differ) and vary axes one at a time: stdout volume, CPU oversubscription, disk stalls.
- With 2a in place, even a non-hanging "slow exit" iteration yields the wedge evidence.

## Phase 3 — dump analysis protocol (when evidence is captured)

Two-frame watchdog dumps (stall shape) or exit-wedge sentinel dumps (masked shape) classify
against the same table:

Two frames 30 s apart (watchdog stall shape); for the sentinel (2a) a single frame at the
30 s bound, plus the fact that the target ignored `done` — **identical stacks = wedged, moved =
slow**. Classification table against current code:

Worker (test) JVM — expected shapes:
- Main test thread in `RuntimeTest.TestApp.stop()` → bounded `process.waitFor` with a pending
  `TimeoutException`/assertion from `started.get(20s)` / `submission.get(10s)` — consistent with
  the surviving chain; the **target frame is then the interesting one**.
- Submission thread parked in `Client.commandLoop` → `protocol.read` (`Client.java:1454`,
  V2 `BinaryWireIO.read`, no timeout) — expected and benign post-#934 (daemon thread); confirm it
  is identical across frames and not the test thread.
- Anything else (JUnit timeout thread state, watchdog scheduler) — note it, but the target frame
  arbitrates.

Target JVM — distinct signatures, each mapping to a different fix:
1. **`resources.TestApp.main` absent, JVM alive** → a non-daemon thread holds it: enumerate live
   non-daemon threads in the frame (agent threads are daemon by construction —
   `Main.java:214/393`, `RemoteClient.java:363`, `BTraceRuntimeImplBase:531/1178` — so this
   signature would itself be a finding).
2. **Main thread still in `readLine()` on stdin** (`resources/TestApp.start`) → `done` never
   arrived: writer side (`TestApp.stop()` `PrintWriter`) or a wedged stdin pipe.
3. **Main thread at `t.join(1000)` / "Dangling worker thread"** → worker thread stuck, most
   plausibly in `System.out.println` against a full stdout pipe (worker-JVM `STDOUT Reader` thread
   died → pipe full → instrumented worker blocked mid-handler).
4. **JVM stuck in exit** → `Shutdown` thread / shutdown-hook thread (`Client.java:352-370`) inside
   `runtime.handleExit(0)` → `onExit` (`Client.java:291`) → `cleanupTransformers()` /
   `retransformLoaded()` — retransform blocked while an instrumented thread is pinned in a probe
   handler. Signature: hook thread identical in both frames inside retransform machinery.
   **This is the signature the pre-merge evidence now favors**: it explains a target that received
   `done` and still never exited, multiple wedged targets per run (5–7 orphans — the hook is
   registered per probe), and silence after "probe started" regardless of which prepared-mode
   method was running. It is also exactly the shape #934 masks rather than reports (finding 6),
   which is why sentinel 2a targets it.
5. **Agent `cmdHandler` parked in `protocol.read()`** (`RemoteClient.java:271-370`, daemon) —
   benign on its own; meaningful only paired with 1–4.

Cross-check locked monitors/owners (`Thread.print -l`): a JVM-side lock wait is internal; an I/O
park is external — pair the two frames' evidence, don't infer one JVM from the other.

Deliverable: diagnosis written into the issue with both frames attached. One line, as promised.

## Phase 4 — product fix (contingent; scope chosen by Phase 3)

Constraints: Java 8 source level, Java 11 toolchain, Spotless/Google Java Format, simple-name
imports.

Likely fix surfaces — selected by dump, implemented only then:
- **Signature 4** (agent exit path): make `handleExit` → `cleanupTransformers`/`retransformLoaded`
  interruption-safe and bounded; ensure probe handler dispatch cannot pin retransform
  (e.g. async/timeout-bounded cleanup). Touches `btrace-agent`/`btrace-runtime` — product change,
  requires e2e coverage (AGENTS.md: cross-process behavior needs `integration-tests` coverage).
- **Signature 2/3** (harness): stdout/stdin pump lifecycle in `RuntimeTest.TestApp` — test-only.
- **Signature 1**: whichever non-daemon thread the dump names; likely agent lifecycle — product.
- Regression test: deterministic reproduction of the identified race (latch-pinned interleaving)
  in `integration-tests`; unit/component tests where useful but not as a substitute for the e2e.

Verification: `spotlessCheck` (apply only if formatting intended); `:btrace-dist:build` before
`:integration-tests:test`; full local matrix tier; if agent/loader behavior touched —
`./gradlew clean :btrace-dist:btraceJar` (masked JAR rules, though no class-layout change is
expected).

## Phase 5 — residual-risk closure on the release path

- Confirm `release.yml` smoke job uploads `stall-dumps` `if: always()` and that its
  `timeout-minutes` sits above the 8-min test bound (a stall must fail the smoke job *loudly*
  before the job timeout, with dumps preserved — the issue's Central-artifacts-without-tag
  exposure).
- Post-fix: one clean full-matrix CI run including `test (17.0.19-tem)` + release-smoke dry run.

## First actions on this branch

1. ✅ `git rebase origin/develop` — done, branch at `1d4f4e1a`.
2. ✅ Phase 0 checklist verified: `junit-platform.properties` tracked and on the test classpath,
   `.gitignore` negation after `junit*`, watchdog/capture/timeout classes + tests present, bounded
   `TestApp.stop()` (30 s + 10 s forced), daemon+named executors; `:btrace-dist:build` green;
   harness tests 19/19 green; `PreparedModeAuthenticationFunctionalTest` smoke 6/6 in 51 s.
3. ✅ Phase 1 sweep complete — see findings above.
4. ✅ Phase 2a implemented and verified — the exit-wedge sentinel:
   - `RuntimeTest.TestApp.stop()`: on wedge, captures the target's threads *before*
     `destroyForcibly()` via `StallCapture.captureExitWedge` into
     `build/reports/stall-dumps/exit-wedge-<pid>-<ts>.txt` + a loud `[exit-wedge]` stdout marker;
     best-effort by contract (failure degrades to a marker line, teardown stays bounded).
   - `StallWatchdog.dumpDir()` / `setDumpDirForTesting` made public (shared diagnostic root);
     `StallCapture.captureExitWedge` added next to the watchdog frames.
   - Grace period made test-tunable (`setShutdownTimeoutSecondsForTesting`), restored in
     `finally` per the single-JVM-leak rules.
   - New fixtures/tests: `resources.IgnoreStopApp` (ignores `done`), `tests.ExitWedgeSentinelTest`.
   - Verified: sentinel test green (wedge → dump → bounded kill); harness 19/19;
     `PreparedModeAuthenticationFunctionalTest` 6/6; **full suite 71/71 in 5m45s with zero
     spurious firings**; `spotlessCheck` green.
5. Report the sweep findings to the issue (5 events not 2; class-scoped to #895's first two
   weeks; 17.0.19-tem correlation; JBang de-conflation; masked-vs-gone distinction) — this
   materially updates #932's timeline and gives the July 28 events their missing context.
6. Next: 2b's Linux/17.0.19-tem reproduction loop if desired; monitor CI for `[exit-wedge]`
   and `stall-dumps` — the sentinel now converts any resurrected exit-path wedge into captured
   evidence with the shutdown-hook stack. If it never fires, the issue can be closed as
   "diagnosed to the class/tier window, mitigated, sentinel in place".

## Open questions

- Gone vs masked: post-#934 the exit-path wedge is silently absorbed (finding 6); the duration
  fingerprint (finding 5) leans gone, but only sentinel 2a can close this for good.
- Tier correlation: why 17.0.19-tem only — all other tiers ran the same new class in the same
  window. A 17.0.19-specific retransform/safepoint behavior is the leading candidate but is a
  hypothesis to be settled by 2a/2b evidence, not by reasoning (per the issue's own rule).
- Whether the trigger was July's runner-pool environment (2b's negative result would support
  this).
- Whether `release.yml`'s job timeout interacts correctly with the 8-min bound — Phase 5.