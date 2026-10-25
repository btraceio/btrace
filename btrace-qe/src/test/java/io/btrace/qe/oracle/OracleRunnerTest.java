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
package io.btrace.qe.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.exec.StepOutcome;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.SpecRef;
import io.btrace.qe.model.VerdictValue;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OracleRunnerTest {
  private static final List<String> TARGET_STEPS =
      List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit");

  private static ExecutionTrace trace(
      boolean timedOut,
      boolean premature,
      Integer exit,
      List<String> out,
      List<String> err,
      Boolean canary,
      String dump,
      boolean hsErr) {
    // every listed step ran to completion; the scenarios differ only in what was observed
    List<StepOutcome> steps =
        TARGET_STEPS.stream().map(s -> StepOutcome.ok(s, 1)).collect(Collectors.toList());
    return new ExecutionTrace(
        steps, timedOut, premature, exit, out, err, canary, dump, hsErr, null);
  }

  private static CaseDef target(String id, List<String> mentions) {
    return new CaseDef(
        id,
        "agent-args",
        Intent.RESILIENCE,
        id,
        null,
        GivenDef.target("SteadyStateTarget", "x"),
        TARGET_STEPS,
        new ExpectationDef(null, mentions),
        CaseDef.TIER_FULL);
  }

  private static CaseDef client(String id, int exit, List<String> mentions) {
    return new CaseDef(
        id,
        "cli",
        Intent.CONFORMANCE,
        id,
        new SpecRef("docs/QuickReference.md", "usage on bad args"),
        GivenDef.client(List.of("-p")),
        List.of("runClient", "awaitExit"),
        new ExpectationDef(exit, mentions),
        CaseDef.TIER_FULL);
  }

  @Test
  void silentlyIgnoredValueIsResilientButUnguiding() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("a", List.of("fileRollMilliseconds")),
                trace(
                    false,
                    false,
                    0,
                    List.of("ready:1", "checksum:1", "checksum:2"),
                    List.of(),
                    true,
                    null,
                    false));
    assertEquals(VerdictValue.PASS, v.resilience());
    assertEquals(VerdictValue.NA, v.conformance());
    assertEquals(VerdictValue.POOR, v.guidance());
    assertEquals(1, v.findings().size());
    assertEquals(Severity.S4, v.findings().get(0).severity());
  }

  @Test
  void prematureExitIsS1AndNotAlsoS2() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("b", Collections.<String>emptyList()),
                trace(
                    false,
                    true,
                    1,
                    List.of(),
                    List.of("BTrace agent initialization failed: x"),
                    null,
                    null,
                    false));
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertEquals(1, v.findings().size());
    assertEquals(Severity.S1, v.findings().get(0).severity());
  }

  @Test
  void canaryFailureOnLiveTargetIsS2() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("c", Collections.<String>emptyList()),
                trace(false, false, 0, List.of("ready:1"), List.of(), false, null, false));
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertEquals(Severity.S2, v.findings().get(0).severity());
  }

  @Test
  void deadlockOnTimeoutIsS1() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("d", Collections.<String>emptyList()),
                trace(
                    true,
                    false,
                    null,
                    List.of("ready:1"),
                    List.of(),
                    null,
                    "Found one Java-level deadlock:\n=============================",
                    false));
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertTrue(v.findings().stream().anyMatch(f -> f.severity() == Severity.S1));
  }

  @Test
  void rawStackTraceIsPoorGuidanceEvenWithoutExpectedTokens() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("e", Collections.<String>emptyList()),
                trace(
                    false,
                    false,
                    0,
                    List.of("ready:1"),
                    List.of(
                        "java.lang.IllegalStateException: boom",
                        "\tat io.btrace.agent.Main.main(Main.java:300)"),
                    true,
                    null,
                    false));
    assertEquals(VerdictValue.POOR, v.guidance());
    assertTrue(v.findings().stream().anyMatch(f -> f.severity() == Severity.S4));
  }

  @Test
  void conformanceMismatchIsS3() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                client("f", 1, Collections.<String>emptyList()),
                trace(false, false, 0, List.of(), List.of(), null, null, false));
    assertEquals(VerdictValue.FAIL, v.conformance());
    assertTrue(v.findings().stream().anyMatch(f -> f.severity() == Severity.S3));
  }

  @Test
  void failedStepWithoutTimeoutIsAHarnessErrorNotAProductFinding() {
    ExecutionTrace t =
        new ExecutionTrace(
            List.of(
                StepOutcome.ok("launch", 10),
                StepOutcome.ok("awaitReady", 10),
                StepOutcome.failed("canary", 1, "java.lang.IllegalStateException: no pid")),
            false,
            false,
            null,
            List.of("ready:1"),
            List.of(),
            null,
            null,
            false,
            null);
    CaseVerdict v = new OracleRunner().evaluate(target("h", List.of("fileRollMilliseconds")), t);
    assertEquals(VerdictValue.NA, v.resilience());
    assertEquals(VerdictValue.NA, v.conformance());
    assertEquals(VerdictValue.NA, v.guidance());
    assertTrue(v.findings().isEmpty(), "findings=" + v.findings());
    assertEquals("canary: java.lang.IllegalStateException: no pid", v.harnessError());
  }

  @Test
  void awaitReadyTimeoutWithCanaryListedIsS1Only() {
    ExecutionTrace t =
        new ExecutionTrace(
            List.of(
                StepOutcome.ok("launch", 10), StepOutcome.failed("awaitReady", 30000, "timed out")),
            true,
            false,
            null,
            List.of(),
            List.of(),
            null,
            "Full thread dump",
            false,
            null);
    CaseVerdict v = new OracleRunner().evaluate(target("i", Collections.<String>emptyList()), t);
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertNull(v.harnessError());
    assertEquals(1, v.findings().size(), "findings=" + v.findings());
    assertEquals(Severity.S1, v.findings().get(0).severity());
  }

  @Test
  void timedOutConformanceCaseHasNaConformanceAndNoS3() {
    ExecutionTrace t =
        new ExecutionTrace(
            List.of(
                StepOutcome.ok("runClient", 10),
                StepOutcome.failed("awaitExit", 30000, "timed out")),
            true,
            false,
            null,
            List.of(),
            List.of(),
            null,
            null,
            false,
            null);
    CaseVerdict v = new OracleRunner().evaluate(client("j", 1, Collections.<String>emptyList()), t);
    assertEquals(VerdictValue.NA, v.conformance());
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertFalse(v.findings().stream().anyMatch(f -> f.severity() == Severity.S3));
  }

  @Test
  void cleanCaseHasNoFindings() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                client("g", 1, List.of("Usage")),
                trace(
                    false,
                    false,
                    1,
                    List.of(),
                    List.of("Usage: btrace <options> <pid> <btrace source>"),
                    null,
                    null,
                    false));
    assertEquals(VerdictValue.PASS, v.resilience());
    assertEquals(VerdictValue.PASS, v.conformance());
    assertEquals(VerdictValue.GOOD, v.guidance());
    assertTrue(v.findings().isEmpty());
  }
}
