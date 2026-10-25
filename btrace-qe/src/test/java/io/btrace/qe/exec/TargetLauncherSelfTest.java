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
package io.btrace.qe.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.GivenDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("qe-self-test")
class TargetLauncherSelfTest {
  private static final String STARTUP =
      "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true,noServer=false";
  private static final String STARTUP_NO_SERVER =
      "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true,noServer=true";

  @Test
  void targetStartsWithStartupProbeAndExitsOnCommand() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-launcher");
    TargetProcess target =
        new TargetLauncher().launch(GivenDef.target("SteadyStateTarget", STARTUP), env, runDir);
    try {
      String ready = target.awaitLine("ready:", 20_000);
      assertNotNull(ready, "no ready line; stderr=" + target.stderrSnapshot());
      target.assignPid(Long.parseLong(ready.substring("ready:".length()).trim()));
      assertNotNull(target.awaitLine("qe-startup-ok", 20_000), "startup probe did not run");
      long start = System.currentTimeMillis();
      assertTrue(
          new ClientProbe().runCanary(target, env, 20_000),
          "canary failed on a healthy target; stderr=" + target.stderrSnapshot());
      assertTrue(target.isAlive(), "canary detach must not kill the target");
      assertTrue(
          System.currentTimeMillis() - start < 30_000, "canary exceeded its bounded lifetime");
      target.writeStdin("exit");
      assertTrue(target.process().waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, target.process().exitValue());
    } finally {
      target.destroyForcibly();
    }
  }

  @Test
  void awaitLineReturnsEarlyWhenTheProcessDies() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-launcher-dead");
    TargetProcess target =
        new TargetLauncher().launch(GivenDef.target("NoSuchTarget", STARTUP), env, runDir);
    try {
      long start = System.currentTimeMillis();
      assertNull(target.awaitLine("ready:", 20_000));
      assertTrue(
          System.currentTimeMillis() - start < 15_000, "awaitLine waited for the full timeout");
    } finally {
      target.destroyForcibly();
    }
  }

  @Test
  void clientRunsThroughTheMaskedJarLoader() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-client");
    TargetProcess client = new ClientLauncher().launch(Arrays.asList("--version"), env, runDir);
    try {
      assertTrue(client.process().waitFor(20, TimeUnit.SECONDS));
      assertEquals(0, client.process().exitValue(), "stderr=" + client.stderrSnapshot());
      assertTrue(client.stdoutSnapshot().size() > 0);
    } finally {
      client.destroyForcibly();
    }
  }

  @Test
  void canaryIsBoundedWhenTheAgentPortIsDead() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-canary-dead");
    TargetProcess target =
        new TargetLauncher()
            .launch(GivenDef.target("SteadyStateTarget", STARTUP_NO_SERVER), env, runDir);
    try {
      String ready = target.awaitLine("ready:", 20_000);
      assertNotNull(ready, "no ready line; stderr=" + target.stderrSnapshot());
      target.assignPid(Long.parseLong(ready.substring("ready:".length()).trim()));
      long start = System.currentTimeMillis();
      new ClientProbe().runCanary(target, env, 5_000);
      assertTrue(
          System.currentTimeMillis() - start < 5_000 + 10_000, "canary blocked past its bound");
    } finally {
      target.destroyForcibly();
    }
  }
}
