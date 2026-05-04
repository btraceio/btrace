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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.btrace.core.SharedSettings;
import io.btrace.core.VerifierException;
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
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "()V", null, null);
    AnnotationVisitor av = mv.visitAnnotation("Lio/btrace/core/annotations/OnMethod;", true);
    av.visit("clazz", "java.lang.String");
    av.visitEnd();
    mv.visitCode();
    Handle bsm =
        new Handle(
            Opcodes.H_INVOKESTATIC,
            bootstrapOwner,
            "bootstrap",
            BOOTSTRAP_DESC,
            false);
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
}
