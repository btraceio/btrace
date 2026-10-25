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
package io.btrace.agent;

import java.io.IOException;

/**
 * An agent option whose value the agent cannot use. The message always names the option and the
 * rejected value, e.g. {@code invalid agent option port=70000: expected 0-65535}, so an operator
 * can find the mistake in the {@code -javaagent} string without a stack trace.
 */
final class InvalidAgentOptionException extends IOException {
  private static final long serialVersionUID = 1L;

  private final String option;
  private final String value;

  InvalidAgentOptionException(String option, String value, String reason) {
    super("invalid agent option " + option + "=" + value + ": " + reason);
    this.option = option;
    this.value = value;
  }

  String getOption() {
    return option;
  }

  String getValue() {
    return value;
  }
}
