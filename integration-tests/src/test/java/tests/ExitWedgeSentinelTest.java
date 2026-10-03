/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import resources.IgnoreStopApp;
import tests.harness.StallWatchdog;
import tests.harness.TargetRegistry;

/**
 * The exit-wedge sentinel in {@link RuntimeTest.TestApp#stop()} (issue #932).
 *
 * <p>The force-kill that bounds a wedged teardown also destroys the evidence: it bypasses JVM
 * shutdown hooks, so a target wedged inside one lets the test method finish, no watchdog fires, and
 * the wedge would vanish without a trace. The sentinel must therefore dump the target's threads in
 * the moment between the grace period expiring and the force-kill landing.
 */
public class ExitWedgeSentinelTest {
  /** Short enough that the test is quick, long enough that it is a real wait. */
  private static final int TEST_GRACE_SECONDS = 2;

  @TempDir Path dumpDir;

  private int previousGraceSeconds;

  @BeforeEach
  public void armSentinel() {
    StallWatchdog.setDumpDirForTesting(dumpDir);
    previousGraceSeconds = RuntimeTest.TestApp.shutdownTimeoutSeconds();
    RuntimeTest.TestApp.setShutdownTimeoutSecondsForTesting(TEST_GRACE_SECONDS);
  }

  @AfterEach
  public void restoreSentinel() {
    RuntimeTest.TestApp.setShutdownTimeoutSecondsForTesting(previousGraceSeconds);
    StallWatchdog.setDumpDirForTesting(null);
  }

  @Test
  @DisplayName("A target ignoring the stop request is dumped before being force-destroyed")
  public void wedgedTargetIsDumpedBeforeForcedDestroy() throws Exception {
    Path codeSource =
        Paths.get(IgnoreStopApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    String javaHome = System.getProperty("java.home");
    Process target =
        new ProcessBuilder(
                Paths.get(javaHome, "bin", "java").toString(),
                "-cp",
                codeSource.toString(),
                IgnoreStopApp.class.getName())
            .start();
    TargetRegistry.Handle handle =
        TargetRegistry.register(
            target, "exit-wedge sentinel target", Paths.get(javaHome, "bin", "jcmd").toString());
    RuntimeTest.TestApp app = new RuntimeTest.TestApp(target, false, handle);

    long startedAt = System.nanoTime();
    app.stop();
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    assertFalse(target.isAlive(), "stop() must force-destroy a target that ignores 'done'");
    assertTrue(
        elapsedMs >= TimeUnit.SECONDS.toMillis(TEST_GRACE_SECONDS),
        "stop() must give the target its full grace period before treating it as wedged");
    assertTrue(
        elapsedMs < TimeUnit.SECONDS.toMillis(40),
        "stop() must stay bounded: sentinel capture plus force-kill");

    Path dump = onlyDumpFile();
    assertNotNull(dump, "the sentinel must leave a dump behind when the wedge fires");
    String report = new String(Files.readAllBytes(dump), StandardCharsets.UTF_8);
    assertTrue(report.contains("EXIT-WEDGE"), "the dump must identify itself as an exit-wedge");
    assertTrue(
        report.contains("exit-wedge sentinel target"),
        "the dump must name the wedged target from the registry");
    assertTrue(
        report.contains("IgnoreStopApp.main"),
        "the dump must contain the target's own threads, captured while it was still alive");
    assertTrue(
        report.contains("==== end of exit-wedge dump ===="),
        "the dump must be complete, not truncated by the force-kill");
  }

  private Path onlyDumpFile() throws Exception {
    List<Path> dumps = new ArrayList<>();
    for (File file : dumpDir.toFile().listFiles()) {
      if (file.getName().startsWith("exit-wedge-") && file.getName().endsWith(".txt")) {
        dumps.add(file.toPath());
      }
    }
    if (dumps.size() > 1) {
      throw new AssertionError("expected a single exit-wedge dump, found " + dumps.size());
    }
    return dumps.isEmpty() ? null : dumps.get(0);
  }
}
