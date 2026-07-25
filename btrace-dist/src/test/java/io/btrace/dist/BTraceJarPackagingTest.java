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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class BTraceJarPackagingTest {

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
    Set<String> expected =
        new HashSet<>(
            Arrays.asList(
                "btrace-contracts",
                "btrace-gpu-bridge",
                "btrace-llm-trace",
                "btrace-metrics",
                "btrace-rag-quality",
                "btrace-statsd",
                "btrace-utils"));
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
