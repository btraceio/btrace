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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.exec.QeEnvironment;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.VerdictValue;
import io.btrace.qe.report.ReproCommand;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class QeMainTest {
  private static final long TIMEOUT = QeEnvironment.DEFAULT_TIMEOUT_MS;

  @Test
  void commandLineIncludesTierOnlyWhenSet() {
    assertFalse(QeMain.commandLine(null, null, 1L, TIMEOUT, null, 0).contains("qe.tier"));
    assertTrue(
        QeMain.commandLine(null, null, 1L, TIMEOUT, "smoke", 0).contains(" -Pqe.tier=smoke"));
  }

  @Test
  void reproCommandIncludesTierOnlyWhenSet() {
    CaseDef def =
        new CaseDef(
            "cli.x",
            "cli",
            Intent.RESILIENCE,
            "t",
            null,
            GivenDef.client(List.of("-p")),
            List.of("runClient", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList()),
            CaseDef.TIER_FULL);
    assertEquals(
        "./gradlew :btrace-qe:qe -Pqe.case=cli.x", ReproCommand.forCase(def, TIMEOUT, null, 7L, 5));
    assertEquals(
        "./gradlew :btrace-qe:qe -Pqe.case=cli.x -Pqe.tier=smoke",
        ReproCommand.forCase(def, TIMEOUT, "smoke", 7L, 5));
  }

  @Test
  void normalizeTierTreatsFullAndEmptyAsDefault() {
    assertEquals(null, QeMain.normalizeTier(null));
    assertEquals(null, QeMain.normalizeTier(""));
    assertEquals(null, QeMain.normalizeTier("full"));
    assertEquals("smoke", QeMain.normalizeTier("smoke"));
    assertEquals("bogus", QeMain.normalizeTier("bogus"));
  }

  @Test
  void unknownTierRunsNoCasesButStillWritesTheReport() throws Exception {
    Path root = Files.createTempDirectory("qe-main-tier");
    Path libs = Files.createDirectories(root.resolve("libs"));
    Files.write(libs.resolve("btrace.jar"), new byte[0]);
    Path corpus = Files.createDirectories(root.resolve("cases"));
    Path report = root.resolve("report");
    String[] keys = {
      "qe.javaHome",
      "btrace.libs",
      "qe.probeClassesDir",
      "qe.targetsClasspath",
      "qe.repoRoot",
      "qe.corpusDir",
      "qe.reportDir",
      "qe.tier"
    };
    String[] values = {
      root.toString(),
      libs.toString(),
      root.toString(),
      root.toString(),
      root.toString(),
      corpus.toString(),
      report.toString(),
      "bogus"
    };
    String[] saved = new String[keys.length];
    String[] comm = {
      "btrace.comm.protocol", "btrace.comm.autoNegotiate", "btrace.comm.forceVersion"
    };
    String[] savedComm = new String[comm.length];
    for (int i = 0; i < comm.length; i++) {
      savedComm[i] = System.getProperty(comm[i]);
    }
    for (int i = 0; i < keys.length; i++) {
      saved[i] = System.getProperty(keys[i]);
      System.setProperty(keys[i], values[i]);
    }
    try {
      QeMain.run();
    } finally {
      restore(keys, saved);
      restore(comm, savedComm);
    }
    String md = new String(Files.readAllBytes(report.resolve("report.md")), StandardCharsets.UTF_8);
    String json =
        new String(Files.readAllBytes(report.resolve("report.json")), StandardCharsets.UTF_8);
    assertTrue(md.contains("unknown qe.tier 'bogus' (expected smoke or full); no cases run"), md);
    assertTrue(json.contains("\"tier\": \"bogus\""), json);
    assertTrue(json.contains("\"cases\": []"), json);
  }

  private static void restore(String[] keys, String[] saved) {
    for (int i = 0; i < keys.length; i++) {
      if (saved[i] == null) {
        System.clearProperty(keys[i]);
      } else {
        System.setProperty(keys[i], saved[i]);
      }
    }
  }

  private static CaseDef anyCase() {
    return new CaseDef(
        "x.case",
        "x",
        Intent.RESILIENCE,
        "t",
        null,
        GivenDef.client(List.of("-p")),
        List.of("runClient", "awaitExit"),
        new ExpectationDef(null, Collections.<String>emptyList()),
        CaseDef.TIER_FULL);
  }

  private static CaseVerdict withFinding(Severity severity) {
    return new CaseVerdict(
        anyCase(),
        VerdictValue.FAIL,
        VerdictValue.NA,
        VerdictValue.NA,
        List.of(Finding.of("x.case", severity, "s", "d")));
  }

  private static CaseVerdict clean() {
    return new CaseVerdict(
        anyCase(),
        VerdictValue.PASS,
        VerdictValue.NA,
        VerdictValue.NA,
        Collections.<Finding>emptyList());
  }

  private static CaseVerdict harnessError() {
    return new CaseVerdict(
        anyCase(),
        VerdictValue.NA,
        VerdictValue.NA,
        VerdictValue.NA,
        Collections.<Finding>emptyList(),
        "harness: boom");
  }

  @Test
  void withoutFailOnTheHarnessOnlyReports() {
    assertEquals(0, QeMain.exitStatus(List.of(withFinding(Severity.S1), harnessError()), null));
    assertEquals(0, QeMain.exitStatus(List.of(withFinding(Severity.S1)), ""));
  }

  @Test
  void findingsAtOrAboveFailOnFailTheRun() {
    assertEquals(1, QeMain.exitStatus(List.of(withFinding(Severity.S2)), "S2"));
    assertEquals(1, QeMain.exitStatus(List.of(clean(), withFinding(Severity.S1)), "S2"));
    assertEquals(0, QeMain.exitStatus(List.of(withFinding(Severity.S3)), "S2"));
    assertEquals(0, QeMain.exitStatus(List.of(withFinding(Severity.S4), clean()), "S3"));
    assertEquals(1, QeMain.exitStatus(List.of(withFinding(Severity.S4)), "S4"));
    assertEquals(0, QeMain.exitStatus(Collections.<CaseVerdict>emptyList(), "S1"));
  }

  @Test
  void harnessErrorsFailTheRunWheneverFailOnIsSet() {
    assertEquals(1, QeMain.exitStatus(List.of(clean(), harnessError()), "S4"));
    assertEquals(1, QeMain.exitStatus(List.of(harnessError()), "S1"));
  }

  @Test
  void failOnAcceptsLowerCaseAndRejectsUnknownValues() {
    assertEquals(1, QeMain.exitStatus(List.of(withFinding(Severity.S2)), "s2"));
    assertEquals(1, QeMain.exitStatus(Collections.<CaseVerdict>emptyList(), "S9"));
    assertEquals(1, QeMain.exitStatus(List.of(clean()), "high"));
  }
}
