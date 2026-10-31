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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class MessageCommandPrintTest {
  private static final String NL = System.lineSeparator();

  private static String printed(String msg) {
    StringWriter target = new StringWriter();
    try (PrintWriter out = new PrintWriter(target)) {
      new MessageCommand(msg).print(out);
    }
    return target.toString();
  }

  @Test
  void messageWithLineSeparatorIsNotFollowedByBlankLine() {
    assertEquals("hello" + NL, printed("hello" + NL));
  }

  @Test
  void messageWithoutLineSeparatorIsTerminated() {
    assertEquals("hello" + NL, printed("hello"));
  }

  @Test
  void explicitEmptyLineIsPreserved() {
    assertEquals(NL, printed(NL));
  }
}
