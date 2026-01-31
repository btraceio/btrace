/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
