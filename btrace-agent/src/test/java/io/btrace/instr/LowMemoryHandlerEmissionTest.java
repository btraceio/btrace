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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.btrace.core.SharedSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** What the preprocessor emits for an {@code @OnLowMemory} handler. */
class LowMemoryHandlerEmissionTest {
  private static final String HANDLER = "io/btrace/core/handlers/LowMemoryHandler";

  @Test
  void preprocessorConstructsTheHandlerWithoutAThresholdProperty() {
    BTraceProbeNode probe =
        (BTraceProbeNode) new BTraceProbeFactory(SharedSettings.GLOBAL).createProbe(probe());
    probe.checkVerified();

    List<String> constructors = new ArrayList<>();
    new ClassReader(probe.getFullBytecode())
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitMethodInsn(
                      int opcode, String owner, String mName, String mDesc, boolean itf) {
                    if (owner.equals(HANDLER) && mName.equals("<init>")) {
                      constructors.add(mDesc);
                    }
                  }
                };
              }
            },
            0);

    // method, pool, threshold, trackUsage
    assertEquals(
        Collections.singletonList("(Ljava/lang/String;Ljava/lang/String;JZ)V"), constructors);
  }

  private static byte[] probe() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
    cw.visitAnnotation("Lio/btrace/core/annotations/BTrace;", true).visitEnd();
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "onLowMemory",
            "(Ljava/lang/management/MemoryUsage;)V",
            null,
            null);
    AnnotationVisitor av = mv.visitAnnotation("Lio/btrace/core/annotations/OnLowMemory;", true);
    av.visit("pool", "Metaspace");
    av.visit("threshold", 1048576L);
    av.visitEnd();
    mv.visitCode();
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }
}
