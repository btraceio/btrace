/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.extension.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddedExtensionManifestIndexTest {
  @TempDir Path tempDir;

  @Test
  void sameJarSeenThroughTwoClassPathEntriesIsNotAConflict() throws Exception {
    // A fat agent is on the bootstrap class path (Boot-Class-Path) and the system class path, so
    // getResources() returns its manifest twice. Model that with a parent and child loader that
    // both list the same jar.
    URL jar = writeJar("agent.jar", "btrace-a,btrace-b").toUri().toURL();
    try (URLClassLoader parent = new URLClassLoader(new URL[] {jar}, null);
        URLClassLoader child = new URLClassLoader(new URL[] {jar}, parent)) {
      List<URL> manifests = list(child);
      assertEquals(2, manifests.size(), "fixture must expose the same manifest twice");

      List<String> conflicts = new ArrayList<>();
      List<String> ids =
          EmbeddedExtensionRepository.collectEmbeddedExtensionIds(
              child.getResources("META-INF/MANIFEST.MF"), conflicts);

      assertEquals(Arrays.asList("btrace-a", "btrace-b"), ids);
      assertTrue(conflicts.isEmpty(), "same jar must not be reported: " + conflicts);
    }
  }

  @Test
  void differentJarsDeclaringTheSameIdAreAConflict() throws Exception {
    URL first = writeJar("first.jar", "btrace-a").toUri().toURL();
    URL second = writeJar("second.jar", "btrace-a,btrace-c").toUri().toURL();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {first, second}, null)) {
      List<String> conflicts = new ArrayList<>();
      List<String> ids =
          EmbeddedExtensionRepository.collectEmbeddedExtensionIds(
              loader.getResources("META-INF/MANIFEST.MF"), conflicts);

      assertEquals(Arrays.asList("btrace-a", "btrace-c"), ids);
      assertEquals(1, conflicts.size(), conflicts.toString());
      assertTrue(conflicts.get(0).contains("'btrace-a'"), conflicts.get(0));
      assertTrue(conflicts.get(0).contains("first.jar"), conflicts.get(0));
      assertTrue(conflicts.get(0).contains("second.jar"), conflicts.get(0));
    }
  }

  private static List<URL> list(ClassLoader loader) throws IOException {
    return Collections.list(loader.getResources("META-INF/MANIFEST.MF"));
  }

  private Path writeJar(String name, String embeddedIds) throws IOException {
    Path jar = tempDir.resolve(name);
    Manifest manifest = new Manifest();
    Attributes attrs = manifest.getMainAttributes();
    attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attrs.putValue("BTrace-Embedded-Extensions", embeddedIds);
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out, manifest)) {
      // manifest only
    }
    return jar;
  }
}
