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
package io.btrace.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MaskedClassLoader} verifying that classes stored as {@code .classdata} files in
 * masked JAR sections are correctly loaded, hidden from the parent classloader, and that ordinary
 * JDK classes are resolved via parent delegation.
 *
 * @author Jaroslav Bachorik
 */
public class MaskedClassLoaderTest {

  /** Fully-qualified name of the class embedded in the test JAR. */
  private static final String MASKED_CLASS_NAME = "io.btrace.boot.TestMaskedClass";

  private File testJar;
  private MaskedClassLoader classLoader;

  /**
   * Build a minimal JAR that contains:
   *
   * <ul>
   *   <li>{@code META-INF/btrace/agent/<path>.classdata} – the masked class
   *   <li>{@code META-INF/btrace/shared/} – section marker (makes it a valid "masked JAR")
   * </ul>
   *
   * <p>The bytecode embedded is a hand-crafted minimal Java 8 class file whose internal name
   * exactly matches {@code io/btrace/boot/TestMaskedClass}. This avoids both an ASM dependency and
   * the JVM name-mismatch rejection that occurs when real class bytes are stored under a different
   * name.
   */
  @BeforeEach
  public void setUp() throws IOException {
    byte[] classBytes = generateTestClassBytecode();

    testJar = Files.createTempFile("masked-cl-test-", ".jar").toFile();

    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(testJar.toPath()))) {
      // Shared section directory marker (used by MaskedJarUtils.isMaskedJar)
      jos.putNextEntry(new JarEntry("META-INF/btrace/shared/"));
      jos.closeEntry();

      // Agent section directory marker
      jos.putNextEntry(new JarEntry("META-INF/btrace/agent/"));
      jos.closeEntry();

      // The masked class stored as .classdata in the agent section
      String entryPath =
          "META-INF/btrace/agent/" + MASKED_CLASS_NAME.replace('.', '/') + ".classdata";
      jos.putNextEntry(new JarEntry(entryPath));
      jos.write(classBytes);
      jos.closeEntry();
    }
  }

  @AfterEach
  public void tearDown() throws Exception {
    if (classLoader != null) {
      classLoader.close();
      classLoader = null;
    }
    if (testJar != null) {
      testJar.delete();
      testJar = null;
    }
  }

  // ---------------------------------------------------------------------------
  // Test cases
  // ---------------------------------------------------------------------------

  /**
   * A class stored as {@code .classdata} in the masked agent section must be loadable by {@link
   * MaskedClassLoader} and must have the correct binary name.
   */
  @Test
  public void testLoadsMaskedClass() throws Exception {
    classLoader = new MaskedClassLoader(testJar, "agent", ClassLoader.getSystemClassLoader());

    Class<?> loaded = classLoader.loadClass(MASKED_CLASS_NAME);

    assertNotNull(loaded, "loadClass must not return null for a masked class");
    assertEquals(
        MASKED_CLASS_NAME, loaded.getName(), "Loaded class must have the expected binary name");
  }

  /**
   * A class whose bytecode is stored only as {@code .classdata} (not as {@code .class}) must NOT be
   * visible to the parent classloader, confirming that the masking strategy works.
   */
  @Test
  public void testMaskedClassNotFoundViaParent() {
    // The system / parent classloader has no knowledge of MASKED_CLASS_NAME because it was
    // never put on the regular classpath as a .class file.
    ClassLoader parent = ClassLoader.getSystemClassLoader();

    assertThrows(
        ClassNotFoundException.class,
        () -> parent.loadClass(MASKED_CLASS_NAME),
        "Parent classloader must not be able to load a class that only exists as .classdata");
  }

  /**
   * Standard JDK classes (e.g. {@code java.lang.String}) must be resolved via parent delegation and
   * must be identical to the class returned by the parent classloader directly.
   */
  @Test
  public void testParentDelegationForNonMaskedClass() throws Exception {
    classLoader = new MaskedClassLoader(testJar, "agent", ClassLoader.getSystemClassLoader());

    Class<?> stringViaParent = Class.forName("java.lang.String");
    Class<?> stringViaMasked = classLoader.loadClass("java.lang.String");

    assertNotNull(stringViaMasked, "String must be loadable via MaskedClassLoader");
    assertEquals(
        stringViaParent,
        stringViaMasked,
        "MaskedClassLoader must delegate java.lang.String to the parent (same Class instance)");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Generates minimal Java 8 class-file bytecode for a trivial class whose internal name is exactly
   * {@code io/btrace/boot/TestMaskedClass}. The bytecode is hand-crafted so that this test requires
   * no ASM dependency and the JVM does not reject the class due to an internal-name mismatch.
   *
   * <p>Constant pool (indices 1-9):
   *
   * <ol>
   *   <li>Class → #8 (io/btrace/boot/TestMaskedClass)
   *   <li>Class → #3 (java/lang/Object)
   *   <li>Utf8 "java/lang/Object"
   *   <li>Utf8 "&lt;init&gt;"
   *   <li>Utf8 "()V"
   *   <li>Utf8 "Code"
   *   <li>NameAndType #4:#5
   *   <li>Utf8 "io/btrace/boot/TestMaskedClass"
   *   <li>Methodref #2.#7 (Object.&lt;init&gt;)
   * </ol>
   */
  private static byte[] generateTestClassBytecode() {
    return new byte[] {
      // Magic
      (byte) 0xca,
      (byte) 0xfe,
      (byte) 0xba,
      (byte) 0xbe,
      // Minor version: 0
      0x00,
      0x00,
      // Major version: 52 (Java 8)
      0x00,
      0x34,
      // Constant pool count: 10 (indices 1-9)
      0x00,
      0x0a,
      // #1 Class{name=#8}
      0x07,
      0x00,
      0x08,
      // #2 Class{name=#3}
      0x07,
      0x00,
      0x03,
      // #3 Utf8 "java/lang/Object"
      0x01,
      0x00,
      0x10,
      0x6a,
      0x61,
      0x76,
      0x61,
      0x2f,
      0x6c,
      0x61,
      0x6e,
      0x67,
      0x2f,
      0x4f,
      0x62,
      0x6a,
      0x65,
      0x63,
      0x74,
      // #4 Utf8 "<init>"
      0x01,
      0x00,
      0x06,
      0x3c,
      0x69,
      0x6e,
      0x69,
      0x74,
      0x3e,
      // #5 Utf8 "()V"
      0x01,
      0x00,
      0x03,
      0x28,
      0x29,
      0x56,
      // #6 Utf8 "Code"
      0x01,
      0x00,
      0x04,
      0x43,
      0x6f,
      0x64,
      0x65,
      // #7 NameAndType{name=#4, descriptor=#5}
      0x0c,
      0x00,
      0x04,
      0x00,
      0x05,
      // #8 Utf8 "io/btrace/boot/TestMaskedClass" (30 bytes)
      0x01,
      0x00,
      0x1e,
      0x69,
      0x6f,
      0x2f,
      0x62,
      0x74,
      0x72,
      0x61,
      0x63,
      0x65,
      0x2f,
      0x62,
      0x6f,
      0x6f,
      0x74,
      0x2f,
      0x54,
      0x65,
      0x73,
      0x74,
      0x4d,
      0x61,
      0x73,
      0x6b,
      0x65,
      0x64,
      0x43,
      0x6c,
      0x61,
      0x73,
      0x73,
      // #9 Methodref{class=#2, name_and_type=#7}
      0x0a,
      0x00,
      0x02,
      0x00,
      0x07,
      // access_flags: public | super
      0x00,
      0x21,
      // this_class: #1
      0x00,
      0x01,
      // super_class: #2
      0x00,
      0x02,
      // interfaces_count: 0
      0x00,
      0x00,
      // fields_count: 0
      0x00,
      0x00,
      // methods_count: 1
      0x00,
      0x01,
      // <init>()V — public
      0x00,
      0x01,
      0x00,
      0x04,
      0x00,
      0x05,
      // attributes_count: 1 (Code)
      0x00,
      0x01,
      // Code attribute name_index=#6, length=17
      0x00,
      0x06,
      0x00,
      0x00,
      0x00,
      0x11,
      // max_stack=1, max_locals=1, code_length=5
      0x00,
      0x01,
      0x00,
      0x01,
      0x00,
      0x00,
      0x00,
      0x05,
      // aload_0, invokespecial #9, return
      0x2a,
      (byte) 0xb7,
      0x00,
      0x09,
      (byte) 0xb1,
      // exception_table_length=0, code attributes_count=0
      0x00,
      0x00,
      0x00,
      0x00,
      // class attributes_count: 0
      0x00,
      0x00
    };
  }
}
