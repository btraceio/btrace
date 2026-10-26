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

/** Replays shipped demo scenarios: a demo app launched from its source file, a prepared JVM. */
@Tag("qe-self-test")
class DemoAttachSelfTest {
  private static final List<String> ATTACH_STEPS =
      List.of("launch", "awaitReady", "attach", "awaitOutput", "detach", "shutdown", "awaitExit");

  private static CaseDef attachCase(String id, GivenDef given, List<String> output) {
    return new CaseDef(
        id,
        "fixture",
        Intent.RESILIENCE,
        id,
        null,
        given,
        ATTACH_STEPS,
        new ExpectationDef(null, Collections.<String>emptyList(), output),
        CaseDef.TIER_FULL);
  }

  @Test
  void demo01OnelinerAgainstTheOrderServiceSourceFile() throws Exception {
    List<String> output = List.of("^execution time: \\d+ ms$");
    CaseDef def =
        attachCase(
            "d.01",
            GivenDef.attach(
                    "demos/01-oneliner/OrderServiceApp.java",
                    List.of(
                        "-p",
                        "${port}",
                        "-n",
                        "OrderService::processOrder @return { print method, time }",
                        "${pid}"))
                .withReadyRegex("^\\[demo\\] order service running"),
            output);
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-demo-01"));
    assertNull(
        trace.harnessError(), "stderr=" + trace.stderr() + " client=" + trace.clientStderr());
    assertFalse(trace.timedOut(), "steps=" + trace.steps());
    assertFalse(trace.exitedPrematurely());
    assertTrue(trace.exitCode() != null, "target exit code recorded");
    assertNull(
        OutputCheck.firstUnmatched(output, trace.clientStdout()),
        "client stdout=" + trace.clientStdout() + " stderr=" + trace.clientStderr());
    assertTrue(
        trace.stdout().stream().anyMatch(l -> l.startsWith("[demo] order service running")),
        "target stdout=" + trace.stdout());
  }

  @Test
  void preparedModeJvmArgsAllowAttachingWithoutAPort() throws Exception {
    List<String> output = List.of("^work$");
    CaseDef def =
        attachCase(
            "d.prepared",
            GivenDef.attach(
                    "AttachDemoTarget",
                    List.of(
                        "-n",
                        "qe.targets.AttachDemoTarget::work @return { print method }",
                        "${pid}"))
                .withJvmArgs(List.of("-javaagent:${btraceJar}=port=0")),
            output);
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(def, Files.createTempDirectory("qe-demo-prepared"));
    assertNull(
        trace.harnessError(), "stderr=" + trace.stderr() + " client=" + trace.clientStderr());
    assertFalse(trace.timedOut(), "steps=" + trace.steps());
    assertEquals(Integer.valueOf(0), trace.exitCode(), "target stdout=" + trace.stdout());
    assertNull(
        OutputCheck.firstUnmatched(output, trace.clientStdout()),
        "client stdout=" + trace.clientStdout() + " stderr=" + trace.clientStderr());
  }
}
