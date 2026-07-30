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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the watchdog's scheduling: that it fires when a test stops progressing, stays silent when
 * one does not, and produces the second frame that makes a stall distinguishable from slowness.
 *
 * <p>Deadlines come from {@link StallTimeout} rather than a system property, because all
 * integration test classes share one worker JVM and a property set here would leak into every class
 * that runs after it.
 */
@ExtendWith(StallWatchdog.class)
public class StallWatchdogTest {
  @TempDir Path dumpDir;

  @BeforeEach
  public void redirectDumps() {
    StallWatchdog.setDumpDirForTesting(dumpDir);
    TargetRegistry.clear();
  }

  @AfterEach
  public void restoreDumpDir() {
    StallWatchdog.setDumpDirForTesting(null);
    TargetRegistry.clear();
  }

  @Test
  @DisplayName("A test that finishes promptly produces no dump")
  public void staysSilentWhenGreen() throws Exception {
    Thread.sleep(200L);
    assertEquals(Collections.emptyList(), dumpFiles(), "a green test must not leave a dump behind");
  }

  @Test
  @StallTimeout(millis = 300L, gapMillis = 60_000L)
  @DisplayName("A stalled test is dumped, naming the stalled thread")
  public void firesOnAStall() throws Exception {
    String originalName = Thread.currentThread().getName();
    Thread.currentThread().setName("stall-watchdog-test-stalled-thread");
    String report;
    try {
      report = awaitFrame(1);
    } finally {
      // Restored, or every later dump in this worker JVM names a test that finished minutes ago.
      Thread.currentThread().setName(originalName);
    }
    assertEquals(1, dumpFiles().size(), "expected exactly one frame, got " + dumpFiles());
    assertTrue(report.contains("STALL DUMP frame 1"), "banner missing:\n" + report);
    assertTrue(
        report.contains("stall-watchdog-test-stalled-thread"),
        "the stalled thread must be named:\n" + report);
    assertTrue(
        dumpFiles().get(0).getFileName().toString().endsWith("-frame1.txt"), "bad file name");
  }

  @Test
  @StallTimeout(millis = 300L, gapMillis = 300L)
  @DisplayName("A continuing stall produces a second frame, so it can be told from slowness")
  public void producesASecondFrame() throws Exception {
    String first = awaitFrame(1);
    String second = awaitFrame(2);
    assertEquals(2, dumpFiles().size(), "expected two frames, got " + dumpFiles());
    assertTrue(first.contains("STALL DUMP frame 1"), "frame 1 missing:\n" + first);
    assertTrue(second.contains("STALL DUMP frame 2"), "frame 2 missing:\n" + second);
    assertNotEquals(
        first, second, "the frames must differ; their difference is the evidence of a stall");
  }

  @Test
  @StallTimeout(millis = 300L, gapMillis = 60_000L)
  @DisplayName("A stall dump includes the threads of a registered target")
  public void dumpsRegisteredTargets() throws Exception {
    Process target = new ProcessBuilder("sleep", "120").start();
    try {
      TargetRegistry.register(target, "watchdog-test-target", "jcmd").setPid("123456789");

      String report = awaitFrame(1);
      assertEquals(1, dumpFiles().size(), "expected exactly one frame, got " + dumpFiles());
      assertTrue(
          report.contains("watchdog-test-target"),
          "a registered target must appear in the dump:\n" + report);
    } finally {
      target.destroyForcibly();
    }
  }

  @Test
  @StallTimeout(millis = 300L, gapMillis = 300L)
  @DisplayName("Once both frames are captured, the stalled test's targets are released")
  public void releasesTargetsAfterTheSecondFrame() throws Exception {
    Process target = new ProcessBuilder("sleep", "120").start();
    try {
      TargetRegistry.register(target, "released-target", "jcmd").setPid("123456789");

      awaitFrame(2);
      long deadline = System.currentTimeMillis() + 15_000L;
      while (target.isAlive() && System.currentTimeMillis() < deadline) {
        Thread.sleep(50L);
      }
      // Evidence alone does not end a stall: the per-test timeout abandons the test on a thread
      // JUnit does not mark as a daemon, and only closing the target's sockets lets it die.
      assertFalse(target.isAlive(), "a stalled test's target must be released once dumped");
    } finally {
      target.destroyForcibly();
    }
  }

  /**
   * Waits for a frame to be written in full.
   *
   * <p>The watchdog flushes each section as it is produced, so that a capture which hangs part way
   * still leaves what it had on disk. A test therefore has to wait for the closing marker rather
   * than for the file to appear.
   */
  private String awaitFrame(int frame) throws Exception {
    String marker = "==== end of stall dump frame " + frame + " ====";
    long deadline = System.currentTimeMillis() + 30_000L;
    String last = "";
    while (System.currentTimeMillis() < deadline) {
      for (Path dump : dumpFiles()) {
        if (dump.getFileName().toString().endsWith("-frame" + frame + ".txt")) {
          last = read(dump);
          if (last.contains(marker)) {
            return last;
          }
        }
      }
      Thread.sleep(100L);
    }
    throw new AssertionError("frame " + frame + " was never completed; last saw:\n" + last);
  }

  private List<Path> dumpFiles() throws IOException {
    File[] files = dumpDir.toFile().listFiles();
    if (files == null) {
      return Collections.emptyList();
    }
    Arrays.sort(files);
    List<Path> paths = new ArrayList<>();
    for (File file : files) {
      paths.add(file.toPath());
    }
    return paths;
  }

  private static String read(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private static String elapsedLine(String report) {
    for (String line : report.split("\n")) {
      if (line.contains("elapsed:")) {
        return line;
      }
    }
    return "";
  }
}
