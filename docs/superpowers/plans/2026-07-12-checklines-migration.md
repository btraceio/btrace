# Migrate Remaining `checkLines` Call Sites to `Completion` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the remaining `int checkLines` line-count waits in `integration-tests` by migrating every call site to content-based `Completion` (`untilContains`/`untilMatches`), matching the pattern already used by `ExternalTypeAdapterIntegrationTest`.

**Architecture:** `docs/superpowers/plans/2026-07-12-test-harness-completion-conditions.md` (already merged) built the `Completion`/`OutputPump` abstraction and migrated exactly one caller. It explicitly kept the `int checkLines` overloads for backward compatibility and scoped full migration out as an optional Task 7. This plan finishes that migration: every remaining call site gets a `Completion` built from the same content markers its `ResultValidator` already asserts on, the now-dead `int` overloads and `runBTrace(String[], int, ...)` are deleted, and `testStartup`'s separate hand-rolled `CountDownLatch(checkLines)` mechanism (used by `testTraceAll`, `testThreadStart(false)`, `launchAgent_manifestLibs`) is rewired onto `OutputPump`/`Completion` the same way `attach()` was in the original plan's Task 3.

**Tech Stack:** Java, JUnit 5, `tests.harness.Completion`, `tests.harness.OutputPump` (both already implemented in `integration-tests/src/test/java/tests/harness/`).

## Global Constraints

- Every migrated call site's `Completion` must be built from markers **already asserted on** by that test's `ResultValidator` — do not invent new markers. Where the validator has no content assertion (`testOSMBean`, `testJfr`) or asserts on something the `Completion` mechanism cannot see (`testExtensionCloseCalledOnError`'s exit-code check, JFR-file checks), use the bespoke condition specified in this plan's Task 6, not a guess.
- `Completion.untilContains(...)` waits for markers to appear "in any order" across the accumulated stdout — safe for markers that are genuinely independent, but do NOT use it where a test's negative assertion depends on stopping *before* an unrelated marker could appear (see Task 5's note on `testProbeArgs`).
- Do not touch `ExternalTypeAdapterIntegrationTest.java` — already migrated, serves as the reference pattern.
- Do not touch `JBangAttachDockerTest.java` — does not use `RuntimeTest`/`Completion` at all.
- After each task, run the affected test class only (not the full suite) via `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.<ClassName>'` and confirm the class passes with **zero** `[harness] timed out` lines in the output for tests that pass. A `[harness] timed out` line followed by a PASS is itself a bug this plan exists to remove — treat it as a task failure, not a pre-existing quirk to tolerate.
- Run `./gradlew spotlessApply` and commit formatting changes before any push (standing repo rule).

---

### Task 1: Delete the dead `runBTrace(String[], int, ...)` overload

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java` (around line 1167, the `int checkLines` overload of `runBTrace`)

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing — pure deletion. The `Completion`-based `runBTrace` overload (around line 1170) remains untouched.

- [ ] **Step 1: Confirm there are truly zero callers**

Run: `grep -rn "runBTrace(" integration-tests/src/test/java | grep -v "RuntimeTest.java"`
Expected: no output (already confirmed during inventory, re-verify before deleting).

- [ ] **Step 2: Delete the `int checkLines` overload**

Remove the `runBTrace(String[] args, int checkLines, StringBuilder stdout, StringBuilder stderr)` method body entirely (it should be a thin wrapper delegating to the `Completion`-based overload — delete the whole method, not just its body).

- [ ] **Step 3: Compile**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:compileTestJava`
Expected: BUILD SUCCESSFUL (proves no hidden caller existed).

- [ ] **Step 4: Commit**

```bash
git add integration-tests/src/test/java/tests/RuntimeTest.java
git commit -m "test(harness): delete dead runBTrace(String[], int, ...) overload"
```

---

### Task 2: Migrate `ClassFileApiTests.java`

**Files:**
- Modify: `integration-tests/src/test/java/tests/ClassFileApiTests.java` (4 call sites: `testEntry` ~L58, `testReturnValue` ~L75, `testDuration` ~L92, `testFeatureSmoke` ~L112)

