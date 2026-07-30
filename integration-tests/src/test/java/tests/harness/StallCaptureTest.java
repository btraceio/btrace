/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.harness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the diagnostic snapshot itself, without the JUnit plumbing that schedules it. */
public class StallCaptureTest {
  private final List<Process> spawned = new ArrayList<>();

  @BeforeEach
  public void clearRegistry() {
    TargetRegistry.clear();
  }

  @AfterEach
  public void stopTargets() {
    TargetRegistry.clear();
    for (Process process : spawned) {
      process.destroyForcibly();
    }
    spawned.clear();
  }

  @Test
  @DisplayName("Capture names a parked thread in the test JVM")
  public void capturesTestJvmThreads() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    Thread parked = new Thread(() -> awaitQuietly(release), "stall-capture-test-parked-thread");
    parked.setDaemon(true);
    parked.start();

    StringBuilder sink = new StringBuilder();
    try {
      StallCapture.capture(sink, "some-test-label", 12_000L, 1, Collections.emptyList(), 10_000L);
    } finally {
      release.countDown();
    }

    String report = sink.toString();
    assertTrue(report.contains("STALL DUMP frame 1"), "banner missing:\n" + report);
    assertTrue(report.contains("some-test-label"), "label missing:\n" + report);
    assertTrue(report.contains("elapsed:  12s"), "elapsed time missing:\n" + report);
    assertTrue(
        report.contains("stall-capture-test-parked-thread"),
        "parked thread missing from dump:\n" + report);
    // ThreadInfo.toString() stops after eight frames, and the frame that explains a stall is
    // rarely in the top eight of a test-harness stack. Assert the dump is not rendered that way.
    assertTrue(
        deepestStack(report) > 8,
        "stacks must not be truncated, deepest was " + deepestStack(report) + ":\n" + report);
    for (String reportLine : report.split("\n")) {
      assertTrue(
          reportLine.startsWith(StallCapture.PREFIX),
          "every line must be greppable, but found: " + reportLine);
    }
  }

  @Test
  @DisplayName("Capture dumps the threads of a live target JVM")
  public void capturesTargetJvmThreads() throws Exception {
    // A target that blocks until killed. A short-lived one would race jcmd's attach and make the
    // assertion nondeterministic.
    Process target = startBlockingJvm();
    TargetRegistry.Handle handle = TargetRegistry.register(target, "blocking-target", jcmd());
    handle.setPid(pidOf(target));

    StringBuilder sink = new StringBuilder();
    StallCapture.capture(sink, "target-test", 1_000L, 1, TargetRegistry.liveTargets(), 30_000L);
    String report = sink.toString();

    assertTrue(report.contains("blocking-target"), "target section missing:\n" + report);
    Assumptions.assumeTrue(
        report.contains("Full thread dump") || report.contains("\"main\""),
        "jcmd could not attach in this environment; skipping the content assertion");
    assertTrue(report.contains("\"main\""), "target's main thread missing:\n" + report);
  }

  @Test
  @DisplayName("A target that cannot be dumped degrades to a note, not a hang")
  public void degradesOnUnreachableTarget() throws Exception {
    // A live process that is not a JVM: isAlive() is true, and jcmd cannot attach to it.
    Process target = startNonJvmProcess();
    TargetRegistry.Handle handle = TargetRegistry.register(target, "bogus-pid-target", jcmd());
    handle.setPid("999999999");

    StringBuilder sink = new StringBuilder();
    long startedAt = System.nanoTime();
    StallCapture.capture(sink, "degrade-test", 1_000L, 1, TargetRegistry.liveTargets(), 30_000L);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    String report = sink.toString();
    assertTrue(
        elapsedMs < 30_000L, "capture must stay inside its budget, took " + elapsedMs + "ms");
    assertTrue(report.contains("bogus-pid-target"), "target section missing:\n" + report);
    assertTrue(report.contains("---- test JVM threads ----"), "test JVM dump lost:\n" + report);
    assertTrue(report.contains("==== end of stall dump frame 1 ===="), "truncated:\n" + report);
  }

  @Test
  @DisplayName("The capture budget covers all targets together")
  public void budgetIsSharedAcrossTargets() throws Exception {
    for (int i = 0; i < 4; i++) {
      Process target = startNonJvmProcess();
      TargetRegistry.register(target, "unreachable-" + i, jcmd()).setPid("99999999" + i);
    }

    StringBuilder sink = new StringBuilder();
    long startedAt = System.nanoTime();
    // A dumper of known duration rather than a real jcmd: how long a failing attach takes differs
    // by platform, and this must assert on the budget, not on the platform.
    StallCapture.TargetDumper slow =
        (dumpSink, target) -> {
          try {
            Thread.sleep(2_000L);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          StallCapture.line(dumpSink, "dumped " + target.getLabel());
        };
    // Enough for one dump. A per-target budget would let all four run and take four times as long.
    StallCapture.capture(
        sink, "budget-test", 1_000L, 1, TargetRegistry.liveTargets(), 2_500L, slow);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    String report = sink.toString();
    assertTrue(
        elapsedMs < 6_000L, "shared budget must not be spent per target, took " + elapsedMs + "ms");
    assertTrue(report.contains("dumped unreachable-0"), "first target not dumped:\n" + report);
    assertTrue(report.contains("capture budget exhausted"), "skips must be recorded:\n" + report);
    assertFalse(
        report.contains("dumped unreachable-3"),
        "a target past the shared budget must not be dumped:\n" + report);
    assertTrue(
        report.contains("---- target unreachable-3"),
        "a skipped target must still be named:\n" + report);
  }

  /** Frames in the longest single-thread stack of a rendered dump. */
  private static int deepestStack(String report) {
    int deepest = 0;
    int current = 0;
    for (String line : report.split("\n")) {
      if (line.startsWith(StallCapture.PREFIX + "\t")) {
        current++;
        deepest = Math.max(deepest, current);
      } else {
        current = 0;
      }
    }
    return deepest;
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Process startBlockingJvm() throws Exception {
    String java = Paths.get(javaHome(), "bin", "java").toString();
    // The code source rather than java.class.path: a Gradle worker may present its classpath
    // through a manifest-only jar, which a child JVM launched with -cp cannot resolve.
    String classpath =
        Paths.get(
                StallCaptureTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
    File marker = File.createTempFile("btrace-blocking-target-", ".log");
    ProcessBuilder pb = new ProcessBuilder(java, "-cp", classpath, BlockingMain.NAME);
    pb.redirectErrorStream(true);
    pb.redirectOutput(marker);
    Process process = pb.start();
    spawned.add(process);
    // Wait for main() to announce itself rather than guessing: a fixed sleep is either wasted time
    // or, on a loaded runner, an attach against a JVM that has not started listening yet.
    long deadline = System.currentTimeMillis() + 30_000L;
    while (System.currentTimeMillis() < deadline) {
      if (marker.isFile() && marker.length() > 0) {
        return process;
      }
      Thread.sleep(50L);
    }
    throw new AssertionError("the blocking target JVM never started");
  }

  /** A live process that is not a JVM, for the cases where the dump is expected to fail. */
  private Process startNonJvmProcess() throws Exception {
    Process process = new ProcessBuilder("sleep", "120").start();
    spawned.add(process);
    return process;
  }

  private static String javaHome() {
    // Deliberately the JVM running these tests, so that jcmd and the target are the same major
    // version; a mismatched-major jcmd cannot attach.
    return System.getProperty("java.home");
  }

  private static String jcmd() {
    return Paths.get(javaHome(), "bin", "jcmd").toString();
  }

  private static String pidOf(Process process) throws Exception {
    // Source level here is 8, so Process.pid() is reached reflectively -- the same route
    // TargetRegistry uses for a target that never reported a pid.
    return String.valueOf(Process.class.getMethod("pid").invoke(process));
  }

  /** Entry point for the blocking target JVM. */
  public static final class BlockingMain {
    static final String NAME = "tests.harness.StallCaptureTest$BlockingMain";

    public static void main(String[] args) throws Exception {
      System.out.println("started");
      System.out.flush();
      new CountDownLatch(1).await();
    }
  }
}
