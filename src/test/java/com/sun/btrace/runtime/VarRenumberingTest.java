/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.runtime;

import org.junit.Test;

/**
 *
 * @author jbachorik
 */
public class VarRenumberingTest extends InstrumentorTestBase {
    @Test
    public void bytecodeValidation() throws Exception {
        originalBC = loadTargetClass("InterestingVarsClass");
        transform("issues/InterestingVarsTest");
        checkTransformation(
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "ALOAD 2\n" +
            "INVOKESTATIC resources/InterestingVarsClass.$btrace$traces$issues$InterestingVarsTest$entry (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$issues$InterestingVarsTest$entry(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"/.*\\\\.InterestingVarsClass/\", method=\"initAndStartApp\")\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/issues/InterestingVarsTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "FRAME SAME\n" +
            "ALOAD 0\n" +
            "INVOKESTATIC com/sun/btrace/BTraceUtils.println (Ljava/lang/Object;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "FRAME SAME1 java/lang/Throwable\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 1\n" +
            "MAXLOCALS = 3"
        );
    }
}
