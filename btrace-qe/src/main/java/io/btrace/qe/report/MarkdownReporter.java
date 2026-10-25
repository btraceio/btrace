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

import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.Severity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MarkdownReporter {

  public void write(RunMetadata meta, List<CaseVerdict> verdicts, Path outFile) throws IOException {
    write(meta, verdicts, outFile, null);
  }

  /** Writes the report; a non-null {@code note} is shown as a warning under the run metadata. */
  public void write(RunMetadata meta, List<CaseVerdict> verdicts, Path outFile, String note)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("# BTrace QE Report\n\n");
    sb.append("- seed: ")
        .append(meta.seed())
        .append(" (recorded; no generated cases in this phase)\n");
    sb.append("- git SHA: ").append(meta.gitSha()).append('\n');
    sb.append("- btrace version: ").append(meta.btraceVersion()).append('\n');
    sb.append("- target JDK: ").append(meta.jdk()).append('\n');
    sb.append("- OS: ").append(meta.os()).append(", CPUs: ").append(meta.cpuCount()).append('\n');
    sb.append("- tier: ").append(meta.tier() != null ? meta.tier() : "full").append('\n');
    sb.append("- command: `").append(meta.commandLine()).append("`\n\n");
    if (note != null) {
      sb.append("> **WARNING:** ").append(note).append("\n\n");
    }

    Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
    int harnessErrors = 0;
    for (CaseVerdict v : verdicts) {
      if (v.harnessError() != null) {
        harnessErrors++;
      }
      for (Finding f : v.findings()) {
        counts.merge(f.severity(), 1, Integer::sum);
      }
    }
    sb.append(
        "## Summary\n\n| Cases run | S1 | S2 | S3 | S4 | Harness errors |\n"
            + "|---|---|---|---|---|---|\n");
    sb.append("| ").append(verdicts.size());
    for (Severity s : Severity.values()) {
      sb.append(" | ").append(counts.getOrDefault(s, 0));
    }
    sb.append(" | ").append(harnessErrors).append(" |\n\n");

    sb.append("## Findings\n\n");
    boolean any = false;
    for (Severity s : Severity.values()) {
      for (CaseVerdict v : verdicts) {
        for (Finding f : v.findings()) {
          if (f.severity() != s) {
            continue;
          }
          any = true;
          sb.append("### ").append(s).append(" — ").append(v.def().id()).append('\n');
          sb.append("- tried: ").append(v.def().title()).append('\n');
          sb.append("- finding: ").append(f.summary()).append('\n');
          sb.append("- detail: ").append(f.detail()).append('\n');
          sb.append("- evidence: `btrace-qe/build/qe-report/runs/")
              .append(v.def().id())
              .append("/`\n");
          sb.append("- repro: `")
              .append(ReproCommand.forCase(v.def(), meta.timeoutMs(), meta.tier()))
              .append("`\n\n");
        }
      }
    }
    if (!any) {
      sb.append("No findings.\n\n");
    }

    sb.append(
        "## All cases\n\n| id | surface | resilience | conformance | guidance | harness error |\n"
            + "|---|---|---|---|---|---|\n");
    for (CaseVerdict v : verdicts) {
      sb.append("| ")
          .append(v.def().id())
          .append(" | ")
          .append(v.def().surface())
          .append(" | ")
          .append(v.resilience())
          .append(" | ")
          .append(v.conformance())
          .append(" | ")
          .append(v.guidance())
          .append(" | ")
          .append(v.harnessError() != null ? cell(v.harnessError()) : "")
          .append(" |\n");
    }
    Files.createDirectories(outFile.getParent());
    Files.write(outFile, sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** Keeps free text from breaking the table row. */
  private static String cell(String text) {
    return text.replace("|", "\\|").replace('\r', ' ').replace('\n', ' ');
  }
}
