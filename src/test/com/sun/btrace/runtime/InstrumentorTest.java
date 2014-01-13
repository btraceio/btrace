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

import support.InstrumentorTestBase;
import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class InstrumentorTest extends InstrumentorTestBase {

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

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\nDUP\nASTORE 1\nALOAD 0\nLDC \"uncaught\"\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V");
    }

    @Test
    public void methodEntryErrorDuration() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ErrorDuration");

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\nINVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 3\nDUP\nASTORE 5\nALOAD 0\nLDC \"uncaught\"\nLLOAD 3\n"
                + "LLOAD 1\nLSUB\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ErrorDuration$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Throwable;)V\n"
                + "ATHROW\nMAXSTACK = 7\nMAXLOCALS = 6"
        );
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

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\nDUP\nDUP\nASTORE 2\nALOAD 0\nLDC \"sync\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntrySyncExit() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/SyncExit");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\nDUP\nPOP\nL6\nLINENUMBER 110 L6\n"
                + "DUP\nDUP\nASTORE 2\nALOAD 0\nLDC \"resources/OnMethodTest\"\nALOAD 2\n"
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
    public void methodEntryArgsReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturn");
        checkTransformation("DUP2\nLSTORE 6\nALOAD 0\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryArgsDuration() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration");
        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 6\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 8\n"
                + "DUP2\nLSTORE 10\nALOAD 0\nLLOAD 10\nLLOAD 8\nLLOAD 6\nLSUB\nALOAD 1\n"
                + "LLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "MAXSTACK");
    }

    @Test
    public void methodEntryArgsDurationBoxed() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationBoxed");
        checkTransformation("");
    }

    @Test
    public void methodEntryArgsDurationConstrucor() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationConstructor");
        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 4\n"
                + "ALOAD 0\nLLOAD 4\nLLOAD 2\nLSUB\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationConstructor$args (Ljava/lang/Object;JLjava/lang/String;)V\n"
                + "MAXSTACK");
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2");
        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 6\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 8\n"
                + "DUP2\nLSTORE 10\nALOAD 0\nLLOAD 10\nLLOAD 8\nLLOAD 6\nLSUB\nALOAD 1\n"
                + "LLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "DUP2\nLSTORE 12\nALOAD 0\nLLOAD 12\nLLOAD 8\nLLOAD 6\nLSUB\nALOAD 1\n"
                + "LLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "MAXSTACK");
    }

    @Test
    public void methodEntryArgsDurationErr() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationErr");
        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n");
//        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
//                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\n"
//                + "LSTORE 6\nL0\nLINENUMBER 44 L0\nLCONST_0\nLRETURN\nL1\n"
//                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\n"
//                + "LSTORE 8\nDUP\nASTORE 10\nALOAD 0\nLLOAD 8\nLLOAD 6\nLSUB\nALOAD 10\n"
//                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
//                + "ATHROW");
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
        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 3\n"
                + "DUP\nASTORE 5\nALOAD 0\nLLOAD 3\nLLOAD 1\nLSUB\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
                + "ATHROW");
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2Err() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2Err");
        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\nTRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 6\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 8\n"
                + "DUP\nASTORE 10\nALOAD 0\nLLOAD 8\nLLOAD 6\nLSUB\nALOAD 10\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Err$args2 (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
                + "ATHROW");
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
    public void methodCallReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallReturn");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 7\nALOAD 6\nLLOAD 4\n"
                + "LSTORE 8\nLLOAD 8\nALOAD 6\nLLOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallReturn$args (JLjava/lang/String;J)V\n"
                + "LLOAD 8");
    }

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