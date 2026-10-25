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

public final class NoCrashCheck implements InvariantCheck {
  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    if (trace.hasHsErrLog()) {
      return CheckResult.of(CheckOutcome.FAIL, "the JVM produced a fatal error log (hs_err_pid*)");
    }
    if (trace.exitedPrematurely()) {
      return CheckResult.of(
          CheckOutcome.FAIL,
          "the target application terminated before it was asked to (exit code "
              + trace.exitCode()
              + "); BTrace took the user's application down");
    }
    for (String line : trace.stderr()) {
      if (line.startsWith("Exception in thread \"main\"")) {
        return CheckResult.of(
            CheckOutcome.FAIL, "the process died with an uncaught exception: " + line);
      }
    }
    return CheckResult.of(CheckOutcome.PASS, "no crash indicators observed");
  }
}
