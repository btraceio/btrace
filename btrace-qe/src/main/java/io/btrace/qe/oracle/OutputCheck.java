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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Judges {@code expect.outputMustMatch} against the attached client's stdout: every pattern must
 * match some line, and each later pattern only counts after the line that satisfied the previous
 * one.
 */
public final class OutputCheck implements InvariantCheck {

  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    List<String> patterns = def.expect().outputMustMatch();
    if (patterns.isEmpty()) {
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "case expects no client output");
    }
    boolean awaited = false;
    for (StepOutcome s : trace.steps()) {
      if ("awaitOutput".equals(s.step())) {
        awaited = true;
        break;
      }
    }
    if (!awaited) {
      // an earlier step stopped the case; that step owns the verdict
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "the awaitOutput step never ran");
    }
    String missing = firstUnmatched(patterns, trace.clientStdout());
    if (missing == null) {
      return CheckResult.of(CheckOutcome.PASS, "client output matched " + patterns + " in order");
    }
    return CheckResult.of(
        CheckOutcome.FAIL,
        "client output never matched /"
            + missing
            + "/ (in order after the preceding patterns); the demo does not show what it promises");
  }

  /** Returns the first pattern not matched in order, or {@code null} when all matched. */
  public static String firstUnmatched(List<String> patterns, List<String> lines) {
    int from = 0;
    for (String regex : patterns) {
      Pattern pattern = Pattern.compile(regex);
      int at = indexOfMatch(pattern, lines, from);
      if (at < 0) {
        return regex;
      }
      from = at + 1;
    }
    return null;
  }

  static int indexOfMatch(Pattern pattern, List<String> lines, int from) {
    for (int i = from; i < lines.size(); i++) {
      if (pattern.matcher(lines.get(i)).find()) {
        return i;
      }
    }
    return -1;
  }
}
