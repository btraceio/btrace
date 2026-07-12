# Test-Harness Completion Conditions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the integration-test harness's brittle `int checkLines` line-count synchronization with an intent-expressing `Completion` condition, so dynamic-attach tests wait for the *actual probe output* they assert on instead of racing an environment-dependent line count.

**Architecture:** Introduce two small test-harness classes — `Completion` (a predicate over a BTrace client's output stream) and `OutputPump` (a single shared reader-thread + timeout implementation) — then reimplement `RuntimeTest`'s duplicated attach/oneliner/runBTrace readers on top of them. Every existing `int checkLines` public method is preserved and delegates to `Completion.lines(n)`, so the change is opt-in and no existing call site churns. Timing-sensitive tests then migrate to `Completion.untilContains(...)`.

**Tech Stack:** Java 8 (source/target for this module's test code), JUnit 5 (jupiter 5.14.4), Gradle.

## Global Constraints

- New harness classes live in `integration-tests/src/test/java/tests/harness/` (package `tests.harness`).
- **Java 8 source compatibility** (module pins `sourceCompatibility = 8` / `targetCompatibility = 8` for test code): no `var`, records, text blocks, switch expressions, or post-Java-8 APIs (`List.of`, `Stream.toList`, `Optional.isEmpty`, etc.). Use `Arrays.asList(...)`, explicit generics, `StringBuilder`.
- **JUnit 5**: `org.junit.jupiter.api.Test` / `org.junit.jupiter.api.Assertions.*`. Jupiter is force-pinned to `5.14.4` in this module.
- **All tests in `:integration-tests` run only with `-Pintegration`** (the `test` task is `onlyIf { project.hasProperty("integration") }`). Every verification command below includes `-Pintegration`.
- **Preserve every existing `int checkLines` public signature** on `RuntimeTest` (≈53 call sites across `BTraceFunctionalTests`, `ExtensionLifecycleIntegrationTest`, `ManifestLibsTests`, `ClassFileApiTests`, `ExternalTypeAdapterIntegrationTest`). New behavior is additive via `Completion` overloads; the int path must delegate to `Completion.lines(n)` with byte-identical waiting behavior.
- **Local build recipe** (host JDK must be modern for the `java24` source set; the launched target JVM is chosen via `TEST_JAVA_HOME`):
  ```bash
  export JAVA_HOME=$HOME/.sdkman/candidates/java/24.0.1-tem
  export PATH=$JAVA_HOME/bin:$PATH
  export TEST_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
  export GRADLE_USER_HOME=$(pwd)/.gradle-user
  ```
- **Integration (attach) tests require the distribution built first** (masked `btrace.jar` + extensions):
  ```bash
  ./gradlew :btrace-dist:build -x test -x spotlessCheck --console=plain
  ```
  Rebuild only when non-test sources change; harness/test-only edits do not require a dist rebuild.
- Run `./gradlew spotlessApply` and commit any formatting changes as part of each task's commit.

---

### Task 1: `Completion` condition abstraction

**Files:**
- Create: `integration-tests/src/test/java/tests/harness/Completion.java`
- Test: `integration-tests/src/test/java/tests/harness/CompletionTest.java`

**Interfaces:**
- Consumes: nothing (leaf).
- Produces:
  - `interface tests.harness.Completion`
    - `boolean onStdout(String line)` — offer one stdout line; return `true` once the awaited condition is satisfied.
    - `boolean onStderr(String line)` — default `false`; return `true` to release the wait early.
    - `String describe()` — human-readable condition, used in timeout diagnostics.
  - `static Completion lines(int n)` — satisfied after `n` offered stdout lines (backward-compat for `checkLines`).
  - `static Completion untilContains(String... markers)` — satisfied once every marker substring has appeared across stdout (any order).
  - `static Completion untilMatches(java.util.regex.Pattern pattern, int n)` — satisfied once `n` stdout lines match `pattern`.

- [ ] **Step 1: Write the failing test**

Create `integration-tests/src/test/java/tests/harness/CompletionTest.java`:

```java
package tests.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompletionTest {

  @Test
  void linesSatisfiedAfterNthLine() {
    Completion c = Completion.lines(2);
    assertFalse(c.onStdout("first"), "not satisfied after 1 line");
    assertTrue(c.onStdout("second"), "satisfied after 2 lines");
  }

  @Test
  void untilContainsNeedsAllMarkersInAnyOrder() {
    Completion c = Completion.untilContains("tag=", "value=");
    assertFalse(c.onStdout("value=42"), "only one marker seen");
    assertFalse(c.onStdout("noise"), "still one marker seen");
    assertTrue(c.onStdout("tag=ext-data-ok"), "both markers now seen");
  }

  @Test
  void untilContainsIsSatisfiedByASingleLineHoldingAllMarkers() {
    Completion c = Completion.untilContains("a", "b");
    assertTrue(c.onStdout("xax by"), "both markers on one line");
  }

  @Test
  void untilMatchesCountsMatchingLinesOnly() {
    Completion c = Completion.untilMatches(Pattern.compile("^event:"), 2);
    assertFalse(c.onStdout("event: one"), "1 match");
    assertFalse(c.onStdout("ignore me"), "still 1 match");
    assertTrue(c.onStdout("event: two"), "2 matches");
  }

  @Test
  void stderrDefaultsToNonReleasing() {
    Completion c = Completion.untilContains("never");
    assertFalse(c.onStderr("anything"), "stderr does not release by default");
  }

  @Test
  void describeIsHumanReadable() {
    assertEquals("2 output line(s)", Completion.lines(2).describe());
    assertTrue(Completion.untilContains("tag=", "value=").describe().contains("tag="));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew -Pintegration :integration-tests:test --tests 'tests.harness.CompletionTest' --console=plain
```
Expected: FAIL — compilation error, `tests.harness.Completion` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `integration-tests/src/test/java/tests/harness/Completion.java`:

```java
package tests.harness;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Decides when a BTrace client's output stream is "done" for a test.
 *
 * <p>Replaces the historical {@code int checkLines} line-count, which conflated framework
 * bootstrap output with real probe output and therefore raced whenever the framework line count
 * shifted between environments (log level, JDK-version warnings, debug flags). A {@code Completion}
 * lets a test wait for the exact content it is about to assert on.
 */
public interface Completion {

  /** Offer one stdout line. Returns {@code true} once the awaited condition is satisfied. */
  boolean onStdout(String line);

  /** Offer one stderr line. Returns {@code true} to release the wait early. Default: never. */
  default boolean onStderr(String line) {
    return false;
  }

  /** Human-readable description of what this condition waits for (used in timeout diagnostics). */
  String describe();

  /** Backward-compatible completion: satisfied after {@code n} offered stdout lines. */
  static Completion lines(final int n) {
    return new Completion() {
      private int seen = 0;

      @Override
      public boolean onStdout(String line) {
        return ++seen >= n;
      }

      @Override
      public String describe() {
        return n + " output line(s)";
      }
    };
  }

  /** Satisfied once every marker substring has appeared across stdout, in any order. */
  static Completion untilContains(final String... markers) {
    return new Completion() {
      private final boolean[] found = new boolean[markers.length];
      private int remaining = markers.length;

      @Override
      public boolean onStdout(String line) {
        for (int i = 0; i < markers.length; i++) {
          if (!found[i] && line.contains(markers[i])) {
            found[i] = true;
            remaining--;
          }
        }
        return remaining <= 0;
      }

      @Override
      public String describe() {
        return "all of " + Arrays.toString(markers);
      }
    };
  }

  /** Satisfied once {@code n} stdout lines match {@code pattern}. */
  static Completion untilMatches(final Pattern pattern, final int n) {
    return new Completion() {
      private int matches = 0;

      @Override
      public boolean onStdout(String line) {
        if (pattern.matcher(line).find()) {
          matches++;
        }
        return matches >= n;
      }

      @Override
      public String describe() {
        return n + " line(s) matching /" + pattern.pattern() + "/";
      }
    };
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew -Pintegration :integration-tests:test --tests 'tests.harness.CompletionTest' --console=plain
```
Expected: PASS — `6 tests completed, 0 failed` (visible in the task output / `build/reports/tests`).

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/harness/Completion.java \
        integration-tests/src/test/java/tests/harness/CompletionTest.java
git commit -m "test(harness): add Completion condition abstraction for output waiting"
```

---

### Task 2: `OutputPump` shared reader

**Files:**
- Create: `integration-tests/src/test/java/tests/harness/OutputPump.java`
- Test: `integration-tests/src/test/java/tests/harness/OutputPumpTest.java`

**Interfaces:**
- Consumes: `tests.harness.Completion` (Task 1).
- Produces:
  - `static boolean OutputPump.run(Process p, Completion completion, long timeoutMs, boolean skipDebugLines, java.util.List<String> stderrSkipSubstrings, java.util.List<String> stderrSkipPrefixes, StringBuilder stdout, StringBuilder stderr)` — starts two daemon reader threads that append every stdout/stderr line into the provided builders (echoing `[btrace out] ` / `[btrace err] ` as today), offer each *non-skipped* line to `completion`, and return `true` if the completion was satisfied before `timeoutMs`, `false` on timeout. A non-skipped stderr line containing `"Exception"` or `"Error"` releases the wait early (preserving the historical fail-fast). The builders remain live references — reader threads keep appending after the method returns, so late output is still captured by the caller.

- [ ] **Step 1: Write the failing test**

Create `integration-tests/src/test/java/tests/harness/OutputPumpTest.java`:

```java
package tests.harness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OutputPumpTest {

  /** Minimal fake Process exposing fixed stdout/stderr streams. */
  private static final class FakeProcess extends Process {
    private final InputStream out;
    private final InputStream err;

    FakeProcess(String stdout, String stderr) {
      this.out = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
      this.err = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return null;
    }

    @Override
    public InputStream getInputStream() {
      return out;
    }

    @Override
    public InputStream getErrorStream() {
      return err;
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {}
  }

  @Test
  void releasesWhenCompletionSatisfiedBeforeTimeout() throws Exception {
    FakeProcess p =
        new FakeProcess("[main] INFO Attaching\n[main] INFO Started\ntag=ext-data-ok\nvalue=42\n", "");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("tag=ext-data-ok", "value=42"),
            TimeUnit.SECONDS.toMillis(5),
            false,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            out,
            err);
    assertTrue(completed, "completion satisfied by tag=/value= lines");
    assertTrue(out.toString().contains("value=42"), "stdout captured");
  }

  @Test
  void timesOutWhenConditionNeverMet() throws Exception {
    FakeProcess p = new FakeProcess("only noise\nmore noise\n", "");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("never-appears"),
            300L,
            false,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            out,
            err);
    assertFalse(completed, "condition never met -> timeout");
  }

  @Test
  void releasesEarlyOnStderrError() throws Exception {
    FakeProcess p = new FakeProcess("", "java.lang.RuntimeException: boom\n");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("never-appears"),
            TimeUnit.SECONDS.toMillis(5),
            false,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            out,
            err);
    assertTrue(completed, "stderr Exception releases the wait");
    assertTrue(err.toString().contains("boom"), "stderr captured");
  }

  @Test
  void appliesStderrSkipFilters() throws Exception {
    FakeProcess p = new FakeProcess("", "Server VM warning: ignore me\n");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("never-appears"),
            300L,
            false,
            Arrays.asList("Server VM warning"),
            Collections.<String>emptyList(),
            out,
            err);
    assertFalse(completed, "skipped warning must not release or be treated as error");
    assertFalse(err.toString().contains("Server VM warning"), "skipped line not captured to stderr");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew -Pintegration :integration-tests:test --tests 'tests.harness.OutputPumpTest' --console=plain
```
Expected: FAIL — compilation error, `tests.harness.OutputPump` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `integration-tests/src/test/java/tests/harness/OutputPump.java`:

```java
package tests.harness;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Single shared implementation of the "read a BTrace client process's stdout/stderr, wait until a
 * {@link Completion} is satisfied (or timeout)" logic. Replaces four near-identical reader loops in
 * {@code RuntimeTest} (attach, attachOneliner, runBTrace, testStartup).
 *
 * <p>The provided {@code stdout}/{@code stderr} builders are live references: the reader threads
 * keep appending to them after {@link #run} returns, so output that arrives slightly after the
 * completion condition is met (or after a timeout) is still captured for the caller's assertions.
 */
public final class OutputPump {

  private OutputPump() {}

  public static boolean run(
      final Process p,
      final Completion completion,
      final long timeoutMs,
      final boolean skipDebugLines,
      final List<String> stderrSkipSubstrings,
      final List<String> stderrSkipPrefixes,
      final StringBuilder stdout,
      final StringBuilder stderr)
      throws InterruptedException {

    final CountDownLatch done = new CountDownLatch(1);

    Thread outT =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try (BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = br.readLine()) != null) {
                    stdout.append(line).append('\n');
                    System.out.println("[btrace out] " + line);
                    if (skipDebugLines && line.contains("DEBUG")) {
                      continue;
                    }
                    if (completion.onStdout(line)) {
                      done.countDown();
                    }
                  }
                } catch (Exception e) {
                  done.countDown();
                }
              }
            },
            "Stdout Reader");

    Thread errT =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try (BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = br.readLine()) != null) {
                    System.out.println("[btrace err] " + line);
                    if (isSkipped(line, stderrSkipSubstrings, stderrSkipPrefixes)) {
                      continue;
                    }
                    stderr.append(line).append('\n');
                    if (completion.onStderr(line)) {
                      done.countDown();
                    }
                    if (line.contains("Exception") || line.contains("Error")) {
                      done.countDown();
                    }
                  }
                } catch (Exception e) {
                  done.countDown();
                }
              }
            },
            "Stderr Reader");

    outT.setDaemon(true);
    errT.setDaemon(true);
    outT.start();
    errT.start();

    return done.await(timeoutMs, TimeUnit.MILLISECONDS);
  }

  private static boolean isSkipped(
      String line, List<String> skipSubstrings, List<String> skipPrefixes) {
    for (String s : skipSubstrings) {
      if (line.contains(s)) {
        return true;
      }
    }
    for (String pfx : skipPrefixes) {
      if (line.startsWith(pfx)) {
        return true;
      }
    }
    return false;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew -Pintegration :integration-tests:test --tests 'tests.harness.OutputPumpTest' --console=plain
```
Expected: PASS — `4 tests completed, 0 failed`.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/harness/OutputPump.java \
        integration-tests/src/test/java/tests/harness/OutputPumpTest.java
git commit -m "test(harness): add OutputPump shared reader with timeout + completion"
```

---

### Task 3: Reimplement `attach()` on `OutputPump`, preserving the `int` API

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java` — the `attach(...)` method (currently lines ~1293–1420) and its imports.

**Interfaces:**
- Consumes: `tests.harness.Completion`, `tests.harness.OutputPump` (Tasks 1–2).
- Produces:
  - `private Process attach(String pid, String trace, String[] cmdArgs, Completion completion, StringBuilder stdout, StringBuilder stderr)` — new primary overload.
  - `private Process attach(String pid, String trace, String[] cmdArgs, int checkLines, StringBuilder stdout, StringBuilder stderr)` — retained; delegates to the `Completion` overload via `Completion.lines(checkLines)`.

This task changes only the reader/wait mechanism inside `attach`; the process-launch argument construction is unchanged. `testDynamic(...)` still calls the `int` overload, so behavior for all existing callers is identical (they wait for `Completion.lines(checkLines)`).

- [ ] **Step 1: Add imports**

At the top of `RuntimeTest.java`, add with the other imports:

```java
import tests.harness.Completion;
import tests.harness.OutputPump;
```

- [ ] **Step 2: Replace the `attach` method body**

Replace the entire existing `private Process attach(...) { ... }` method (from its signature through its closing brace, ~lines 1293–1420) with:

```java
  private Process attach(
      String pid,
      String trace,
      String[] cmdArgs,
      int checkLines,
      StringBuilder stdout,
      StringBuilder stderr)
      throws Exception {
    return attach(pid, trace, cmdArgs, Completion.lines(checkLines), stdout, stderr);
  }

  private Process attach(
      String pid,
      String trace,
      String[] cmdArgs,
      Completion completion,
      StringBuilder stdout,
      StringBuilder stderr)
      throws Exception {
    File traceFile = locateTrace(trace);
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-Dcom.sun.btrace.unsafe=" + isUnsafe,
                "-Dcom.sun.btrace.debug=" + debugBTrace,
                "-Dcom.sun.btrace.trackRetransforms=" + trackRetransforms,
                "-Dbtrace.comm.protocol=2",
                "-Dbtrace.comm.autoNegotiate=false",
                "-Dbtrace.comm.forceVersion=true",
                "-Dbtrace.port=" + getBTracePort(),
                "-Dbtrace.libs=" + System.getProperty("btrace.libs"),
                "-Dbtrace.suppressJavaDeprecationWarning=true",
                "-cp",
                cp,
                "io.btrace.boot.Loader",
                "-p",
                String.valueOf(getBTracePort()),
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString(),
                "-pd",
                traceFile.getParentFile().getAbsolutePath()));
    if (debugBTrace) {
      argVals.add("-v");
    }
    if (dumpOneliner) {
      int cpIdx = argVals.indexOf("-cp");
      if (cpIdx > -1) {
        argVals.add(cpIdx, "-Dbtrace.oneliner.dump=true");
      }
    }
    if (unattended) {
      argVals.add("-x");
    }
    argVals.addAll(Arrays.asList(pid, traceFile.getAbsolutePath()));
    if (cmdArgs != null) {
      argVals.addAll(Arrays.asList(cmdArgs));
    }
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    boolean completed =
        OutputPump.run(
            p,
            completion,
            timeout,
            debugBTrace,
            Arrays.asList("Server VM warning", "XML libraries not available", "Connection reset"),
            Arrays.asList("[traced app]", "[btrace out]"),
            stdout,
            stderr);
    if (!completed) {
      System.out.println(
          "[harness] timed out after "
              + timeout
              + "ms waiting for "
              + completion.describe()
              + "; stdout so far:\n"
              + stdout);
    }

    return p;
  }
