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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Helper class to build test extension JARs programmatically. */
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
    createApiJar(id, version, output, privileged, null);
  }

  /**
   * Creates a valid API JAR with extension metadata, optionally declaring the services the
   * extension provides.
   *
   * @param id Extension ID
   * @param version Extension version
   * @param output Output file path
   * @param privileged Whether extension requires privileged permissions
   * @param services comma-separated service class names for {@code BTrace-Extension-Services}, or
   *     {@code null} to omit the attribute
   * @throws IOException if JAR creation fails
   */
  static void createApiJar(
      String id, String version, Path output, boolean privileged, String services)
      throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("BTrace-Extension-Id", id);
    manifest.getMainAttributes().putValue("BTrace-Extension-Version", version);
    if (services != null) {
      manifest.getMainAttributes().putValue("BTrace-Extension-Services", services);
    }
    if (privileged) {
      // The manifest is where the inspector reads permissions from; without this the `privileged`
      // flag would not reach it and privileged detection would go untested.
      manifest.getMainAttributes().putValue("BTrace-Extension-Permissions", "NETWORK");
    }

    try (JarOutputStream jos =
        new JarOutputStream(new FileOutputStream(output.toFile()), manifest)) {
      // Add META-INF/btrace/exports.index
      jos.putNextEntry(new JarEntry("META-INF/btrace/"));
      jos.closeEntry();

      jos.putNextEntry(new JarEntry("META-INF/btrace/exports.index"));
      jos.write("org.example.TestService\n".getBytes(StandardCharsets.UTF_8));
      jos.closeEntry();

      // Add permissions.properties if privileged
      if (privileged) {
        jos.putNextEntry(new JarEntry("META-INF/btrace/permissions.properties"));
        jos.write("permissions=IO,NETWORK\n".getBytes(StandardCharsets.UTF_8));
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

    try (JarOutputStream jos =
        new JarOutputStream(new FileOutputStream(output.toFile()), manifest)) {
      // Add META-INF/services entry
      jos.putNextEntry(new JarEntry("META-INF/services/"));
      jos.closeEntry();

      jos.putNextEntry(new JarEntry("META-INF/services/org.example.TestService"));
      jos.write("org.example.TestServiceImpl\n".getBytes(StandardCharsets.UTF_8));
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
      // Recursively clean up temp directory and all contents
      Files.walk(tempDir)
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
              });
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
  static void createExtensionDirectory(
      String id, String version, Path outputDir, boolean privileged) throws IOException {
    createExtensionDirectory(id, version, outputDir, privileged, null);
  }

  /**
   * Creates an extension directory structure with API and implementation JARs, optionally declaring
   * the services the extension provides.
   *
   * @param id Extension ID
   * @param version Extension version
   * @param outputDir Output directory path
   * @param privileged Whether extension requires privileged permissions
   * @param services comma-separated service class names for {@code BTrace-Extension-Services}, or
   *     {@code null} to omit the attribute
   * @throws IOException if creation fails
   */
  static void createExtensionDirectory(
      String id, String version, Path outputDir, boolean privileged, String services)
      throws IOException {
    Files.createDirectories(outputDir);
    Path apiJar = outputDir.resolve(id + "-" + version + "-api.jar");
    Path implJar = outputDir.resolve(id + "-" + version + "-impl.jar");

    createApiJar(id, version, apiJar, privileged, services);
    createImplJar(id, implJar);
  }

  private static void addFileToZip(ZipOutputStream zos, Path file, String name) throws IOException {
    zos.putNextEntry(new ZipEntry(name));
    Files.copy(file, zos);
    zos.closeEntry();
  }

  /**
   * Creates a minimal structurally valid Java class file for testing.
   *
   * <p>Constant pool layout:
   *
   * <pre>
   *   #1 CONSTANT_Class -> #3
   *   #2 CONSTANT_Class -> #4
   *   #3 CONSTANT_Utf8  "org/example/TestServiceImpl"
   *   #4 CONSTANT_Utf8  "java/lang/Object"
   * </pre>
   */
  private static byte[] createMinimalClassFile() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // Magic number
    writeU4(baos, 0xCAFEBABEL);
    // Minor version: 0, Major version: 52 (Java 8)
    writeU2(baos, 0);
    writeU2(baos, 52);
    // Constant pool count: 5 (entries 1..4)
    writeU2(baos, 5);
    // #1 CONSTANT_Class -> #3
    baos.write(7);
    writeU2(baos, 3);
    // #2 CONSTANT_Class -> #4
    baos.write(7);
    writeU2(baos, 4);
    // #3 CONSTANT_Utf8 "org/example/TestServiceImpl"
    byte[] thisName = "org/example/TestServiceImpl".getBytes(StandardCharsets.UTF_8);
    baos.write(1);
    writeU2(baos, thisName.length);
    baos.write(thisName, 0, thisName.length);
    // #4 CONSTANT_Utf8 "java/lang/Object"
    byte[] superName = "java/lang/Object".getBytes(StandardCharsets.UTF_8);
    baos.write(1);
    writeU2(baos, superName.length);
    baos.write(superName, 0, superName.length);
    // Access flags: public
    writeU2(baos, 0x0021);
    // This class: #1
    writeU2(baos, 1);
    // Super class: #2
    writeU2(baos, 2);
    // Interfaces count: 0
    writeU2(baos, 0);
    // Fields count: 0
    writeU2(baos, 0);
    // Methods count: 0
    writeU2(baos, 0);
    // Attributes count: 0
    writeU2(baos, 0);
    return baos.toByteArray();
  }

  private static void writeU2(ByteArrayOutputStream baos, int value) {
    baos.write((value >> 8) & 0xFF);
    baos.write(value & 0xFF);
  }

  private static void writeU4(ByteArrayOutputStream baos, long value) {
    baos.write((int) ((value >> 24) & 0xFF));
    baos.write((int) ((value >> 16) & 0xFF));
    baos.write((int) ((value >> 8) & 0xFF));
    baos.write((int) (value & 0xFF));
  }
}