**Interfaces:**
- Consumes: `RuntimeTest.testDynamic(String testApp, String testScript, Completion completion, ResultValidator v)` (already exists, L257-261), `Completion.untilContains(String...)`.
- Produces: nothing new for later tasks — this file has no other dependents.

- [ ] **Step 1: Add the `Completion` import**

Add `import tests.harness.Completion;` to `ClassFileApiTests.java`'s import block if not already present (check first — it may already be imported for another reason; if absent, add it).

- [ ] **Step 2: Replace the 3 single-marker call sites**

| Test | Old 3rd arg | New 3rd arg |
|---|---|---|
| `testEntry` | `5` | `Completion.untilContains("Math.abs entered: abs")` |
| `testReturnValue` | `5` | `Completion.untilContains("Math.abs returned: ")` |
| `testDuration` | `5` | `Completion.untilContains("Math.max duration: ")` |

Each is a `testDynamic(testApp, testScript, <arg>, validator)` call — replace only the third argument in place, keep everything else identical.

- [ ] **Step 3: Replace `testFeatureSmoke`'s `checkLines=100` with all 11 markers**

Read the test method body to get the exact 11 marker strings the `assertContainsAll` call already checks (they are literal substrings passed to that helper). Replace the `100` argument with:

```java
Completion.untilContains(
    "cfapi <marker-1>",
    "cfapi <marker-2>",
    // ... all 11 markers, copied verbatim from the assertContainsAll(...) call below in the
    // same method, in the same order they appear there
    "cfapi <marker-11>")
```

Do not paraphrase the markers — copy the exact strings used in the existing `assertContainsAll(...)` call so the wait condition and the assertion can never drift apart.

- [ ] **Step 4: Run the migrated class**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.ClassFileApiTests'`
Expected: all 4 tests PASS (note: `testFeatureSmoke` only runs on JDK26+; if the local/CI JDK is older it will be skipped, which is fine — do not force it).

- [ ] **Step 5: Commit**

```bash
git add integration-tests/src/test/java/tests/ClassFileApiTests.java
git commit -m "test(harness): migrate ClassFileApiTests off checkLines to Completion.untilContains"
```

---

### Task 3: Migrate `ExtensionLifecycleIntegrationTest.java`

**Files:**
- Modify: `integration-tests/src/test/java/tests/ExtensionLifecycleIntegrationTest.java` (3 call sites: `testExtensionMethodCalled` ~L57, `testExtensionCloseCalledOnError` ~L78, `testMultipleExtensionsAllClosed` ~L101)

**Interfaces:**
- Consumes: same `testDynamic(..., Completion, ResultValidator)` overload as Task 2.
- Produces: nothing new for later tasks.

- [ ] **Step 1: Migrate `testExtensionMethodCalled`**

Replace its `checkLines=10` argument with:
```java
Completion.untilContains("LIFECYCLE: extension method called")
```

- [ ] **Step 2: Migrate `testMultipleExtensionsAllClosed`**

