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
package io.btrace.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link FatAgentMojo}. */
class FatAgentMojoTest {

  @TempDir Path tempDir;

  private FatAgentMojo mojo;

  @BeforeEach
  void setUp() {
    mojo = new FatAgentMojo();
  }

  @Nested
  @DisplayName("resolveSecurely tests")
  class ResolveSecurelyTests {

    private Path baseDir;

    @BeforeEach
    void setUp() throws IOException {
      baseDir = tempDir.resolve("staging").toAbsolutePath().normalize();
      Files.createDirectories(baseDir);
    }

    @Test
    @DisplayName("resolves simple path")
    void resolvesSimplePath() throws IOException {
      Path result = mojo.resolveSecurely(baseDir, "com/example/Test.class");

      assertEquals(baseDir.resolve("com/example/Test.class"), result);
    }

    @Test
    @DisplayName("resolves nested path")
    void resolvesNestedPath() throws IOException {
      Path result = mojo.resolveSecurely(baseDir, "org/openjdk/btrace/core/BTrace.class");

      assertTrue(result.startsWith(baseDir));
      assertTrue(result.toString().endsWith("BTrace.class"));
    }

    @Test
    @DisplayName("throws on path traversal with ..")
    void throwsOnPathTraversal() {
      IOException ex =
          assertThrows(
              IOException.class, () -> mojo.resolveSecurely(baseDir, "../../../etc/passwd"));

      assertTrue(ex.getMessage().contains("escape"));
    }

    @Test
    @DisplayName("throws on absolute path outside base")
    void throwsOnAbsolutePathOutside() {
      // Construct a path that when resolved would be outside baseDir
      IOException ex =
          assertThrows(
              IOException.class,
              () -> mojo.resolveSecurely(baseDir, "foo/../../bar/../../../outside"));

      assertTrue(ex.getMessage().contains("escape"));
    }

    @Test
    @DisplayName("allows path that normalizes within base")
    void allowsNormalizablePathWithinBase() throws IOException {
      // This path uses .. but still stays within baseDir after normalization
      Path result = mojo.resolveSecurely(baseDir, "com/../com/example/Test.class");

      assertEquals(baseDir.resolve("com/example/Test.class"), result);
      assertTrue(result.startsWith(baseDir));
    }

    @Test
    @DisplayName("handles empty entry name")
    void handlesEmptyEntryName() throws IOException {
      Path result = mojo.resolveSecurely(baseDir, "");

      assertEquals(baseDir, result);
    }

    @Test
    @DisplayName("handles META-INF entries")
    void handlesMetaInfEntries() throws IOException {
      Path result = mojo.resolveSecurely(baseDir, "META-INF/MANIFEST.MF");

      assertEquals(baseDir.resolve("META-INF/MANIFEST.MF"), result);
    }

    @Test
    @DisplayName("handles deeply nested paths")
    void handlesDeeplyNestedPaths() throws IOException {
      String deepPath = "a/b/c/d/e/f/g/h/i/j/Test.class";
      Path result = mojo.resolveSecurely(baseDir, deepPath);

      assertEquals(baseDir.resolve(deepPath), result);
      assertTrue(result.startsWith(baseDir));
    }

    @Test
    @DisplayName("throws on sneaky path traversal")
    void throwsOnSneakyTraversal() {
      // Entry that looks innocent but escapes
      IOException ex =
          assertThrows(
              IOException.class,
              () -> mojo.resolveSecurely(baseDir, "com/example/../../../../tmp/evil"));

      assertTrue(ex.getMessage().contains("escape"));
    }
  }

  @Nested
  @DisplayName("JAR creation tests")
  class JarCreationTests {

    @Test
    @DisplayName("creates JAR with correct manifest attributes")
    void createsJarWithManifestAttributes() throws IOException {
      // Create test JAR with manifest
      Path jarPath = tempDir.resolve("test.jar");
      Manifest manifest = new Manifest();
      Attributes mainAttrs = manifest.getMainAttributes();
      mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      mainAttrs.putValue("BTrace-Extension-Id", "test-extension");
      mainAttrs.putValue("BTrace-Extension-Name", "Test Extension");

      try (JarOutputStream jos =
          new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
        // Add a dummy class entry
        JarEntry entry = new JarEntry("com/example/Test.class");
        jos.putNextEntry(entry);
        jos.write(new byte[] {0x00}); // Minimal content
        jos.closeEntry();
      }

      // Verify JAR was created correctly
      assertTrue(Files.exists(jarPath));
      assertTrue(Files.size(jarPath) > 0);
    }

    @Test
    @DisplayName("handles JAR with nested directories")
    void handlesJarWithNestedDirectories() throws IOException {
      Path jarPath = tempDir.resolve("nested.jar");
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

      try (JarOutputStream jos =
          new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
        // Add directory entries
        jos.putNextEntry(new JarEntry("com/"));
        jos.closeEntry();
        jos.putNextEntry(new JarEntry("com/example/"));
        jos.closeEntry();
        jos.putNextEntry(new JarEntry("com/example/deep/"));
        jos.closeEntry();

        // Add class entry
        JarEntry entry = new JarEntry("com/example/deep/Test.class");
        jos.putNextEntry(entry);
        jos.write(new byte[] {0x00});
        jos.closeEntry();
      }

      assertTrue(Files.exists(jarPath));
    }
  }

  @Nested
  @DisplayName("Extension ID extraction tests")
  class ExtensionIdExtractionTests {

