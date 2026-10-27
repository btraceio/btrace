/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that a {@link ClassDataLoader} reads class data only from its own extension's {@code
 * META-INF/btrace-extensions/<id>/} tree, so embedded extensions that carry classes of the same
 * name do not collapse into one.
 */
class ClassDataLoaderTest {
  /** Class whose bytes are planted as {@code .classdata} under different extension ids. */
  public static final class Payload {}

  private static final String PAYLOAD_NAME = Payload.class.getName();

  @TempDir Path root;

  @Test
  void loadsClassDataFromTheExtensionsOwnPrefix() throws Exception {
    plant("ext-a", PAYLOAD_NAME);
    ClassLoader resources = resourceLoader();

    Class<?> loaded = new ClassDataLoader("ext-a", resources, null).loadClass(PAYLOAD_NAME);

    assertEquals(PAYLOAD_NAME, loaded.getName());
    // Defined by the ClassDataLoader, not delegated to the loader that defines this test.
    assertNotSame(Payload.class, loaded);
    assertEquals("ClassDataLoader", loaded.getClassLoader().getClass().getSimpleName());
  }

  @Test
  void twoExtensionsWithTheSameClassNameGetSeparateClasses() throws Exception {
    plant("ext-a", PAYLOAD_NAME);
    plant("ext-b", PAYLOAD_NAME);
    ClassLoader resources = resourceLoader();

    ClassDataLoader loaderA = new ClassDataLoader("ext-a", resources, null);
    ClassDataLoader loaderB = new ClassDataLoader("ext-b", resources, null);
    Class<?> fromA = loaderA.loadClass(PAYLOAD_NAME);
    Class<?> fromB = loaderB.loadClass(PAYLOAD_NAME);

    assertNotSame(fromA, fromB);
    assertSame(loaderA, fromA.getClassLoader());
    assertSame(loaderB, fromB.getClassLoader());
  }

  @Test
  void doesNotReadAnotherExtensionsClassData() throws Exception {
    plant("ext-a", PAYLOAD_NAME);
    ClassLoader resources = resourceLoader();

    assertThrows(
        ClassNotFoundException.class,
        () -> new ClassDataLoader("ext-b", resources, null).loadClass(PAYLOAD_NAME));
  }

  @Test
  void ignoresUnprefixedClassDataAtTheJarRoot() throws Exception {
    // The pre-namespacing layout: one flat tree shared by every embedded extension.
    write(root.resolve(PAYLOAD_NAME.replace('.', '/') + ".classdata"), payloadBytes());
    ClassLoader resources = resourceLoader();

    assertThrows(
        ClassNotFoundException.class,
        () -> new ClassDataLoader("ext-a", resources, null).loadClass(PAYLOAD_NAME));
  }

  @Test
  void rejectsExtensionIdsThatAreNotASinglePathSegment() {
    ClassLoader resources = getClass().getClassLoader();
    assertThrows(IllegalArgumentException.class, () -> new ClassDataLoader(null, resources, null));
    assertThrows(IllegalArgumentException.class, () -> new ClassDataLoader("", resources, null));
    assertThrows(IllegalArgumentException.class, () -> new ClassDataLoader("a/b", resources, null));
    assertThrows(
        IllegalArgumentException.class, () -> new ClassDataLoader("..\\b", resources, null));
  }

  /** The layout contract the loader must honor, spelled out rather than taken from the loader. */
  private void plant(String extensionId, String className) throws IOException {
    String prefixed =
        "META-INF/btrace-extensions/"
            + extensionId
            + "/"
            + className.replace('.', '/')
            + ".classdata";
    write(root.resolve(prefixed), payloadBytes());
  }

  private static void write(Path file, byte[] bytes) throws IOException {
    Files.createDirectories(file.getParent());
    Files.write(file, bytes);
  }

  private static byte[] payloadBytes() throws IOException {
    String resource = PAYLOAD_NAME.replace('.', '/') + ".class";
    try (InputStream in =
        ClassDataLoaderTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException("missing test payload " + resource);
      }
      byte[] buffer = new byte[8192];
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    }
  }

  /** A resource loader over the temp tree only, with the bootstrap loader as parent. */
  private ClassLoader resourceLoader() throws IOException {
    return new URLClassLoader(new URL[] {root.toUri().toURL()}, null);
  }
}