```

- [ ] **Step 3: Verify it compiles and an existing dynamic-attach test still passes**

Build the dist once (if not already built this session), then run a stable dynamic-attach characterization test:

```bash
./gradlew :btrace-dist:build -x test -x spotlessCheck --console=plain
./gradlew -Pintegration :integration-tests:test \
  --tests 'tests.BTraceFunctionalTests.testOnMethod' \
  --tests 'tests.BTraceFunctionalTests.testOnTimer' \
  --console=plain --rerun
```
Expected: PASS — both methods green. (These exercise `attach` → `Completion.lines(n)` and prove the reimplementation preserves behavior.)

- [ ] **Step 4: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/RuntimeTest.java
git commit -m "test(harness): route attach() through OutputPump; keep int checkLines API"
```

---

### Task 4: Expose `Completion` overloads on the public `testDynamic` entry points

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java` — the `testDynamic` overloads (int variants at ~lines 249–252 and ~447–632) and the `attach` call inside the big `testDynamic` body (~line 575).

**Interfaces:**
- Consumes: `tests.harness.Completion`, the `attach(... Completion ...)` overload (Task 3).
- Produces:
  - `public void testDynamic(String testApp, String testScript, String[] cmdArgs, Completion completion, ResultValidator v)` — new primary overload containing the full launch/attach/teardown/validate body.
  - `public void testDynamic(String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)` — delegates via `Completion.lines(checkLines)`.
  - `public void testDynamic(String testApp, String testScript, Completion completion, ResultValidator v)` — 2-arg-cmdArgs-less convenience, delegates with `cmdArgs = null`.
  - Existing `public void testDynamic(String testApp, String testScript, int checkLines, ResultValidator v)` retained, delegates to the int/cmdArgs overload with `cmdArgs = null` (unchanged behavior).

- [ ] **Step 1: Convert the big `testDynamic` body to take a `Completion`**

Change the signature of the large `testDynamic` method (currently `public void testDynamic(String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)`, ~line 447) to:

```java
  @SuppressWarnings("DefaultCharset")
  public void testDynamic(
      String testApp,
      String testScript,
      String[] cmdArgs,
      Completion completion,
      ResultValidator v)
      throws Exception {
```

Inside that method body, change the `attach` call (currently `Process client = attach(pid, testScript, cmdArgs, checkLines, stdout, stderr);`, ~line 575) to:

```java
      Process client = attach(pid, testScript, cmdArgs, completion, stdout, stderr);
```

Leave the rest of the method (launch, `testAppLatch`, `attachDelayMs`, teardown, JFR dump, `v.validate(...)`) unchanged.

- [ ] **Step 2: Add the delegating overloads**

Immediately below the small `public void testDynamic(String testApp, String testScript, int checkLines, ResultValidator v)` overload (~lines 249–252), add:

```java
  @SuppressWarnings("DefaultCharset")
  public void testDynamic(
      String testApp, String testScript, Completion completion, ResultValidator v)
      throws Exception {
    testDynamic(testApp, testScript, null, completion, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void testDynamic(
      String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)
      throws Exception {
    testDynamic(testApp, testScript, cmdArgs, Completion.lines(checkLines), v);
  }
```

Verify the existing `public void testDynamic(String testApp, String testScript, int checkLines, ResultValidator v)` still reads:

```java
  @SuppressWarnings("DefaultCharset")
  public void testDynamic(String testApp, String testScript, int checkLines, ResultValidator v)
      throws Exception {
    testDynamic(testApp, testScript, null, checkLines, v);
  }
```

(It now resolves to the new `int`/`cmdArgs` delegator, which forwards to the `Completion` body — behavior unchanged.)

- [ ] **Step 3: Verify compilation and a broad dynamic characterization run**

```bash
./gradlew -Pintegration :integration-tests:test \
  --tests 'tests.BTraceFunctionalTests' \
  --console=plain --rerun
```
Expected: PASS — the functional suite is green (all existing `int checkLines` callers unaffected).

- [ ] **Step 4: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/RuntimeTest.java
git commit -m "test(harness): add Completion overloads to testDynamic entry points"
```

---

### Task 5: Migrate `ExternalTypeAdapterIntegrationTest` to a content condition

**Files:**
- Modify: `integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java`

**Interfaces:**
- Consumes: `testDynamic(String, String, Completion, ResultValidator)` (Task 4), `Completion.untilContains(...)` (Task 1).

This replaces the `checkLines = 6` band-aid (committed as the CI hotfix) with the condition the test actually asserts on, so the wait and the assertion reference the same signal.

- [ ] **Step 1: Update imports**

Add to `ExternalTypeAdapterIntegrationTest.java` imports:

```java
import tests.harness.Completion;
```

- [ ] **Step 2: Replace the `testDynamic` call**

Replace the current call (the `testDynamic("resources.Main", "btrace/ExternalTypeAdapterTest.java", null, 6, new ResultValidator() {...})` block, including the multi-line `checkLines = 6` comment) with:

```java
    testDynamic(
        "resources.Main",
        "btrace/ExternalTypeAdapterTest.java",
        // Wait for the probe's actual output — the same signals the validator asserts on — so the
        // harness never tears the target down before the async retransform of the already-loaded
        // resources.Main class has fired the probe.
        Completion.untilContains("tag=ext-data-ok", "value=42"),
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(
                stdout.contains("FAILED"), "Probe should not have failed. stderr: " + stderr);

            // Static dispatch via TCCL: ExternalDataType$Ext.tag() -> ExternalData.tag()
            assertTrue(
                stdout.contains("tag=ext-data-ok"),
                "@ExternalType static dispatch failed. stdout: " + stdout);

            // Virtual dispatch: ExternalDataType$Ext.value(data) -> ExternalData.value()
            assertTrue(
                stdout.contains("value=42"),
                "@ExternalType virtual dispatch failed. stdout: " + stdout);
          }
        });
```

- [ ] **Step 3: Verify the previously-flaky test is now robust**

Run it repeatedly back-to-back on the JDK that reproduced the race (17); all runs must pass:

```bash
for i in 1 2 3 4 5 6; do
  ./gradlew -Pintegration :integration-tests:test \
    --tests 'tests.ExternalTypeAdapterIntegrationTest' --console=plain --rerun \
    > /tmp/ext_fix_run$i.log 2>&1
  echo "run $i: $(grep -o 'BUILD SUCCESSFUL\|BUILD FAILED' /tmp/ext_fix_run$i.log | tail -1)"
done
```
Expected: `run 1..6: BUILD SUCCESSFUL` (6/6). The pre-fix band-aid raced ~5/6 on this JDK with `checkLines = 2`; the content condition must be deterministic.

- [ ] **Step 4: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java
git commit -m "test: wait on probe output condition in ExternalTypeAdapterIntegrationTest"
```

---

### Task 6: Reimplement `attachOneliner()` and `runBTrace(int checkLines)` on `OutputPump`

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java` — `attachOneliner(...)` (~lines 1422–1549) and `runBTrace(String[] args, int checkLines, StringBuilder, StringBuilder)` (~lines 1141–1238).

**Interfaces:**
- Consumes: `tests.harness.Completion`, `tests.harness.OutputPump`.
- Produces:
  - `private Process attachOneliner(String pid, String oneliner, String[] cmdArgs, Completion completion, StringBuilder stdout, StringBuilder stderr)` + retained `int` delegator.
  - `public Process runBTrace(String[] args, Completion completion, StringBuilder stdout, StringBuilder stderr)` + retained `int` delegator.

The `attachOneliner` stderr skip-list is broader than `attach`'s (it also skips `Successfully started BTrace probe`, the `sun.misc.Unsafe` deprecation lines, `A restricted method`, etc.). Pass that full list to `OutputPump` so behavior is preserved.

- [ ] **Step 1: Replace `attachOneliner` reader/wait**

Change the `attachOneliner` signature to take a `Completion`, add an `int` delegator, and replace the two inline reader-thread blocks + `l.await(...)` with a single `OutputPump.run(...)` call carrying `attachOneliner`'s existing skip substrings:

```java
  private Process attachOneliner(
      String pid,
      String oneliner,
      String[] cmdArgs,
      int checkLines,
      StringBuilder stdout,
      StringBuilder stderr)
      throws Exception {
    return attachOneliner(pid, oneliner, cmdArgs, Completion.lines(checkLines), stdout, stderr);
  }

  private Process attachOneliner(
      String pid,
      String oneliner,
      String[] cmdArgs,
      Completion completion,
      StringBuilder stdout,
      StringBuilder stderr)
      throws Exception {
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-Dcom.sun.btrace.unsafe=" + isUnsafe,
                "-Dcom.sun.btrace.debug=" + debugBTrace,
                "-Dcom.sun.btrace.trackRetransforms=" + trackRetransforms,
                "-Dbtrace.comm.protocol=2",
                "-Dbtrace.comm.autoNegotiate=false",
                "-Dbtrace.comm.forceVersion=true",
                "-Dbtrace.suppressJavaDeprecationWarning=true",
                "-cp",
                cp,
                "io.btrace.boot.Loader",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString(),
                "-n",
                oneliner,
                "-pd",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-oneliner").toString()));
    if (debugBTrace) {
      argVals.add("-v");
    }
    if (unattended) {
      argVals.add("-x");
    }
    if (btracePort > 0) {
      argVals.add("-p");
      argVals.add(String.valueOf(btracePort));
    }
    argVals.addAll(Arrays.asList(pid));
    if (cmdArgs != null) {
      argVals.addAll(Arrays.asList(cmdArgs));
    }
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    boolean completed =
        OutputPump.run(
            p,
            completion,
            timeout,
            debugBTrace,
            Arrays.asList(
                "Server VM warning",
                "XML libraries not available",
                "Successfully started BTrace probe",
                "Connection reset",
                "terminally deprecated method in sun.misc.Unsafe",
                "sun.misc.Unsafe::objectFieldOffset",
                "sun.misc.Unsafe::arrayBaseOffset",
                "Please consider reporting this to the maintainers of class",
                "org.jctools.util.UnsafeAccess",
                "A restricted method"),
            Arrays.asList("[traced app]", "[btrace out]"),
            stdout,
            stderr);
    if (!completed) {
      System.out.println(
          "[harness] timed out after "
              + timeout
              + "ms waiting for "
              + completion.describe()
              + "; stdout so far:\n"
              + stdout);
    }

    return p;
  }
```

- [ ] **Step 2: Replace the `runBTrace(int checkLines)` reader/wait**

Change the `public Process runBTrace(String[] args, int checkLines, StringBuilder stdout, StringBuilder stderr)` to add a `Completion` overload and delegate, replacing its two reader-thread blocks + `l.await(...)` with `OutputPump.run(...)` using `runBTrace`'s existing skip substrings (`Server VM warning`, `XML libraries not available`, `Connection reset`):

```java
  public Process runBTrace(
      String[] args, int checkLines, StringBuilder stdout, StringBuilder stderr) throws Exception {
    return runBTrace(args, Completion.lines(checkLines), stdout, stderr);
  }

  public Process runBTrace(
      String[] args, Completion completion, StringBuilder stdout, StringBuilder stderr)
      throws Exception {
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-Dbtrace.suppressJavaDeprecationWarning=true",
                "-cp",
                cp,
                "io.btrace.boot.Loader",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString()));
    if (debugBTrace) {
      int mainClassIdx = argVals.indexOf("io.btrace.boot.Loader");
      argVals.add(mainClassIdx + 1, "-v");
    }
    argVals.addAll(Arrays.asList(args));
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    OutputPump.run(
        p,
        completion,
        timeout,
        debugBTrace,
        Arrays.asList("Server VM warning", "XML libraries not available", "Connection reset"),
        Arrays.asList("[traced app]", "[btrace out]"),
        stdout,
        stderr);

    return p;
  }
```

Note: this uses `line.contains("DEBUG")` (via `OutputPump`'s `skipDebugLines`) where the old `runBTrace` used `"DEBUG:"`. Under `debugBTrace` this only skips a strict superset of lines from the *count*, which is a dev-only flag and does not affect any committed test's assertions.

- [ ] **Step 3: Verify oneliner + runBTrace-based tests still pass**

```bash
./gradlew -Pintegration :integration-tests:test \
  --tests 'tests.BTraceFunctionalTests.testOnelinerRuntime' \
  --console=plain --rerun
```
Expected: PASS. (If other oneliner/list-probe tests exist in the suite, run the whole `tests.BTraceFunctionalTests` class to be safe.)

- [ ] **Step 4: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/RuntimeTest.java
git commit -m "test(harness): route attachOneliner + runBTrace through OutputPump"
```

---

### Task 7 (optional follow-up): Consolidate `testStartup` and reduce framework-log noise

**Files:**
- Modify: `integration-tests/src/test/java/tests/RuntimeTest.java` — `testStartup(...)` (~lines 634–824).

**Interfaces:**
- Consumes: `tests.harness.Completion`, `tests.harness.OutputPump`.
- Produces: `public void testStartup(String testApp, String testScript, String[] cmdArgs, Completion completion, ResultValidator v)` + retained `int` delegator.

`testStartup` is structurally different from dynamic attach (it launches the target with `-javaagent` and keys the app-ready latch on the `ready:` line rather than on a separate client process), so it is deferred to its own task. Only attempt this once Tasks 1–6 are merged and green.

- [ ] **Step 1: Extract the `ready:`-detection into a small app-ready wait, then feed subsequent target stdout lines to a `Completion` via `OutputPump`** (the target JVM's stdout carries both `ready:` and probe output, so `OutputPump` here reads the *target* process, with a skip-list for the startup banner). Keep the `int checkLines` overload delegating to `Completion.lines(n)`.

- [ ] **Step 2: Verify startup-agent tests still pass**

```bash
./gradlew -Pintegration :integration-tests:test \
  --tests 'tests.ManifestLibsTests' --console=plain --rerun
```
Expected: PASS — the launch-time / on-startup agent tests are green.

- [ ] **Step 3: Format and commit**

```bash
./gradlew spotlessApply --console=plain
git add integration-tests/src/test/java/tests/RuntimeTest.java
git commit -m "test(harness): route testStartup through OutputPump completion"
```

---

## Self-Review

**Spec coverage** (against the design proposal from analysis):
- "Wait on a content predicate, not a line count" → Task 1 (`untilContains`/`untilMatches`) + Task 5 (migration).
- "Unify the wait condition with the assertion" → Task 5 (same `tag=`/`value=` signals drive both).
- "Timeout logs captured output on genuine failure" → Task 3/Task 6 timeout diagnostic (`[harness] timed out … stdout so far: …`).
- "Consolidate the four duplicated readers" → Task 2 (`OutputPump`) consumed by Tasks 3, 6, 7 (attach, attachOneliner, runBTrace, testStartup).
- "Backward-compat `lines(n)` shim so existing tests don't churn" → `Completion.lines(int)` + every `int` overload delegates (Tasks 1, 3, 4, 6, 7).
- "Separate framework output from probe output" → partially addressed by `OutputPump`'s stderr skip-lists; full log-routing is left as a noted optional extension inside Task 7's scope (not required for correctness once `untilContains` is in use).

**Placeholder scan:** No `TBD`/`add error handling`/`similar to Task N` — every code step shows complete Java. Task 7's Step 1 is intentionally described rather than coded because it is an optional follow-up whose exact shape depends on Tasks 1–6 landing first; it is marked optional and not required for the CI fix.

**Type consistency:** `Completion` methods (`onStdout`/`onStderr`/`describe`) and factories (`lines`/`untilContains`/`untilMatches`) are named identically in Task 1's definition and every later consumer. `OutputPump.run(...)`'s parameter order (process, completion, timeoutMs, skipDebugLines, stderrSkipSubstrings, stderrSkipPrefixes, stdout, stderr) matches every call site in Tasks 3 and 6. The `attach`/`attachOneliner`/`runBTrace` `Completion` overloads and their `int` delegators use consistent signatures throughout.

**Note on the current CI hotfix:** the already-committed `checkLines = 6` change in `ExternalTypeAdapterIntegrationTest` is superseded by Task 5, which replaces it with `Completion.untilContains("tag=ext-data-ok", "value=42")`. If this plan is executed before that hotfix merges, Task 5 still applies cleanly (it rewrites the whole `testDynamic(...)` call).
