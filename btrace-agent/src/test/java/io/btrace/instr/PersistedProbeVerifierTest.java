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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import io.btrace.core.VerifierException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * The agent's checks for a probe read back from a pack. A pack can come from any client that
 * reaches the agent, so the checks are the only thing standing between it and the target: a loop
 * and a request for trusted mode both used to pass, because the loop check consulted labels that
 * were never recorded and the trusted annotation was not looked at.
 */
class PersistedProbeVerifierTest {
  private static final String ON_TIMER = "Lio/btrace/core/annotations/OnTimer;";

  private static byte[] probe(boolean trusted, boolean loop) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "test/Probe", null, "java/lang/Object", null);
    AnnotationVisitor bt = cw.visitAnnotation(Constants.BTRACE_DESC, true);
    if (trusted) {
      bt.visit("trusted", Boolean.TRUE);
    }
    bt.visitEnd();

    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "tick", "()V", null, null);
    AnnotationVisitor timer = mv.visitAnnotation(ON_TIMER, true);
    timer.visit("value", 1000L);
    timer.visitEnd();
    mv.visitCode();
    if (loop) {
      // int i = 0; while (i == 0) {} -- a backward jump
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 0);
      Label top = new Label();
      mv.visitLabel(top);
      mv.visitVarInsn(ILOAD, 0);
      Label exit = new Label();
      mv.visitJumpInsn(IFEQ, exit);
      mv.visitJumpInsn(GOTO, top);
      mv.visitLabel(exit);
    }
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void plainProbePasses() {
    assertDoesNotThrow(() -> PersistedProbeVerifier.verify(probe(false, false), t -> false));
  }

  @Test
  void loopIsRefused() {
    VerifierException e =
        assertThrows(
            VerifierException.class,
            () -> PersistedProbeVerifier.verify(probe(false, true), t -> false));
    assertTrue(e.getMessage().contains("loops"), e.getMessage());
  }

  @Test
  void requestForTrustedModeIsRefused() {
    VerifierException e =
        assertThrows(
            VerifierException.class,
            () -> PersistedProbeVerifier.verify(probe(true, false), t -> false));
    assertTrue(e.getMessage().contains("Trusted mode"), e.getMessage());
  }
}