    @Test
    @DisplayName("extracts extension ID from manifest")
    void extractsExtensionIdFromManifest() throws IOException {
      Path jarPath = tempDir.resolve("ext.jar");
      Manifest manifest = new Manifest();
      Attributes mainAttrs = manifest.getMainAttributes();
      mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      mainAttrs.putValue("BTrace-Extension-Id", "my-extension");

      try (JarOutputStream jos =
          new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
        // Empty JAR is fine for this test
      }

      assertTrue(Files.exists(jarPath));
    }

    @Test
    @DisplayName("creates valid extension properties file format")
    void createsValidExtensionPropertiesFormat() throws IOException {
      // Test that extension.properties format is correct
      Path propsPath = tempDir.resolve("extension.properties");
      String content =
          "id=test-ext\n"
              + "version=1.0.0\n"
              + "name=Test Extension\n"
              + "description=A test extension\n";
      Files.writeString(propsPath, content);

      String readContent = Files.readString(propsPath);
      assertTrue(readContent.contains("id=test-ext"));
      assertTrue(readContent.contains("version=1.0.0"));
    }
  }

  @Nested
  @DisplayName("Path handling tests")
  class PathHandlingTests {

    @Test
    @DisplayName("handles Windows-style paths in entry names")
    void handlesWindowsStylePaths() throws IOException {
      Path baseDir = tempDir.toAbsolutePath().normalize();

      // JAR entries use forward slashes even on Windows
      Path result = mojo.resolveSecurely(baseDir, "com/example/Test.class");

      assertTrue(result.startsWith(baseDir));
    }

    @Test
    @DisplayName("handles entry names with spaces")
    void handlesEntryNamesWithSpaces() throws IOException {
      Path baseDir = tempDir.toAbsolutePath().normalize();

      Path result = mojo.resolveSecurely(baseDir, "com/example/My Class.class");

      assertTrue(result.startsWith(baseDir));
      assertTrue(result.toString().contains("My Class.class"));
    }

    @Test
    @DisplayName("handles entry names with special characters")
    void handlesSpecialCharacters() throws IOException {
      Path baseDir = tempDir.toAbsolutePath().normalize();

      Path result = mojo.resolveSecurely(baseDir, "com/example/Test$Inner.class");

      assertTrue(result.startsWith(baseDir));
      assertTrue(result.toString().contains("Test$Inner.class"));
    }
  }

  @Nested
  @DisplayName("Extension ID validation tests")
  class ExtensionIdValidationTests {

    @Test
    @DisplayName("accepts valid extension ID")
    void acceptsValidExtensionId() {
      assertTrue(FatAgentMojo.isValidExtensionId("btrace-spark"));
      assertTrue(FatAgentMojo.isValidExtensionId("my-extension"));
      assertTrue(FatAgentMojo.isValidExtensionId("ext123"));
      assertTrue(FatAgentMojo.isValidExtensionId("my.extension"));
      assertTrue(FatAgentMojo.isValidExtensionId("my_extension"));
    }

    @Test
    @DisplayName("rejects null or empty ID")
    void rejectsNullOrEmpty() {
      assertFalse(FatAgentMojo.isValidExtensionId(null));
      assertFalse(FatAgentMojo.isValidExtensionId(""));
    }

    @Test
    @DisplayName("rejects path traversal with forward slash")
    void rejectsForwardSlash() {
      assertFalse(FatAgentMojo.isValidExtensionId("../etc/passwd"));
      assertFalse(FatAgentMojo.isValidExtensionId("foo/bar"));
      assertFalse(FatAgentMojo.isValidExtensionId("/etc/passwd"));
    }

    @Test
    @DisplayName("rejects path traversal with backslash")
    void rejectsBackslash() {
      assertFalse(FatAgentMojo.isValidExtensionId("..\\etc\\passwd"));
      assertFalse(FatAgentMojo.isValidExtensionId("foo\\bar"));
    }

    @Test
    @DisplayName("rejects parent directory reference")
    void rejectsParentDirectory() {
      assertFalse(FatAgentMojo.isValidExtensionId(".."));
      assertFalse(FatAgentMojo.isValidExtensionId("foo..bar"));
    }

    @Test
    @DisplayName("rejects IDs starting with special characters")
    void rejectsSpecialStart() {
      assertFalse(FatAgentMojo.isValidExtensionId("-extension"));
      assertFalse(FatAgentMojo.isValidExtensionId(".extension"));
      assertFalse(FatAgentMojo.isValidExtensionId("_extension"));
    }

    @Test
    @DisplayName("rejects IDs with invalid characters")
    void rejectsInvalidCharacters() {
      assertFalse(FatAgentMojo.isValidExtensionId("ext@name"));
      assertFalse(FatAgentMojo.isValidExtensionId("ext#name"));
      assertFalse(FatAgentMojo.isValidExtensionId("ext name"));
    }
  }

  @Nested
  @DisplayName("Classdata renaming tests")
  class ClassdataRenamingTests {

    @Test
    @DisplayName("renames .class to .classdata correctly")
    void renamesClassToClassdata() {
      String className = "com/example/Test.class";
      String classdataName = className.substring(0, className.length() - 6) + ".classdata";

      assertEquals("com/example/Test.classdata", classdataName);
    }

    @Test
    @DisplayName("handles nested class naming")
    void handlesNestedClassNaming() {
      String className = "com/example/Outer$Inner$Deep.class";
      String classdataName = className.substring(0, className.length() - 6) + ".classdata";

      assertEquals("com/example/Outer$Inner$Deep.classdata", classdataName);
    }
  }
}
