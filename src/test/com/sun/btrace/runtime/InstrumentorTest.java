/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.btrace.runtime;

import com.sun.btrace.instr.RandomIntProvider;
import java.lang.reflect.Field;
import org.junit.BeforeClass;
import support.InstrumentorTestBase;
import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class InstrumentorTest extends InstrumentorTestBase {
    @BeforeClass
    public static void classSetup() throws Exception {
        try {
            Field f = RandomIntProvider.class.getDeclaredField("useBtraceEnter");
            f.setAccessible(true);
            f.setBoolean(null, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void matchDerivedClass() throws Exception {
        originalBC = loadTargetClass("DerivedClass");
        transform("onmethod/MatchDerived");

        checkTransformation("ALOAD 0\nALOAD 1\nALOAD 2\n"
                + "INVOKESTATIC resources/DerivedClass.$btrace$traces$onmethod$MatchDerived$args (Lresources/AbstractClass;Ljava/lang/String;Ljava/util/Map;)V");
    }

    @Test
    public void methodEntryCheckcastBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/CheckcastBefore");

        checkTransformation("DUP\nASTORE 2\nALOAD 0\nLDC \"resources.OnMethodTest\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$CheckcastBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V");
    }

    @Test
    public void methodEntryCheckcastAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/CheckcastAfter");

        checkTransformation("DUP\nALOAD 0\nLDC \"casts\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$CheckcastAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n");
    }

    @Test
    public void methodEntryInstanceofBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/InstanceofBefore");

        checkTransformation("DUP\nASTORE 3\nALOAD 0\nLDC \"resources.OnMethodTest\"\nALOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$InstanceofBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V");
    }

    @Test
    public void methodEntryInstanceofAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/InstanceofAfter");

        checkTransformation("DUP\nASTORE 3\nALOAD 0\nLDC \"casts\"\nALOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$InstanceofAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n");
    }

    @Test
    public void methodEntryCatch() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Catch");

        checkTransformation("DUP\nALOAD 0\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Catch$args (Ljava/lang/Object;Ljava/io/IOException;)V\n"
                + "ASTORE 2");
    }

    @Test
    public void methodEntryThrow() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Throw");

        checkTransformation("DUP\nASTORE 1\nALOAD 0\nLDC \"resources.OnMethodTest\"\nLDC \"exception\"\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Throw$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V");
    }

    @Test
    public void methodEntryError() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Error");

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
                + "DUP\nASTORE 1\nALOAD 0\nLDC \"uncaught\"\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V");
    }

    @Test
    public void methodEntryErrorDuration() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ErrorDuration");

        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LDC 0\n" +
            "LSTORE 1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 3\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 3\n" +
            "LSUB\n" +
            "LSTORE 1\n" +
            "DUP\n" +
            "ASTORE 5\n" +
            "ALOAD 0\n" +
            "LDC \"uncaught\"\n" +
            "LLOAD 1\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ErrorDuration$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 6");
    }

    @Test
    public void methodEntryLine() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Line");

        checkTransformation("LDC \"field\"\nLDC 84\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Line$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
                + "ALOAD 0");
    }

    @Test
    public void methodEntryNewBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewBefore");

        checkTransformation("ALOAD 0\nLDC \"java.util.HashMap\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewBefore$args (Ljava/lang/Object;Ljava/lang/String;)V");
    }

    @Test
    public void methodEntryNewAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewAfter");

        checkTransformation("ASTORE 1\nALOAD 0\nALOAD 1\nLDC \"java.util.HashMap\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewAfter$args (Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;)V\n"
                + "DUP");
    }

    @Test
    public void methodEntrySyncEntry() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/SyncEntry");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
                + "DUP\nASTORE 2\nALOAD 0\nLDC \"sync\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntrySyncExit() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/SyncExit");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
                + "L6\nLINENUMBER 110 L6\n"
                + "DUP\nASTORE 2\nALOAD 0\nLDC \"resources/OnMethodTest\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n");
    }

    @Test
    public void methodEntryNewArrayIntBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayIntBefore");

        checkTransformation("ALOAD 0\nLDC \"int\"\nLDC 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryNewArrayStringBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayStringBefore");

        checkTransformation("ALOAD 0\nLDC \"java.lang.String\"\nLDC 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryNewArrayIntAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayIntAfter");

        checkTransformation("DUP\nALOAD 0\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayIntAfter$args (Ljava/lang/Object;[I)V");
    }

    @Test
    public void methodEntryNewArrayStringAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayStringAfter");

        checkTransformation("DUP\nALOAD 0\nALOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayStringAfter$args (Ljava/lang/Object;[Ljava/lang/String;)V");
    }

    @Test
    public void methodEntryArrayGetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArrayGetBefore");

        checkTransformation("DUP2\nISTORE 3\nASTORE 4\nALOAD 0\nALOAD 4\nILOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArrayGetBefore$args (Ljava/lang/Object;[II)V");
    }

    @Test
    public void methodEntryArrayGetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArrayGetAfter");

        checkTransformation("DUP2\nISTORE 3\nASTORE 4\nDUP\nISTORE 5\nALOAD 0\nILOAD 5\nALOAD 4\nILOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArrayGetAfter$args (Ljava/lang/Object;I[II)V");
    }

    @Test
    public void methodEntryArraySetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArraySetBefore");

        checkTransformation("ISTORE 4\nDUP2\nISTORE 5\nASTORE 6\nILOAD 4\nALOAD 0\nALOAD 6\nILOAD 5\nILOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArraySetBefore$args (Ljava/lang/Object;[III)V");
    }

    @Test
    public void methodEntryArraySetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArraySetAfter");

        checkTransformation("ISTORE 4\nDUP2\nISTORE 5\nASTORE 6\nILOAD 4\nALOAD 0\nALOAD 6\nILOAD 5\nILOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArraySetAfter$args (Ljava/lang/Object;[III)V");
    }

    @Test
    public void methodEntryFieldGetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldGetBefore");

        checkTransformation("DUP\nASTORE 1\nALOAD 0\nALOAD 1\nLDC \"field\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldGetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V");
    }

    @Test
    public void methodEntryFieldGetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldGetAfter");

        checkTransformation("DUP\nASTORE 1\nDUP\nISTORE 2\nALOAD 0\nALOAD 1\nLDC \"field\"\nILOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldGetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryFieldSetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldSetBefore");

        checkTransformation("ISTORE 1\nDUP\nASTORE 2\nILOAD 1\nALOAD 0\nALOAD 2\nLDC \"field\"\nILOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldSetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryFieldSetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldSetAfter");

        checkTransformation("ISTORE 1\nDUP\nASTORE 2\nILOAD 1\nALOAD 0\nALOAD 2\nLDC \"field\"\nILOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldSetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryArgsNoSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsNoSelf");

        checkTransformation("ALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsNoSelf$argsNoSelf (Ljava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryNoArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgs");

        checkTransformation("ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgs$argsEmpty (Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Args");
        checkTransformation("ALOAD 0\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryArgsSampledNoSampling() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsSampledNoSampling");
        checkTransformation("LDC 5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 6\n" +
            "IFEQ L0\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsSampledNoSampling$argsSampled (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsSampledNoSampling$argsNoSampling (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "LINENUMBER 44 L1\n" +
            "L2");
    }

    @Test
    public void methodEntryArgsSampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsSampled");
        checkTransformation("LDC 5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 6\n" +
            "IFEQ L0\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsSampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void methodEntryArgs2Sampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Args2Sampled");
        checkTransformation("LDC 5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 6\n" +
            "IFEQ L0\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Args2Sampled$args2 (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "ILOAD 6\n" +
            "IFEQ L1\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Args2Sampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "LINENUMBER 44 L1\n" +
            "L2"
        );
    }

    @Test
    public void methodEntryArgsSampledAdaptive() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsSampledAdaptive");
        checkTransformation("LDC 5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitAdaptive (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 6\n" +
            "IFEQ L0\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "ILOAD 6\n" +
            "IFEQ L1\n" +
            "LDC 5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.updateEndTs (I)V\n" +
            "L1\n" +
            "L2"
        );
    }

    @Test
    public void methodEntryArgsReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturn");
        checkTransformation("DUP2\nLSTORE 6\nALOAD 0\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryArgsReturnSampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturnSampled");
        checkTransformation("LDC 5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 6\n" +
            "IFEQ L1\n" +
            "DUP2\n" +
            "LSTORE 7\n" +
            "ALOAD 0\n" +
            "LLOAD 7\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturnSampled$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "L2"
        );
    }

    @Test
    public void methodEntryArgsDuration() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration");
        checkTransformation("LDC 0\nLSTORE 6\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLSTORE 8\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLLOAD 8\n"
                + "LSUB\nLSTORE 6\nDUP2\nLSTORE 10\nALOAD 0\nLLOAD 10\nLLOAD 6\n"
                + "ALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "MAXSTACK");
    }

    @Test
    public void methodEntryArgsDurationMultiReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationMultiReturn");
        checkTransformation(
            "LDC 0\n" +
            "LSTORE 6\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP2\n" +
            "LSTORE 10\n" +
            "ALOAD 0\n" +
            "LLOAD 10\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP2\n" +
            "LSTORE 12\n" +
            "ALOAD 0\n" +
            "LLOAD 12\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP2\n" +
            "LSTORE 14\n" +
            "ALOAD 0\n" +
            "LLOAD 14\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 16");
    }

    @Test
    public void methodEntryArgsDurationSampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationSampled");
        checkTransformation(
            "LDC 0\nLSTORE 6\nLDC 5\nINVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\nLSTORE 8\nL2I\nISTORE 10\nILOAD 10\nIFEQ L1\n" +
            "LDC 5\nINVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 8\nLSUB\nLSTORE 6\nDUP2\nLSTORE 11\n" +
            "ALOAD 0\nLLOAD 11\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationSampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\nL2\n"
        );
    }

    @Test
    public void methodEntryArgsDurationBoxed() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationBoxed");
        checkTransformation("");
    }

    @Test
    public void methodEntryArgsDurationConstructor() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationConstructor");
        checkTransformation("LDC 0\nLSTORE 2\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLSTORE 4\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
                + "LLOAD 4\nLSUB\nLSTORE 2\n"
                + "ALOAD 0\nLLOAD 2\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationConstructor$args (Ljava/lang/Object;JLjava/lang/String;)V\n"
                + "MAXSTACK");
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2");
        checkTransformation("LDC 0\nLSTORE 6\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLSTORE 8\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
                + "LLOAD 8\nLSUB\nLSTORE 6\n"
                + "DUP2\nLSTORE 10\nALOAD 0\nLLOAD 10\nLLOAD 6\n"
                + "ALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "DUP2\nLSTORE 12\nALOAD 0\nLLOAD 12\n"
                + "LLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "MAXSTACK = 12\nMAXLOCALS = 14");
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2Sampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2Sampled");
        checkTransformation("LDC 0\nLSTORE 6\nLDC 5\n"
                + "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n"
                + "DUP2\nLSTORE 8\nL2I\nISTORE 10\nILOAD 10\nIFEQ L1\n"
                + "LDC 5\nINVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n"
                + "LLOAD 8\nLSUB\nLSTORE 6\nDUP2\nLSTORE 11\n"
                + "ALOAD 0\nLLOAD 11\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Sampled$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "L1\nILOAD 10\nIFEQ L2\n"
                + "DUP2\nLSTORE 13\n"
                + "ALOAD 0\nLLOAD 13\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Sampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "L2\nL3\n"
        );
    }

    @Test
    public void methodEntryArgsDurationErr() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationErr");

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LDC 0\n" +
            "LSTORE 6\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP\n" +
            "ASTORE 10\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 10\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 11"
        );
    }

    @Test
    public void methodEntryArgsDurationBoxedErr() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationBoxedErr");
        checkTransformation("");
    }

    @Test
    public void methodEntryArgsDurationConstructorErr() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationConstructorErr");
        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L2\n" +
            "LINENUMBER 39 L2\n" +
            "LDC 0\n" +
            "LSTORE 1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 3\n" +
            "L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 3\n" +
            "LSUB\n" +
            "LSTORE 1\n" +
            "DUP\n" +
            "ASTORE 5\n" +
            "ALOAD 0\n" +
            "LLOAD 1\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 6\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L2\n" +
            "LINENUMBER 40 L2\n" +
            "LDC 0\n" +
            "LSTORE 2\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 4\n" +
            "L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 4\n" +
            "LSUB\n" +
            "LSTORE 2\n" +
            "DUP\n" +
            "ASTORE 6\n" +
            "ALOAD 0\n" +
            "LLOAD 2\n" +
            "ALOAD 6\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L2 L1 1\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 7");
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2Err() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2Err");
        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n" +
            "LDC 0\n" +
            "LSTORE 6\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP\n" +
            "ASTORE 10\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 10\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Err$args2 (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "L2\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP\n" +
            "ASTORE 11\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 11\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Err$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 12\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "ATHROW\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LDC 0\n" +
            "LSTORE 6\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "IFLE L2\n" +
            "L3\n" +
            "LINENUMBER 124 L3\n" +
            "L2\n" +
            "LINENUMBER 127 L2\n" +
            "IFLE L4\n" +
            "L5\n" +
            "LINENUMBER 128 L5\n" +
            "L4\n" +
            "LINENUMBER 132 L4\n" +
            "L6\n" +
            "LINENUMBER 133 L6\n" +
            "L1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP\n" +
            "ASTORE 10\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 10\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Err$args2 (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L1 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L1 1\n" +
            "LOCALVARIABLE b J L0 L1 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L1 4\n" +
            "LOCALVARIABLE d [I L0 L1 5\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 11");
    }

    @Test
    public void methodEntryAnytypeArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/AnytypeArgs");
        checkTransformation("ALOAD 0\nICONST_4\nANEWARRAY java/lang/Object\nDUP\n"
                + "ICONST_0\nALOAD 1\nAASTORE\nDUP\n"
                + "ICONST_1\nLLOAD 2\nINVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\nAASTORE\nDUP\n"
                + "ICONST_2\nALOAD 4\nAASTORE\nDUP\n"
                + "ICONST_3\nALOAD 5\nAASTORE\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryAnytypeArgsNoSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/AnytypeArgsNoSelf");
        checkTransformation("ICONST_4\nANEWARRAY java/lang/Object\nDUP\nICONST_0\nALOAD 1\nAASTORE\n"
                + "DUP\nICONST_1\nLLOAD 2\nINVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\nAASTORE\n"
                + "DUP\nICONST_2\nALOAD 4\nAASTORE\nDUP\nICONST_3\nALOAD 5\nAASTORE\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryStaticArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgs");

        checkTransformation("ALOAD 0\nLLOAD 1\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticArgs$args (Ljava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryStaticArgsReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgsReturn");

        checkTransformation("DUP2\nLSTORE 5\nALOAD 0\nLLOAD 5\nLLOAD 1\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticArgsReturn$args (Ljava/lang/String;JJ[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryStaticArgsSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgsSelf");

        checkTransformation("");
    }

    @Test
    public void methodEntryStaticNoArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticNoArgs");

        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticNoArgs$argsEmpty ()V");
    }

    @Test
    public void methodEntryStaticNoArgsSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticNoArgsSelf");

        checkTransformation("");
    }

    @Test
    public void methodCall() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCall");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 0\nALOAD 6\nLLOAD 4\nALOAD 7\n"
                + "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevel\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCall$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 7\nALOAD 6\nLLOAD 4");
    }

    @Test
    public void methodCallSampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallSampled");

        checkTransformation("LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 4\n" +
            "LSTORE 5\n" +
            "ASTORE 7\n" +
            "ASTORE 8\n" +
            "ILOAD 4\n" +
            "IFEQ L1\n" +
            "ALOAD 0\n" +
            "ALOAD 7\n" +
            "LLOAD 5\n" +
            "ALOAD 8\n" +
            "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevel\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallSampled$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L1\n" +
            "ALOAD 8\n" +
            "ALOAD 7\n" +
            "LLOAD 5\n" +
            "L2");
    }

    @Test
    public void methodCallSampledAdaptive() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallSampledAdaptive");

        checkTransformation("LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitAdaptive (I)Z\n" +
            "ISTORE 4\n" +
            "LSTORE 5\n" +
            "ASTORE 7\n" +
            "ASTORE 8\n" +
            "ILOAD 4\n" +
            "IFEQ L1\n" +
            "ALOAD 0\n" +
            "ALOAD 7\n" +
            "LLOAD 5\n" +
            "ALOAD 8\n" +
            "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevel\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L1\n" +
            "ALOAD 8\n" +
            "ALOAD 7\n" +
            "LLOAD 5\n" +
            "ILOAD 4\n" +
            "IFEQ L2\n" +
            "LDC 9\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.updateEndTs (I)V\n" +
            "L2\n" +
            "L3");
    }

    @Test
    public void methodCallNoArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallNoArgs");

        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallNoArgs$args ()V");
    }

    @Test
    public void methodCallReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallReturn");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 7\nALOAD 6\nLLOAD 4\n"
                + "LSTORE 8\nLLOAD 8\nALOAD 6\nLLOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallReturn$args (JLjava/lang/String;J)V\n"
                + "LLOAD 8");
    }

    @Test
    public void methodCallDuration() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallDuration");

        checkTransformation("LDC 0\n" +
            "LSTORE 4\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 6\n" +
            "LSTORE 8\n" +
            "ASTORE 10\n" +
            "ASTORE 11\n" +
            "ALOAD 11\n" +
            "ALOAD 10\n" +
            "LLOAD 8\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 6\n" +
            "LSUB\n" +
            "LSTORE 4\n" +
            "LSTORE 12\n" +
            "LLOAD 12\n" +
            "LLOAD 4\n" +
            "ALOAD 10\n" +
            "LLOAD 8\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallDuration$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 12\n" +
            "MAXSTACK = 7\n" +
            "MAXLOCALS = 14");
    }

    @Test
    public void methodCallDurationSampled() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallDurationSampled");

        checkTransformation("LDC 0\n" +
            "LSTORE 4\n" +
            "LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 6\n" +
            "L2I\n" +
            "ISTORE 8\n" +
            "LSTORE 9\n" +
            "ASTORE 11\n" +
            "ASTORE 12\n" +
            "ALOAD 12\n" +
            "ALOAD 11\n" +
            "LLOAD 9\n" +
            "ILOAD 8\n" +
            "IFEQ L1\n" +
            "LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 6\n" +
            "LSUB\n" +
            "LSTORE 4\n" +
            "LSTORE 13\n" +
            "LLOAD 13\n" +
            "LLOAD 4\n" +
            "ALOAD 11\n" +
            "LLOAD 9\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallDurationSampled$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 13\n" +
            "L1\n" +
            "L2");
    }

    @Test
    public void methodCallDurationSampledMulti() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallDurationSampledMulti");

        checkTransformation("LDC 0\n" +
            "LSTORE 4\n" +
            "LDC 20\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 6\n" +
            "L2I\n" +
            "ISTORE 8\n" +
            "LSTORE 9\n" +
            "ASTORE 11\n" +
            "ASTORE 12\n" +
            "ALOAD 12\n" +
            "ALOAD 11\n" +
            "LLOAD 9\n" +
            "ILOAD 8\n" +
            "IFEQ L1\n" +
            "LDC 20\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 6\n" +
            "LSUB\n" +
            "LSTORE 4\n" +
            "LSTORE 13\n" +
            "LLOAD 13\n" +
            "LLOAD 4\n" +
            "ALOAD 11\n" +
            "LLOAD 9\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 13\n" +
            "L1\n" +
            "LDC 21\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 15\n" +
            "L2I\n" +
            "ISTORE 17\n" +
            "LSTORE 18\n" +
            "ASTORE 20\n" +
            "ALOAD 20\n" +
            "LLOAD 18\n" +
            "ILOAD 17\n" +
            "IFEQ L2\n" +
            "LDC 21\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 15\n" +
            "LSUB\n" +
            "LSTORE 21\n" +
            "LLOAD 21\n" +
            "ALOAD 20\n" +
            "LLOAD 18\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 21\n" +
            "L2\n" +
            "LADD\n" +
            "LSTORE 23\n" +
            "L3\n" +
            "LINENUMBER 115 L3\n" +
            "LLOAD 23\n" +
            "L4");
    }

    // multiple instrumentation of a call site is not handled well
