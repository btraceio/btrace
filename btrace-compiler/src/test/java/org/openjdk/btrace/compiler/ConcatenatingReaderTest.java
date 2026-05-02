package org.openjdk.btrace.compiler;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class ConcatenatingReaderTest {

  private static String read(String input) throws Exception {
    ConcatenatingReader cr = new ConcatenatingReader(new BufferedReader(new StringReader(input)));
    StringBuilder sb = new StringBuilder();
    int ch;
    while ((ch = cr.read()) != -1) sb.append((char) ch);
    return sb.toString();
  }

  @Test
  void plainLineKeepsNewline() throws Exception {
    assertEquals("hello\n", read("hello\n"));
  }

  @Test
  void backslashContinuationJoinsLines() throws Exception {
    // "foo\\\nbar\n" → lines "foo\" and "bar", should produce "foobar\n"
    assertEquals("foobar\n", read("foo\\\nbar\n"));
  }

  @Test
  void multipleContinuations() throws Exception {
    assertEquals("abcdef\n", read("ab\\\ncd\\\nef\n"));
  }

  @Test
  void emptyInput() throws Exception {
    assertEquals("", read(""));
  }

  @Test
  void noTrailingNewline() throws Exception {
    assertEquals("hello" + System.lineSeparator(), read("hello"));
  }

  @Test
  void readyReturnsTrueWhileDataAvailable() throws Exception {
    ConcatenatingReader cr = new ConcatenatingReader(new BufferedReader(new StringReader("x\n")));
    assertTrue(cr.ready());
    cr.read();
    cr.read(); // consume newline
    // After consuming all data, ready() reflects if there's more data
    // StringReader may still report ready() as true even after EOF,
    // so we verify by attempting to read and checking result
    assertEquals(-1, cr.read());
  }

  @Test
  void markNotSupported() {
    ConcatenatingReader cr = new ConcatenatingReader(new BufferedReader(new StringReader("")));
    assertFalse(cr.markSupported());
    assertThrows(java.io.IOException.class, () -> cr.mark(1));
    assertThrows(java.io.IOException.class, cr::reset);
  }
}
