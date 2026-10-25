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

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CaseRegistryTest {

  private CaseDef caseOf(String id, String surface) {
    return caseOf(id, surface, "full");
  }

  private CaseDef caseOf(String id, String surface, String tier) {
    return new CaseDef(
        id,
        surface,
        Intent.RESILIENCE,
        id,
        null,
        GivenDef.client(List.of("-p")),
        List.of("runClient", "awaitExit"),
        new ExpectationDef(null, Collections.<String>emptyList()),
        tier);
  }

  @Test
  void sortsByIdAndRejectsDuplicates() {
    CaseRegistry registry =
        CaseRegistry.of(List.of(caseOf("b.case", "cli"), caseOf("a.case", "cli")));
    assertEquals(
        List.of("a.case", "b.case"),
        registry.all().stream().map(CaseDef::id).collect(Collectors.toList()));
    assertThrows(
        CaseValidationException.class,
        () -> CaseRegistry.of(List.of(caseOf("dup", "cli"), caseOf("dup", "cli"))));
  }

  @Test
  void filtersBySurfaceAndCaseId() {
    CaseRegistry registry =
        CaseRegistry.of(
            List.of(
                caseOf("cli.one", "cli"),
                caseOf("cli.two", "cli"),
                caseOf("agent-args.one", "agent-args")));
    assertEquals(2, registry.filter("cli", null, null).size());
    assertEquals(3, registry.filter("cli, agent-args", null, null).size());
    assertEquals(1, registry.filter(null, "agent-args.one", null).size());
    assertEquals(3, registry.filter(null, null, null).size());
    assertEquals(0, registry.filter("script-compile", null, null).size());
  }

  @Test
  void filtersByTier() {
    CaseRegistry registry =
        CaseRegistry.of(
            List.of(
                caseOf("cli.fast", "cli", "smoke"),
                caseOf("cli.slow", "cli", "full"),
                caseOf("agent-args.fast", "agent-args", "smoke")));
    assertEquals(2, registry.filter(null, null, "smoke").size());
    assertEquals(3, registry.filter(null, null, "full").size());
    assertEquals(3, registry.filter(null, null, null).size());
    assertEquals(3, registry.filter(null, null, "").size());
    assertEquals(
        List.of("cli.fast"),
        registry.filter("cli", null, "smoke").stream()
            .map(CaseDef::id)
            .collect(Collectors.toList()));
    assertEquals(0, registry.filter(null, "cli.slow", "smoke").size());
  }
}
