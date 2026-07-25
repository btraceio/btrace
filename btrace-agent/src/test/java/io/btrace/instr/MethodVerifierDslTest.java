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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.SharedSettings;
import io.btrace.core.VerifierException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodVerifierDslTest {

  private static final String BOOTSTRAP_OWNER = "io/btrace/runtime/BTraceBootstrap";
  private static final String INDY_DISPATCHER = "io/btrace/runtime/IndyDispatcher";
  private static final String BOOTSTRAP_DESC =
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
          + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

  private void verifyProbeBytes(byte[] bytes) {
    BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);
    BTraceProbe probe = factory.createProbe(bytes, null);
    probe.checkVerified();
  }

  private byte[] probeWithIndy(String bootstrapOwner) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
    cw.visitAnnotation("Lio/btrace/core/annotations/BTrace;", true).visitEnd();
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "()V", null, null);
    AnnotationVisitor av = mv.visitAnnotation("Lio/btrace/core/annotations/OnMethod;", true);
    av.visit("clazz", "java.lang.String");
    av.visitEnd();
    mv.visitCode();
    Handle bsm =
        new Handle(Opcodes.H_INVOKESTATIC, bootstrapOwner, "bootstrap", BOOTSTRAP_DESC, false);
    mv.visitInvokeDynamicInsn("println", "()V", bsm);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 0);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void indyWithBTraceBootstrap_passesVerifier() {
    assertDoesNotThrow(() -> verifyProbeBytes(probeWithIndy(BOOTSTRAP_OWNER)));
  }

  @Test
  void indyWithIndyDispatcher_passesVerifier() {
    assertDoesNotThrow(() -> verifyProbeBytes(probeWithIndy(INDY_DISPATCHER)));
  }

  @Test
  void indyWithUnknownBootstrap_failsVerifier() {
    assertThrows(
        VerifierException.class, () -> verifyProbeBytes(probeWithIndy("com/evil/Bootstrap")));
  }

  @Test
  void directRecursionFailsVerifier() {
    assertRecursionFails(probeWithHandlerCall("TestProbe", "handler"));
  }

  @Test
  void mutualRecursionFailsVerifier() {
    ClassWriter cw = newProbeWriter();
    MethodVisitor handler = handler(cw, "handler");
    handler.visitMethodInsn(Opcodes.INVOKESTATIC, "TestProbe", "helper", "()V", false);
    handler.visitInsn(Opcodes.RETURN);
    handler.visitMaxs(0, 0);
    handler.visitEnd();
    MethodVisitor helper = cw.visitMethod(Opcodes.ACC_STATIC, "helper", "()V", null, null);
    helper.visitCode();
    helper.visitMethodInsn(Opcodes.INVOKESTATIC, "TestProbe", "handler", "()V", false);
    helper.visitInsn(Opcodes.RETURN);
    helper.visitMaxs(0, 0);
    helper.visitEnd();
    cw.visitEnd();

    assertRecursionFails(cw.toByteArray());
  }

  @Test
  void acyclicSameClassHelperPassesVerifier() {
    ClassWriter cw = newProbeWriter();
    MethodVisitor handler = handler(cw, "handler");
    handler.visitMethodInsn(Opcodes.INVOKESTATIC, "TestProbe", "helper", "()V", false);
    handler.visitInsn(Opcodes.RETURN);
    handler.visitMaxs(0, 0);
    handler.visitEnd();
    MethodVisitor helper = cw.visitMethod(Opcodes.ACC_STATIC, "helper", "()V", null, null);
    helper.visitCode();
    helper.visitInsn(Opcodes.RETURN);
    helper.visitMaxs(0, 0);
    helper.visitEnd();
    cw.visitEnd();

    assertDoesNotThrow(() -> verifyProbeBytes(cw.toByteArray()));
  }

  @Test
  void sameNamedExternalStaticCallDoesNotCreateLocalCycle() {
    VerifierException exception =
        assertThrows(
            VerifierException.class,
            () -> verifyProbeBytes(probeWithHandlerCall("io/btrace/External", "handler")));
    assertFalse(exception.getMessage().contains("endless loop"));
  }

  @Test
  void persistedRecursiveProbeRetainsVerificationFailure() throws Exception {
    boolean trusted = SharedSettings.GLOBAL.isTrusted();
    try {
      SharedSettings.GLOBAL.setTrusted(true);
      BTraceProbeFactory trustedFactory = new BTraceProbeFactory(SharedSettings.GLOBAL);
      BTraceProbeNode recursiveProbe =
          (BTraceProbeNode)
              trustedFactory.createProbe(probeWithHandlerCall("TestProbe", "handler"));
      BTraceProbePersisted persisted = BTraceProbePersisted.from(recursiveProbe);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      persisted.write(new DataOutputStream(bytes));

      SharedSettings.GLOBAL.setTrusted(false);
      BTraceProbe restored =
          new BTraceProbeFactory(SharedSettings.GLOBAL).createProbe(bytes.toByteArray());
      assertFalse(restored.isVerified());
      assertRecursionFails(restored);
    } finally {
      SharedSettings.GLOBAL.setTrusted(trusted);
    }
  }

  private void assertRecursionFails(byte[] bytes) {
    assertRecursionFails(() -> verifyProbeBytes(bytes));
  }

  private static void assertRecursionFails(BTraceProbe probe) {
    assertRecursionFails(probe::checkVerified);
  }

  private static void assertRecursionFails(Runnable verification) {
    VerifierException exception = assertThrows(VerifierException.class, verification::run);
    assertTrue(exception.getMessage().contains("endless loop"));
  }

  private static ClassWriter newProbeWriter() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
    cw.visitAnnotation("Lio/btrace/core/annotations/BTrace;", true).visitEnd();
    return cw;
  }

  private static MethodVisitor handler(ClassWriter cw, String name) {
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()V", null, null);
    AnnotationVisitor av = mv.visitAnnotation("Lio/btrace/core/annotations/OnMethod;", true);
    av.visit("clazz", "java.lang.String");
    av.visitEnd();
    mv.visitCode();
    return mv;
  }

  private static byte[] probeWithHandlerCall(String owner, String name) {
    ClassWriter cw = newProbeWriter();
    MethodVisitor handler = handler(cw, "handler");
    handler.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, "()V", false);
    handler.visitInsn(Opcodes.RETURN);
    handler.visitMaxs(0, 0);
    handler.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }
}
