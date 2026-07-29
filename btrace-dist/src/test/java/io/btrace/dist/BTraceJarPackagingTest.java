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
package io.btrace.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class BTraceJarPackagingTest {

  /**
   * The extensions expected to ship with the distribution. Mirrors {@code releaseExtensionProjects}
   * in {@code btrace-dist/build.gradle}, which is a build-script local and therefore not readable
   * from test code.
   */
  private static final Set<String> RELEASE_EXTENSIONS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "btrace-contracts",
                  "btrace-gpu-bridge",
                  "btrace-llm-trace",
                  "btrace-metrics",
                  "btrace-rag-quality",
                  "btrace-statsd",
                  "btrace-utils")));

  @Test
  void includesBootstrapLoaderInRootJar() throws IOException {
    File resourcesDir = new File("build/resources/main");
    File[] versionDirs = resourcesDir.listFiles(File::isDirectory);
    assertNotNull(versionDirs, "Expected versioned dist directory under build/resources/main");
    assertEquals(1, versionDirs.length, "Expected exactly one versioned dist directory");

    File btraceJar = new File(versionDirs[0], "libs/btrace.jar");
    assertTrue(btraceJar.isFile(), "Expected assembled btrace.jar to exist");

    try (JarFile jarFile = new JarFile(btraceJar)) {
      assertNotNull(
          jarFile.getJarEntry("io/btrace/boot/Loader.class"),
          "Expected bootstrap loader class in root of masked JAR");
      assertNotNull(
          jarFile.getJarEntry("META-INF/btrace/client/io/btrace/client/Main.classdata"),
          "Expected masked client main class in client section");

      Attributes attributes = jarFile.getManifest().getMainAttributes();
      assertEquals("io.btrace.boot.Loader", attributes.getValue("Main-Class"));
    }
  }

  @Test
  void packagesOnlyMaintainedExtensions() {
    Set<String> expected = RELEASE_EXTENSIONS;
    File extensionsDir = getExtensionsDir();

    File[] archives = extensionsDir.listFiles(file -> file.getName().endsWith("-extension.zip"));
    assertNotNull(archives, "Expected packaged extension archives");
    assertEquals(expected.size(), archives.length, "Unexpected number of packaged extensions");
    Set<String> packaged = new HashSet<>();
    for (File archive : archives) {
      for (String extension : expected) {
        if (archive.getName().startsWith(extension + "-")) {
          packaged.add(extension);
        }
      }
    }
    assertEquals(expected, packaged, "Unexpected packaged extension archives");

    File[] exploded = extensionsDir.listFiles(File::isDirectory);
    assertNotNull(exploded, "Expected exploded extension directories");
    Set<String> explodedNames = new HashSet<>();
    for (File extension : exploded) {
      explodedNames.add(extension.getName());
    }
    assertEquals(expected, explodedNames, "Unexpected exploded extensions");
  }

  @Test
  void extensionManifestsAreSelfConsistent() throws IOException {
    File extensionsDir = getExtensionsDir();
    String distVersion = extensionsDir.getParentFile().getName().replaceFirst("^v", "");

    // The extensions directory holds both exploded directories and *-extension.zip archives.
    File[] exploded = extensionsDir.listFiles(File::isDirectory);
    assertNotNull(exploded, "Expected exploded extension directories");

    Set<String> inspected = new HashSet<>();
    for (File extensionDir : exploded) {
      String name = extensionDir.getName();
      if (!RELEASE_EXTENSIONS.contains(name)) {
        continue;
      }
      inspected.add(name);

      File[] apiJars = extensionDir.listFiles(file -> file.getName().endsWith("-api.jar"));
      assertNotNull(apiJars, name + ": cannot list extension directory");
      assertEquals(1, apiJars.length, name + ": expected exactly one *-api.jar");

      try (JarFile apiJar = new JarFile(apiJars[0])) {
        assertNotNull(apiJar.getManifest(), name + ": API JAR has no manifest");
        Attributes attributes = apiJar.getManifest().getMainAttributes();

        assertEquals(
            name,
            attributes.getValue("BTrace-Extension-Id"),
            name + ": BTrace-Extension-Id must match the extension directory name");
        assertEquals(
            distVersion,
            attributes.getValue("BTrace-Extension-Version"),
            name + ": BTrace-Extension-Version must match the distribution version directory");

        for (String attribute :
            Arrays.asList(
                "BTrace-Extension-Id",
                "BTrace-Extension-Version",
                "BTrace-Extension-Name",
                "BTrace-Extension-Services",
                "BTrace-Extension-Permissions")) {
          String value = attributes.getValue(attribute);
          assertNotNull(value, name + ": missing manifest attribute " + attribute);
          assertFalse(value.trim().isEmpty(), name + ": blank manifest attribute " + attribute);
        }

        String impl = attributes.getValue("BTrace-Extension-Impl");
        assertNotNull(impl, name + ": missing manifest attribute BTrace-Extension-Impl");
        assertTrue(
            new File(extensionDir, impl).isFile(),
            name + ": BTrace-Extension-Impl names a missing file: " + impl);

        for (String service : attributes.getValue("BTrace-Extension-Services").split(",")) {
          String fqcn = service.trim();
          if (fqcn.isEmpty()) {
            continue;
          }
          String entry = fqcn.replace('.', '/') + ".class";
          assertNotNull(
              apiJar.getJarEntry(entry),
              name + ": service class " + fqcn + " is not present in the API JAR as " + entry);
        }
      }
    }

    assertEquals(RELEASE_EXTENSIONS, inspected, "Not every release extension was inspected");
  }

  /**
   * Asserts that what extension inspection <em>reports</em> matches the manifest it read, for every
   * extension the distribution ships.
   *
   * <p>The manifest is the authoritative description of an extension - it is what the Gradle plugin
   * writes and what the runtime reads when it loads and permission-checks an extension. This drives
   * the real {@code btracex inspect} entry point rather than any internal helper, so a reader that
   * silently sources metadata from somewhere else fails here.
   */
  @Test
  void inspectionReportsWhatTheManifestDeclares() throws Exception {
    File extensionsDir = getExtensionsDir();
    File[] exploded = extensionsDir.listFiles(File::isDirectory);
    assertNotNull(exploded, "Expected exploded extension directories");

    Set<String> inspected = new HashSet<>();
    for (File extensionDir : exploded) {
      String name = extensionDir.getName();
      if (!RELEASE_EXTENSIONS.contains(name)) {
        continue;
      }
      inspected.add(name);

      File[] apiJars = extensionDir.listFiles(file -> file.getName().endsWith("-api.jar"));
      assertNotNull(apiJars, name + ": cannot list extension directory");
      assertEquals(1, apiJars.length, name + ": expected exactly one *-api.jar");

      Attributes attributes;
      try (JarFile apiJar = new JarFile(apiJars[0])) {
        attributes = apiJar.getManifest().getMainAttributes();
      }

      String report = runInspect(extensionDir);

      assertEquals(
          attributes.getValue("BTrace-Extension-Id"),
          reportField(report, "Extension: "),
          name + ": reported id does not match BTrace-Extension-Id");
      assertEquals(
          attributes.getValue("BTrace-Extension-Version"),
          reportField(report, "Version  : "),
          name + ": reported version does not match BTrace-Extension-Version");
      assertEquals(
          splitAttribute(attributes.getValue("BTrace-Extension-Services")),
          splitAttribute(reportField(report, "Services : ")),
          name + ": reported services do not match BTrace-Extension-Services");

      // Permissions are a merge - the manifest plus any @ServiceDescriptor on the service
      // interfaces - so the manifest set must be contained in, not equal to, what is reported.
      Set<String> reportedPerms =
          splitAttribute(reportField(report, "Required : ").replaceAll("[\\[\\]]", ""));
      for (String declared : splitAttribute(attributes.getValue("BTrace-Extension-Permissions"))) {
        assertTrue(
            reportedPerms.contains(declared),
            name + ": declared permission " + declared + " is missing from the report " + report);
      }
    }

    assertEquals(RELEASE_EXTENSIONS, inspected, "Not every release extension was inspected");
  }

  /** Runs {@code btracex inspect <dir>} in-process and returns what it printed. */
  private static String runInspect(File extensionDir) throws Exception {
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(captured, true, "UTF-8"));
      io.btrace.extcli.Main.main(new String[] {"inspect", extensionDir.getAbsolutePath()});
    } finally {
      System.setOut(original);
    }
    return captured.toString("UTF-8");
  }

  private static String reportField(String report, String prefix) {
    for (String line : report.split("\\R")) {
      if (line.startsWith(prefix)) {
        return line.substring(prefix.length()).trim();
      }
    }
    throw new AssertionError("No '" + prefix.trim() + "' line in report:\n" + report);
  }

  private static Set<String> splitAttribute(String value) {
    Set<String> result = new HashSet<>();
    if (value == null) {
      return result;
    }
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  private static File getExtensionsDir() {
    File resourcesDir = new File("build/resources/main");
    File[] versionDirs = resourcesDir.listFiles(File::isDirectory);
    assertNotNull(versionDirs, "Expected versioned dist directory under build/resources/main");
    assertEquals(1, versionDirs.length, "Expected exactly one versioned dist directory");
    File extensionsDir = new File(versionDirs[0], "extensions");
    assertTrue(extensionsDir.isDirectory(), "Expected assembled extensions directory to exist");
    return extensionsDir;
  }
}
