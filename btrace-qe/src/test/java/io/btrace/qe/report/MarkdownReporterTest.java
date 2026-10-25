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
package io.btrace.qe.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.VerdictValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownReporterTest {

  @Test
  void reportsFindingWithEvidenceAndRepro() throws Exception {
    CaseDef def =
        new CaseDef(
            "agent-args.fixture",
            "agent-args",
            Intent.RESILIENCE,
            "fixture case",
            null,
            GivenDef.target("SteadyStateTarget", "x"),
            List.of("launch", "awaitReady", "shutdown", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList()));
    CaseVerdict verdict =
        new CaseVerdict(
            def,
            VerdictValue.PASS,
            VerdictValue.NA,
            VerdictValue.POOR,
            List.of(Finding.of(def.id(), Severity.S4, "the user is not guided", "detail text")));
    RunMetadata meta =
        new RunMetadata(1L, "abc123", "3.0.0-SNAPSHOT", "jdk", "os", 4, "./gradlew :btrace-qe:qe");
    Path out = Files.createTempDirectory("qe-md").resolve("report.md");

    new MarkdownReporter().write(meta, List.of(verdict), out);

    String content = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    assertTrue(content.contains("### S4 — agent-args.fixture"));
    assertTrue(content.contains("-Pqe.case=agent-args.fixture"));
    assertTrue(content.contains("runs/agent-args.fixture/"));
    assertTrue(content.contains("| 1 | 0 | 0 | 0 | 1 | 0 |"));
  }

  @Test
  void reportsHarnessErrorsSeparatelyFromFindings() throws Exception {
    CaseDef def =
        new CaseDef(
            "agent-args.broken",
            "agent-args",
            Intent.RESILIENCE,
            "broken case",
            null,
            GivenDef.target("SteadyStateTarget", "x"),
            List.of("launch", "awaitReady", "shutdown", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList()));
    CaseVerdict verdict =
        new CaseVerdict(
            def,
            VerdictValue.NA,
            VerdictValue.NA,
            VerdictValue.NA,
            Collections.<Finding>emptyList(),
            "launch: java.io.IOException: no java");
    RunMetadata meta =
        new RunMetadata(
            1L, "abc123", "3.0.0-SNAPSHOT", "jdk", "os", 4, "./gradlew :btrace-qe:qe", 45_000L);
    Path out = Files.createTempDirectory("qe-md-harness").resolve("report.md");

    new MarkdownReporter().write(meta, List.of(verdict), out, "no cases matched surface=x case=y");

    String content = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    assertTrue(content.contains("| Harness errors |"), content);
    assertTrue(content.contains("| 1 | 0 | 0 | 0 | 0 | 1 |"), content);
    assertTrue(
        content.contains("| agent-args.broken | agent-args | NA | NA | NA | launch: java.io"),
        content);
    assertTrue(content.contains("WARNING:** no cases matched surface=x case=y"), content);
    assertTrue(content.contains("No findings."), content);
  }
}
