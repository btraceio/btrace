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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Helper class to build test extension JARs programmatically.
 */
class TestExtensionBuilder {

  /**
   * Creates a valid API JAR with extension metadata.
   *
   * @param id Extension ID
   * @param version Extension version
   * @param output Output file path
   * @param privileged Whether extension requires privileged permissions
   * @throws IOException if JAR creation fails
   */
  static void createApiJar(String id, String version, Path output, boolean privileged)
      throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("BTrace-Extension-Id", id);
    manifest.getMainAttributes().putValue("BTrace-Extension-Version", version);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output.toFile()), manifest)) {
      // Add META-INF/btrace/exports.index
      jos.putNextEntry(new JarEntry("META-INF/btrace/"));
      jos.closeEntry();

      jos.putNextEntry(new JarEntry("META-INF/btrace/exports.index"));
      jos.write("org.example.TestService\n".getBytes());
      jos.closeEntry();

      // Add permissions.properties if privileged
      if (privileged) {
        jos.putNextEntry(new JarEntry("META-INF/btrace/permissions.properties"));
        jos.write("permissions=IO,NETWORK\n".getBytes());
        jos.closeEntry();
      }
    }
  }

  /**
   * Creates a valid implementation JAR.
   *
   * @param id Extension ID
   * @param output Output file path
   * @throws IOException if JAR creation fails
   */
  static void createImplJar(String id, Path output) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output.toFile()), manifest)) {
      // Add META-INF/services entry
      jos.putNextEntry(new JarEntry("META-INF/services/"));
      jos.closeEntry();

      jos.putNextEntry(new JarEntry("META-INF/services/org.example.TestService"));
      jos.write("org.example.TestServiceImpl\n".getBytes());
      jos.closeEntry();

      // Add a dummy class file
      jos.putNextEntry(new JarEntry("org/example/TestServiceImpl.class"));
      // Minimal valid class file (empty class)
      byte[] classBytes = createMinimalClassFile();
      jos.write(classBytes);
      jos.closeEntry();
    }
  }

  /**
   * Creates a valid extension ZIP containing API and implementation JARs.
   *
   * @param id Extension ID
   * @param version Extension version
   * @param output Output ZIP file path
   * @param privileged Whether extension requires privileged permissions
   * @throws IOException if ZIP creation fails
   */
  static void createExtensionZip(String id, String version, Path output, boolean privileged)
      throws IOException {
    Path tempDir = Files.createTempDirectory("btrace-ext-test");
    try {
      Path apiJar = tempDir.resolve(id + "-" + version + "-api.jar");
      Path implJar = tempDir.resolve(id + "-" + version + "-impl.jar");

      createApiJar(id, version, apiJar, privileged);
      createImplJar(id, implJar);

      try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output.toFile()))) {
        addFileToZip(zos, apiJar, apiJar.getFileName().toString());
        addFileToZip(zos, implJar, implJar.getFileName().toString());
      }
    } finally {
      // Cleanup temp files
      Files.deleteIfExists(tempDir.resolve(id + "-" + version + "-api.jar"));
      Files.deleteIfExists(tempDir.resolve(id + "-" + version + "-impl.jar"));
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Creates an extension directory structure with API and implementation JARs.
   *
   * @param id Extension ID
   * @param version Extension version
   * @param outputDir Output directory path
   * @param privileged Whether extension requires privileged permissions
   * @throws IOException if creation fails
   */
  static void createExtensionDirectory(String id, String version, Path outputDir, boolean privileged)
      throws IOException {
    Files.createDirectories(outputDir);
    Path apiJar = outputDir.resolve(id + "-" + version + "-api.jar");
    Path implJar = outputDir.resolve(id + "-" + version + "-impl.jar");

    createApiJar(id, version, apiJar, privileged);
    createImplJar(id, implJar);
  }

  private static void addFileToZip(ZipOutputStream zos, Path file, String name)
      throws IOException {
    zos.putNextEntry(new ZipEntry(name));
    Files.copy(file, zos);
    zos.closeEntry();
  }

  /**
   * Creates a minimal valid Java class file for testing.
   * This is a class file for an empty class that extends Object.
   */
  private static byte[] createMinimalClassFile() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // Java class file magic number
    baos.write(0xCA);
    baos.write(0xFE);
    baos.write(0xBA);
    baos.write(0xBE);
    // Minor version: 0
    baos.write(0x00);
    baos.write(0x00);
    // Major version: 52 (Java 8)
    baos.write(0x00);
    baos.write(0x34);
    // Constant pool count: 1 (no constants)
    baos.write(0x00);
    baos.write(0x01);
    // Access flags: public
    baos.write(0x00);
    baos.write(0x21);
    // This class: 0
    baos.write(0x00);
    baos.write(0x00);
    // Super class: 0 (java.lang.Object)
    baos.write(0x00);
    baos.write(0x00);
    // Interfaces count: 0
    baos.write(0x00);
    baos.write(0x00);
    // Fields count: 0
    baos.write(0x00);
    baos.write(0x00);
    // Methods count: 0
    baos.write(0x00);
    baos.write(0x00);
    // Attributes count: 0
    baos.write(0x00);
    baos.write(0x00);

    return baos.toByteArray();
  }
}
