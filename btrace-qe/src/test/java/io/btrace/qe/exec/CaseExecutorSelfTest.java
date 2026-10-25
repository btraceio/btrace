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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.SpecRef;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("qe-self-test")
class CaseExecutorSelfTest {
  private static final String STARTUP =
      "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true,noServer=false";
  private static final List<String> TARGET_STEPS =
      List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit");

  private static CaseDef targetCase(String id, String agentArgs) {
    return new CaseDef(
        id,
        "fixture",
        Intent.RESILIENCE,
        id,
        null,
        GivenDef.target("SteadyStateTarget", agentArgs),
        TARGET_STEPS,
        new ExpectationDef(null, Collections.<String>emptyList()),
        CaseDef.TIER_FULL);
  }

  @Test
  void happyPathTargetCase() throws Exception {
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(targetCase("t.happy", STARTUP), Files.createTempDirectory("qe-exec-happy"));
    assertFalse(trace.timedOut(), "steps=" + trace.steps().size());
    assertFalse(trace.exitedPrematurely());
    assertEquals(Integer.valueOf(0), trace.exitCode());
    assertEquals(Boolean.TRUE, trace.canaryOk());
    assertTrue(trace.stdout().contains("qe-startup-ok"));
  }

  @Test
  void targetThatDiesAtStartupIsAPrematureExitNotAHang() throws Exception {
    CaseDef def =
        new CaseDef(
            "t.dies",
            "fixture",
            Intent.RESILIENCE,
            "dies",
            null,
            GivenDef.target("NoSuchTarget", STARTUP),
            TARGET_STEPS,
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
    long start = System.currentTimeMillis();
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-dies"));
    assertFalse(trace.timedOut());
    assertTrue(trace.exitedPrematurely());
    assertTrue(trace.exitCode() != null && trace.exitCode() != 0);
    assertTrue(System.currentTimeMillis() - start < 20_000, "should not wait out the step timeout");
  }

  @Test
  void clientModeCapturesExitCode() throws Exception {
    CaseDef def =
        new CaseDef(
            "c.missing-operand",
            "fixture",
            Intent.CONFORMANCE,
            "missing operand",
            new SpecRef("docs/QuickReference.md", "usage on bad args"),
            GivenDef.client(List.of("-p")),
            List.of("runClient", "awaitExit"),
            new ExpectationDef(1, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-client"));
    assertFalse(trace.timedOut());
    assertFalse(trace.exitedPrematurely());
    assertEquals(Integer.valueOf(1), trace.exitCode(), "stderr=" + trace.stderr());
  }

  @Test
  void targetThatDiesAfterReadyIsAPrematureExit() throws Exception {
    CaseDef def =
        new CaseDef(
            "t.dies-after-ready",
            "fixture",
            Intent.RESILIENCE,
            "dies after ready",
            null,
            GivenDef.target("ExitAfterReadyTarget", STARTUP),
            List.of("launch", "awaitReady", "awaitExit", "shutdown"),
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-dies-after-ready"));
    assertFalse(trace.timedOut());
    assertTrue(trace.exitedPrematurely());
    assertEquals(Integer.valueOf(3), trace.exitCode());
  }

  @Test
  void wedgedTargetTimesOutWithThreadDump() throws Exception {
    // shortest allowed per-step timeout, scoped to this test by setting/restoring qe.timeout
    String previous = System.getProperty("qe.timeout");
    System.setProperty("qe.timeout", "10000");
    QeEnvironment env;
    try {
      env = QeEnvironment.fromSystemProperties();
    } finally {
      if (previous == null) {
        System.clearProperty("qe.timeout");
      } else {
        System.setProperty("qe.timeout", previous);
      }
    }
    CaseDef def =
        new CaseDef(
            "t.deaf",
            "fixture",
            Intent.RESILIENCE,
            "deaf target",
            null,
            GivenDef.target("DeafTarget", STARTUP),
            List.of("launch", "awaitReady", "shutdown", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
    long start = System.currentTimeMillis();
    ExecutionTrace trace =
        new CaseExecutor(env).execute(def, Files.createTempDirectory("qe-exec-deaf"));
    long elapsed = System.currentTimeMillis() - start;
    assertTrue(trace.timedOut());
    assertTrue(trace.threadDumpText() != null, "thread dump expected");
    assertTrue(elapsed < 10_000 + 15_000, "elapsed=" + elapsed);
    assertTrue(trace.threadDumpText().contains("Full thread dump"), trace.threadDumpText());
    long pid = 0;
    for (String line : trace.stdout()) {
      if (line.startsWith("ready:")) {
        pid = Long.parseLong(line.substring("ready:".length()).trim());
      }
    }
    assertTrue(pid > 0, "ready line expected");
    final long target = pid;
    long deadline = System.currentTimeMillis() + 5_000;
    while (ProcessHandle.of(target).map(ProcessHandle::isAlive).orElse(false)
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertFalse(
        ProcessHandle.of(target).map(ProcessHandle::isAlive).orElse(false), "target left alive");
  }
}