Replace its `checkLines=10` argument with:
```java
Completion.untilContains(
    "LIFECYCLE: printer extension called", "LIFECYCLE: metrics extension called")
```
(This is a textbook multi-marker case — mirrors `ExternalTypeAdapterIntegrationTest`'s already-migrated pattern exactly.)

- [ ] **Step 3: Migrate `testExtensionCloseCalledOnError` — bespoke condition, do NOT use plain `untilContains` alone**

This test asserts `retcode == 1` in addition to the two markers `"LIFECYCLE: extension method called"` and `"Triggering error exit"`. A `Completion` cannot observe the process exit code (it only sees stdout/stderr lines), so `untilContains` on the two markers is still the right wait condition — the exit-code assertion happens after `testDynamic` returns, unaffected by which `Completion` was used. Replace the `checkLines=10` argument with:
```java
Completion.untilContains("LIFECYCLE: extension method called", "Triggering error exit")
```
This is safe specifically because both markers are printed by the probe itself before the error-triggered process exit, so waiting for both before checking `retcode` is correct and no longer racy on unrelated framework output.

- [ ] **Step 4: Add the `Completion` import if missing, run the migrated class**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.ExtensionLifecycleIntegrationTest'`
Expected: all 3 tests PASS, no `[harness] timed out` lines.

- [ ] **Step 5: Commit**

```bash
git add integration-tests/src/test/java/tests/ExtensionLifecycleIntegrationTest.java
git commit -m "test(harness): migrate ExtensionLifecycleIntegrationTest off checkLines to Completion.untilContains"
```

---

### Task 4: Migrate `ManifestLibsTests.java`'s `dynamicAttach_manifestLibs`

**Files:**
- Modify: `integration-tests/src/test/java/tests/ManifestLibsTests.java` (~L57)

**Interfaces:**
- Consumes: same `testDynamic(..., Completion, ResultValidator)` overload.

- [ ] **Step 1: Migrate the call site**

`dynamicAttach_manifestLibs` reuses `btrace/OnTimerArgTest.java` and asserts `contains("timer")`. Replace its `checkLines=10` argument with:
```java
Completion.untilContains("timer")
```

- [ ] **Step 2: Run and commit**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.ManifestLibsTests.dynamicAttach_manifestLibs'`
Expected: PASS.

```bash
git add integration-tests/src/test/java/tests/ManifestLibsTests.java
git commit -m "test(harness): migrate ManifestLibsTests off checkLines to Completion.untilContains"
```

---

### Task 5: Migrate `BTraceFunctionalTests.java` — straightforward marker-based tests

**Files:**
- Modify: `integration-tests/src/test/java/tests/BTraceFunctionalTests.java`

**Interfaces:**
- Consumes: same `testDynamic`/`testDynamicOneliner` `Completion` overloads.

This task covers every call site in this file **except** `testOSMBean`, `testJfr`, `testOnMethodTrackRetransform`, and `testOnelinerCompilationError` — those four have no clean content-marker mapping and are handled separately in Task 6.

- [ ] **Step 1: Replace the single/multi-marker call sites per this table**

| Test | Old `checkLines` | New `Completion` expression |
|---|---|---|
| `testOnProbe` | `5` | `Completion.untilContains("[this, noargs]", "[this, args]")` |
| `testOnTimer` | `10` | `Completion.untilContains("vm version", "vm starttime", "timer")` |
| `testOnTimerArg` | `10` | `Completion.untilContains("vm version", "vm starttime", "timer")` |
| `testOnExit` | `5` | `Completion.untilContains("onexit")` |
| `testOnMethod` | `14` | `Completion.untilContains("[this, noargs]", "[this, args]", "{xxx}", "heap:init", "prop: test", "fieldSet:", "fieldGet:")` — copy the exact marker substrings from this test's existing `assertTrue(stdout.contains(...))` calls; do not paraphrase |
| `testOnelinerRuntime` | `30` | `Completion.untilContains("callB", "Hello World")` |
| `testExtensionLifecycleClose` | `10` | `Completion.untilContains("extension close: btrace-utils")` — this marker is framework/teardown output, not probe output, but it is exactly what the validator asserts on, so it is still the correct (and only) wait target |
| `testOnMethodLevel` | `5` | `Completion.untilContains("[this, noargs]", "[this, args]", "{xxx}")` |
| `testOnMethodReturn` | `5` | `Completion.untilContains("[this, anytype(void)]", "[this, void]", "[this, 2]")` |
| `testOnMethodSubclass` | `5` | `Completion.untilContains("print:class resources.Main")` — verify the exact marker text against the existing assertion in this test before typing it in; the inventory noted the marker text is approximate, so read the assertion directly rather than trusting this plan's paraphrase |
| `testProbeArgs` | `5` | `Completion.untilContains("arg#=", "arg1=", "arg2=val2")` — keep the existing negative assertion (`assertFalse(contains("matching probe"))`) unchanged; `untilContains` only affects when the harness stops *waiting*, not what it asserts afterward, so the negative check still runs against whatever accumulated by the time these 3 markers appeared |
| `testPerfCounter` | `5` | `Completion.untilContains("matching probe")` |
| `testReflection` | `5` | copy the exact 2 marker strings from this test's 2 `assertTrue(contains(...))` calls into `Completion.untilContains(marker1, marker2)` |
| `testThreadStart(dynamic=true)` | `10` | `Completion.untilContains("starting testThread")` |
| `testMetrics` | `20` | copy all distinct marker strings this test's 7 `assertTrue(contains(...))` calls check (header, report banner, Count/Mean/P50/P95/P99 labels) into a single `Completion.untilContains(...)` call |
| `testOnelinerMethodEntry` | `10` | `Completion.untilContains("callA")` |
| `testOnelinerWithArguments` | `10` | `Completion.untilContains("[1, Hello World]")` |
| `testOnelinerWithReturn` | `10` | `Completion.untilContains("callB")` |
| `testOnelinerWithRegexClassMatch` | `10` | `Completion.untilContains("callA")` |
| `testOnelinerStack` | `10` | `Completion.untilContains("resources.Main.callA")` — verify exact marker against this test's actual assertion (`contains("resources.Main.callA")` or `contains("resources.Main")`); use whichever the assertion literally checks |
| `flatDslOpsWork` | `5` | `Completion.untilContains("flat-dsl:")` |

For each row: find the call site (`testDynamic(...)` or `testDynamicOneliner(...)`), replace only the `checkLines` int argument with the `Completion` expression, and switch the call to the `Completion`-taking overload (same method name, different overload — no other change needed). Leave the `ResultValidator` bodies untouched.

- [ ] **Step 2: Add the `Completion` import if missing**

- [ ] **Step 3: Run the migrated tests**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.BTraceFunctionalTests'`
Expected: every test migrated in this task PASSES. (`testOSMBean`, `testJfr`, `testOnMethodTrackRetransform`, `testOnelinerCompilationError` are still on `checkLines` at this point — Task 6 handles those — so don't be alarmed if they still show `[harness] timed out` noise here.)

- [ ] **Step 4: Commit**

```bash
git add integration-tests/src/test/java/tests/BTraceFunctionalTests.java
git commit -m "test(harness): migrate most of BTraceFunctionalTests off checkLines to Completion.untilContains"
```

---

### Task 6: Migrate `BTraceFunctionalTests.java` — tests with no clean content marker

**Files:**
- Modify: `integration-tests/src/test/java/tests/BTraceFunctionalTests.java` (`testOSMBean`, `testJfr`, `testOnMethodTrackRetransform`, `testOnelinerCompilationError`)

**Interfaces:**
- Consumes: `Completion.untilContains`, `Completion.lines` (kept deliberately for one case below — see Step 1), `Completion.untilMatches`.

These four don't fit the "wait for what you assert on" pattern cleanly. Handle each on its own terms rather than forcing `untilContains`:

- [ ] **Step 1: `testOSMBean` — validator has no content assertion at all**

The validator only checks `assertFalse(stdout.contains("FAILED"))` and `assertTrue(stderr.isEmpty())` — there is no positive marker to wait for, because the script (`btrace/OSMBeanTest.java`) prints exactly one line on success and two on failure, and the test doesn't know in advance which it'll get. Use:
```java
Completion.untilMatches(Pattern.compile(".+"), 1)
```
This waits for exactly one non-empty output line (success or failure path both produce at least one), replacing the old `checkLines=2` which was tuned to the failure path only. Add `import java.util.regex.Pattern;` if not already present.

- [ ] **Step 2: `testJfr` — assertion is against a JFR binary file, not stdout**

The validator's real check is `assertNotNull(jfrFile)` plus JFR event-type/value inspection via `RecordingFile` — stdout content is irrelevant to pass/fail. The probe (`btrace/JfrTest.java`) prints `"Main.callA"` exactly once. Use:
```java
Completion.untilContains("Main.callA")
```
This is the one line the probe reliably produces, giving the harness a real signal that the probe fired (and therefore likely populated the JFR recording) instead of waiting for 30 arbitrary lines of framework noise. Do not attempt to make the `Completion` aware of the JFR file itself — that is out of scope for this interface.

- [ ] **Step 3: `testOnMethodTrackRetransform` — marker is a framework/debug log line**

The validator asserts `contains("Going to retransform class")`, which the BTrace framework itself logs (not the probe). This is still valid `Completion` usage — the interface waits on any stdout content, framework or probe — so:
```java
Completion.untilContains("Going to retransform class")
```
Replace the old `checkLines=2`.

- [ ] **Step 4: `testOnelinerCompilationError` — script is expected to fail compilation, not produce output**

The oneliner is intentionally invalid (`resources.Main::callB @invalid { print }`), and the validator checks `!stderr.isEmpty() || contains("error") || contains("Error")`. Waiting for stdout content here is backwards — a compile error may produce zero stdout lines and only stderr. Use `Completion`'s stderr hook instead of a stdout-only wait:
```java
new Completion() {
  @Override
  public boolean onStdout(String line) {
    return line.toLowerCase(Locale.ROOT).contains("error");
  }

  @Override
  public boolean onStderr(String line) {
    return true;
  }

  @Override
  public String describe() {
    return "a compile error on stdout or any stderr line";
  }
}
```
Add `import java.util.Locale;` if not already present. This releases the wait the moment either signal (a stdout error message OR any stderr output) appears, instead of waiting for a fixed stdout line count that a compile-failure path may never reach.

- [ ] **Step 5: Run all four migrated tests**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.BTraceFunctionalTests.testOSMBean' --tests 'tests.BTraceFunctionalTests.testJfr' --tests 'tests.BTraceFunctionalTests.testOnMethodTrackRetransform' --tests 'tests.BTraceFunctionalTests.testOnelinerCompilationError'`
Expected: all 4 PASS, no `[harness] timed out` lines.

- [ ] **Step 6: Commit**

```bash
git add integration-tests/src/test/java/tests/BTraceFunctionalTests.java
git commit -m "test(harness): migrate remaining BTraceFunctionalTests edge cases off checkLines"
```

---

### Task 7: Migrate `testStartup`'s hand-rolled `CountDownLatch(checkLines)` to `OutputPump`/`Completion`

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java` (`testStartup`, ~L658-838; its caller `test(...)`, ~L242-248)
- Modify: `integration-tests/src/test/java/tests/BTraceFunctionalTests.java` (`testTraceAll`, `testThreadStart(dynamic=false)` call sites)
- Modify: `integration-tests/src/test/java/tests/ManifestLibsTests.java` (`launchAgent_manifestLibs`)

**Interfaces:**
- Consumes: `tests.harness.OutputPump.run(...)` (same signature already used by `attach()`/`attachOneliner()` — read those two methods in `RuntimeTest.java` first to copy the exact call shape and skip-line/skip-prefix lists), `Completion.untilContains`.
- Produces: `testStartup(String testApp, String testScript, String[] cmdArgs, Completion completion, ResultValidator v)` — a new overload later tasks/tests could use, though none currently need to.

`testStartup` currently hand-rolls its own stdout/stderr reader threads with a raw `CountDownLatch(checkLines)` (L727) plus a bug-adjacent stderr fallback that force-releases the latch on ANY non-filtered stderr line (L788-791: `for (int i=0;i<checkLines;i++) stdoutLatch.countDown();`) — meaning today, in practice, a single qualifying stderr line already satisfies the wait regardless of `checkLines`'s value. This is the same race `OutputPump`/`Completion` were built to remove from `attach()`.

- [ ] **Step 1: Read the existing `attach()` method's `OutputPump` wiring completely**

Before writing any code, read `RuntimeTest.java`'s `attach(String pid, String trace, String[] cmdArgs, Completion completion, StringBuilder stdout, StringBuilder stderr)` method in full (the one Task 3 of the original harness plan built). It already solves the same problem (spawn process, pump stdout/stderr through `OutputPump` with a `Completion`, apply the same stderr skip-substring list `testStartup` uses). `testStartup` is going to be restructured to follow the same shape.

- [ ] **Step 2: Add a `Completion`-based `testStartup` overload**

Add a new overload:
```java
public void testStartup(
    String testApp, String testScript, String[] cmdArgs, Completion completion, ResultValidator v)
    throws Exception {
  // Copy testStartup's existing setup exactly (agentPath, testJavaHome, args list construction,
  // agentSetup javaagent string, ProcessBuilder pb, jfrFile handling) up to the point where it
  // currently constructs `stdoutReader`/`stderrReader`/the two hand-rolled Thread objects and the
  // CountDownLatch fields.
  //
  // Replace that reader-thread block with the same OutputPump.run(...) call attach() uses,
  // passing `completion` through instead of `Completion.lines(checkLines)`. Preserve the existing
  // `testAppLatch`/`pidStringRef` "ready:" line handling from the current stdout loop (L737-742)
  // by keeping it as a side-channel line inspection ahead of/alongside the OutputPump call --
  // read how attach() (or attachOneliner(), whichever already coexists with a "ready:" style
  // pre-scan) structures this before deciding the exact placement, since RuntimeTest.java already
  // has a working precedent for combining a "ready:" latch with an OutputPump-driven Completion
  // wait in the same method.
  //
  // Keep the rest of testStartup's body identical: the JFR dump-before-kill block (already fixed
  // in a prior commit — do not reorder it), the final v.validate(...) call, and the finally-block
  // process cleanup.
}
```

- [ ] **Step 3: Make the old `int checkLines` `testStartup` overload delegate to the new one**

```java
public void testStartup(
    String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)
    throws Exception {
  testStartup(testApp, testScript, cmdArgs, Completion.lines(checkLines), v);
}
```

- [ ] **Step 4: Migrate `test(...)`'s internal call, and the 3 direct callers**

`test(...)` (L242-248) calls both `testDynamic(...)` and `testStartup(...)` with the same `checkLines`. Since `testDynamic` already takes `Completion` after Task 5/6, change `test(...)`'s signature to take a `Completion` too, and have both inner calls use it:
```java
public void test(
    String testApp, String testScript, String[] cmdArgs, Completion completion, ResultValidator v)
    throws Exception {
  testDynamic(testApp, testScript, cmdArgs, completion, v);
  testStartup(testApp, testScript.replace(".java", ".class"), cmdArgs, completion, v);
}
```
Keep the old `int checkLines` `test(...)` overload delegating to this one via `Completion.lines(checkLines)`, for now (Task 8 removes it once all callers are off it).

Then migrate the 3 direct callers found during inventory:
- `testTraceAll` (via `test(...)`) — reuses `traces/TraceAllTest.class`; check its `ResultValidator` for markers (if, like `testOSMBean`, it has none beyond `!contains("FAILED")`, apply the same `Completion.untilMatches(Pattern.compile(".+"), 1)` treatment as Task 6 Step 1).
- `testThreadStart(dynamic=false)` (via `testStartup` directly) — same script as the already-migrated `dynamic=true` case (Task 5's `Completion.untilContains("starting testThread")`); reuse the identical marker.
- `launchAgent_manifestLibs` in `ManifestLibsTests.java` (via `testStartup` directly, `traces/TraceAllTest.class`, `checkLines=5`) — same `TraceAllTest` script as `testTraceAll`; reuse whatever `Completion` Task 7 Step 4's first bullet settles on for `testTraceAll`, since both exercise the identical script.

- [ ] **Step 5: Run the migrated tests**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test --tests 'tests.BTraceFunctionalTests.testTraceAll' --tests 'tests.BTraceFunctionalTests.testThreadStart' --tests 'tests.ManifestLibsTests.launchAgent_manifestLibs'`
Expected: all PASS, no `[harness] timed out` lines.

- [ ] **Step 6: Commit**

```bash
git add integration-tests/src/test/java/tests/RuntimeTest.java integration-tests/src/test/java/tests/BTraceFunctionalTests.java integration-tests/src/test/java/tests/ManifestLibsTests.java
git commit -m "test(harness): migrate testStartup's CountDownLatch(checkLines) to OutputPump/Completion"
```

---

### Task 8: Delete the now-dead `int checkLines` overloads

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing — pure deletion, only proceed once Tasks 1-7 have removed every caller.

- [ ] **Step 1: Confirm zero remaining callers of each `int checkLines` overload**

Run: `grep -rn "checkLines" integration-tests/src/test/java/tests/*.java | grep -v "RuntimeTest.java\|harness/"`
Expected: no output. If anything remains, that call site was missed by an earlier task — go back and migrate it (do not delete the overload while a caller still needs it).

- [ ] **Step 2: Delete the `int checkLines` overloads**

Delete: `testDynamic(String, String, String[], int, ResultValidator)`, `testDynamic(String, String, int, ResultValidator)` (the 4-arg convenience wrapper — check whether it delegates to the `int` or the soon-to-be-only `Completion` 4-arg form; if it currently delegates to the `int` 5-arg form, repoint it at the `Completion` 5-arg form instead of deleting, since it's a genuinely useful convenience overload), `testDynamicOneliner(String, String, int, ResultValidator)` and its `String[] cmdArgs` sibling (same convenience-overload judgment call as `testDynamic`), `attach(..., int checkLines, ...)`, `attachOneliner(..., int checkLines, ...)`, `testStartup(..., int checkLines, ...)`, and `test(..., int checkLines, ...)`.

For each: if it's a thin `Completion.lines(checkLines)` delegator with no other logic, delete it outright. If it's a convenience overload whose only "extra" is supplying a default (e.g., `cmdArgs = null`), keep it but change its parameter from `int checkLines` to `Completion completion` and have it delegate to the `Completion`-based longer overload instead.

- [ ] **Step 3: Compile the whole module**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:compileTestJava`
Expected: BUILD SUCCESSFUL. Any compile error here means Step 1's grep missed a caller — find it and migrate it properly rather than papering over the compile error.

- [ ] **Step 4: Commit**

```bash
git add integration-tests/src/test/java/tests/RuntimeTest.java
git commit -m "test(harness): delete dead int-checkLines overloads now that all callers use Completion"
```

---

### Task 9: Full-suite verification

**Files:** none (verification only)

- [ ] **Step 1: Run spotlessApply**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessApply`
If it modifies any files, `git add` and commit them separately: `git commit -m "spotless"`.

- [ ] **Step 2: Run the full integration-tests suite**

Run: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test`
Expected: all tests PASS. Capture the full output to a log file rather than relying on terminal scrollback:
`GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test > /tmp/checklines-migration-verify.log 2>&1; echo "exit: $?"`

- [ ] **Step 3: Confirm zero spurious harness timeouts**

Run: `grep -c "\[harness\] timed out" /tmp/checklines-migration-verify.log`
Expected: `0`. If any remain, find which test produced them (search the log for the preceding test name) — either that call site was missed, or its `Completion` markers don't match what the script actually prints (re-check against the actual `.java` probe source under `integration-tests/src/test/btrace/`, not this plan's transcription of it).

- [ ] **Step 4: Push and let CI confirm across all 6 JDK versions**

This is the real test — the local JDK is only one of the 6 matrix versions. Push the branch and check `gh run view --json jobs` once the run completes, the same way prior CI investigation in this session did. Do not declare this plan complete until CI shows all 6 `test (*)` jobs green, not just local success on one JDK.

---

## Self-Review

**Spec coverage:** All 27 call sites from the inventory are covered — Tasks 2-6 cover the 4 named `RuntimeTest` helpers' direct callers (`ClassFileApiTests` ×4, `ExtensionLifecycleIntegrationTest` ×3, `ManifestLibsTests.dynamicAttach_manifestLibs` ×1, `BTraceFunctionalTests` ×19 split across Tasks 5 and 6). Task 7 covers the separate `testStartup` `CountDownLatch` mechanism and its 3 callers (`testTraceAll`, `testThreadStart(dynamic=false)`, `launchAgent_manifestLibs`) that the original harness plan explicitly left out of scope. Task 1 and Task 8 clean up dead/superseded overloads. Task 9 verifies against the full suite and CI, matching this session's actual failure signal (multiple JDK jobs, different tests each time).

**Placeholder scan:** Task 7's Step 2 code block contains descriptive comments instead of full inline code for the `OutputPump` wiring — this is intentional and flagged explicitly as "read `attach()`'s existing implementation first and mirror its exact shape," because copying that ~80-line method's exact structure into this plan verbatim would drift out of sync with the real file the moment either changes; the task tells the implementer precisely which existing method to mirror and which two behaviors (the "ready:" latch, the JFR dump ordering) must be preserved. This is the one deliberate exception to "no placeholders" in this plan, scoped to a single step, with a concrete named reference implementation to copy rather than an abstract instruction.

**Type consistency:** `Completion`, `untilContains`, `untilMatches`, `lines`, `onStdout`, `onStderr`, `describe` are used identically to their existing definitions in `tests.harness.Completion` (no new methods on the interface are introduced by this plan). The new `testStartup(..., Completion, ...)` and `test(..., Completion, ...)` overloads in Task 7 match the exact parameter-ordering convention already established by `testDynamic(..., Completion, ...)`.
