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
import io.btrace.qe.exec.StepOutcome;
import io.btrace.qe.model.CaseDef;

public final class CanaryCheck implements InvariantCheck {
  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    if (!def.steps().contains("canary")) {
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "case has no canary step");
    }
    boolean canaryRan = false;
    for (StepOutcome s : trace.steps()) {
      if ("canary".equals(s.step())) {
        canaryRan = true;
        break;
      }
    }
    if (!canaryRan) {
      // an earlier step stopped the case (e.g. awaitReady timed out); that step owns the verdict
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "the canary step never ran");
    }
    if (trace.exitedPrematurely()) {
      // the crash check already owns this; a dead JVM is not additionally an S2
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "target was not alive to canary");
    }
    if (!Boolean.TRUE.equals(trace.canaryOk())) {
      return CheckResult.of(
          CheckOutcome.FAIL,
          "canary probe produced no output after the abuse; BTrace can no longer operate on this JVM");
    }
    return CheckResult.of(CheckOutcome.PASS, "canary probe produced output after the abuse");
  }
}
