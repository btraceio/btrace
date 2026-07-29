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
    Assumptions.assumeFalse(
        report.contains("jcmd failed") || report.contains("jcmd produced no output"),
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
    // A budget below one jcmd timeout, so the first target consumes it and the rest are skipped.
    StallCapture.capture(sink, "budget-test", 1_000L, 1, TargetRegistry.liveTargets(), 1L);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    String report = sink.toString();
    assertTrue(
        elapsedMs < 30_000L,
        "shared budget must not be spent per target, took " + elapsedMs + "ms");
    assertTrue(report.contains("capture budget exhausted"), "skips must be recorded:\n" + report);
    assertFalse(report.contains("STALL DUMP frame 2"), "only one frame was requested:\n" + report);
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
    ProcessBuilder pb = new ProcessBuilder(java, "-cp", classpath, BlockingMain.NAME);
    pb.redirectErrorStream(true);
    pb.redirectOutput(new File(System.getProperty("java.io.tmpdir"), "btrace-blocking-target.log"));
    Process process = pb.start();
    spawned.add(process);
    // Give it time to reach main() so that jcmd has something to attach to.
    Thread.sleep(2_000L);
    return process;
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
      new CountDownLatch(1).await();
    }
  }
}
