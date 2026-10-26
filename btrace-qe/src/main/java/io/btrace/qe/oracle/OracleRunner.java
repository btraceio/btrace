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
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.VerdictValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OracleRunner {

  public CaseVerdict evaluate(CaseDef def, ExecutionTrace trace) {
    String harnessError = trace.harnessError();
    if (harnessError != null) {
      // the harness failed, not the product: no dimension can be judged
      return new CaseVerdict(
          def,
          VerdictValue.NA,
          VerdictValue.NA,
          VerdictValue.NA,
          Collections.<Finding>emptyList(),
          harnessError);
    }
    List<Finding> findings = new ArrayList<>();
    VerdictValue resilience = VerdictValue.PASS;

    resilience =
        apply(new NoCrashCheck(), def, trace, Severity.S1, "process crashed", findings, resilience);
    resilience =
        apply(
            new NoHangCheck(),
            def,
            trace,
            Severity.S1,
            "process hung or deadlocked",
            findings,
            resilience);
    resilience =
        apply(
            new NoCorruptionCheck(),
            def,
            trace,
            Severity.S1,
            "target state corrupted",
            findings,
            resilience);
    resilience =
        apply(
            new CanaryCheck(),
            def,
            trace,
            Severity.S2,
            "BTrace stopped operating after the abuse",
            findings,
            resilience);
    resilience =
        apply(
            new OutputCheck(),
            def,
            trace,
            Severity.S2,
            "the attached probe did not produce the expected output",
            findings,
            resilience);

    VerdictValue conformance = VerdictValue.NA;
    // a timed-out case has no exit code by construction; the hang is already an S1
    if (def.intent() == Intent.CONFORMANCE && !trace.timedOut()) {
      Integer expected = def.expect().exitCode();
      boolean ok = expected == null || expected.equals(trace.exitCode());
      conformance = ok ? VerdictValue.PASS : VerdictValue.FAIL;
      if (!ok) {
        findings.add(
            Finding.of(
                def.id(),
                Severity.S3,
                "behavior does not match " + def.spec().doc() + ": " + def.spec().claim(),
                "expected exit code " + expected + ", observed " + trace.exitCode()));
      }
    }

    CheckResult guidance = new GuidanceCheck().check(def, trace);
    VerdictValue guidanceVerdict;
    if (guidance.outcome() == CheckOutcome.NOT_APPLICABLE) {
      guidanceVerdict = VerdictValue.NA;
    } else if (guidance.outcome() == CheckOutcome.PASS) {
      guidanceVerdict = VerdictValue.GOOD;
    } else {
      guidanceVerdict = VerdictValue.POOR;
      findings.add(Finding.of(def.id(), Severity.S4, "the user is not guided", guidance.detail()));
    }

    return new CaseVerdict(def, resilience, conformance, guidanceVerdict, findings);
  }

  private static VerdictValue apply(
      InvariantCheck check,
      CaseDef def,
      ExecutionTrace trace,
      Severity severity,
      String summary,
      List<Finding> findings,
      VerdictValue current) {
    CheckResult result = check.check(def, trace);
    if (result.outcome() == CheckOutcome.FAIL) {
      findings.add(Finding.of(def.id(), severity, summary, result.detail()));
      return VerdictValue.FAIL;
    }
    return current;
  }
}
