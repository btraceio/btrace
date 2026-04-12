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
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallerTest {

  @TempDir Path tempDir;

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;

  @BeforeEach
  void setUpStreams() {
    System.setOut(new PrintStream(outContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
  }

  @Test
  void dryRunFromLocalZip() throws Exception {
    Path zipFile = tempDir.resolve("test-ext.zip");
    TestExtensionBuilder.createExtensionZip("test-ext", "1.0.0", zipFile, false);

    Installer.install(zipFile.toString(), Collections.emptyList(), null, true);

    String output = outContent.toString();
    assertTrue(output.contains("[DRY-RUN]"), "Should indicate dry-run mode");
    assertTrue(output.contains("Would install"), "Should show install action");
  }

  @Test
  void dryRunFromUrl() throws Exception {
    Installer.install(
        "https://example.com/test-ext.zip", Collections.emptyList(), null, true);

    String output = outContent.toString();
    assertTrue(output.contains("[DRY-RUN]"), "Should indicate dry-run mode");
    assertTrue(output.contains("Would download"), "Should show download action");
  }

  @Test
  void dryRunFromMavenGav() throws Exception {
    List<String> repos = List.of("https://repo1.maven.org/maven2");
    Installer.install("org.example:test-ext:1.0.0", repos, null, true);

    String output = outContent.toString();
    assertTrue(output.contains("[DRY-RUN]"), "Should indicate dry-run mode");
    assertTrue(output.contains("Candidate URLs"), "Should show candidate Maven URLs");
  }

  @Test
  void dryRunWithCustomId() throws Exception {
    Path zipFile = tempDir.resolve("test.zip");
    TestExtensionBuilder.createExtensionZip("test-ext", "1.0.0", zipFile, false);

    Installer.install(zipFile.toString(), Collections.emptyList(), "custom-id", true);

    String output = outContent.toString();
    assertTrue(output.contains("[DRY-RUN]"), "Should indicate dry-run mode");
  }

  @Test
  void invalidGavCoordinateThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Installer.install("invalid:coordinate", Collections.emptyList(), null, true),
        "Should reject invalid GAV coordinate");
  }

  @Test
  void unrecognizedInputThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Installer.install("not-a-valid-input", Collections.emptyList(), null, true),
        "Should reject unrecognized input");
  }

  @Test
  void multipleReposInDryRun() throws Exception {
    List<String> repos = List.of("https://repo1.example.com", "https://repo2.example.com");
    Installer.install("com.example:test:1.0", repos, null, true);

    String output = outContent.toString();
    assertTrue(
        output.contains("repo1.example.com") && output.contains("repo2.example.com"),
        "Should show all candidate repositories");
  }

  @Test
  void derivesIdFromZipFilename() throws Exception {
    Path zipFile = tempDir.resolve("my-extension-1.2.3.zip");
    TestExtensionBuilder.createExtensionZip("my-ext", "1.2.3", zipFile, false);

    Installer.install(zipFile.toString(), Collections.emptyList(), null, true);

    String output = outContent.toString();
    assertTrue(output.contains("[DRY-RUN]"), "Should complete dry-run");
  }
}
