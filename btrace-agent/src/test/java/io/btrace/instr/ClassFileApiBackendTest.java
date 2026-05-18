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

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.extensions.Permission;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.objectweb.asm.ClassVisitor;

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
    requireJdk26ForVersion70();
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
    requireJdk26ForVersion70();
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
   * Returns the running JDK's major version using only Java 8-compatible APIs. {@code
   * java.specification.version} is {@code "1.8"} on Java 8 and {@code "9"}, {@code "10"}, … {@code
   * "26"} on Java 9+.
   */
  private static int javaMajorVersion() {
    String spec = System.getProperty("java.specification.version", "8");
    try {
      if (spec.startsWith("1.")) {
        return Integer.parseInt(spec.substring(2));
      }
      return Integer.parseInt(spec);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Skips the test on JDK versions that cannot parse class file version 70 (Java 26). The ClassFile
   * API only supports class files up to the running JDK's own major version (JDK 24 → v68, JDK 25 →
   * v69, JDK 26 → v70). Tests that instrument version-70 class files require JDK 26+.
   */
  private static void requireJdk26ForVersion70() {
    int major = javaMajorVersion();
    Assumptions.assumeTrue(
        major >= 26,
        "ClassFile API on JDK "
            + major
            + " cannot parse class file version 70; test requires JDK 26+");
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
   * OnMethod handler for the given kind with the supplied descriptor. Uses -1 for both
   * returnParameter and durationParameter (absent).
   */
  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final io.btrace.core.annotations.Kind kind,
      final String targetDescriptor) {
    return buildStubProbe(
        probeInternalName, targetJavaClass, targetMethod, kind, targetDescriptor, -1, -1);
  }

  /**
   * Builds a minimal BTraceProbe stub that matches targetJavaClass#targetMethod and reports one
   * OnMethod handler for the given kind with the supplied descriptor. {@code returnParameter} and
   * {@code durationParameter} specify the handler parameter index for {@code @Return} and
   * {@code @Duration} respectively; pass -1 if not used.
   */
  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final io.btrace.core.annotations.Kind kind,
      final String targetDescriptor,
      final int returnParameter,
      final int durationParameter) {

    final Location loc = new Location();
    loc.setValue(kind);
    final OnMethod om = new OnMethod();
    om.setClazz(targetJavaClass);
    om.setMethod(targetMethod);
    om.setLocation(loc);
    om.setTargetName("onProbe");
    om.setTargetDescriptor(targetDescriptor);
    if (returnParameter != -1) {
      om.setReturnParameter(returnParameter);
    }
    if (durationParameter != -1) {
      om.setDurationParameter(durationParameter);
    }
    // All other special parameter indices default to -1 (absent) — no @Self etc.

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

  // ---------------------------------------------------------------------------
  // @Return / @Duration helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a class with a single static method returning the given type using ASM, then patches the
   * class file major version to the given value.
   */
  private static byte[] buildClassWithNonVoidMethod(
      int majorVersion, String internalClassName, String methodName, String returnDescriptor) {
    org.objectweb.asm.ClassWriter cw =
        new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        org.objectweb.asm.Opcodes.V11,
        org.objectweb.asm.Opcodes.ACC_PUBLIC,
        internalClassName,
        null,
        "java/lang/Object",
        null);
    String methodDesc = "()" + returnDescriptor;
    org.objectweb.asm.MethodVisitor mv =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            methodName,
            methodDesc,
            null,
            null);
    mv.visitCode();
    // emit appropriate return for the type
    switch (returnDescriptor) {
      case "I":
      case "Z":
      case "B":
      case "S":
      case "C":
        mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.IRETURN);
        break;
      case "J":
        mv.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
        break;
      case "F":
        mv.visitInsn(org.objectweb.asm.Opcodes.FCONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.FRETURN);
        break;
      case "D":
        mv.visitInsn(org.objectweb.asm.Opcodes.DCONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.DRETURN);
        break;
      default: // reference type
        mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        break;
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  /**
   * Returns the INVOKEDYNAMIC instruction descriptor for the first matching call site, or null if
   * not found.
   */
  private static String getInvokeDynamicDescriptor(
      byte[] classBytes, String methodName, String nameSubstring) {
    final String[] found = {null};
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
                if (name.contains(nameSubstring) && found[0] == null) found[0] = desc;
              }
            };
          }
        },
        0);
    return found[0];
  }

  /** Counts INVOKEDYNAMIC instructions whose name contains the given substring. */
  private static int countInvokeDynamic(
      byte[] classBytes, String methodName, String nameSubstring) {
    int[] count = {0};
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
                if (name.contains(nameSubstring)) count[0]++;
              }
            };
          }
        },
        0);
    return count[0];
  }

  /**
   * Scans local variable store instructions in the named method and returns the set of slot indices
   * that are written to. Used to verify no slot collisions.
   */
  private static java.util.Set<Integer> getStoredLocalSlots(byte[] classBytes, String methodName) {
    java.util.Set<Integer> slots = new java.util.HashSet<>();
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
    cr.accept(
        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
          @Override
          public org.objectweb.asm.MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
              @Override
              public void visitVarInsn(int opcode, int varIndex) {
                // All xSTORE opcodes
                if (opcode == org.objectweb.asm.Opcodes.ISTORE
                    || opcode == org.objectweb.asm.Opcodes.LSTORE
                    || opcode == org.objectweb.asm.Opcodes.FSTORE
                    || opcode == org.objectweb.asm.Opcodes.DSTORE
                    || opcode == org.objectweb.asm.Opcodes.ASTORE) {
                  slots.add(varIndex);
                }
              }
            };
          }
        },
        0);
    return slots;
  }

  // ---------------------------------------------------------------------------
  // @Return tests
  // The following tests instrument class file version 70 (Java 26+). Each test calls
  // requireJdk26ForVersion70() to skip on JDK < 26 — the ClassFile API can only parse
  // class files up to the running JDK's own major version (JDK 24→v68, JDK 25→v69, JDK 26→v70).
  // The class-level @EnabledForJreRange(min = JRE.JAVA_24) covers only basic parsing tests.
  // ---------------------------------------------------------------------------

  @Test
  void returnProbeInjectedWithReturnValueInt() {
    requireJdk26ForVersion70();
    // Build class with method returning int
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "compute", "I");
    // Handler descriptor: (I)V — index 0 is the return value
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "compute",
            io.btrace.core.annotations.Kind.RETURN,
            "(I)V",
            0, // returnParameter at index 0
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for @Return probe on int method");
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "compute", "$btrace$");
    assertNotNull(desc, "Expected INVOKEDYNAMIC in instrumented compute method");
    // Descriptor must include int parameter at index 0
    assertTrue(
        desc.startsWith("(I"), "Expected int parameter at position 0 in descriptor, got: " + desc);
  }

  @Test
  void returnProbeInjectedWithReturnValueLong() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "compute", "J");
    // Handler descriptor: (J)V — index 0 is long return value (2 slots)
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "compute",
            io.btrace.core.annotations.Kind.RETURN,
            "(J)V",
            0, // returnParameter at index 0
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    String desc = getInvokeDynamicDescriptor(patchVersion(result, 65), "compute", "$btrace$");
    assertNotNull(desc);
    assertTrue(desc.startsWith("(J"), "Expected long parameter in descriptor, got: " + desc);
  }

  @Test
  void returnProbeSkippedForVoidMethod() {
    requireJdk26ForVersion70();
    // void method: @Return handler should be silently skipped (no INVOKEDYNAMIC emitted)
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "doWork",
            io.btrace.core.annotations.Kind.RETURN,
            "(I)V", // handler expects int return value, but method is void
            0, // returnParameter
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    // Either null (nothing matched) or result with zero INVOKEDYNAMIC instructions
    if (result != null) {
      byte[] loadable = patchVersion(result, 65);
      int count = countInvokeDynamic(loadable, "doWork", "$btrace$");
      assertEquals(0, count, "Expected no INVOKEDYNAMIC for @Return on void method");

      // Load instrumented bytes to confirm no VerifyError from stack corruption
      assertDoesNotThrow(
          () -> {
            ClassLoader cl =
                new ClassLoader(null) {
                  @Override
                  protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if ("com.example.Target".equals(name)) {
                      return defineClass(name, loadable, 0, loadable.length);
                    }
                    throw new ClassNotFoundException(name);
                  }
                };
            cl.loadClass("com.example.Target");
          },
          "Instrumented void-return class must load without VerifyError");
    }
  }

  // ---------------------------------------------------------------------------
  // @Duration tests
  // ---------------------------------------------------------------------------

  @Test
  void durationProbeInjectedOnNormalReturn() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "timed");
    // Handler descriptor: (J)V — index 0 is duration (long)
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "timed",
            io.btrace.core.annotations.Kind.RETURN,
            "(J)V",
            -1, // no @Return
            0); // durationParameter at index 0

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for @Duration probe");
    String desc = getInvokeDynamicDescriptor(patchVersion(result, 65), "timed", "$btrace$");
    assertNotNull(desc, "Expected INVOKEDYNAMIC in instrumented timed method");
    assertTrue(
        desc.startsWith("(J"), "Expected long (duration) parameter in descriptor, got: " + desc);
  }

  @Test
  void durationProbeInjectedOnExceptionExit() {
    requireJdk26ForVersion70();
    // Build a class whose method can throw (we'll verify the exception handler block exists)
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "risky");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "risky",
            io.btrace.core.annotations.Kind.RETURN,
            "(J)V",
            -1, // no @Return
            0); // durationParameter

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for @Duration probe");
    // The instrumented class must have an exception table entry (for the finally-block pattern).
    // We verify by checking that an exception handler INVOKEDYNAMIC exists
    // (the exception handler block contains its own INVOKEDYNAMIC for the duration probe).
    byte[] readable = patchVersion(result, 65);
    int indyCount = countInvokeDynamic(readable, "risky", "$btrace$");
    // There should be 2 INVOKEDYNAMIC calls: one for normal return, one in exception handler
    assertEquals(
        2,
        indyCount,
        "Expected 2 INVOKEDYNAMIC calls (normal exit + exception handler), got: " + indyCount);
  }

  // ---------------------------------------------------------------------------
  // @Return + @Duration combined test
  // ---------------------------------------------------------------------------

  @Test
  void returnAndDurationSlotsDoNotCollide() {
    requireJdk26ForVersion70();
    // Method returns int; handler has both @Return (int, index 0) and @Duration (long, index 1)
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "combined", "I");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "combined",
            io.btrace.core.annotations.Kind.RETURN,
            "(IJ)V", // @Return at 0, @Duration at 1
            0, // returnParameter
            1); // durationParameter

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes");
    byte[] readable = patchVersion(result, 65);

    // Verify INVOKEDYNAMIC is present
    assertTrue(containsInvokeDynamic(readable, "combined", "$btrace$"));

    // Verify no slot collision: @Return slot and @Duration slot must be distinct
    java.util.Set<Integer> storedSlots = getStoredLocalSlots(readable, "combined");
    // There must be at least 2 store instructions (one for retVal, one for duration)
    assertTrue(
        storedSlots.size() >= 2,
        "Expected at least 2 distinct local slots (retVal + duration), got: " + storedSlots);
  }
}
