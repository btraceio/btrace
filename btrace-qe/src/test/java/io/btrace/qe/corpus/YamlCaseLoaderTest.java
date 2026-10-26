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
package io.btrace.qe.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlCaseLoaderTest {

  private Path fixtureDir(String... names) throws Exception {
    Path tmp = Files.createTempDirectory("qe-corpus-test");
    for (String name : names) {
      Path source = Paths.get(getClass().getResource("/io/btrace/qe/corpus/" + name).toURI());
      Files.copy(source, tmp.resolve(name));
    }
    return tmp;
  }

  @Test
  void loadsAndValidatesAValidCase() throws Exception {
    List<CaseDef> cases = new YamlCaseLoader().loadAll(fixtureDir("valid-case.yaml"));
    assertEquals(1, cases.size());
    CaseDef def = cases.get(0);
    assertEquals("fixture.valid", def.id());
    assertEquals(Intent.RESILIENCE, def.intent());
    assertEquals(GivenDef.Mode.TARGET, def.given().mode());
    assertEquals(List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit"), def.steps());
    assertEquals(List.of("example"), def.expect().messageMustMention());
  }

  private CaseDef loadWithTier(String tierLine) throws Exception {
    Path dir = Files.createTempDirectory("qe-corpus-tier");
    String yaml =
        "id: fixture.tier\nsurface: fixture\nintent: resilience\ntitle: t\n"
            + tierLine
            + "given:\n  mode: client\n  clientArgs: [\"-p\"]\n"
            + "steps: [runClient, awaitExit]\nexpect: {}\n";
    Files.write(dir.resolve("tier-case.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    return new YamlCaseLoader().loadAll(dir).get(0);
  }

  @Test
  void tierDefaultsToFullAndAcceptsSmokeAndFull() throws Exception {
    assertEquals("full", loadWithTier("").tier());
    assertEquals("smoke", loadWithTier("tier: smoke\n").tier());
    assertEquals("full", loadWithTier("tier: full\n").tier());
  }

  @Test
  void rejectsAnUnknownTierNamingTheFile() {
    for (String bad : new String[] {"tier: nightly\n", "tier: Smoke\n", "tier: 1\n"}) {
      CaseValidationException ex =
          assertThrows(CaseValidationException.class, () -> loadWithTier(bad));
      assertTrue(ex.getMessage().contains("tier-case.yaml"), ex.getMessage());
      assertTrue(ex.getMessage().contains("tier"), ex.getMessage());
    }
  }

  @Test
  void rejectsAConformanceCaseMissingSpec() throws Exception {
    Path dir = fixtureDir("conformance-missing-spec.yaml");
    CaseValidationException ex =
        assertThrows(CaseValidationException.class, () -> new YamlCaseLoader().loadAll(dir));
    assertTrue(ex.getMessage().contains("conformance cases require spec.doc"));
  }

  @Test
  void rejectsKeysBelongingToTheOtherGivenMode() throws Exception {
    Path dir = Files.createTempDirectory("qe-corpus-given");
    String yaml =
        "id: fixture.wrong-mode\nsurface: fixture\nintent: resilience\ntitle: t\n"
            + "given:\n  mode: client\n  clientArgs: [\"-p\"]\n  agentArgs: \"x=1\"\n"
            + "steps: [runClient, awaitExit]\n"
            + "expect:\n  messageMustMention: [\"x\"]\n";
    Files.write(dir.resolve("wrong-mode.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    CaseValidationException ex =
        assertThrows(CaseValidationException.class, () -> new YamlCaseLoader().loadAll(dir));
    assertTrue(ex.getMessage().contains("wrong-mode.yaml"));
    assertTrue(ex.getMessage().contains("agentArgs"));
  }

  @Test
  void rejectsUnknownKeysWithTheFileName() throws Exception {
    Path dir = Files.createTempDirectory("qe-corpus-typo");
    String yaml =
        "id: fixture.typo\nsurface: fixture\nintent: resilience\ntitle: t\n"
            + "given:\n  mode: client\n  clientArgs: [\"-p\"]\n"
            + "steps: [runClient, awaitExit]\n"
            + "expect:\n  messageMustMentoin: [\"x\"]\n";
    Files.write(dir.resolve("typo.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    CaseValidationException ex =
        assertThrows(CaseValidationException.class, () -> new YamlCaseLoader().loadAll(dir));
    assertTrue(ex.getMessage().contains("typo.yaml"));
    assertTrue(ex.getMessage().contains("messageMustMentoin"));
  }

  @Test
  void skipsNonYamlFiles() throws Exception {
    Path dir = fixtureDir("valid-case.yaml");
    Files.write(dir.resolve("notes.txt"), "ignore me".getBytes(StandardCharsets.UTF_8));
    assertEquals(1, new YamlCaseLoader().loadAll(dir).size());
  }

  private static final String ATTACH_HEAD =
      "id: fixture.attach\nsurface: demos\nintent: resilience\ntitle: t\n";
  private static final String ATTACH_STEPS =
      "steps: [launch, awaitReady, attach, awaitOutput, detach, shutdown, awaitExit]\n";

  private List<CaseDef> loadYaml(String name, String yaml) throws Exception {
    Path dir = Files.createTempDirectory("qe-corpus-attach");
    Files.write(dir.resolve(name), yaml.getBytes(StandardCharsets.UTF_8));
    return new YamlCaseLoader().loadAll(dir);
  }

  @Test
  void loadsAnAttachCaseWithOutputMustMatch() throws Exception {
    List<CaseDef> cases =
        loadYaml(
            "attach.yaml",
            ATTACH_HEAD
                + "given:\n  mode: attach\n  target: AttachDemoTarget\n"
                + "  clientArgs: [\"-n\", \"X::y { print method }\", \"${pid}\"]\n"
                + ATTACH_STEPS
                + "expect:\n  outputMustMatch: [\"^work$\", \"execution time: \\\\d+ ms\"]\n");
    assertEquals(1, cases.size());
    CaseDef def = cases.get(0);
    assertEquals(GivenDef.Mode.ATTACH, def.given().mode());
    assertEquals("AttachDemoTarget", def.given().target());
    assertEquals(List.of("-n", "X::y { print method }", "${pid}"), def.given().clientArgs());
    assertEquals(List.of("^work$", "execution time: \\d+ ms"), def.expect().outputMustMatch());
  }

  @Test
  void rejectsAgentArgsInAttachMode() throws Exception {
    CaseValidationException ex =
        assertThrows(
            CaseValidationException.class,
            () ->
                loadYaml(
                    "attach-agent-args.yaml",
                    ATTACH_HEAD
                        + "given:\n  mode: attach\n  target: AttachDemoTarget\n  agentArgs: \"\"\n"
                        + "  clientArgs: [\"${pid}\"]\n"
                        + ATTACH_STEPS
                        + "expect:\n  outputMustMatch: [\"work\"]\n"));
    assertTrue(ex.getMessage().contains("agentArgs"), ex.getMessage());
    assertTrue(ex.getMessage().contains("attach-agent-args.yaml"), ex.getMessage());
  }

  @Test
  void attachModeRequiresClientArgs() throws Exception {
    CaseValidationException ex =
        assertThrows(
            CaseValidationException.class,
            () ->
                loadYaml(
                    "attach-no-client-args.yaml",
                    ATTACH_HEAD
                        + "given:\n  mode: attach\n  target: AttachDemoTarget\n"
                        + ATTACH_STEPS
                        + "expect:\n  outputMustMatch: [\"work\"]\n"));
    assertTrue(ex.getMessage().contains("clientArgs"), ex.getMessage());
  }

  @Test
  void outputMustMatchMustBeANonEmptyList() throws Exception {
    CaseValidationException ex =
        assertThrows(
            CaseValidationException.class,
            () ->
                loadYaml(
                    "attach-bad-expect.yaml",
                    ATTACH_HEAD
                        + "given:\n  mode: attach\n  target: AttachDemoTarget\n"
                        + "  clientArgs: [\"${pid}\"]\n"
                        + ATTACH_STEPS
                        + "expect:\n  outputMustMatch: work\n"));
    assertTrue(ex.getMessage().contains("outputMustMatch"), ex.getMessage());
  }
}
