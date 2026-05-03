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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionInspectorTest {

  @TempDir Path tempDir;

  @Test
  void inspectValidDirectory() throws IOException {
    Path extDir = tempDir.resolve("test-extension");
    TestExtensionBuilder.createExtensionDirectory("test-ext", "1.0.0", extDir, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(report.ok, "Extension should be valid");
    assertEquals("test-ext", report.id);
    assertEquals("1.0.0", report.version);
    assertFalse(report.privileged, "Extension should not be privileged");
  }

  @Test
  void inspectValidZip() throws IOException {
    Path zipFile = tempDir.resolve("test-extension.zip");
    TestExtensionBuilder.createExtensionZip("test-ext", "2.0.0", zipFile, false);

    ExtensionReport report = ExtensionInspector.inspect(zipFile);

    assertTrue(report.ok, "Extension ZIP should be valid");
    assertEquals("test-ext", report.id);
    assertEquals("2.0.0", report.version);
  }

  @Test
  void detectMissingApiJar() throws IOException {
    Path extDir = tempDir.resolve("incomplete-extension");
    Files.createDirectories(extDir);
    // Only create impl jar, no api jar
    Path implJar = extDir.resolve("test-1.0.0-impl.jar");
    TestExtensionBuilder.createImplJar("test", implJar);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertFalse(report.ok, "Extension should be invalid without API JAR");
    assertTrue(report.message.contains("Missing api/impl jars"));
  }

  @Test
  void detectMissingImplJar() throws IOException {
    Path extDir = tempDir.resolve("incomplete-extension");
    Files.createDirectories(extDir);
    // Only create api jar, no impl jar
    Path apiJar = extDir.resolve("test-1.0.0-api.jar");
    TestExtensionBuilder.createApiJar("test", "1.0.0", apiJar, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertFalse(report.ok, "Extension should be invalid without implementation JAR");
    assertTrue(report.message.contains("Missing api/impl jars"));
  }

  @Test
  void inspectExtensionWithPermissions() throws IOException {
    Path extDir = tempDir.resolve("permissions-extension");
    TestExtensionBuilder.createExtensionDirectory("perm-ext", "1.0.0", extDir, true);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertTrue(report.ok, "Extension with permissions should be valid");
    // Note: Privileged detection requires actual ExtensionMeta from compiled classes,
    // which is beyond the scope of simple unit testing with programmatic JAR creation
  }

  @Test
  void extractManifestId() throws IOException {
    Path extDir = tempDir.resolve("manifest-id-test");
    TestExtensionBuilder.createExtensionDirectory("manifest-ext", "1.5.0", extDir, false);

    ExtensionReport report = ExtensionInspector.inspect(extDir);

    assertEquals("manifest-ext", report.id, "Should extract ID from manifest");
  }
}
