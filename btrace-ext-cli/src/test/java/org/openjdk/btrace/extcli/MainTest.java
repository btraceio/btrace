/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.extcli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