//    @Test
//    public void methodCallDuration2() throws Exception {
//        originalBC = loadTargetClass("OnMethodTest");
//        transform("onmethod/MethodCallDuration2");
//
//        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\n"
//                + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
//                + "LSTORE 8\nALOAD 7\nALOAD 6\nLLOAD 4\nLSTORE 10\n"
//                + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
//                + "LSTORE 12\nLLOAD 10\nLLOAD 12\nLLOAD 8\nLSUB\n"
//                + "ALOAD 6\nLLOAD 4\n"
//                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallDuration$args (JJLjava/lang/String;J)V\n"
//                + "LLOAD 10\nMAXSTACK = 7\nMAXLOCALS = 14\n");
//    }


    @Test
    public void methodCallStatic() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallStatic");

        checkTransformation("LSTORE 4\nASTORE 6\nALOAD 0\nALOAD 6\nLLOAD 4\n"
                + "LDC \"resources/OnMethodTest.callTargetStatic(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevel\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallStatic$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 6\nLLOAD 4");
    }

    @Test
    public void staticMethodCall() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticMethodCall");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 6\nLLOAD 4\nALOAD 7\n"
                + "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevelStatic\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticMethodCall$args (Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 7\nALOAD 6\nLLOAD 4");
    }

    @Test
    public void staticMethodCallStatic() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticMethodCallStatic");

        checkTransformation("LSTORE 4\nASTORE 6\nALOAD 6\nLLOAD 4\n"
                + "LDC \"resources/OnMethodTest.callTargetStatic(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevelStatic\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticMethodCallStatic$args (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 6\nLLOAD 4");
    }

    @Test
    public void methodEntryNoArgsEntryReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgsEntryReturn");

        checkTransformation("ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturn$argsEmptyEntry (Ljava/lang/Object;)V\n"
                + "ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturn$argsEmptyReturn (Ljava/lang/Object;)V");
    }
}