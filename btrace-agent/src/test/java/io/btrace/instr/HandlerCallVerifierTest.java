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
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import io.btrace.core.SharedSettings;
import io.btrace.core.VerifierException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

/**
 * A guarded handler that calls another guarded handler does nothing at run time, because the
 * callee's re-entrancy guard returns at once. The agent refuses such a probe whether it arrives as
 * a class or as a pack.
 */
class HandlerCallVerifierTest {
  private static final String ON_TIMER = "Lio/btrace/core/annotations/OnTimer;";
  private static final String ON_EVENT = "Lio/btrace/core/annotations/OnEvent;";
  private static final String ON_EXIT = "Lio/btrace/core/annotations/OnExit;";

  /** A probe whose {@code caller} (annotated {@code callerAnnotation}) calls {@code callee}. */
  private static byte[] probe(String callerAnnotation, String callerDesc, String calleeAnnotation) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_8, ACC_PUBLIC, "test/Probe", null, "java/lang/Object", null);
    cw.visitAnnotation(Constants.BTRACE_DESC, true).visitEnd();

    MethodVisitor callee =
        cw.visitMethod(
            calleeAnnotation != null ? ACC_PUBLIC | ACC_STATIC : ACC_PRIVATE | ACC_STATIC,
            "callee",
            "()V",
            null,
            null);
    if (calleeAnnotation != null) {
      annotate(callee, calleeAnnotation);
    }
    callee.visitCode();
    callee.visitInsn(RETURN);
    callee.visitMaxs(0, 0);
    callee.visitEnd();

    MethodVisitor caller =
        cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "caller", callerDesc, null, null);
    annotate(caller, callerAnnotation);
    caller.visitCode();
    caller.visitMethodInsn(INVOKESTATIC, "test/Probe", "callee", "()V", false);
    caller.visitInsn(RETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void annotate(MethodVisitor mv, String annotation) {
    AnnotationVisitor av = mv.visitAnnotation(annotation, true);
    if (ON_TIMER.equals(annotation)) {
      av.visit("value", 1000L);
    }
    av.visitEnd();
  }

  @Test
  void guardedHandlerCallingGuardedHandlerIsRefused() {
    byte[] bytes = probe(ON_TIMER, "()V", ON_EVENT);
    VerifierException e =
        assertThrows(
            VerifierException.class, () -> HandlerCallVerifier.check(new ClassReader(bytes)));
    assertTrue(e.getMessage().contains("cannot call another handler"), e.getMessage());
    assertTrue(e.getMessage().contains("caller calls callee"), e.getMessage());
    // a pack goes through the same check
    assertThrows(VerifierException.class, () -> PersistedProbeVerifier.verify(bytes, t -> false));
    // and so does a plain class, through the probe factory
    BTraceProbe probe = new BTraceProbeFactory(new SharedSettings()).createProbe(bytes);
    VerifierException fromFactory = assertThrows(VerifierException.class, probe::checkVerified);
    assertTrue(
        fromFactory.getMessage().contains("cannot call another handler"), fromFactory.getMessage());
  }

  @Test
  void helperAndUnguardedCallersPass() {
    assertDoesNotThrow(
        () -> HandlerCallVerifier.check(new ClassReader(probe(ON_TIMER, "()V", null))));
    assertDoesNotThrow(
        () -> HandlerCallVerifier.check(new ClassReader(probe(ON_EXIT, "(I)V", ON_EVENT))));
  }
}
