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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.extensions.Permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.objectweb.asm.ClassVisitor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClassFileApiBackend. All tests are enabled only on JDK 24+ where java.lang.classfile is
 * available (and the backend class is loadable).
 */
@EnabledForJreRange(min = JRE.JAVA_24)
class ClassFileApiBackendTest {

  @Test
  void supportsVersionAbove69() {
    InstrumentationBackend backend = BackendSelector.select(70);
    assertFalse(
        backend instanceof AsmInstrumentationBackend,
        "Expected ClassFile API backend for version 70 on JDK 24+");
    assertTrue(backend.supports(70));
    assertTrue(backend.supports(80));
    assertFalse(backend.supports(69));
  }

  @Test
  void returnsNullWhenNoProbesMatch() {
    InstrumentationBackend backend = BackendSelector.select(70);
    // Pass empty probe list — no match possible
    byte[] result = backend.instrument(null, buildMinimalClass(70), Collections.emptyList());
    assertNull(result);
  }

  @Test
  void entryProbeInjectedIntoMatchingMethod() {
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "doWork",
            io.btrace.core.annotations.Kind.ENTRY,
            "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes when probe matches");
    // Patch back to ASM-readable version to inspect the result
    assertTrue(
        containsInvokeDynamic(patchVersion(result, 65), "doWork", "$btrace$"),
        "Expected INVOKEDYNAMIC for BTrace probe in doWork");
  }

  @Test
  void entryProbeNotInjectedWhenMethodNameMismatches() {
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "otherMethod",
            io.btrace.core.annotations.Kind.ENTRY,
            "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected null when no method name matches");
  }

  @Test
  void returnProbeInjectedBeforeReturn() {
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "compute");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "compute",
            io.btrace.core.annotations.Kind.RETURN,
            "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for RETURN probe");
    assertTrue(
        containsInvokeDynamic(patchVersion(result, 65), "compute", "$btrace$"),
        "Expected INVOKEDYNAMIC for BTrace probe in compute");
  }

  @Test
  void noInjectionForUnsupportedKind() {
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "doWork",
            io.btrace.core.annotations.Kind.CALL, // unsupported by ClassFileApiBackend
            "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected null for unsupported probe kind");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Loads the bytes of a known class file on the classpath and patches bytes 6-7 to simulate a
   * future/EA JDK class version. This produces a syntactically valid class body (only the version
   * header is patched), which is sufficient for ClassFileApiBackend to parse.
   */
  private static byte[] buildMinimalClass(int majorVersion) {
    try {
      byte[] bytes;
      try (java.io.InputStream is =
          ClassFileApiBackendTest.class.getResourceAsStream(
              "/io/btrace/instr/AsmInstrumentationBackend.class")) {
        if (is == null)
          throw new IllegalStateException("AsmInstrumentationBackend.class not found on classpath");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        bytes = baos.toByteArray();
      }
      bytes[6] = (byte) (majorVersion >> 8);
      bytes[7] = (byte) (majorVersion & 0xFF);
      return bytes;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Builds a class with a single static void method using ASM, then patches the class file major
   * version to the given value.
   */
  private static byte[] buildClassWithMethod(
      int majorVersion, String internalClassName, String methodName) {
    org.objectweb.asm.ClassWriter cw =
        new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        org.objectweb.asm.Opcodes.V11,
        org.objectweb.asm.Opcodes.ACC_PUBLIC,
        internalClassName,
        null,
        "java/lang/Object",
        null);
    org.objectweb.asm.MethodVisitor mv =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            methodName,
            "()V",
            null,
            null);
    mv.visitCode();
    mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  /** Patches bytes 6-7 of a class file to the given major version. */
  private static byte[] patchVersion(byte[] bytes, int majorVersion) {
    byte[] copy = Arrays.copyOf(bytes, bytes.length);
    copy[6] = (byte) (majorVersion >> 8);
    copy[7] = (byte) (majorVersion & 0xFF);
    return copy;
  }

  /**
   * Returns true if the named method in the class bytes contains an INVOKEDYNAMIC instruction whose
   * name contains the given substring.
   */
  private static boolean containsInvokeDynamic(
      byte[] classBytes, String methodName, String nameSubstring) {
    final boolean[] found = {false};
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
    cr.accept(
        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
          @Override
          public org.objectweb.asm.MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, org.objectweb.asm.Handle bsm, Object... bsmArgs) {
                if (name.contains(nameSubstring)) found[0] = true;
              }
            };
          }
        },
        0);
    return found[0];
  }

  /**
   * Builds a minimal BTraceProbe stub that matches targetJavaClass#targetMethod and reports one
   * OnMethod handler for the given kind with the supplied descriptor.
   */
  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final io.btrace.core.annotations.Kind kind,
      final String targetDescriptor) {

    final Location loc = new Location();
    loc.setValue(kind);
    final OnMethod om = new OnMethod();
    om.setClazz(targetJavaClass);
    om.setMethod(targetMethod);
    om.setLocation(loc);
    om.setTargetName("onProbe");
    om.setTargetDescriptor(targetDescriptor);
    // All special parameter indices default to -1 (absent) — no @Self, @Return etc.

    return new BTraceProbe() {
      @Override
      public String getActionPrefix() {
        return InstrumentUtils.getActionPrefix(probeInternalName);
      }

      @Override
      public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        return getApplicableHandlers(
            new ClassMeta() {
              @Override
              public String getJavaClassName() {
                return cr.getJavaClassName();
              }

              @Override
              public String getInternalName() {
                return cr.getClassName();
              }

              @Override
              public Collection<String> getAnnotationTypes() {
                return cr.getAnnotationTypes();
              }

              @Override
              public ClassLoader getClassLoader() {
                return cr.getClassLoader();
              }
            });
      }

      @Override
      public Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
        if (targetJavaClass.equals(meta.getJavaClassName())) {
          return Collections.singletonList(om);
        }
        return Collections.emptyList();
      }

      @Override
      public byte[] getFullBytecode() {
        return new byte[0];
      }

      @Override
      public byte[] getDataHolderBytecode() {
        return new byte[0];
      }

      @Override
      public String getClassName() {
        return probeInternalName.replace('/', '.');
      }

      @Override
      public String getClassName(boolean internal) {
        return internal ? probeInternalName : probeInternalName.replace('/', '.');
      }

      @Override
      public boolean isClassRenamed() {
        return false;
      }

      @Override
      public boolean isTransforming() {
        return true;
      }

      @Override
      public boolean isVerified() {
        return true;
      }

      @Override
      public void notifyTransform(String className) {}

      @Override
      public Iterable<OnMethod> onmethods() {
        return Collections.singletonList(om);
      }

      @Override
      public Iterable<OnProbe> onprobes() {
        return Collections.emptyList();
      }

      @Override
      public Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t) {
        return null;
      }

      @Override
      public Class<?> getProbeClass() {
        return null;
      }

      @Override
      public void unregister() {}

      @Override
      public boolean willInstrument(Class<?> clz) {
        return true;
      }

      @Override
      public void checkVerified() {}

      @Override
      public void copyHandlers(ClassVisitor cv) {}

      @Override
      public void applyArgs(ArgsMap argsMap) {}

      @Override
      public BTraceRuntime.Impl getRuntime() {
        return null;
      }

      @Override
      public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
      }
    };
  }
}
