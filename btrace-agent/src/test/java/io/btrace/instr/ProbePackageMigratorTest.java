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

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ProbePackageMigratorTest {

  private static byte[] buildOldPackageClass() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        "org/openjdk/btrace/test/FakeProbe",
        null,
        "java/lang/Object",
        null);
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "probe",
            "(Lorg/openjdk/btrace/core/BTraceRuntime;)V",
            null,
            null);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void migratesOldPackageReferences() {
    byte[] old = buildOldPackageClass();
    byte[] migrated = ProbePackageMigrator.migrate(old);

    final String[] info = new String[2];
    new ClassReader(migrated)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
                info[0] = name;
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if ("probe".equals(name)) info[1] = descriptor;
                return null;
              }
            },
            0);

    assertEquals("io/btrace/test/FakeProbe", info[0]);
    assertEquals("(Lio/btrace/core/BTraceRuntime;)V", info[1]);
  }

  @Test
  void noOpForNewPackage() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        "io/btrace/test/NewProbe",
        null,
        "java/lang/Object",
        null);
    cw.visitEnd();
    byte[] original = cw.toByteArray();

    byte[] result = ProbePackageMigrator.migrate(original);
    assertEquals("io/btrace/test/NewProbe", new ClassReader(result).getClassName());
  }

  @Test
  void migratesClassNames() {
    assertEquals(
        "io/btrace/runtime/auxiliary/P",
        ProbePackageMigrator.migrateClassName("org/openjdk/btrace/runtime/auxiliary/P"));
    assertEquals(
        "io.btrace.runtime.auxiliary.P",
        ProbePackageMigrator.migrateClassName("org.openjdk.btrace.runtime.auxiliary.P"));
    assertEquals("my/Probe", ProbePackageMigrator.migrateClassName("my/Probe"));
  }

  @Test
  void rewritesLegacyRuntimeGlueToBridge() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "P", null, "java/lang/Object", null);
    cw.visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "runtime",
            "Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;",
            null,
            null)
        .visitEnd();
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m", "()V", null, null);
    mv.visitCode();
    mv.visitFieldInsn(
        Opcodes.GETSTATIC, "P", "runtime", "Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "org/openjdk/btrace/runtime/BTraceRuntimeAccess",
        "enter",
        "(Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z",
        false);
    mv.visitInsn(Opcodes.POP);
    mv.visitFieldInsn(
        Opcodes.GETSTATIC, "P", "runtime", "Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;");
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "org/openjdk/btrace/runtime/BTraceRuntimeImplBase",
        "leave",
        "()V",
        false);
    mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType("org/openjdk/btrace/runtime/auxiliary/P"));
    mv.visitInsn(Opcodes.POP);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();

    byte[] migrated = ProbePackageMigrator.migrate(cw.toByteArray());

    final java.util.List<String> calls = new java.util.ArrayList<>();
    new ClassReader(migrated)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String d, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitMethodInsn(
                      int opcode, String owner, String n, String desc, boolean itf) {
                    calls.add(opcode + " " + owner + "." + n + desc + " " + itf);
                  }

                  @Override
                  public void visitLdcInsn(Object value) {
                    calls.add("ldc " + value);
                  }
                };
              }
            },
            0);

    assertEquals(
        java.util.Arrays.asList(
            Opcodes.INVOKESTATIC
                + " io/btrace/runtime/BTraceRuntimeAccess.enter(Lio/btrace/core/BTraceRuntimeBridge;)Z false",
            Opcodes.INVOKEINTERFACE + " io/btrace/core/BTraceRuntimeBridge.leave()V true",
            "ldc Lio/btrace/runtime/auxiliary/P;"),
        calls);
  }
}
