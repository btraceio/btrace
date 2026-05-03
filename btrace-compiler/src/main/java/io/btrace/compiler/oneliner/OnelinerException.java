/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.compiler.oneliner;

/**
 * Exception thrown when parsing or validating BTrace oneliner expressions. Includes position
 * tracking for better error messages.
 */
public class OnelinerException extends RuntimeException {
  private final int position;
  private final String input;

  public OnelinerException(String message, String input, int position) {
    super(formatMessage(message, input, position));
    this.position = position;
    this.input = input;
  }

  public OnelinerException(String message, String input, int position, Throwable cause) {
    super(formatMessage(message, input, position), cause);
    this.position = position;
    this.input = input;
  }

  public int getPosition() {
    return position;
  }

  public String getInput() {
    return input;
  }

  private static String formatMessage(String message, String input, int position) {
    StringBuilder sb = new StringBuilder();
    sb.append("Oneliner syntax error at position ").append(position).append(":\n");
    sb.append(input).append("\n");

    if (position >= 0 && position < input.length()) {
      for (int i = 0; i < position; i++) {
        sb.append(" ");
      }
      sb.append("^\n");
    }

    sb.append(message);
    return sb.toString();
  }
}
