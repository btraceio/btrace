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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExpectationDef {
  private final Integer exitCode;
  private final List<String> messageMustMention;
  private final List<String> outputMustMatch;

  public ExpectationDef(Integer exitCode, List<String> messageMustMention) {
    this(exitCode, messageMustMention, null);
  }

  public ExpectationDef(
      Integer exitCode, List<String> messageMustMention, List<String> outputMustMatch) {
    this.exitCode = exitCode;
    this.messageMustMention = copy(messageMustMention);
    this.outputMustMatch = copy(outputMustMatch);
  }

  private static List<String> copy(List<String> list) {
    return list == null
        ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(list));
  }

  public Integer exitCode() {
    return exitCode;
  }

  public List<String> messageMustMention() {
    return messageMustMention;
  }

  /**
   * Regular expressions that must each match a line of the attached client's output, in this order
   * (a later pattern is only searched after the line that satisfied the previous one).
   */
  public List<String> outputMustMatch() {
    return outputMustMatch;
  }
}
