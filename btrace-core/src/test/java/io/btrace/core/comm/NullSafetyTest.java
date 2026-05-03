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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NullSafetyTest {

  @Test
  void testErrorCommandPrintWithNullCause() {
    ErrorCommand cmd = new ErrorCommand(null);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    assertDoesNotThrow(() -> cmd.print(pw));

    String output = sw.toString();
    assertTrue(output.contains("! ERROR"));
    assertTrue(output.contains("No exception information available"));
  }

  @Test
  void testGridDataCommandWithNullRow() {
    List<Object[]> data = new ArrayList<>();
    data.add(new Object[] {"Row1", 100});
    data.add(null);
    data.add(new Object[] {"Row3", 300});

    GridDataCommand cmd = new GridDataCommand("TestGrid", data);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    assertDoesNotThrow(() -> cmd.print(pw));
    String output = sw.toString();
    assertTrue(output.contains("Row1"));
    assertTrue(output.contains("Row3"));
  }

  @Test
  void testMessageCommandWithNullMessage() {
    MessageCommand cmd = new MessageCommand((String) null);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    assertDoesNotThrow(() -> cmd.print(pw));
  }
}
