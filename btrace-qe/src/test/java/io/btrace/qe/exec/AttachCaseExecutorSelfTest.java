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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.oracle.OutputCheck;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Replays a demo: target without the agent, the real CLI attached by PID with a oneliner. */
@Tag("qe-self-test")
class AttachCaseExecutorSelfTest {
  private static final List<String> ONELINER_ARGS =
      List.of(
          "-p",
          "${port}",
          "-n",
          "qe.targets.AttachDemoTarget::work @return { print method, time }",
          "${pid}");
  private static final List<String> OUTPUT = List.of("^work$", "execution time: \\d+ ms");

  private static CaseDef attachCase(String id, List<String> steps, List<String> output) {
    return attachCase(id, ONELINER_ARGS, steps, output);
  }

  private static CaseDef attachCase(
      String id, List<String> clientArgs, List<String> steps, List<String> output) {
    return new CaseDef(
        id,
        "fixture",
        Intent.RESILIENCE,
        id,
        null,
        GivenDef.attach("AttachDemoTarget", clientArgs),
        steps,
        new ExpectationDef(null, Collections.<String>emptyList(), output),
        CaseDef.TIER_FULL);
  }

  @Test
  void onelinerAttachedByPidProducesTheExpectedOutput() throws Exception {
    CaseDef def =
        attachCase(
            "a.happy",
            List.of(
                "launch", "awaitReady", "attach", "awaitOutput", "detach", "shutdown", "awaitExit"),
            OUTPUT);
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-attach"));
    assertNull(
        trace.harnessError(), "stderr=" + trace.stderr() + " client=" + trace.clientStderr());
    assertFalse(trace.timedOut(), "steps=" + trace.steps().size());
    assertFalse(trace.exitedPrematurely());
    assertEquals(Integer.valueOf(0), trace.exitCode(), "target exit code");
    assertNull(
        OutputCheck.firstUnmatched(OUTPUT, trace.clientStdout()),
        "client stdout=" + trace.clientStdout() + " stderr=" + trace.clientStderr());
    assertTrue(
        trace.stdout().stream().anyMatch(l -> l.startsWith("ready:")),
        "target stdout=" + trace.stdout());
  }

  @Test
  void detachLeavesTheTargetRunningAndTheClientGone() throws Exception {
    CaseDef def =
        attachCase(
            "a.detach",
            List.of(
                "launch", "awaitReady", "attach", "awaitOutput", "detach", "shutdown", "awaitExit"),
            List.of("^work$"));
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-detach"));
    assertNull(trace.harnessError(), "client=" + trace.clientStderr());
    // the target was still alive to be asked to exit, so it reports a clean 'exiting:0'
    assertTrue(trace.stdout().contains("exiting:0"), "target stdout=" + trace.stdout());
    assertEquals(Integer.valueOf(0), trace.exitCode());
    // detach terminates the client before the target is stopped, so its step completed in time
    assertTrue(
        trace.steps().stream().anyMatch(s -> "detach".equals(s.step()) && s.success()),
        "steps=" + trace.steps());
  }

  @Test
  void shutdownWithoutDetachStillStopsTheTargetCleanly() throws Exception {
    CaseDef def =
        attachCase(
            "a.no-detach",
            List.of("launch", "awaitReady", "attach", "awaitOutput", "shutdown", "awaitExit"),
            List.of("^work$"));
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-no-detach"));
    assertNull(trace.harnessError(), "client=" + trace.clientStderr());
    assertFalse(trace.timedOut());
    assertEquals(Integer.valueOf(0), trace.exitCode());
    assertNull(OutputCheck.firstUnmatched(List.of("^work$"), trace.clientStdout()));
  }

  @Test
  void outputThatNeverAppearsIsReportedNotHung() throws Exception {
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
        attachCase(
            "a.missing",
            List.of(
                "launch", "awaitReady", "attach", "awaitOutput", "detach", "shutdown", "awaitExit"),
            List.of("^this line is never printed$"));
    long start = System.currentTimeMillis();
    ExecutionTrace trace =
        new CaseExecutor(env).execute(def, Files.createTempDirectory("qe-exec-missing"));
    long elapsed = System.currentTimeMillis() - start;
    assertNull(trace.harnessError(), "client=" + trace.clientStderr());
    assertFalse(trace.timedOut(), "awaitOutput must give up before the step budget");
    assertEquals(Integer.valueOf(0), trace.exitCode(), "the target is still shut down cleanly");
    assertEquals(
        "^this line is never printed$",
        OutputCheck.firstUnmatched(def.expect().outputMustMatch(), trace.clientStdout()));
    assertTrue(elapsed < 10_000 + 25_000, "elapsed=" + elapsed);
  }

  @Test
  void awaitClientExitWaitsForAClientThatEndsOnItsOwn() throws Exception {
    // -l lists the local JVMs and exits; the target must be among them by the time the step ends
    CaseDef def =
        attachCase(
            "a.client-exit",
            List.of("-l"),
            List.of("launch", "awaitReady", "attach", "awaitClientExit", "shutdown", "awaitExit"),
            Collections.<String>emptyList());
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-client-exit"));
    assertNull(trace.harnessError(), "client=" + trace.clientStderr());
    assertFalse(trace.timedOut(), "steps=" + trace.steps());
    assertTrue(
        trace.steps().stream().anyMatch(s -> "awaitClientExit".equals(s.step()) && s.success()),
        "steps=" + trace.steps());
    assertTrue(
        trace.clientStdout().stream().anyMatch(l -> l.contains("AttachDemoTarget")),
        "client stdout=" + trace.clientStdout());
    assertEquals(Integer.valueOf(0), trace.exitCode(), "target exit code");
    assertEquals(Integer.valueOf(0), trace.clientExitCode(), "client exit code");
  }

  @Test
  void clientExitCodeIsRecordedWhenTheClientFails() throws Exception {
    // the target has no agent, so -r cannot find a control endpoint and the client exits 1
    CaseDef def =
        attachCase(
            "a.client-fails",
            List.of("-r", "qe-some-probe", "exit", "${pid}"),
            List.of("launch", "awaitReady", "attach", "awaitClientExit", "shutdown", "awaitExit"),
            Collections.<String>emptyList());
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-exec-client-fails"));
    assertNull(trace.harnessError(), "client=" + trace.clientStderr());
    assertEquals(
        Integer.valueOf(1), trace.clientExitCode(), "client stderr=" + trace.clientStderr());
  }

  @Test
  void awaitClientExitOnAClientThatNeverEndsIsAHang() throws Exception {
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
    // an attached oneliner keeps tracing until it is detached, so it never exits by itself
    CaseDef def =
        attachCase(
            "a.client-hang",
            List.of("launch", "awaitReady", "attach", "awaitClientExit", "shutdown", "awaitExit"),
            Collections.<String>emptyList());
    ExecutionTrace trace =
        new CaseExecutor(env).execute(def, Files.createTempDirectory("qe-exec-client-hang"));
    assertTrue(trace.timedOut(), "steps=" + trace.steps());
    StepOutcome last = trace.steps().get(trace.steps().size() - 1);
    assertEquals("awaitClientExit", last.step());
    assertFalse(last.success());
    assertNull(trace.clientExitCode(), "a client the harness killed has no exit code of its own");
  }
}
