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
package org.openjdk.btrace.runtime;

class DotWriterFormatter {
  // Maximum number of string characters displayed.
  private int stringLimit = 32;

  DotWriterFormatter() {}

  // Set maximum number of string characters displayed.
  void stringLimit(int stringLimit) {
    this.stringLimit = stringLimit;
  }

  // Formats a string value, truncating if needed.
  String formatString(String string, String quote) {
    boolean isLong = string.length() > stringLimit;
    if (isLong) string = string.substring(0, stringLimit - 1);
    string = quote + string + quote;
    if (isLong) string += "...";
    return string;
  }
}
