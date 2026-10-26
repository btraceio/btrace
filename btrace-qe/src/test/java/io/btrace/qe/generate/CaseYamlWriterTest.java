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

import io.btrace.qe.corpus.YamlCaseLoader;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseYamlWriterTest {

  @Test
  void attachCaseRoundTripsThroughTheLoader() throws Exception {
    CaseDef def =
        new CaseDef(
            "demos.round-trip",
            "demos",
            Intent.RESILIENCE,
            "round trip",
            null,
            GivenDef.attach("AttachDemoTarget", List.of("-n", "X::y { print method }", "${pid}")),
            List.of(
                "launch", "awaitReady", "attach", "awaitOutput", "detach", "shutdown", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList(), List.of("^work$")),
            CaseDef.TIER_SMOKE);
    Path dir = Files.createTempDirectory("qe-yaml-writer");
    Files.write(
        dir.resolve("case.yaml"), CaseYamlWriter.toYaml(def).getBytes(StandardCharsets.UTF_8));
    List<CaseDef> loaded = new YamlCaseLoader().loadAll(dir);
    assertEquals(1, loaded.size());
    CaseDef back = loaded.get(0);
    assertEquals(GivenDef.Mode.ATTACH, back.given().mode());
    assertEquals("AttachDemoTarget", back.given().target());
    assertEquals(def.given().clientArgs(), back.given().clientArgs());
    assertEquals(def.steps(), back.steps());
    assertEquals(List.of("^work$"), back.expect().outputMustMatch());
    assertEquals(CaseDef.TIER_SMOKE, back.tier());
  }
}
