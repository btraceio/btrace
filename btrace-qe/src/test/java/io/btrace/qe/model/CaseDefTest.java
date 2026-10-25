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
package io.btrace.qe.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseDefTest {

  private static CaseDef caseOf(
      Intent intent, SpecRef spec, GivenDef given, List<String> steps, Integer exitCode) {
    return new CaseDef(
        "x.case",
        "x",
        intent,
        "title",
        spec,
        given,
        steps,
        new ExpectationDef(exitCode, Collections.<String>emptyList()));
  }

  @Test
  void validTargetCasePasses() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.target("SteadyStateTarget", "script=X.class"),
            List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit"),
            null);
    assertDoesNotThrow(def::validate);
  }

  @Test
  void conformanceCaseWithoutSpecFails() {
    CaseDef def =
        caseOf(
            Intent.CONFORMANCE,
            null,
            GivenDef.client(List.of("-p")),
            List.of("runClient", "awaitExit"),
            1);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("spec"));
  }

  @Test
  void exitCodeOnResilienceCaseFails() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.client(List.of("-p")),
            List.of("runClient", "awaitExit"),
            1);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("exitCode"));
  }

  @Test
  void targetCaseMustStartWithLaunch() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.target("SteadyStateTarget", ""),
            List.of("awaitReady", "launch", "shutdown", "awaitExit"),
            null);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("launch"), ex.getMessage());
  }

  @Test
  void targetCaseCanaryBeforeAwaitReadyFails() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.target("SteadyStateTarget", ""),
            List.of("launch", "canary", "awaitReady", "shutdown", "awaitExit"),
            null);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("'awaitReady' must precede 'canary'"), ex.getMessage());
  }

  @Test
  void targetCaseShutdownWithoutAwaitReadyFails() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.target("SteadyStateTarget", ""),
            List.of("launch", "shutdown", "awaitExit"),
            null);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("'awaitReady' must precede 'shutdown'"), ex.getMessage());
  }

  @Test
  void clientCaseMustStartWithRunClient() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.client(List.of("-p")),
            List.of("awaitExit", "runClient"),
            null);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("runClient"), ex.getMessage());
  }

  @Test
  void stepNotValidForModeFails() {
    CaseDef def =
        caseOf(Intent.RESILIENCE, null, GivenDef.client(List.of("-p")), List.of("canary"), null);
    assertThrows(CaseValidationException.class, def::validate);
  }
}
