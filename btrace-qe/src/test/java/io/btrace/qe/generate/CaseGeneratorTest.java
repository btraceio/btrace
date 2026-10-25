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
package io.btrace.qe.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.corpus.YamlCaseLoader;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.report.ReproCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseGeneratorTest {
  private static final List<String> EXCLUDED =
      Arrays.asList(
          "script",
          "scriptdir",
          "port",
          "noServer",
          "stdout",
          "systemClassPath",
          "bootClassPath",
          "agentJar",
          "config",
          "libs",
          "authTokenFile",
          "probeDescPath",
          "dumpDir",
          "scriptOutputFile",
          "scriptOutputDir",
          "help",
          "telemetry");

  @Test
  void sameSeedYieldsIdenticalCases() throws Exception {
    List<CaseDef> a = CaseGenerator.generate(42L, 12);
    List<CaseDef> b = CaseGenerator.generate(42L, 12);
    assertEquals(fingerprint(a), fingerprint(b));
    assertFalse(a.isEmpty());
  }

  @Test
  void differentSeedYieldsDifferentCases() throws Exception {
    assertNotEquals(
        fingerprint(CaseGenerator.generate(1L, 20)), fingerprint(CaseGenerator.generate(2L, 20)));
    // beyond the seed in the id, the generated content itself must differ
    assertNotEquals(
        content(CaseGenerator.generate(1L, 20)), content(CaseGenerator.generate(2L, 20)));
  }

  @Test
  void budgetIsPerFamilyAndRespected() {
    for (MutatorFamily family : CaseGenerator.families()) {
      assertEquals(7, family.generate(new java.util.Random(1), 7).size(), family.id());
      assertEquals(0, family.generate(new java.util.Random(1), 0).size(), family.id());
    }
    assertEquals(0, CaseGenerator.generate(42L, 0).size());
    assertEquals(2 * 5, CaseGenerator.generate(42L, 5).size());
  }

  @Test
  void idsFollowTheConventionAndAreUnique() {
    List<CaseDef> cases = CaseGenerator.generate(42L, 10);
    Set<String> ids = new HashSet<>();
    for (CaseDef def : cases) {
      assertTrue(def.id().matches("gen\\.(agent-args\\.values|cli\\.flags)\\.42\\.\\d+"), def.id());
      assertTrue(ids.add(def.id()), "duplicate " + def.id());
    }
  }

  @Test
  void everyCaseIsValidFullTierResilienceWithEmptyExpectations() {
    for (CaseDef def : CaseGenerator.generate(7L, 40)) {
      def.validate();
      assertEquals(CaseDef.TIER_FULL, def.tier());
      assertEquals(Intent.RESILIENCE, def.intent());
      assertEquals(null, def.expect().exitCode());
      assertTrue(def.expect().messageMustMention().isEmpty(), def.id());
      assertTrue(def.surface().equals("agent-args") || def.surface().equals("cli"));
    }
  }

  @Test
  void excludedOptionsNeverAppearAsMutatedOption() {
    for (long seed = 0; seed < 5; seed++) {
      for (CaseDef def : CaseGenerator.generate(seed, 200)) {
        if (def.given().mode() != GivenDef.Mode.TARGET) {
          continue;
        }
        String mutated = mutatedOption(def.given().agentArgs());
        assertFalse(EXCLUDED.contains(mutated), def.id() + " mutates " + mutated);
      }
    }
  }

  @Test
  void agentOptionNamesComeFromArgsAndSkipExcluded() {
    List<String> names = AgentArgsValuesFamily.optionNames();
    assertTrue(names.contains("fileRollMilliseconds"));
    assertTrue(names.contains("debug"));
    for (String excluded : EXCLUDED) {
      assertFalse(names.contains(excluded), excluded);
    }
    List<String> sorted = new ArrayList<>(names);
    java.util.Collections.sort(sorted);
    assertEquals(sorted, names);
  }

  @Test
  void valuesArePassableOnACommandLine() {
    for (CaseDef def : CaseGenerator.generate(3L, 300)) {
      List<String> parts = new ArrayList<>(def.given().clientArgs());
      if (def.given().agentArgs() != null) {
        parts.add(def.given().agentArgs());
      }
      for (String part : parts) {
        for (int i = 0; i < part.length(); i++) {
          char c = part.charAt(i);
          assertTrue(c >= 0x20 && c != 0x7f, def.id() + " contains control char " + (int) c);
        }
      }
    }
  }

  @Test
  void clientCasesEndWithNonExistentJvmName() {
    for (CaseDef def : CaseGenerator.generate(5L, 50)) {
      if (def.given().mode() == GivenDef.Mode.CLIENT) {
        List<String> args = def.given().clientArgs();
        assertEquals("qe-no-such-script.java", args.get(args.size() - 1), def.id());
        assertEquals("2147483647", args.get(args.size() - 2), def.id());
        assertEquals(Arrays.asList("runClient", "awaitExit"), def.steps());
      } else {
        assertEquals(
            Arrays.asList("launch", "awaitReady", "canary", "shutdown", "awaitExit"), def.steps());
        assertTrue(
            def.given()
                .agentArgs()
                .startsWith(
                    "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,"
                        + "noServer=false,stdout=true,"));
      }
    }
  }

  @Test
  void writtenCaseYamlRoundTripsThroughTheLoaderOnceRenamed(@TempDir Path dir) throws Exception {
    List<CaseDef> cases = CaseGenerator.generate(42L, 60);
    int n = 0;
    for (CaseDef def : cases) {
      // promoting a finding means renaming its id out of the reserved gen. namespace
      String yaml = CaseYamlWriter.toYaml(def).replaceFirst("(?m)^id: gen\\.", "id: promoted.");
      Files.write(dir.resolve("c" + (n++) + ".yaml"), yaml.getBytes("UTF-8"));
    }
    List<String> expected = new ArrayList<>();
    for (CaseDef def : cases) {
      expected.add(content(def));
    }
    List<String> actual = new ArrayList<>();
    for (CaseDef def : new YamlCaseLoader().loadAll(dir)) {
      assertTrue(def.id().startsWith("promoted."), def.id());
      actual.add(content(def));
    }
    java.util.Collections.sort(expected);
    java.util.Collections.sort(actual);
    assertEquals(expected, actual);
  }

  @Test
  void reproCommandCarriesSeedAndGenerateWhenGenerating() {
    CaseDef def = CaseGenerator.generate(42L, 1).get(0);
    String cmd = ReproCommand.forCase(def, 30_000L, null, 42L, 5);
    assertTrue(cmd.contains(" -Pqe.seed=42"), cmd);
    assertTrue(cmd.contains(" -Pqe.generate=5"), cmd);
    assertFalse(ReproCommand.forCase(def, 30_000L, null, 42L, 0).contains("qe.generate"));
    CaseDef handWritten =
        new CaseDef(
            "cli.x",
            "cli",
            Intent.RESILIENCE,
            "t",
            null,
            GivenDef.client(Arrays.asList("-p")),
            Arrays.asList("runClient", "awaitExit"),
            new io.btrace.qe.model.ExpectationDef(null, new ArrayList<String>()),
            CaseDef.TIER_FULL);
    assertEquals(
        "./gradlew :btrace-qe:qe -Pqe.case=cli.x",
        ReproCommand.forCase(handWritten, 30_000L, null, 42L, 5));
  }

  @Test
  void loaderRejectsCorpusIdsInTheGeneratedNamespace(@TempDir Path dir) throws Exception {
    CaseDef def = CaseGenerator.generate(1L, 1).get(0);
    CaseYamlWriter.write(def, dir.resolve("reserved.yaml"));
    org.junit.jupiter.api.function.Executable load = () -> new YamlCaseLoader().loadAll(dir);
    io.btrace.qe.model.CaseValidationException e =
        org.junit.jupiter.api.Assertions.assertThrows(
            io.btrace.qe.model.CaseValidationException.class, load);
    assertTrue(e.getMessage().contains("reserved.yaml"), e.getMessage());
    assertTrue(e.getMessage().contains("reserved"), e.getMessage());
  }

  private static String mutatedOption(String agentArgs) {
    String tail = agentArgs.substring(agentArgs.indexOf("stdout=true,") + "stdout=true,".length());
    int eq = tail.indexOf('=');
    return eq < 0 ? tail : tail.substring(0, eq);
  }

  private static List<String> fingerprint(List<CaseDef> cases) {
    List<String> out = new ArrayList<>();
    for (CaseDef d : cases) {
      out.add(d.id() + "|" + content(d));
    }
    return out;
  }

  private static String content(List<CaseDef> cases) {
    StringBuilder sb = new StringBuilder();
    for (CaseDef d : cases) {
      sb.append(content(d)).append('\n');
    }
    return sb.toString();
  }

  private static String content(CaseDef d) {
    return d.surface()
        + "|"
        + d.title()
        + "|"
        + d.given().mode()
        + "|"
        + d.given().agentArgs()
        + "|"
        + d.given().clientArgs()
        + "|"
        + d.steps()
        + "|"
        + d.tier();
  }
}
