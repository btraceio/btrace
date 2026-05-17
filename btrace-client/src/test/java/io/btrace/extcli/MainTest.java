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
package io.btrace.extcli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @TempDir Path tempDir;

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  @BeforeEach
  void setUpStreams() {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void showsHelpWithNoArgs() throws Exception {
    Main.main(new String[] {});

    String output = outContent.toString();
    assertTrue(output.contains("Usage") || output.contains("usage"), "Should show usage");
  }

  @Test
  void showsHelpWithHelpFlag() throws Exception {
    Main.main(new String[] {"--help"});

    String output = outContent.toString();
    assertTrue(output.contains("Usage") || output.contains("usage"), "Should show usage");
  }

  @Test
  void inspectCommandWithValidExtension() throws Exception {
    Path extDir = tempDir.resolve("test-ext");
    TestExtensionBuilder.createExtensionDirectory("test-ext", "1.0.0", extDir, false);

    Main.main(new String[] {"inspect", extDir.toString()});

    String output = outContent.toString();
    assertTrue(output.contains("test-ext"), "Should display extension ID");
  }

  @Test
  void inspectCommandWithJsonFlag() throws Exception {
    Path extDir = tempDir.resolve("test-ext");
    TestExtensionBuilder.createExtensionDirectory("test-ext", "1.0.0", extDir, false);

    Main.main(new String[] {"inspect", extDir.toString(), "--json"});

    String output = outContent.toString();
    assertTrue(output.contains("{") && output.contains("}"), "Should output JSON");
    assertTrue(output.contains("\"id\""), "JSON should contain id field");
  }

  @Test
  void listCommandExecutes() throws Exception {
    Main.main(new String[] {"list"});

    // Should execute without throwing exception
    String output = outContent.toString();
    assertNotNull(output);
  }

  @Test
  void unknownCommandShowsError() throws Exception {
    Main.main(new String[] {"invalid-command"});

    String error = errContent.toString();
    assertTrue(error.contains("Unknown command"), "Should show unknown command error");
  }
}
