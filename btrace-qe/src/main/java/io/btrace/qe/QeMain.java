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
package io.btrace.qe;

import io.btrace.qe.corpus.CaseRegistry;
import io.btrace.qe.corpus.YamlCaseLoader;
import io.btrace.qe.exec.CaseExecutor;
import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.exec.QeEnvironment;
import io.btrace.qe.generate.CaseGenerator;
import io.btrace.qe.generate.CaseYamlWriter;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.VerdictValue;
import io.btrace.qe.oracle.OracleRunner;
import io.btrace.qe.report.EvidenceStore;
import io.btrace.qe.report.JsonReporter;
import io.btrace.qe.report.MarkdownReporter;
import io.btrace.qe.report.ReproCommand;
import io.btrace.qe.report.RunMetadata;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QeMain {
  private QeMain() {}

  public static void main(String[] args) {
    try {
      run();
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      System.exit(0);
    }
  }

  /** Empty and {@code full} are the default tier and are not recorded; other values are kept. */
  static String normalizeTier(String raw) {
    return raw == null || raw.isEmpty() || CaseDef.TIER_FULL.equals(raw) ? null : raw;
  }

  static void run() throws Exception {
    System.setProperty("btrace.comm.protocol", "2");
    System.setProperty("btrace.comm.autoNegotiate", "false");
    System.setProperty("btrace.comm.forceVersion", "true");

    QeEnvironment env = QeEnvironment.fromSystemProperties();
    long seed = Long.parseLong(System.getProperty("qe.seed", "20260811"));
    String surface = System.getProperty("qe.surface");
    String caseId = System.getProperty("qe.case");
    String tier = normalizeTier(System.getProperty("qe.tier"));
    String note = null;
    if (tier != null && !CaseDef.isValidTier(tier)) {
      note = "unknown qe.tier '" + tier + "' (expected smoke or full); no cases run";
    }

    int generate = parseGenerate(System.getProperty("qe.generate"));

    List<CaseDef> all = new ArrayList<>();
    all.addAll(new YamlCaseLoader().loadAll(Paths.get(System.getProperty("qe.corpusDir"))));
    all.addAll(CaseGenerator.generate(seed, generate));
    List<CaseDef> selected =
        note != null
            ? Collections.<CaseDef>emptyList()
            : CaseRegistry.of(all).filter(surface, caseId, tier);

    Path reportDir = Paths.get(System.getProperty("qe.reportDir"));
    Path runsDir = reportDir.resolve("runs");
    Files.createDirectories(runsDir);

    List<CaseVerdict> verdicts = new ArrayList<>();
    for (CaseDef def : selected) {
      Path runDir = runsDir.resolve(def.id());
      System.out.println("[qe] " + def.id());
      try {
        resetRunDir(runDir);
        if (CaseGenerator.isGenerated(def)) {
          CaseYamlWriter.write(def, runDir.resolve("case.yaml"));
        }
        ExecutionTrace trace = new CaseExecutor(env).execute(def, runDir);
        new EvidenceStore().persist(trace, runDir);
        verdicts.add(new OracleRunner().evaluate(def, trace));
      } catch (Exception | StackOverflowError | AssertionError | LinkageError e) {
        recordHarnessError(def, runDir, e, verdicts);
      }
    }

    if (generate == 0 && caseId != null && caseId.startsWith(CaseGenerator.ID_PREFIX)) {
      System.out.println(
          "[qe] WARNING: generated case ids need -Pqe.generate=<n> and the original -Pqe.seed");
    }
    if (note == null && selected.isEmpty()) {
      note =
          "no cases matched surface="
              + surface
              + " case="
              + caseId
              + (tier != null ? " tier=" + tier : "");
    }
    if (note != null) {
      System.out.println("[qe] WARNING: " + note);
    }
    RunMetadata meta =
        RunMetadata.capture(
            seed,
            commandLine(surface, caseId, seed, env.defaultTimeoutMs(), tier, generate),
            env.defaultTimeoutMs(),
            tier,
            generate);
    new JsonReporter().write(meta, verdicts, reportDir.resolve("report.json"));
    new MarkdownReporter().write(meta, verdicts, reportDir.resolve("report.md"), note);

    long findings = 0;
    long harnessErrors = 0;
    for (CaseVerdict v : verdicts) {
      findings += v.findings().size();
      if (v.harnessError() != null) {
        harnessErrors++;
      }
    }
    System.out.println(
        "[qe] ran "
            + selected.size()
            + " case(s), "
            + findings
            + " finding(s), "
            + harnessErrors
            + " harness error(s). Report: "
            + reportDir.resolve("report.md"));
  }

  /**
   * Evidence from a previous run of the same case (an old hs_err file, a stale thread dump) must
   * never be judged as fresh, so the case's run directory is wiped before it runs. Other cases'
   * directories are left alone. The walk does not follow symlinks, so it is bounded by the tree.
   */
  private static void resetRunDir(Path runDir) throws IOException {
    if (Files.exists(runDir, LinkOption.NOFOLLOW_LINKS)) {
      Files.walkFileTree(
          runDir,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              if (exc != null) {
                throw exc;
              }
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
    Files.createDirectories(runDir);
  }

  private static void recordHarnessError(
      CaseDef def, Path runDir, Throwable e, List<CaseVerdict> verdicts) {
    System.out.println("[qe] HARNESS ERROR " + def.id() + ": " + e);
    try {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      Files.write(
          runDir.resolve("harness-error.txt"), sw.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // best effort: the verdict below is recorded regardless
    }
    verdicts.add(
        new CaseVerdict(
            def,
            VerdictValue.NA,
            VerdictValue.NA,
            VerdictValue.NA,
            Collections.<Finding>emptyList(),
            "harness: " + e));
  }

  /** Non-numeric or negative {@code qe.generate} means off (0). */
  static int parseGenerate(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      System.out.println("[qe] WARNING: ignoring non-numeric qe.generate '" + raw + "'");
      return 0;
    }
  }

  static String commandLine(
      String surface, String caseId, long seed, long timeoutMs, String tier, int generate) {
    StringBuilder sb = new StringBuilder("./gradlew :btrace-qe:qe -Pqe.seed=").append(seed);
    if (surface != null) {
      sb.append(" -Pqe.surface=").append(surface);
    }
    if (caseId != null) {
      sb.append(" -Pqe.case=").append(caseId);
    }
    if (generate > 0) {
      sb.append(" -Pqe.generate=").append(generate);
    }
    sb.append(ReproCommand.tierFlag(tier));
    sb.append(ReproCommand.timeoutFlag(timeoutMs));
    return sb.toString();
  }
}
