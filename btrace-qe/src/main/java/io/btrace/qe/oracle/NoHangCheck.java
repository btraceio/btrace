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
import java.util.regex.Pattern;

public final class NoHangCheck implements InvariantCheck {
  private static final Pattern DEADLOCK = Pattern.compile("Found (one|\\d+) Java-level deadlock");

  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    if (!trace.timedOut()) {
      return CheckResult.of(CheckOutcome.PASS, "every step completed within its timeout");
    }
    String dump = trace.threadDumpText();
    if (dump != null && DEADLOCK.matcher(dump).find()) {
      return CheckResult.of(
          CheckOutcome.FAIL, "a step timed out and the thread dump shows a deadlock");
    }
    return CheckResult.of(
        CheckOutcome.FAIL,
        "a step timed out with the process still alive"
            + (dump != null ? "; no deadlock in the thread dump (see thread-dump.txt)" : ""));
  }
}
