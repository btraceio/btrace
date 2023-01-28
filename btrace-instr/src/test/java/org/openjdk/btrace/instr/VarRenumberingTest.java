/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openjdk.btrace.instr;

import org.junit.jupiter.api.Test;

/**
 * @author jbachorik
 */
public class VarRenumberingTest extends InstrumentorTestBase {
  @Test
  public void bytecodeValidation() throws Exception {
    originalBC = loadTargetClass("InterestingVarsClass");
    transform("issues/InterestingVarsTest");
    checkTransformation(
        "ALOAD 0\n"
            + "ALOAD 1\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/InterestingVarsClass.$btrace$org$openjdk$btrace$runtime$auxiliary$InterestingVarsTest$entry (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$InterestingVarsTest$entry(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"/.*\\\\.InterestingVarsClass/\", method=\"initAndStartApp\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/InterestingVarsTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "ALOAD 0\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/InterestingVarsTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/InterestingVarsTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/InterestingVarsTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 3");
  }
}
