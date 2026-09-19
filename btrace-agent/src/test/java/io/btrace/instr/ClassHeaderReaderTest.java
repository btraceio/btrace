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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * {@link ClassHeaderReader} dispatch: ASM for class files it can parse, the JDK ClassFile API for
 * newer ones. The ClassFile API path itself is exercised by {@code ClassFileApiBackendTest} on a
 * JDK that has it; here (the plain test JVM) it is only proven that newer class files are never
 * handed to ASM.
 */
class ClassHeaderReaderTest {

  static final int NEWER_THAN_ASM = AsmInstrumentationBackend.MAX_ASM_MAJOR_VERSION + 1;
  static final String CLASS_NAME = "com/example/NewerThanAsm";

  @BeforeAll
  static void installRuntime() throws Throwable {
    // ClassInfo.isBootstrap() consults the installed BTrace runtime (same setup as ClassInfoTest)
    Class<?> accessImpl = Class.forName("io.btrace.runtime.BTraceRuntimeAccessImpl");
    Method m = accessImpl.getDeclaredMethod("install");
    m.setAccessible(true);
    m.invoke(null);
  }

  static byte[] classBytes(int majorVersion) {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        majorVersion,
        Opcodes.ACC_PUBLIC,
        CLASS_NAME,
        null,
        "java/util/AbstractList",
        new String[] {"java/io/Serializable"});
    cw.visitEnd();
    return cw.toByteArray();
  }

  static ClassLoader loaderServing(byte[] bytes) {
    return new ClassLoader(ClassHeaderReaderTest.class.getClassLoader()) {
      @Override
      public InputStream getResourceAsStream(String name) {
        if ((CLASS_NAME + ".class").equals(name)) {
          return new ByteArrayInputStream(bytes);
        }
        return super.getResourceAsStream(name);
      }
    };
  }

  @Test
  void asmReadsSupportedClassFiles() throws Exception {
    ClassHeader header = ClassHeaderReader.read(new ByteArrayInputStream(classBytes(Opcodes.V1_8)));
    assertEquals(CLASS_NAME, header.getClassName());
    assertEquals("java/util/AbstractList", header.getSuperName());
    assertArrayEquals(new String[] {"java/io/Serializable"}, header.getInterfaces());
    assertFalse(header.isInterface());
  }

  @Test
  void asmReadsInterfaceFlag() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
        "com/example/Itf",
        null,
        "java/lang/Object",
        null);
    cw.visitEnd();
    assertTrue(ClassHeaderReader.read(cw.toByteArray()).isInterface());
  }

  @Test
  void classFileMajorVersionIsReadFromTheHeader() {
    assertEquals(52, ClassHeaderReader.classFileMajorVersion(classBytes(Opcodes.V1_8)));
    assertEquals(
        NEWER_THAN_ASM, ClassHeaderReader.classFileMajorVersion(classBytes(NEWER_THAN_ASM)));
  }

  @Test
  @DisabledForJreRange(min = JRE.JAVA_24, disabledReason = "JVMs with the ClassFile API read it")
  void newerClassFilesAreNeverHandedToAsm() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ClassHeaderReader.read(classBytes(NEWER_THAN_ASM)));
    assertTrue(
        e.getMessage().contains("ClassFile API is unavailable"),
        "must fail on the missing ClassFile API, not inside ASM: " + e.getMessage());
  }

  @Test
  @DisabledForJreRange(min = JRE.JAVA_24, disabledReason = "JVMs with the ClassFile API read it")
  void classInfoStaysUnavailableWithoutClassFileApi() {
    ClassInfo info =
        ClassCache.getInstance().get(loaderServing(classBytes(NEWER_THAN_ASM)), CLASS_NAME);
    assertFalse(info.isAvailable());
  }
}
