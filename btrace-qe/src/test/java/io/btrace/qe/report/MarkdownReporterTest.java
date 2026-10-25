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
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
    CaseVerdict verdict =
        new CaseVerdict(
            def,
            VerdictValue.PASS,
            VerdictValue.NA,
            VerdictValue.POOR,
            List.of(Finding.of(def.id(), Severity.S4, "the user is not guided", "detail text")));
    RunMetadata meta =
        new RunMetadata(
            1L,
            "abc123",
            "3.0.0-SNAPSHOT",
            "jdk",
            "os",
            4,
            "./gradlew :btrace-qe:qe",
            30_000L,
            null);
    Path out = Files.createTempDirectory("qe-md").resolve("report.md");

    new MarkdownReporter().write(meta, List.of(verdict), out);

    String content = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    assertTrue(content.contains("### S4 — agent-args.fixture"));
    assertTrue(content.contains("-Pqe.case=agent-args.fixture"));
    assertTrue(content.contains("runs/agent-args.fixture/"));
    assertTrue(content.contains("| 1 | 0 | 0 | 0 | 1 | 0 |"));
    assertTrue(content.contains("- tier: full\n"), content);
  }

  @Test
  void recordsTheTierInHeaderAndReproCommand() throws Exception {
    CaseDef def =
        new CaseDef(
            "cli.fixture",
            "cli",
            Intent.RESILIENCE,
            "fixture case",
            null,
            GivenDef.client(List.of("-p")),
            List.of("runClient", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_SMOKE);
    CaseVerdict verdict =
        new CaseVerdict(
            def,
            VerdictValue.PASS,
            VerdictValue.NA,
            VerdictValue.POOR,
            List.of(Finding.of(def.id(), Severity.S4, "s", "d")));
    RunMetadata meta = new RunMetadata(1L, "abc", "3", "jdk", "os", 4, "cmd", 30_000L, "smoke");
    Path dir = Files.createTempDirectory("qe-tier");

    new MarkdownReporter().write(meta, List.of(verdict), dir.resolve("report.md"));
    new JsonReporter().write(meta, List.of(verdict), dir.resolve("report.json"));

    String md = new String(Files.readAllBytes(dir.resolve("report.md")), StandardCharsets.UTF_8);
    String json =
        new String(Files.readAllBytes(dir.resolve("report.json")), StandardCharsets.UTF_8);
    assertTrue(md.contains("- tier: smoke\n"), md);
    assertTrue(md.contains("-Pqe.case=cli.fixture -Pqe.tier=smoke"), md);
    assertTrue(json.contains("\"tier\": \"smoke\""), json);
  }

  @Test
  void jsonTierIsNullWhenNotSet() throws Exception {
    RunMetadata meta = new RunMetadata(1L, "abc", "3", "jdk", "os", 4, "cmd", 30_000L, null);
    Path out = Files.createTempDirectory("qe-tier-null").resolve("report.json");
    new JsonReporter().write(meta, Collections.<CaseVerdict>emptyList(), out);
    String json = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    assertTrue(json.contains("\"tier\": null"), json);
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
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
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
            1L,
            "abc123",
            "3.0.0-SNAPSHOT",
            "jdk",
            "os",
            4,
            "./gradlew :btrace-qe:qe",
            45_000L,
            null);
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
