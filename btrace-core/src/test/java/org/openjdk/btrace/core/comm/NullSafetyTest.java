/*
 * Tests for null safety in command print() methods to prevent NullPointerExceptions.
 */
package org.openjdk.btrace.core.comm;

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

