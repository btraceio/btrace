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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GuidanceCheck implements InvariantCheck {
  private static final Pattern STACK_FRAME = Pattern.compile("^\\s+at [\\w$./<>]+\\(.*\\)$");

  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    List<String> required = def.expect().messageMustMention();
    String firstFrame = null;
    for (String line : trace.stderr()) {
      if (STACK_FRAME.matcher(line).matches()) {
        firstFrame = line.trim();
        break;
      }
    }
    if (required.isEmpty() && firstFrame == null) {
      return CheckResult.of(
          CheckOutcome.NOT_APPLICABLE, "case specifies no guidance and none was mis-shown");
    }
    if (firstFrame != null) {
      return CheckResult.of(
          CheckOutcome.FAIL,
          "a raw stack trace is shown to the user on the default path (first frame: "
              + firstFrame
              + ")");
    }
    String combined = String.join("\n", trace.stdout()) + "\n" + String.join("\n", trace.stderr());
    List<String> missing = new ArrayList<>();
    for (String token : required) {
      if (!combined.contains(token)) {
        missing.add(token);
      }
    }
    if (missing.isEmpty()) {
      return CheckResult.of(CheckOutcome.PASS, "diagnostic output mentions " + required);
    }
    return CheckResult.of(
        CheckOutcome.FAIL,
        "diagnostic output never mentions "
            + missing
            + "; the problem is not surfaced to the user");
  }
}
