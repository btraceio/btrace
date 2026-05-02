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
package org.openjdk.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentManifestLibsTest {

  @TempDir Path tempDir;

  @Nested
  @DisplayName("resolveEntry tests")
  class ResolveEntryTests {

    @Test
    @DisplayName("resolves relative path against base directory")
    void relativePath() {
      Path baseDir = tempDir.resolve("libs");
      Path result = AgentManifestLibs.resolveEntry("foo.jar", baseDir);

      assertNotNull(result);
      assertEquals(baseDir.resolve("foo.jar").normalize(), result);
    }

    @Test
    @DisplayName("resolves nested relative path")
    void nestedRelativePath() {
      Path baseDir = tempDir.resolve("libs");
      Path result = AgentManifestLibs.resolveEntry("sub/foo.jar", baseDir);

      assertNotNull(result);
      assertEquals(baseDir.resolve("sub/foo.jar").normalize(), result);
    }

    @Test
    @DisplayName("returns absolute path unchanged")
    void absolutePath() {
      Path absoluteJar = tempDir.resolve("absolute.jar");
      Path result = AgentManifestLibs.resolveEntry(absoluteJar.toString(), tempDir);

      assertNotNull(result);
      assertEquals(absoluteJar, result);
    }

    @Test
    @DisplayName("handles file: URI scheme")
    void fileUri() throws Exception {
      Path jarPath = tempDir.resolve("uri-test.jar");
      String fileUri = jarPath.toUri().toString();
      Path result = AgentManifestLibs.resolveEntry(fileUri, null);

      assertNotNull(result);
      assertEquals(jarPath, result);
    }

    @Test
    @DisplayName("normalizes parent references in path")
    void parentReferences() {
      Path baseDir = tempDir.resolve("libs");
      Path result = AgentManifestLibs.resolveEntry("../other/foo.jar", baseDir);

      assertNotNull(result);
      assertEquals(tempDir.resolve("other/foo.jar").normalize(), result);
    }

    @Test
    @DisplayName("handles null base directory for absolute paths")
    void nullBaseDirAbsolute() {
      Path absoluteJar = tempDir.resolve("test.jar");
      Path result = AgentManifestLibs.resolveEntry(absoluteJar.toString(), null);

      assertNotNull(result);
      assertEquals(absoluteJar, result);
    }

    @Test
    @DisplayName("returns non-absolute path when base is null")
    void nullBaseDirRelative() {
      Path result = AgentManifestLibs.resolveEntry("relative.jar", null);

      assertNotNull(result);
      assertFalse(result.isAbsolute());
    }
  }

  @Nested
  @DisplayName("addEntries tests")
  class AddEntriesTests {

    private Set<Path> entries;

    @BeforeEach
    void setUp() {
      entries = new LinkedHashSet<>();
    }

    @Test
    @DisplayName("parses single entry")
    void singleEntry() {
      AgentManifestLibs.addEntries(entries, "foo.jar", tempDir);

      assertEquals(1, entries.size());
      assertTrue(entries.contains(tempDir.resolve("foo.jar")));
    }

    @Test
    @DisplayName("parses multiple space-separated entries")
    void multipleEntries() {
      AgentManifestLibs.addEntries(entries, "foo.jar bar.jar baz.jar", tempDir);

      assertEquals(3, entries.size());
      assertTrue(entries.contains(tempDir.resolve("foo.jar")));
      assertTrue(entries.contains(tempDir.resolve("bar.jar")));
      assertTrue(entries.contains(tempDir.resolve("baz.jar")));
    }

    @Test
    @DisplayName("handles multiple whitespace between entries")
    void multipleWhitespace() {
      AgentManifestLibs.addEntries(entries, "foo.jar   bar.jar\tbaz.jar", tempDir);

      assertEquals(3, entries.size());
    }

    @Test
    @DisplayName("ignores null value")
    void nullValue() {
      AgentManifestLibs.addEntries(entries, null, tempDir);

      assertTrue(entries.isEmpty());
    }

    @Test
    @DisplayName("ignores empty value")
    void emptyValue() {
      AgentManifestLibs.addEntries(entries, "", tempDir);

      assertTrue(entries.isEmpty());
    }

    @Test
    @DisplayName("deduplicates identical entries")
    void deduplication() {
      AgentManifestLibs.addEntries(entries, "foo.jar foo.jar", tempDir);

      assertEquals(1, entries.size());
    }
  }

  @Nested
  @DisplayName("filterAndNormalize tests")
  class FilterAndNormalizeTests {

    private Path homeDir;
    private Path libsDir;

    @BeforeEach
    void setUp() throws IOException {
      // Create a mock BTRACE_HOME structure
      homeDir = tempDir.resolve("btrace-home");
      libsDir = homeDir.resolve("libs");
      Files.createDirectories(libsDir);
    }

    private Path createJar(Path dir, String name) throws IOException {
      Path jarPath = dir.resolve(name);
      Files.createDirectories(jarPath.getParent());
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
      try (JarOutputStream jos =
          new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
        // Empty JAR
      }
      return jarPath;
    }

    @Test
    @DisplayName("accepts JAR inside BTRACE_HOME")
    void acceptsJarInsideHome() throws IOException {
      Path jar = createJar(libsDir, "valid.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertEquals(1, result.size());
      assertTrue(result.get(0).toString().endsWith("valid.jar"));
    }

    @Test
    @DisplayName("rejects JAR outside BTRACE_HOME by default")
    void rejectsJarOutsideHome() throws IOException {
      Path externalDir = tempDir.resolve("external");
      Path jar = createJar(externalDir, "external.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("accepts JAR outside BTRACE_HOME when allowExternal=true")
    void acceptsExternalWhenAllowed() throws IOException {
      Path externalDir = tempDir.resolve("external");
      Path jar = createJar(externalDir, "external.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, true);

      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("skips non-existent files")
    void skipsNonExistent() {
      Path nonExistent = libsDir.resolve("missing.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(nonExistent);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("skips non-JAR files")
    void skipsNonJarFiles() throws IOException {
      Path txtFile = libsDir.resolve("readme.txt");
      Files.writeString(txtFile, "not a jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(txtFile);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("accepts JAR with uppercase extension")
    void acceptsUppercaseJar() throws IOException {
      Path jar = createJar(libsDir, "uppercase.JAR");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("handles null home directory")
    void nullHomeDirectory() throws IOException {
      Path jar = createJar(tempDir, "any.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, null, false);

      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("preserves order of valid entries")
    void preservesOrder() throws IOException {
      Path jar1 = createJar(libsDir, "a.jar");
      Path jar2 = createJar(libsDir, "b.jar");
      Path jar3 = createJar(libsDir, "c.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar1);
      input.add(jar2);
      input.add(jar3);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertEquals(3, result.size());
      assertTrue(result.get(0).toString().endsWith("a.jar"));
      assertTrue(result.get(1).toString().endsWith("b.jar"));
      assertTrue(result.get(2).toString().endsWith("c.jar"));
    }

    @Test
    @DisplayName("accepts nested JAR inside BTRACE_HOME")
    void acceptsNestedJar() throws IOException {
      Path nestedDir = libsDir.resolve("subdir").resolve("nested");
      Path jar = createJar(nestedDir, "nested.jar");
      Set<Path> input = new LinkedHashSet<>();
      input.add(jar);

      List<Path> result = AgentManifestLibs.filterAndNormalize(input, homeDir, false);

      assertEquals(1, result.size());
    }
  }

  @Nested
  @DisplayName("scanLibTree tests")
  class ScanLibTreeTests {

    private Path createJar(Path dir, String name) throws IOException {
      Path jarPath = dir.resolve(name);
      Files.createDirectories(jarPath.getParent());
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
      try (JarOutputStream jos =
          new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
        // Empty JAR
      }
      return jarPath;
    }

    @Test
    @DisplayName("finds JARs in root directory")
    void findsJarsInRoot() throws IOException {
      Path libDir = tempDir.resolve("libs");
      createJar(libDir, "a.jar");
      createJar(libDir, "b.jar");
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(libDir, result);

      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("finds JARs in nested directories")
    void findsNestedJars() throws IOException {
      Path libDir = tempDir.resolve("libs");
      createJar(libDir, "root.jar");
      createJar(libDir.resolve("sub1"), "sub1.jar");
      createJar(libDir.resolve("sub1").resolve("sub2"), "sub2.jar");
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(libDir, result);

      assertEquals(3, result.size());
    }

    @Test
    @DisplayName("ignores non-JAR files")
    void ignoresNonJars() throws IOException {
      Path libDir = tempDir.resolve("libs");
      Files.createDirectories(libDir);
      createJar(libDir, "valid.jar");
      Files.writeString(libDir.resolve("readme.txt"), "text file");
      Files.writeString(libDir.resolve("config.xml"), "<config/>");
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(libDir, result);

      assertEquals(1, result.size());
      assertTrue(result.iterator().next().toString().endsWith("valid.jar"));
    }

    @Test
    @DisplayName("handles null root gracefully")
    void handlesNullRoot() {
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(null, result);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("handles non-existent root gracefully")
    void handlesNonExistentRoot() {
      Path nonExistent = tempDir.resolve("does-not-exist");
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(nonExistent, result);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("handles empty directory")
    void handlesEmptyDirectory() throws IOException {
      Path emptyDir = tempDir.resolve("empty");
      Files.createDirectories(emptyDir);
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(emptyDir, result);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("finds JAR with uppercase extension")
    void findsUppercaseJar() throws IOException {
      Path libDir = tempDir.resolve("libs");
      createJar(libDir, "upper.JAR");
      Set<Path> result = new LinkedHashSet<>();

      AgentManifestLibs.scanLibTree(libDir, result);

      assertEquals(1, result.size());
    }
  }

  @Nested
  @DisplayName("ResolvedLibs tests")
  class ResolvedLibsTests {

    @Test
    @DisplayName("stores boot and system jars")
    void storesJars() {
      List<Path> boot = List.of(tempDir.resolve("boot.jar"));
      List<Path> system = List.of(tempDir.resolve("sys.jar"));

      AgentManifestLibs.ResolvedLibs resolved = new AgentManifestLibs.ResolvedLibs(boot, system);

      assertEquals(1, resolved.bootJars.size());
      assertEquals(1, resolved.systemJars.size());
      assertTrue(resolved.bootJars.get(0).toString().endsWith("boot.jar"));
      assertTrue(resolved.systemJars.get(0).toString().endsWith("sys.jar"));
    }
  }
}
