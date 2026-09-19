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
package io.btrace.instr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the {@link ClassHeader} of a class file. Class files the bundled ASM release can parse
 * (major version up to {@link AsmInstrumentationBackend#MAX_ASM_MAJOR_VERSION}) are read with ASM;
 * newer ones are read with the JDK ClassFile API through {@code ClassFileApiClassHeaderReader} from
 * the Java 24 source set, loaded reflectively so that this class stays loadable on Java 8. This
 * mirrors the split between {@link AsmInstrumentationBackend} and the ClassFile API backend: a
 * target JVM whose own classes are newer than ASM can parse must never fall back to ASM for them.
 */
final class ClassHeaderReader {
  private static final Logger log = LoggerFactory.getLogger(ClassHeaderReader.class);

  private static volatile Method classFileApiRead;
  private static volatile boolean classFileApiReadAttempted;

  private ClassHeaderReader() {}

  static ClassHeader read(InputStream in) throws IOException {
    return read(readAllBytes(in));
  }

  /**
   * @throws IllegalArgumentException if the bytes are not a class file this JVM can parse: ASM
   *     rejects them and the ClassFile API is unavailable (agent running on Java &lt; 24) or
   *     rejects them as well
   */
  static ClassHeader read(byte[] bytes) {
    if (classFileMajorVersion(bytes) > AsmInstrumentationBackend.MAX_ASM_MAJOR_VERSION) {
      return readWithClassFileApi(bytes);
    }
    return readWithAsm(bytes);
  }

  static ClassHeader readWithAsm(byte[] bytes) {
    ClassReader cr = new ClassReader(bytes);
    return new ClassHeader(
        cr.getClassName(),
        cr.getSuperName(),
        cr.getInterfaces(),
        (cr.getAccess() & Opcodes.ACC_INTERFACE) != 0);
  }

  static ClassHeader readWithClassFileApi(byte[] bytes) {
    Method m = getClassFileApiRead();
    if (m == null) {
      throw new IllegalArgumentException(
          "Class file major version "
              + classFileMajorVersion(bytes)
              + " is newer than the bundled ASM supports and the JDK ClassFile API is unavailable"
              + " on this JVM");
    }
    try {
      return (ClassHeader) m.invoke(null, (Object) bytes);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new IllegalArgumentException("ClassFile API failed to read the class header", cause);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("ClassFile API header reader is not accessible", e);
    }
  }

  static int classFileMajorVersion(byte[] bytes) {
    if (bytes.length < 8) {
      throw new IllegalArgumentException("Not a class file: " + bytes.length + " bytes");
    }
    return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
  }

  // Lazily loaded so that JVMs without java.lang.classfile never pay for the failed defineClass.
  private static Method getClassFileApiRead() {
    Method m = classFileApiRead;
    if (m != null) {
      return m;
    }
    if (classFileApiReadAttempted) {
      return null;
    }
    synchronized (ClassHeaderReader.class) {
      if (!classFileApiReadAttempted) {
        classFileApiReadAttempted = true;
        classFileApiRead = loadClassFileApiRead();
      }
    }
    return classFileApiRead;
  }

  private static Method loadClassFileApiRead() {
    try {
      Class<?> cls =
          Class.forName(
              "io.btrace.instr.ClassFileApiClassHeaderReader",
              true,
              ClassHeaderReader.class.getClassLoader());
      return cls.getMethod("read", byte[].class);
    } catch (Throwable t) {
      log.debug(
          "ClassFile API class header reader unavailable (expected on JDK < 24): {}",
          t.getMessage());
      return null;
    }
  }

  private static byte[] readAllBytes(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, in.available()));
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }
}
