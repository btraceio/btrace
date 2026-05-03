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
    Installer.install("https://example.com/test-ext.zip", Collections.emptyList(), null, true);

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
