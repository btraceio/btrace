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
package io.btrace.compiler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import static org.junit.jupiter.api.Assertions.*;

public class PostprocessorDslRewriteTest {

  private static final String BTRACE_DSL = "io/btrace/BTrace";
  private static final String BOOTSTRAP_OWNER = "io/btrace/runtime/BTraceBootstrap";
  private static final String BOOTSTRAP_DESC =
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
          + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

  @Test
  void invokestatic_toBTraceDsl_rewrittenToInvokeDynamic() throws Exception {
    // Build bytecode with INVOKESTATIC io/btrace/BTrace.println
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "()V", null, null);
    mv.visitCode();
    mv.visitLdcInsn("hello");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, BTRACE_DSL, "println", "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 0);
    mv.visitEnd();
    cw.visitEnd();
    byte[] original = cw.toByteArray();

    // Run through Postprocessor
    ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    new ClassReader(original).accept(new Postprocessor(out), ClassReader.EXPAND_FRAMES);
    byte[] rewritten = out.toByteArray();

    AtomicBoolean sawIndy = new AtomicBoolean(false);
    AtomicBoolean sawInvokeStatic = new AtomicBoolean(false);
    new ClassReader(rewritten)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitInvokeDynamicInsn(
                      String name, String desc, Handle bsm, Object... args) {
                    if ("println".equals(name)
                        && BOOTSTRAP_OWNER.equals(bsm.getOwner())
                        && "bootstrap".equals(bsm.getName())) {
                      sawIndy.set(true);
                    }
                  }

                  @Override
                  public void visitMethodInsn(
                      int op, String owner, String name, String desc, boolean itf) {
                    if (op == Opcodes.INVOKESTATIC && BTRACE_DSL.equals(owner)) {
                      sawInvokeStatic.set(true);
                    }
                  }
                };
              }
            },
            0);

    assertTrue(sawIndy.get(), "Expected INVOKEDYNAMIC for println");
    assertFalse(sawInvokeStatic.get(), "INVOKESTATIC to io/btrace/BTrace should be gone");
  }

  @Test
  void invokestatic_toOtherClass_notRewritten() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe2", null, "java/lang/Object", null);
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "()V", null, null);
    mv.visitCode();
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "io/btrace/core/BTraceUtils",
        "println",
        "(Ljava/lang/String;)V",
        false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    byte[] original = cw.toByteArray();

    ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    new ClassReader(original).accept(new Postprocessor(out), ClassReader.EXPAND_FRAMES);
    byte[] rewritten = out.toByteArray();

    AtomicBoolean sawIndy = new AtomicBoolean(false);
    new ClassReader(rewritten)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitInvokeDynamicInsn(
                      String name, String desc, Handle bsm, Object... args) {
                    if (BOOTSTRAP_OWNER.equals(bsm.getOwner())) sawIndy.set(true);
                  }
                };
              }
            },
            0);

    assertFalse(sawIndy.get(), "INVOKESTATIC to BTraceUtils must NOT be rewritten");
  }
}
