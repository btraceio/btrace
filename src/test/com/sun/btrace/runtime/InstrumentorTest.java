/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
        loadTargetClass("DerivedClass");
        transform("onmethod/MatchDerived");

        checkTransformation("ALOAD 0\nALOAD 1\nALOAD 2\n"
                + "INVOKESTATIC resources/DerivedClass.$btrace$traces$onmethod$MatchDerived$args (Lresources/AbstractClass;Ljava/lang/String;Ljava/util/Map;)V");
    }

    @Test
    public void methodEntryCheckcastBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/CheckcastBefore");

        checkTransformation("DUP\nASTORE 2\nALOAD 0\nLDC \"resources.OnMethodTest\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$CheckcastBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V");

        resetClassLoader();

        transform("onmethod/leveled/CheckcastBefore");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/CheckcastBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP\n" +
            "ALOAD 0\n" +
            "LDC \"resources.OnMethodTest\"\n" +
            "ALOAD 2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$CheckcastBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n" +
            "CHECKCAST java/util/HashMap\n" +
            "ASTORE 3\n" +
            "L3\n" +
            "LINENUMBER 102 L3\n" +
            "IFEQ L4\n" +
            "L5\n" +
            "LINENUMBER 103 L5\n" +
            "L4\n" +
            "LINENUMBER 105 L4\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n" +
            "LOCALVARIABLE d Ljava/util/HashMap; L3 L6 3\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntryCheckcastAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/CheckcastAfter");

        checkTransformation("DUP\nALOAD 0\nLDC \"casts\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$CheckcastAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n");

        resetClassLoader();

        transform("onmethod/leveled/CheckcastAfter");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/CheckcastAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP\n" +
            "ALOAD 0\n" +
            "LDC \"casts\"\n" +
            "ALOAD 2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$CheckcastAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n" +
            "ASTORE 3\n" +
            "L3\n" +
            "LINENUMBER 102 L3\n" +
            "IFEQ L4\n" +
            "L5\n" +
            "LINENUMBER 103 L5\n" +
            "L4\n" +
            "LINENUMBER 105 L4\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n" +
            "LOCALVARIABLE d Ljava/util/HashMap; L3 L6 3\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntryInstanceofBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/InstanceofBefore");

        checkTransformation("DUP\nASTORE 3\nALOAD 0\nLDC \"resources.OnMethodTest\"\nALOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$InstanceofBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V");

        resetClassLoader();

        transform("onmethod/leveled/InstanceofBefore");

        checkTransformation(
            "DUP\n" +
            "ASTORE 3\n" +
            "GETSTATIC traces/onmethod/leveled/InstanceofBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LDC \"resources.OnMethodTest\"\n" +
            "ALOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$InstanceofBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n" +
            "L3\n" +
            "IFEQ L4\n" +
            "L5\n" +
            "LINENUMBER 103 L5\n" +
            "L4\n" +
            "LINENUMBER 105 L4\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n" +
            "LOCALVARIABLE d Ljava/util/HashMap; L2 L6 2\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntryInstanceofAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/InstanceofAfter");

        checkTransformation("DUP\nASTORE 3\nALOAD 0\nLDC \"casts\"\nALOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$InstanceofAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n");

        resetClassLoader();

        transform("onmethod/leveled/InstanceofAfter");

        checkTransformation(
            "DUP\n" +
            "ASTORE 3\n" +
            "GETSTATIC traces/onmethod/leveled/InstanceofAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LDC \"casts\"\n" +
            "ALOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$InstanceofAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n" +
            "L3\n" +
            "IFEQ L4\n" +
            "L5\n" +
            "LINENUMBER 103 L5\n" +
            "L4\n" +
            "LINENUMBER 105 L4\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n" +
            "LOCALVARIABLE d Ljava/util/HashMap; L2 L6 2\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntryCatch() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/Catch");

        checkTransformation("DUP\nALOAD 0\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Catch$args (Ljava/lang/Object;Ljava/io/IOException;)V\n"
                + "ASTORE 2");

        resetClassLoader();

        transform("onmethod/leveled/Catch");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/Catch.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Catch$args (Ljava/lang/Object;Ljava/io/IOException;)V\n" +
            "ASTORE 2\n" +
            "L3\n" +
            "LINENUMBER 68 L3\n" +
            "ALOAD 2\n" +
            "L4\n" +
            "LINENUMBER 70 L4\n" +
            "L5\n" +
            "LOCALVARIABLE e Ljava/io/IOException; L3 L4 2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n" +
            "MAXLOCALS = 3"
        );
    }

    @Test
    public void methodEntryThrow() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/Throw");

        checkTransformation("DUP\nASTORE 1\nALOAD 0\nLDC \"resources.OnMethodTest\"\nLDC \"exception\"\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Throw$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V");

        resetClassLoader();

        transform("onmethod/leveled/Throw");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/Throw.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP\n" +
            "ASTORE 1\n" +
            "ALOAD 0\n" +
            "LDC \"resources.OnMethodTest\"\n" +
            "LDC \"exception\"\n" +
            "ALOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Throw$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V\n" +
            "L2\n" +
            "ASTORE 2\n" +
            "L3\n" +
            "LINENUMBER 68 L3\n" +
            "ALOAD 2\n" +
            "L4\n" +
            "LINENUMBER 70 L4\n" +
            "L5\n" +
            "LOCALVARIABLE e Ljava/io/IOException; L3 L4 2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 3"
        );
    }

    @Test
    public void methodEntryError() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/Error");

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
                + "DUP\nASTORE 1\nALOAD 0\nLDC \"uncaught\"\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V");

        resetClassLoader();

        transform("onmethod/leveled/Error");

        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "DUP\n" +
            "ASTORE 1\n" +
            "GETSTATIC traces/onmethod/leveled/Error.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 0\n" +
            "LDC \"uncaught\"\n" +
            "ALOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V\n" +
            "L2\n" +
            "ATHROW\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 2"
        );
    }

    @Test
    public void methodEntryErrorDuration() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ErrorDuration");

        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LCONST_0\n" +
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

        resetClassLoader();

        transform("onmethod/leveled/ErrorDuration");

        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LCONST_0\n" +
            "LSTORE 1\n" +
            "LCONST_0\n" +
            "LSTORE 3\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 5\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 3\n" +
            "ILOAD 5\n" +
            "IFLE L2\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 3\n" +
            "LSUB\n" +
            "LSTORE 1\n" +
            "L2\n" +
            "DUP\n" +
            "ASTORE 6\n" +
            "GETSTATIC traces/onmethod/leveled/ErrorDuration.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LDC \"uncaught\"\n" +
            "LLOAD 1\n" +
            "ALOAD 6\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ErrorDuration$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Throwable;)V\n" +
            "L3\n" +
            "ATHROW\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void methodEntryLine() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/Line");

        checkTransformation("LDC \"field\"\nLDC 84\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Line$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
                + "ALOAD 0");

        resetClassLoader();

        transform("onmethod/leveled/Line");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/Line.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "LDC \"field\"\n" +
            "LDC 84\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Line$args (Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L1\n" +
            "ALOAD 0\n" +
            "L2\n" +
            "LINENUMBER 85 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0"
        );
    }

    @Test
    public void methodEntryNewBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NewBefore");

        checkTransformation("ALOAD 0\nLDC \"java.util.HashMap\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewBefore$args (Ljava/lang/Object;Ljava/lang/String;)V");

        resetClassLoader();

        transform("onmethod/leveled/NewBefore");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NewBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "LDC \"java.util.HashMap\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewBefore$args (Ljava/lang/Object;Ljava/lang/String;)V\n" +
            "L1\n" +
            "L2\n" +
            "LINENUMBER 89 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE m Ljava/util/Map; L2 L3 1"
        );
    }

    @Test
    public void methodEntryNewAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NewAfter");

        checkTransformation("ASTORE 1\nALOAD 0\nALOAD 1\nLDC \"java.util.HashMap\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewAfter$args (Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;)V\n"
                + "DUP");

        resetClassLoader();

        transform("onmethod/leveled/NewAfter");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NewAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LDC \"java.util.HashMap\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewAfter$args (Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;)V\n" +
            "DUP\n" +
            "INVOKESPECIAL java/util/HashMap.<init> ()V\n" +
            "ASTORE 2\n" +
            "L2\n" +
            "LINENUMBER 89 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE m Ljava/util/Map; L2 L3 2\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 3"
        );
    }

    @Test
    public void methodEntrySyncEntry() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/SyncEntry");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
                + "DUP\nASTORE 2\nALOAD 0\nLDC \"sync\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V");

        resetClassLoader();

        transform("onmethod/leveled/SyncEntry");

        checkTransformation(
            "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n" +
            "GETSTATIC traces/onmethod/leveled/SyncEntry.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L6\n" +
            "DUP\n" +
            "ASTORE 2\n" +
            "ALOAD 0\n" +
            "LDC \"sync\"\n" +
            "ALOAD 2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n" +
            "L6\n" +
            "L7\n" +
            "LINENUMBER 110 L7\n" +
            "GOTO L8\n" +
            "ASTORE 3\n" +
            "ALOAD 3\n" +
            "L8\n" +
            "LINENUMBER 111 L8\n" +
            "L5\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntrySyncExit() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/SyncExit");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
                + "L6\nLINENUMBER 110 L6\n"
                + "DUP\nASTORE 2\nALOAD 0\nLDC \"resources/OnMethodTest\"\nALOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n");

        resetClassLoader();

        transform("onmethod/leveled/SyncExit");

        checkTransformation(
            "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n" +
            "L6\n" +
            "LINENUMBER 110 L6\n" +
            "GETSTATIC traces/onmethod/leveled/SyncExit.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L7\n" +
            "DUP\n" +
            "ASTORE 2\n" +
            "ALOAD 0\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "ALOAD 2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n" +
            "L7\n" +
            "GOTO L8\n" +
            "ASTORE 3\n" +
            "GETSTATIC traces/onmethod/leveled/SyncExit.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L9\n" +
            "DUP\n" +
            "ASTORE 4\n" +
            "ALOAD 0\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "ALOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n" +
            "L9\n" +
            "ALOAD 3\n" +
            "L8\n" +
            "LINENUMBER 111 L8\n" +
            "L5\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 5"
        );
    }

    @Test
    public void methodEntryNewArrayIntBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayIntBefore");

        checkTransformation("ALOAD 0\nLDC \"int\"\nICONST_1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V");

        resetClassLoader();

        transform("onmethod/leveled/NewArrayIntBefore");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NewArrayIntBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "LDC \"int\"\n" +
            "ICONST_1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L1\n" +
            "L2\n" +
            "LINENUMBER 93 L2\n" +
            "ICONST_1\n" +
            "GETSTATIC traces/onmethod/leveled/NewArrayIntBefore.$btrace$$level : I\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LDC \"int\"\n" +
            "ICONST_2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L3\n" +
            "L4\n" +
            "LINENUMBER 94 L4\n" +
            "L5\n" +
            "LINENUMBER 95 L5\n" +
            "L6\n" +
            "LINENUMBER 96 L6\n" +
            "L7\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L7 0\n" +
            "LOCALVARIABLE a [I L2 L7 1\n" +
            "LOCALVARIABLE b [[I L4 L7 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L5 L7 3\n" +
            "LOCALVARIABLE d [[Ljava/lang/String; L6 L7 4\n" +
            "MAXSTACK = 5"
        );
    }

    @Test
    public void methodEntryNewArrayStringBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayStringBefore");

        checkTransformation("ALOAD 0\nLDC \"java.lang.String\"\nICONST_1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V");

        resetClassLoader();

        transform("onmethod/leveled/NewArrayStringBefore");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NewArrayStringBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LDC \"java.lang.String\"\n" +
            "ICONST_1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L3\n" +
            "L4\n" +
            "LINENUMBER 95 L4\n" +
            "ICONST_1\n" +
            "GETSTATIC traces/onmethod/leveled/NewArrayStringBefore.$btrace$$level : I\n" +
            "IF_ICMPLT L5\n" +
            "ALOAD 0\n" +
            "LDC \"java.lang.String\"\n" +
            "ICONST_2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L5\n" +
            "L6\n" +
            "LINENUMBER 96 L6\n" +
            "L7\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L7 0\n" +
            "LOCALVARIABLE a [I L1 L7 1\n" +
            "LOCALVARIABLE b [[I L2 L7 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L4 L7 3\n" +
            "LOCALVARIABLE d [[Ljava/lang/String; L6 L7 4\n" +
            "MAXSTACK = 5"
        );
    }

    @Test
    public void methodEntryNewArrayIntAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayIntAfter");

        checkTransformation("DUP\nALOAD 0\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayIntAfter$args (Ljava/lang/Object;[I)V");

        resetClassLoader();

        transform("onmethod/leveled/NewArrayIntAfter");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NewArrayIntAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "DUP\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewArrayIntAfter$args (Ljava/lang/Object;[I)V\n" +
            "ASTORE 2\n" +
            "L2\n" +
            "LINENUMBER 93 L2\n" +
            "ASTORE 3\n" +
            "L3\n" +
            "LINENUMBER 94 L3\n" +
            "ASTORE 4\n" +
            "L4\n" +
            "LINENUMBER 95 L4\n" +
            "ASTORE 5\n" +
            "L5\n" +
            "LINENUMBER 96 L5\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE a [I L2 L6 2\n" +
            "LOCALVARIABLE b [[I L3 L6 3\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L4 L6 4\n" +
            "LOCALVARIABLE d [[Ljava/lang/String; L5 L6 5\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 6"
        );
    }

    @Test
    public void methodEntryNewArrayStringAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayStringAfter");

        checkTransformation("DUP\nALOAD 0\nALOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayStringAfter$args (Ljava/lang/Object;[Ljava/lang/String;)V");

        resetClassLoader();

        transform("onmethod/leveled/NewArrayStringAfter");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NewArrayStringAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "DUP\n" +
            "ALOAD 0\n" +
            "ALOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NewArrayStringAfter$args (Ljava/lang/Object;[Ljava/lang/String;)V\n" +
            "ASTORE 4\n" +
            "L4\n" +
            "LINENUMBER 95 L4\n" +
            "ASTORE 5\n" +
            "L5\n" +
            "LINENUMBER 96 L5\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE a [I L1 L6 1\n" +
            "LOCALVARIABLE b [[I L2 L6 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L4 L6 4\n" +
            "LOCALVARIABLE d [[Ljava/lang/String; L5 L6 5\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 6"
        );
    }

    @Test
    public void methodEntryArrayGetBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArrayGetBefore");

        checkTransformation("DUP2\nISTORE 3\nASTORE 4\nALOAD 0\nALOAD 4\nILOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArrayGetBefore$args (Ljava/lang/Object;[II)V");

        resetClassLoader();

        transform("onmethod/leveled/ArrayGetBefore");

        checkTransformation(
            "DUP2\n" +
            "ASTORE 4\n" +
            "GETSTATIC traces/onmethod/leveled/ArrayGetBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 0\n" +
            "ALOAD 4\n" +
            "ILOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArrayGetBefore$args (Ljava/lang/Object;[II)V\n" +
            "IALOAD\n" +
            "ISTORE 5\n" +
            "L3\n" +
            "LINENUMBER 80 L3\n" +
            "L4\n" +
            "LINENUMBER 81 L4\n" +
            "L5\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n" +
            "LOCALVARIABLE a I L0 L5 1\n" +
            "LOCALVARIABLE arr [I L1 L5 2\n" +
            "LOCALVARIABLE b I L3 L5 5\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 6"
        );
    }

    @Test
    public void methodEntryArrayGetAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArrayGetAfter");

        checkTransformation("DUP2\nISTORE 3\nASTORE 4\nDUP\nISTORE 5\nALOAD 0\nILOAD 5\nALOAD 4\nILOAD 3\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArrayGetAfter$args (Ljava/lang/Object;I[II)V");

        resetClassLoader();

        transform("onmethod/leveled/ArrayGetAfter");

        checkTransformation(
            "DUP2\n" +
            "ASTORE 4\n" +
            "GETSTATIC traces/onmethod/leveled/ArrayGetAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "ISUB\n" +
            "DUP\n" +
            "ISTORE 5\n" +
            "IFLT L2\n" +
            "IALOAD\n" +
            "ILOAD 5\n" +
            "IFLT L3\n" +
            "DUP\n" +
            "ISTORE 6\n" +
            "ALOAD 0\n" +
            "ILOAD 6\n" +
            "ALOAD 4\n" +
            "ILOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArrayGetAfter$args (Ljava/lang/Object;I[II)V\n" +
            "L3\n" +
            "ISTORE 7\n" +
            "L4\n" +
            "LINENUMBER 80 L4\n" +
            "L5\n" +
            "LINENUMBER 81 L5\n" +
            "L6\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n" +
            "LOCALVARIABLE a I L0 L6 1\n" +
            "LOCALVARIABLE arr [I L1 L6 2\n" +
            "LOCALVARIABLE b I L4 L6 7\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArraySetBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArraySetBefore");

        checkTransformation("ISTORE 4\nDUP2\nISTORE 5\nASTORE 6\nILOAD 4\nALOAD 0\nALOAD 6\nILOAD 5\nILOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArraySetBefore$args (Ljava/lang/Object;[III)V");

        resetClassLoader();

        transform("onmethod/leveled/ArraySetBefore");

        checkTransformation(
            "ISTORE 4\n" +
            "DUP2\n" +
            "ISTORE 5\n" +
            "ASTORE 6\n" +
            "ILOAD 4\n" +
            "GETSTATIC traces/onmethod/leveled/ArraySetBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "ALOAD 6\n" +
            "ILOAD 5\n" +
            "ILOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArraySetBefore$args (Ljava/lang/Object;[III)V\n" +
            "L3\n" +
            "L4\n" +
            "LINENUMBER 81 L4\n" +
            "L5\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n" +
            "LOCALVARIABLE a I L0 L5 1\n" +
            "LOCALVARIABLE arr [I L1 L5 2\n" +
            "LOCALVARIABLE b I L2 L5 3\n" +
            "MAXSTACK = 7\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void methodEntryArraySetAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArraySetAfter");

        checkTransformation("ISTORE 4\nDUP2\nISTORE 5\nASTORE 6\nILOAD 4\nALOAD 0\nALOAD 6\nILOAD 5\nILOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArraySetAfter$args (Ljava/lang/Object;[III)V");

        resetClassLoader();

        transform("onmethod/leveled/ArraySetAfter");

        checkTransformation(
            "ISTORE 4\n" +
            "DUP2\n" +
            "ISTORE 5\n" +
            "ASTORE 6\n" +
            "ILOAD 4\n" +
            "GETSTATIC traces/onmethod/leveled/ArraySetAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "ISUB\n" +
            "DUP\n" +
            "ISTORE 7\n" +
            "IFLT L3\n" +
            "L3\n" +
            "ILOAD 7\n" +
            "IFLT L4\n" +
            "ALOAD 0\n" +
            "ALOAD 6\n" +
            "ILOAD 5\n" +
            "ILOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArraySetAfter$args (Ljava/lang/Object;[III)V\n" +
            "L4\n" +
            "LINENUMBER 81 L4\n" +
            "L5\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n" +
            "LOCALVARIABLE a I L0 L5 1\n" +
            "LOCALVARIABLE arr [I L1 L5 2\n" +
            "LOCALVARIABLE b I L2 L5 3\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryFieldGetBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/FieldGetBefore");

        checkTransformation("DUP\nASTORE 1\nALOAD 0\nALOAD 1\nLDC \"field\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldGetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V");

        resetClassLoader();

        transform("onmethod/leveled/FieldGetBefore");

        checkTransformation(
            "DUP\n" +
            "ASTORE 1\n" +
            "GETSTATIC traces/onmethod/leveled/FieldGetBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LDC \"field\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$FieldGetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V\n" +
            "L1\n" +
            "L2\n" +
            "LINENUMBER 85 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 2"
        );
    }

    @Test
    public void methodEntryFieldGetAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/FieldGetAfter");

        checkTransformation("DUP\nASTORE 1\nDUP\nISTORE 2\nALOAD 0\nALOAD 1\nLDC \"field\"\nILOAD 2\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldGetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");

        resetClassLoader();

        transform("onmethod/leveled/FieldGetAfter");

        checkTransformation(
            "DUP\n" +
            "ASTORE 1\n" +
            "GETSTATIC traces/onmethod/leveled/FieldGetAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "ISUB\n" +
            "DUP\n" +
            "ISTORE 2\n" +
            "IFLT L1\n" +
            "L1\n" +
            "ILOAD 2\n" +
            "IFLT L2\n" +
            "DUP\n" +
            "ISTORE 3\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LDC \"field\"\n" +
            "ILOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$FieldGetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L2\n" +
            "L3\n" +
            "LINENUMBER 85 L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntryFieldSetBefore() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/FieldSetBefore");

        checkTransformation(
            "ISTORE 1\n" +
            "DUP\n" +
            "ASTORE 2\n" +
            "ILOAD 1\n" +
            "ALOAD 0\n" +
            "ALOAD 2\n" +
            "LDC \"field\"\n" +
            "ILOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldSetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 3"
        );

        resetClassLoader();

        transform("onmethod/leveled/FieldSetBefore");

        checkTransformation(
            "ISTORE 1\n" +
            "DUP\n" +
            "ASTORE 2\n" +
            "ILOAD 1\n" +
            "GETSTATIC traces/onmethod/leveled/FieldSetBefore.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "ALOAD 2\n" +
            "LDC \"field\"\n" +
            "ILOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$FieldSetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L1\n" +
            "L2\n" +
            "LINENUMBER 85 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 3"
        );
    }

    @Test
    public void methodEntryFieldSetAfter() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/FieldSetAfter");

        checkTransformation("ISTORE 1\nDUP\nASTORE 2\nILOAD 1\nALOAD 0\nALOAD 2\nLDC \"field\"\nILOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldSetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");

        resetClassLoader();

        transform("onmethod/leveled/FieldSetAfter");

        checkTransformation("ISTORE 1\n" +
            "DUP\n" +
            "ASTORE 2\n" +
            "ILOAD 1\n" +
            "GETSTATIC traces/onmethod/leveled/FieldSetAfter.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "ISUB\n" +
            "DUP\n" +
            "ISTORE 3\n" +
            "IFLT L1\n" +
            "L1\n" +
            "ILOAD 3\n" +
            "IFLT L2\n" +
            "ALOAD 0\n" +
            "ALOAD 2\n" +
            "LDC \"field\"\n" +
            "ILOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$FieldSetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n" +
            "L2\n" +
            "LINENUMBER 85 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }

    @Test
    public void methodEntryArgsNoSelf() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsNoSelf");

        checkTransformation("ALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsNoSelf$argsNoSelf (Ljava/lang/String;J[Ljava/lang/String;[I)V");

        resetClassLoader();

        transform("onmethod/leveled/ArgsNoSelf");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/ArgsNoSelf.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsNoSelf$argsNoSelf (Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 5"
        );
    }

    @Test
    public void methodEntryNoArgs() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgs");

        checkTransformation("ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgs$argsEmpty (Ljava/lang/Object;)V");

        resetClassLoader();

        transform("onmethod/leveled/NoArgs");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NoArgs.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ALOAD 0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NoArgs$argsEmpty (Ljava/lang/Object;)V"
        );
    }

    @Test
    public void methodEntryArgs() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/Args");
        checkTransformation("ALOAD 0\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V");

        resetClassLoader();

        transform("onmethod/leveled/Args");
        checkTransformation("GETSTATIC traces/onmethod/leveled/Args.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 6"
        );
    }

    @Test
    public void methodEntryArgsSampledNoSampling() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsSampledNoSampling");
        checkTransformation(
            "ICONST_5\n" +
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
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L1 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L1 L2 1\n" +
            "LOCALVARIABLE b J L1 L2 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L1 L2 4\n" +
            "LOCALVARIABLE d [I L1 L2 5\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 7"
        );

        resetClassLoader();

        transform("onmethod/leveled/ArgsSampledNoSampling");
        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 6\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 7\n" +
            "IFLE L0\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 7\n" +
            "IFLE L1\n" +
            "ILOAD 6\n" +
            "IFEQ L2\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsSampledNoSampling.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsSampledNoSampling$argsSampled (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsSampledNoSampling.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsSampledNoSampling$argsNoSampling (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L3\n" +
            "LINENUMBER 44 L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L3 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L3 L4 1\n" +
            "LOCALVARIABLE b J L3 L4 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L3 L4 4\n" +
            "LOCALVARIABLE d [I L3 L4 5\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsSampled() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsSampled");
        checkTransformation("ICONST_5\n" +
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
    public void methodEntryArgsSampledLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsSampled");
        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 6\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 7\n" +
            "IFLE L0\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 7\n" +
            "IFLE L1\n" +
            "ILOAD 6\n" +
            "IFEQ L2\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsSampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsSampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "LINENUMBER 44 L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L2 L3 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L2 L3 1\n" +
            "LOCALVARIABLE b J L2 L3 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L2 L3 4\n" +
            "LOCALVARIABLE d [I L2 L3 5\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgs2Sampled() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/Args2Sampled");
        checkTransformation(
            "ICONST_5\n" +
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
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L1 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L1 L2 1\n" +
            "LOCALVARIABLE b J L1 L2 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L1 L2 4\n" +
            "LOCALVARIABLE d [I L1 L2 5\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void methodEntryArgs2SampledLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/Args2Sampled");
        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 6\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 7\n" +
            "IFLE L0\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 7\n" +
            "IFLE L1\n" +
            "ILOAD 6\n" +
            "IFEQ L2\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/Args2Sampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Args2Sampled$args2 (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "ILOAD 7\n" +
            "IFLE L3\n" +
            "ILOAD 6\n" +
            "IFEQ L4\n" +
            "L3\n" +
            "GETSTATIC traces/onmethod/leveled/Args2Sampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L4\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$Args2Sampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L4\n" +
            "LINENUMBER 44 L4\n" +
            "L5\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L4 L5 1\n" +
            "LOCALVARIABLE b J L4 L5 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L4 L5 4\n" +
            "LOCALVARIABLE d [I L4 L5 5\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsSampledAdaptive() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsSampledAdaptive");
        checkTransformation("ICONST_5\n" +
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
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.updateEndTs (I)V\n" +
            "L1\n" +
            "L2"
        );
    }

    @Test
    public void methodEntryArgsSampledAdaptiveLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsSampledAdaptive");
        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 6\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 7\n" +
            "IFLE L0\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitAdaptive (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 7\n" +
            "IFLE L1\n" +
            "ILOAD 6\n" +
            "IFEQ L2\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsSampledAdaptive.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "LINENUMBER 44 L2\n" +
            "ILOAD 6\n" +
            "IFEQ L3\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.updateEndTs (I)V\n" +
            "L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L2 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L2 L4 1\n" +
            "LOCALVARIABLE b J L2 L4 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L2 L4 4\n" +
            "LOCALVARIABLE d [I L2 L4 5\n" +
            "MAXSTACK = 6\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsReturn() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturn");
        checkTransformation("DUP2\nLSTORE 6\nALOAD 0\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryArgsReturnLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsReturn");
        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/ArgsReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "DUP2\n" +
            "LSTORE 6\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n" +
            "LOCALVARIABLE d [I L0 L2 5\n" +
            "MAXSTACK = 10\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsReturnAugmented() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturnAugmented", true);
        checkTransformation("LSTORE 6\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "LLOAD 6\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturnAugmented$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[IJ)J\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsReturnAugmentedLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsReturnAugmented", true);
        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/ArgsReturnAugmented.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "LSTORE 6\n" +
            "ALOAD 0\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "LLOAD 6\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsReturnAugmented$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[IJ)J\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n" +
            "LOCALVARIABLE d [I L0 L2 5\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsReturnAugmented1() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturnAugmented1", true);
        checkTransformation("LSTORE 6\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturnAugmented1$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)J\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsReturnAugmented1Level() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsReturnAugmented1", true);
        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/ArgsReturnAugmented1.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "LSTORE 6\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsReturnAugmented1$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)J\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n" +
            "LOCALVARIABLE d [I L0 L2 5\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodEntryArgsReturnSampled() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturnSampled");
        checkTransformation(
            "ICONST_5\n" +
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

        resetClassLoader();

        transform("onmethod/leveled/ArgsReturnSampled");
        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 6\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 7\n" +
            "IFLE L0\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 6\n" +
            "ILOAD 7\n" +
            "IFLE L1\n" +
            "ILOAD 6\n" +
            "IFEQ L2\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsReturnSampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP2\n" +
            "LSTORE 8\n" +
            "ALOAD 0\n" +
            "LLOAD 8\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsReturnSampled$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n" +
            "LOCALVARIABLE b J L0 L3 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n" +
            "LOCALVARIABLE d [I L0 L3 5\n" +
            "MAXSTACK = 10\n" +
            "MAXLOCALS = 10"
        );
    }

    @Test
    public void methodEntryArgsDuration() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration");
        checkTransformation("LCONST_0\nLSTORE 6\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLSTORE 8\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLLOAD 8\n"
                + "LSUB\nLSTORE 6\nDUP2\nLSTORE 10\nALOAD 0\nLLOAD 10\nLLOAD 6\n"
                + "ALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
                + "MAXSTACK");
    }

    @Test
    public void methodEntryArgsDurationLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsDuration");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "LCONST_0\n" +
            "LSTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 10\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "ILOAD 10\n" +
            "IFLE L1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP2\n" +
            "LSTORE 11\n" +
            "ALOAD 0\n" +
            "LLOAD 11\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n" +
            "LOCALVARIABLE b J L0 L3 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n" +
            "LOCALVARIABLE d [I L0 L3 5\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 13"
        );
    }

    @Test
    public void methodEntryArgsDurationMultiReturn() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationMultiReturn");
        checkTransformation(
            "LCONST_0\n" +
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
    public void methodEntryArgsDurationMultiReturnLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsDurationMultiReturn");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "LCONST_0\n" +
            "LSTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 10\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "ILOAD 10\n" +
            "IFLE L3\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L3\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationMultiReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L4\n" +
            "DUP2\n" +
            "LSTORE 11\n" +
            "ALOAD 0\n" +
            "LLOAD 11\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L4\n" +
            "IFLE L5\n" +
            "L6\n" +
            "LINENUMBER 128 L6\n" +
            "ILOAD 10\n" +
            "IFLE L7\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L7\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationMultiReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L8\n" +
            "DUP2\n" +
            "LSTORE 13\n" +
            "ALOAD 0\n" +
            "LLOAD 13\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L8\n" +
            "L5\n" +
            "LINENUMBER 132 L5\n" +
            "L9\n" +
            "LINENUMBER 133 L9\n" +
            "ILOAD 10\n" +
            "IFLE L10\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L10\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationMultiReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L11\n" +
            "DUP2\n" +
            "LSTORE 15\n" +
            "ALOAD 0\n" +
            "LLOAD 15\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L11\n" +
            "L12\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L12 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L12 1\n" +
            "LOCALVARIABLE b J L0 L12 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L12 4\n" +
            "LOCALVARIABLE d [I L0 L12 5\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 17"
        );
    }

    @Test
    public void methodEntryArgsDurationSampled() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationSampled");
        checkTransformation(
            "LCONST_0\nLSTORE 6\nICONST_5\nINVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\nLSTORE 8\nL2I\nISTORE 10\nILOAD 10\nIFEQ L1\n" +
            "ICONST_5\nINVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 8\nLSUB\nLSTORE 6\nDUP2\nLSTORE 11\n" +
            "ALOAD 0\nLLOAD 11\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationSampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\nL2\n"
        );
    }

    @Test
    public void methodEntryArgsDurationSampledLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsDurationSampled");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "LCONST_0\n" +
            "LSTORE 8\n" +
            "ICONST_0\n" +
            "ISTORE 10\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 11\n" +
            "IFLE L0\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 8\n" +
            "L2I\n" +
            "ISTORE 10\n" +
            "ILOAD 11\n" +
            "IFLE L1\n" +
            "ILOAD 10\n" +
            "IFEQ L2\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationSampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP2\n" +
            "LSTORE 12\n" +
            "ALOAD 0\n" +
            "LLOAD 12\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationSampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n" +
            "LOCALVARIABLE b J L0 L3 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n" +
            "LOCALVARIABLE d [I L0 L3 5\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 14"
        );
    }

    @Test
    public void methodEntryArgsDurationBoxed() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationBoxed");
        checkTransformation("");
    }

    @Test
    public void methodEntryArgsDurationConstructor() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationConstructor");
        checkTransformation("LCONST_0\nLSTORE 2\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\nLSTORE 4\n"
                + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
                + "LLOAD 4\nLSUB\nLSTORE 2\n"
                + "ALOAD 0\nLLOAD 2\nALOAD 1\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDurationConstructor$args (Ljava/lang/Object;JLjava/lang/String;)V\n"
                + "MAXSTACK");
    }

    @Test
    public void methodEntryArgsDurationConstructorLevel() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/leveled/ArgsDurationConstructor");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 2\n" +
            "LCONST_0\n" +
            "LSTORE 4\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 6\n" +
            "IFLE L1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 4\n" +
            "L1\n" +
            "ILOAD 6\n" +
            "IFLE L2\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 4\n" +
            "LSUB\n" +
            "LSTORE 2\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationConstructor.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LLOAD 2\n" +
            "ALOAD 1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationConstructor$args (Ljava/lang/Object;JLjava/lang/String;)V\n" +
            "L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 7"
        );
    }


    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2");
        checkTransformation(
            "LCONST_0\n" +
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
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "DUP2\n" +
            "LSTORE 12\n" +
            "ALOAD 0\n" +
            "LLOAD 12\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 14"
        );

        resetClassLoader();

        transform("onmethod/leveled/ArgsDuration2");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "LCONST_0\n" +
            "LSTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 10\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "ILOAD 10\n" +
            "IFLE L1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L1\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration2.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP2\n" +
            "LSTORE 11\n" +
            "ALOAD 0\n" +
            "LLOAD 11\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration2.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "DUP2\n" +
            "LSTORE 13\n" +
            "ALOAD 0\n" +
            "LLOAD 13\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n" +
            "LOCALVARIABLE b J L0 L4 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L4 4\n" +
            "LOCALVARIABLE d [I L0 L4 5\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 15"
        );
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2Sampled() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2Sampled");
        checkTransformation("LCONST_0\n" +
            "LSTORE 6\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 8\n" +
            "L2I\n" +
            "ISTORE 10\n" +
            "ILOAD 10\n" +
            "IFEQ L1\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "DUP2\n" +
            "LSTORE 11\n" +
            "ALOAD 0\n" +
            "LLOAD 11\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Sampled$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "ILOAD 10\n" +
            "IFEQ L2\n" +
            "DUP2\n" +
            "LSTORE 13\n" +
            "ALOAD 0\n" +
            "LLOAD 13\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2Sampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n" +
            "LOCALVARIABLE b J L0 L3 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n" +
            "LOCALVARIABLE d [I L0 L3 5\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 15"
        );

        resetClassLoader();

        transform("onmethod/leveled/ArgsDuration2Sampled");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 8\n" +
            "L2I\n" +
            "ISTORE 10\n" +
            "ILOAD 10\n" +
            "IFEQ L1\n" +
            "ICONST_5\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration2Sampled.$btrace$$level : I\n" +
            "ICONST_5\n" +
            "IF_ICMPGT L1\n" +
            "DUP2\n" +
            "LSTORE 11\n" +
            "ALOAD 0\n" +
            "LLOAD 11\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration2Sampled$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "ILOAD 10\n" +
            "IFEQ L2\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration2Sampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "DUP2\n" +
            "LSTORE 13\n" +
            "ALOAD 0\n" +
            "LLOAD 13\n" +
            "LLOAD 6\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration2Sampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "L2\n" +
            "L3\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n" +
            "LOCALVARIABLE b J L0 L3 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n" +
            "LOCALVARIABLE d [I L0 L3 5\n" +
            "MAXSTACK = 12\n" +
            "MAXLOCALS = 15"
        );
    }

    @Test
    public void methodEntryArgsDurationErr() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationErr");

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LCONST_0\n" +
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

        resetClassLoader();

        transform("onmethod/leveled/ArgsDurationErr");

        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "LCONST_0\n" +
            "LSTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 10\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "ILOAD 10\n" +
            "IFLE L2\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L2\n" +
            "DUP\n" +
            "ASTORE 11\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationErr.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 11\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "L3\n" +
            "ATHROW\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 12"
        );
    }

    @Test
    public void methodEntryArgsDurationBoxedErr() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationBoxedErr");
        checkTransformation("");
    }

    @Test
    public void methodEntryArgsDurationConstructorErr() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDurationConstructorErr");
        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L2\n" +
            "LINENUMBER 39 L2\n" +
            "LCONST_0\n" +
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
            "LCONST_0\n" +
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
            "MAXLOCALS = 7"
        );

        resetClassLoader();

        transform("onmethod/leveled/ArgsDurationConstructorErr");
        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L2\n" +
            "LINENUMBER 39 L2\n" +
            "LCONST_0\n" +
            "LSTORE 1\n" +
            "LCONST_0\n" +
            "LSTORE 3\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 5\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 3\n" +
            "L0\n" +
            "ILOAD 5\n" +
            "IFLE L3\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 3\n" +
            "LSUB\n" +
            "LSTORE 1\n" +
            "L3\n" +
            "DUP\n" +
            "ASTORE 6\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationConstructorErr.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L4\n" +
            "ALOAD 0\n" +
            "LLOAD 1\n" +
            "ALOAD 6\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "L4\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 7\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L2\n" +
            "LINENUMBER 40 L2\n" +
            "LCONST_0\n" +
            "LSTORE 2\n" +
            "LCONST_0\n" +
            "LSTORE 4\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 6\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 4\n" +
            "L0\n" +
            "ILOAD 6\n" +
            "IFLE L3\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 4\n" +
            "LSUB\n" +
            "LSTORE 2\n" +
            "L3\n" +
            "DUP\n" +
            "ASTORE 7\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDurationConstructorErr.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L4\n" +
            "ALOAD 0\n" +
            "LLOAD 2\n" +
            "ALOAD 7\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "L4\n" +
            "ATHROW\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L2 L1 1\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2Err() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2Err");
        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n" +
            "LCONST_0\n" +
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
            "MAXLOCALS = 12"
        );

        resetClassLoader();

        transform("onmethod/leveled/ArgsDuration2Err");
        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n" +
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "LCONST_0\n" +
            "LSTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 10\n" +
            "IFLE L0\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 8\n" +
            "ILOAD 10\n" +
            "IFLE L3\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L3\n" +
            "DUP\n" +
            "ASTORE 11\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration2Err.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L4\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 11\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration2Err$args2 (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "L4\n" +
            "ATHROW\n" +
            "L2\n" +
            "ILOAD 10\n" +
            "IFLE L5\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 8\n" +
            "LSUB\n" +
            "LSTORE 6\n" +
            "L5\n" +
            "DUP\n" +
            "ASTORE 12\n" +
            "GETSTATIC traces/onmethod/leveled/ArgsDuration2Err.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L6\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "ALOAD 12\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$ArgsDuration2Err$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n" +
            "L6\n" +
            "ATHROW\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 13"
        );
    }

    @Test
    public void methodEntryAnytypeArgs() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/AnytypeArgs");
        checkTransformation("ALOAD 0\nICONST_4\nANEWARRAY java/lang/Object\nDUP\n"
                + "ICONST_0\nALOAD 1\nAASTORE\nDUP\n"
                + "ICONST_1\nLLOAD 2\nINVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\nAASTORE\nDUP\n"
                + "ICONST_2\nALOAD 4\nAASTORE\nDUP\n"
                + "ICONST_3\nALOAD 5\nAASTORE\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V");

        resetClassLoader();

        transform("onmethod/leveled/AnytypeArgs");
        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/AnytypeArgs.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ALOAD 0\n" +
            "ICONST_4\n" +
            "ANEWARRAY java/lang/Object\n" +
            "DUP\n" +
            "ICONST_0\n" +
            "ALOAD 1\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_1\n" +
            "LLOAD 2\n" +
            "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_2\n" +
            "ALOAD 4\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_3\n" +
            "ALOAD 5\n" +
            "AASTORE\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V\n" +
            "MAXSTACK = 6"
        );
    }

    @Test
    public void methodEntryAnytypeArgsNoSelf() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/AnytypeArgsNoSelf");
        checkTransformation("ICONST_4\nANEWARRAY java/lang/Object\nDUP\nICONST_0\nALOAD 1\nAASTORE\n"
                + "DUP\nICONST_1\nLLOAD 2\nINVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\nAASTORE\n"
                + "DUP\nICONST_2\nALOAD 4\nAASTORE\nDUP\nICONST_3\nALOAD 5\nAASTORE\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V");

        resetClassLoader();

        transform("onmethod/leveled/AnytypeArgsNoSelf");
        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/AnytypeArgsNoSelf.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ICONST_4\n" +
            "ANEWARRAY java/lang/Object\n" +
            "DUP\n" +
            "ICONST_0\n" +
            "ALOAD 1\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_1\n" +
            "LLOAD 2\n" +
            "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_2\n" +
            "ALOAD 4\n" +
            "AASTORE\n" +
            "DUP\n" +
            "ICONST_3\n" +
            "ALOAD 5\n" +
            "AASTORE\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V\n" +
            "MAXSTACK = 5"
        );
    }

    @Test
    public void methodEntryStaticArgs() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgs");

        checkTransformation("ALOAD 0\nLLOAD 1\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticArgs$args (Ljava/lang/String;J[Ljava/lang/String;[I)V");

        resetClassLoader();

        transform("onmethod/leveled/StaticArgs");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/StaticArgs.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ALOAD 0\n" +
            "LLOAD 1\n" +
            "ALOAD 3\n" +
            "ALOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$StaticArgs$args (Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 5"
        );
    }

    @Test
    public void methodEntryStaticArgsReturn() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgsReturn");

        checkTransformation("DUP2\nLSTORE 5\nALOAD 0\nLLOAD 5\nLLOAD 1\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticArgsReturn$args (Ljava/lang/String;JJ[Ljava/lang/String;[I)V");

        resetClassLoader();

        transform("onmethod/leveled/StaticArgsReturn");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/StaticArgsReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "DUP2\n" +
            "LSTORE 5\n" +
            "ALOAD 0\n" +
            "LLOAD 5\n" +
            "LLOAD 1\n" +
            "ALOAD 3\n" +
            "ALOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$StaticArgsReturn$args (Ljava/lang/String;JJ[Ljava/lang/String;[I)V\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 0\n" +
            "LOCALVARIABLE b J L0 L2 1\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L2 3\n" +
            "LOCALVARIABLE d [I L0 L2 4\n" +
            "MAXSTACK = 9\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void methodEntryStaticArgsSelf() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgsSelf");

        checkTransformation("");
    }

    @Test
    public void methodEntryStaticNoArgs() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticNoArgs");

        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticNoArgs$argsEmpty ()V");

        resetClassLoader();

        transform("onmethod/leveled/StaticNoArgs");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/StaticNoArgs.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$StaticNoArgs$argsEmpty ()V\n" +
            "MAXSTACK = 2"
        );
    }

    @Test
    public void methodEntryStaticNoArgsSelf() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticNoArgsSelf");

        checkTransformation("");
    }

    @Test
    public void methodCall() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCall");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 0\nALOAD 6\nLLOAD 4\nALOAD 7\n"
                + "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevel\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCall$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 7\nALOAD 6\nLLOAD 4");

        resetClassLoader();

        transform("onmethod/leveled/MethodCall");

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCall.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "ALOAD 7\n" +
            "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevel\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCall$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L1\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void methodCallSampled() throws Exception {
        loadTargetClass("OnMethodTest");
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

        resetClassLoader();

        transform("onmethod/leveled/MethodCallSampled");

        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 4\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 5\n" +
            "IFLE L1\n" +
            "LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hit (I)Z\n" +
            "ISTORE 4\n" +
            "L1\n" +
            "LSTORE 6\n" +
            "ASTORE 8\n" +
            "ASTORE 9\n" +
            "ILOAD 5\n" +
            "IFLE L2\n" +
            "ILOAD 4\n" +
            "IFEQ L3\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallSampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "ALOAD 8\n" +
            "LLOAD 6\n" +
            "ALOAD 9\n" +
            "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevel\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallSampled$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L3\n" +
            "ALOAD 9\n" +
            "ALOAD 8\n" +
            "LLOAD 6\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n" +
            "LOCALVARIABLE b J L0 L4 2\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 10"
        );
    }

    @Test
    public void methodCallSampledAdaptive() throws Exception {
        loadTargetClass("OnMethodTest");
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

        resetClassLoader();

        transform("onmethod/leveled/MethodCallSampledAdaptive");

        checkTransformation(
            "ICONST_0\n" +
            "ISTORE 4\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 5\n" +
            "IFLE L1\n" +
            "LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitAdaptive (I)Z\n" +
            "ISTORE 4\n" +
            "L1\n" +
            "LSTORE 6\n" +
            "ASTORE 8\n" +
            "ASTORE 9\n" +
            "ILOAD 5\n" +
            "IFLE L2\n" +
            "ILOAD 4\n" +
            "IFEQ L3\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallSampledAdaptive.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "ALOAD 0\n" +
            "ALOAD 8\n" +
            "LLOAD 6\n" +
            "ALOAD 9\n" +
            "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevel\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L3\n" +
            "ALOAD 9\n" +
            "ALOAD 8\n" +
            "LLOAD 6\n" +
            "ILOAD 4\n" +
            "IFEQ L4\n" +
            "LDC 9\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.updateEndTs (I)V\n" +
            "L4\n" +
            "L5\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L5 1\n" +
            "LOCALVARIABLE b J L0 L5 2\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 10"
        );
    }

    @Test
    public void methodCallNoArgs() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallNoArgs");

        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallNoArgs$args ()V");

        resetClassLoader();

        transform("onmethod/leveled/MethodCallNoArgs");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/MethodCallNoArgs.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallNoArgs$args ()V\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "MAXSTACK = 6"
        );
    }

    @Test
    public void methodCallReturn() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallReturn");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 7\nALOAD 6\nLLOAD 4\n"
                + "LSTORE 8\nLLOAD 8\nALOAD 6\nLLOAD 4\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallReturn$args (JLjava/lang/String;J)V\n"
                + "LLOAD 8");

        resetClassLoader();

        transform("onmethod/leveled/MethodCallReturn");

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "LSTORE 8\n" +
            "LLOAD 8\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallReturn$args (JLjava/lang/String;J)V\n" +
            "LLOAD 8\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "MAXLOCALS = 10"
        );
    }

    @Test
    public void methodCallReturnAugmented() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallReturnAugmented", true);

        checkTransformation("LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "LSTORE 8\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "LLOAD 8\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallReturnAugmented$args (Ljava/lang/String;JJ)J\n" +
            "MAXLOCALS = 10"
        );

        resetClassLoader();

        transform("onmethod/leveled/MethodCallReturnAugmented", true);

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallReturnAugmented.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "LSTORE 8\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "LLOAD 8\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallReturnAugmented$args (Ljava/lang/String;JJ)J\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "MAXLOCALS = 10"
        );
    }

    @Test
    public void methodCallReturnAugmented1() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallReturnAugmented1", true);

        checkTransformation("LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "LSTORE 8\n" +
            "LLOAD 8\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallReturnAugmented1$args (JLjava/lang/String;J)J\n" +
            "MAXLOCALS = 10"
        );

        resetClassLoader();

        transform("onmethod/leveled/MethodCallReturnAugmented1", true);

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallReturnAugmented1.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "LSTORE 8\n" +
            "LLOAD 8\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallReturnAugmented1$args (JLjava/lang/String;J)J\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "MAXLOCALS = 10"
        );
    }

    @Test
    public void methodCallDuration() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallDuration");

        checkTransformation("LCONST_0\n" +
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

        resetClassLoader();

        transform("onmethod/leveled/MethodCallDuration");

        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 4\n" +
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 8\n" +
            "IFLE L1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 6\n" +
            "L1\n" +
            "LSTORE 9\n" +
            "ASTORE 11\n" +
            "ASTORE 12\n" +
            "ALOAD 12\n" +
            "ALOAD 11\n" +
            "LLOAD 9\n" +
            "ILOAD 8\n" +
            "IFLE L2\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 6\n" +
            "LSUB\n" +
            "LSTORE 4\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallDuration.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "LSTORE 13\n" +
            "LLOAD 13\n" +
            "LLOAD 4\n" +
            "ALOAD 11\n" +
            "LLOAD 9\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallDuration$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 13\n" +
            "L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n" +
            "LOCALVARIABLE b J L0 L4 2\n" +
            "MAXSTACK = 7\n" +
            "MAXLOCALS = 15"
        );
    }

    @Test
    public void methodCallDurationSampled() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallDurationSampled");

        checkTransformation("LCONST_0\n" +
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

        resetClassLoader();

        transform("onmethod/leveled/MethodCallDurationSampled");

        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 4\n" +
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "ICONST_0\n" +
            "ISTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 9\n" +
            "IFLE L1\n" +
            "LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 6\n" +
            "L2I\n" +
            "ISTORE 8\n" +
            "L1\n" +
            "LSTORE 10\n" +
            "ASTORE 12\n" +
            "ASTORE 13\n" +
            "ALOAD 13\n" +
            "ALOAD 12\n" +
            "LLOAD 10\n" +
            "ILOAD 9\n" +
            "IFLE L2\n" +
            "ILOAD 8\n" +
            "IFEQ L3\n" +
            "LDC 10\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 6\n" +
            "LSUB\n" +
            "LSTORE 4\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallDurationSampled.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "LSTORE 14\n" +
            "LLOAD 14\n" +
            "LLOAD 4\n" +
            "ALOAD 12\n" +
            "LLOAD 10\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallDurationSampled$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 14\n" +
            "L3\n" +
            "L4\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n" +
            "LOCALVARIABLE b J L0 L4 2\n" +
            "MAXSTACK = 8\n" +
            "MAXLOCALS = 16"
        );
    }

    @Test
    public void methodCallDurationSampledMulti() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallDurationSampledMulti");

        checkTransformation("LCONST_0\n" +
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

        resetClassLoader();

        transform("onmethod/leveled/MethodCallDurationSampledMulti");

        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 4\n" +
            "LCONST_0\n" +
            "LSTORE 6\n" +
            "ICONST_0\n" +
            "ISTORE 8\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 9\n" +
            "IFLE L1\n" +
            "LDC 20\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 6\n" +
            "L2I\n" +
            "ISTORE 8\n" +
            "L1\n" +
            "LSTORE 10\n" +
            "ASTORE 12\n" +
            "ASTORE 13\n" +
            "ALOAD 13\n" +
            "ALOAD 12\n" +
            "LLOAD 10\n" +
            "ILOAD 9\n" +
            "IFLE L2\n" +
            "ILOAD 8\n" +
            "IFEQ L3\n" +
            "LDC 20\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 6\n" +
            "LSUB\n" +
            "LSTORE 4\n" +
            "L2\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallDurationSampledMulti.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L3\n" +
            "LSTORE 14\n" +
            "LLOAD 14\n" +
            "LLOAD 4\n" +
            "ALOAD 12\n" +
            "LLOAD 10\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 14\n" +
            "L3\n" +
            "LCONST_0\n" +
            "LSTORE 16\n" +
            "ICONST_0\n" +
            "ISTORE 18\n" +
            "GETSTATIC resources/OnMethodTest.$btrace$$level : I\n" +
            "DUP\n" +
            "ISTORE 19\n" +
            "IFLE L4\n" +
            "LDC 21\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.hitTimed (I)J\n" +
            "DUP2\n" +
            "LSTORE 16\n" +
            "L2I\n" +
            "ISTORE 18\n" +
            "L4\n" +
            "LSTORE 20\n" +
            "ASTORE 22\n" +
            "ALOAD 22\n" +
            "LLOAD 20\n" +
            "ILOAD 19\n" +
            "IFLE L5\n" +
            "ILOAD 18\n" +
            "IFEQ L6\n" +
            "LDC 21\n" +
            "INVOKESTATIC com/sun/btrace/instr/MethodTracker.getEndTs (I)J\n" +
            "LLOAD 16\n" +
            "LSUB\n" +
            "L5\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallDurationSampledMulti.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L6\n" +
            "LSTORE 23\n" +
            "LLOAD 23\n" +
            "ALOAD 22\n" +
            "LLOAD 20\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n" +
            "LLOAD 23\n" +
            "L6\n" +
            "LADD\n" +
            "LSTORE 25\n" +
            "L7\n" +
            "LINENUMBER 115 L7\n" +
            "LLOAD 25\n" +
            "L8\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L8 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L8 1\n" +
            "LOCALVARIABLE b J L0 L8 2\n" +
            "LOCALVARIABLE i J L7 L8 25\n" +
            "MAXSTACK = 9\n" +
            "MAXLOCALS = 27"
        );
    }

    // multiple instrumentation of a call site is not handled well
//    @Test
//    public void methodCallDuration2() throws Exception {
//        loadTargetClass("OnMethodTest");
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
        loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallStatic");

        checkTransformation("LSTORE 4\nASTORE 6\nALOAD 0\nALOAD 6\nLLOAD 4\n"
                + "LDC \"resources/OnMethodTest.callTargetStatic(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevel\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallStatic$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 6\nLLOAD 4");

        resetClassLoader();

        transform("onmethod/leveled/MethodCallStatic");

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "GETSTATIC traces/onmethod/leveled/MethodCallStatic.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "LDC \"resources/OnMethodTest.callTargetStatic(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevel\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$MethodCallStatic$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L1\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "MAXSTACK = 9\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void staticMethodCall() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticMethodCall");

        checkTransformation("LSTORE 4\nASTORE 6\nASTORE 7\nALOAD 6\nLLOAD 4\nALOAD 7\n"
                + "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevelStatic\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticMethodCall$args (Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 7\nALOAD 6\nLLOAD 4");

        resetClassLoader();

        transform("onmethod/leveled/StaticMethodCall");

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "GETSTATIC traces/onmethod/leveled/StaticMethodCall.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "ALOAD 7\n" +
            "LDC \"resources/OnMethodTest.callTarget(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevelStatic\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$StaticMethodCall$args (Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L2\n" +
            "ALOAD 7\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "L3\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 0\n" +
            "LOCALVARIABLE b J L0 L3 1\n" +
            "LOCALVARIABLE instance Lresources/OnMethodTest; L1 L3 3\n" +
            "MAXSTACK = 9\n" +
            "MAXLOCALS = 8"
        );
    }

    @Test
    public void staticMethodCallStatic() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/StaticMethodCallStatic");

        checkTransformation("LSTORE 4\nASTORE 6\nALOAD 6\nLLOAD 4\n"
                + "LDC \"resources/OnMethodTest.callTargetStatic(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n"
                + "LDC \"callTopLevelStatic\"\n"
                + "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticMethodCallStatic$args (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
                + "ALOAD 6\nLLOAD 4");

        resetClassLoader();

        transform("onmethod/leveled/StaticMethodCallStatic");

        checkTransformation(
            "LSTORE 4\n" +
            "ASTORE 6\n" +
            "GETSTATIC traces/onmethod/leveled/StaticMethodCallStatic.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L2\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "LDC \"resources/OnMethodTest.callTargetStatic(Ljava/lang/String;J)J\"\n" +
            "LDC \"resources/OnMethodTest\"\n" +
            "LDC \"callTopLevelStatic\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$StaticMethodCallStatic$args (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "L2\n" +
            "ALOAD 6\n" +
            "LLOAD 4\n" +
            "L3\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L3 0\n" +
            "LOCALVARIABLE b J L0 L3 1\n" +
            "LOCALVARIABLE instance Lresources/OnMethodTest; L1 L3 3\n" +
            "MAXLOCALS = 7"
        );
    }

    @Test
    public void methodEntryNoArgsEntryReturn() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgsEntryReturn");

        checkTransformation(
            "ALOAD 0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturn$argsEmptyEntry (Ljava/lang/Object;)V\n" +
            "DUP2\n" +
            "LSTORE 6\n" +
            "ALOAD 0\n" +
            "LLOAD 6\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturn$argsEmptyReturn (Ljava/lang/Object;J)V\n" +
            "MAXSTACK = 5\n" +
            "MAXLOCALS = 8"
        );

        resetClassLoader();

        transform("onmethod/leveled/NoArgsEntryReturn");

        checkTransformation(
            "GETSTATIC traces/onmethod/leveled/NoArgsEntryReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L0\n" +
            "ALOAD 0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NoArgsEntryReturn$argsEmptyEntry (Ljava/lang/Object;)V\n" +
            "GETSTATIC traces/onmethod/leveled/NoArgsEntryReturn.$btrace$$level : I\n" +
            "ICONST_1\n" +
            "IF_ICMPLT L1\n" +
            "ALOAD 0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$leveled$NoArgsEntryReturn$argsEmptyReturn (Ljava/lang/Object;)V\n" +
            "L1\n" +
            "L2\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n" +
            "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n" +
            "LOCALVARIABLE b J L0 L2 2\n" +
            "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n" +
            "LOCALVARIABLE d [I L0 L2 5\n" +
            "MAXSTACK = 4"
        );
    }

    @Test
    public void methodEntryNoArgsEntryReturnNoCapture() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgsEntryReturnNoCapture");

        checkTransformation(
            "ALOAD 0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturnNoCapture$argsEmptyEntry (Ljava/lang/Object;)V\n" +
            "ALOAD 0\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturnNoCapture$argsEmptyReturn (Ljava/lang/Object;)V"
        );
    }

    @Test
    public void servicesTest() throws Exception {
        // a sanity test for the runtime verifier accepting the services method calls
        loadTargetClass("OnMethodTest");
        transform("ServicesTest");

        checkTransformation(
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$ServicesTest$testSimpleService ()V\n" +
            "LDC \"resources.OnMethodTest\"\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$ServicesTest$testFieldInjection (Ljava/lang/String;)V\n" +
            "MAXSTACK = 1\n" +
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$ServicesTest$testRuntimeService (Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 5\n" +
            "ALOAD 0\n" +
            "LLOAD 1\n" +
            "ALOAD 3\n" +
            "ALOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$ServicesTest$testSingletonService (Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 5\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$ServicesTest$testRuntimeService(Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/ServicesTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "NEW resources/services/DummyRuntimService\n" +
            "DUP\n" +
            "GETSTATIC traces/ServicesTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESPECIAL resources/services/DummyRuntimService.<init> (Lcom/sun/btrace/BTraceRuntime;)V\n" +
            "ASTORE 5\n" +
            "ALOAD 5\n" +
            "BIPUSH 10\n" +
            "LDC \"hello\"\n" +
            "INVOKEVIRTUAL resources/services/DummyRuntimService.doit (ILjava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 6\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$ServicesTest$testSimpleService()V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"noargs\")\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/ServicesTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "NEW resources/services/DummySimpleService\n" +
            "DUP\n" +
            "INVOKESPECIAL resources/services/DummySimpleService.<init> ()V\n" +
            "ASTORE 0\n" +
            "ALOAD 0\n" +
            "LDC \"hello\"\n" +
            "BIPUSH 10\n" +
            "INVOKEVIRTUAL resources/services/DummySimpleService.doit (Ljava/lang/String;I)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 1\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$ServicesTest$testSingletonService(Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args$static\")\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/ServicesTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "NEW resources/services/DummySimpleService\n" +
            "DUP\n" +
            "LDC \"getInstance\"\n" +
            "INVOKESPECIAL resources/services/DummySimpleService.<init> (Ljava/lang/String;)V\n" +
            "ASTORE 5\n" +
            "ALOAD 5\n" +
            "LDC \"hello\"\n" +
            "BIPUSH 10\n" +
            "INVOKEVIRTUAL resources/services/DummySimpleService.doit (Ljava/lang/String;I)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 6\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$ServicesTest$testFieldInjection(Ljava/lang/String;)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"noargs$static\")\n" +
            "@Lcom/sun/btrace/annotations/ProbeClassName;() // parameter 0\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/ServicesTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "NEW resources/services/DummyRuntimService\n" +
            "DUP\n" +
            "DUP\n" +
            "GETSTATIC traces/ServicesTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESPECIAL resources/services/DummyRuntimService.<init> (Lcom/sun/btrace/BTraceRuntime;)V\n" +
            "ASTORE 1\n" +
            "BIPUSH 10\n" +
            "LDC \"hey\"\n" +
            "INVOKEVIRTUAL resources/services/DummyRuntimService.doit (ILjava/lang/String;)V\n" +
            "ALOAD 1\n" +
            "BIPUSH 20\n" +
            "LDC \"ho\"\n" +
            "INVOKEVIRTUAL resources/services/DummyRuntimService.doit (ILjava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 2"
        );
    }

    @Test
    public void unsafeTest() throws Exception {
        loadTargetClass("OnMethodTest");
        System.err.println(asmify(originalBC));
        transform("onmethod/ArgsUnsafe", true);
        System.err.println("\n" + asmify(transformedBC));
    }

    @Test
    public void tlsTest() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("TLSTest");

        checkTrace(
            "public static Lcom/sun/btrace/BTraceRuntime; runtime\n" +
            "// access flags 0x19\n" +
            "// signature Ljava/lang/ThreadLocal<Ljava/util/Deque;>;\n" +
            "// declaration: java.lang.ThreadLocal<java.util.Deque>\n" +
            "public final static Ljava/lang/ThreadLocal; entryTimes\n" +
            "// access flags 0x19\n" +
            "// signature Ljava/lang/ThreadLocal<Ljava/lang/String;>;\n" +
            "// declaration: java.lang.ThreadLocal<java.lang.String>\n" +
            "public final static Ljava/lang/ThreadLocal; name\n" +
            "// access flags 0x19\n" +
            "// signature Ljava/lang/ThreadLocal<Ljava/lang/Integer;>;\n" +
            "// declaration: java.lang.ThreadLocal<java.lang.Integer>\n" +
            "public final static Ljava/lang/ThreadLocal; x\n" +
            "// access flags 0x19\n" +
            "// signature Ljava/lang/ThreadLocal<Ljava/lang/Double;>;\n" +
            "// declaration: java.lang.ThreadLocal<java.lang.Double>\n" +
            "public final static Ljava/lang/ThreadLocal; y\n" +
            "// signature Ljava/lang/ThreadLocal<Ljava/lang/Long;>;\n" +
            "// declaration: java.lang.ThreadLocal<java.lang.Long>\n" +
            "public final static Ljava/lang/ThreadLocal; z\n" +
            "\n" +
            "// access flags 0x49\n" +
            "public static volatile I $btrace$$level = 0\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/TLSTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "GETSTATIC traces/TLSTest.entryTimes : Ljava/lang/ThreadLocal;\n" +
            "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n" +
            "CHECKCAST java/util/Deque\n" +
            "GETSTATIC traces/TLSTest.name : Ljava/lang/ThreadLocal;\n" +
            "SWAP\n" +
            "INVOKEVIRTUAL java/lang/ThreadLocal.set (Ljava/lang/Object;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L0\n" +
            "LDC Ltraces/TLSTest;.class\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.forClass (Ljava/lang/Class;)Lcom/sun/btrace/BTraceRuntime;\n" +
            "PUTSTATIC traces/TLSTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "GETSTATIC traces/TLSTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L2\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L2\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n" +
            "PUTSTATIC traces/TLSTest.entryTimes : Ljava/lang/ThreadLocal;\n" +
            "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n" +
            "PUTSTATIC traces/TLSTest.x : Ljava/lang/ThreadLocal;\n" +
            "ACONST_NULL\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n" +
            "PUTSTATIC traces/TLSTest.name : Ljava/lang/ThreadLocal;\n" +
            "DCONST_0\n" +
            "INVOKESTATIC java/lang/Double.valueOf (D)Ljava/lang/Double;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n" +
            "PUTSTATIC traces/TLSTest.y : Ljava/lang/ThreadLocal;\n" +
            "LDC 10\n" +
            "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n" +
            "PUTSTATIC traces/TLSTest.z : Ljava/lang/ThreadLocal;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.start ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "MAXSTACK = 2"
        );

        checkTransformation(
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$TLSTest$testArgs (Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 5\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$TLSTest$testArgs(Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/TLSTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "GETSTATIC traces/TLSTest.entryTimes : Ljava/lang/ThreadLocal;\n" +
            "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n" +
            "CHECKCAST java/util/Deque\n" +
            "LLOAD 1\n" +
            "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceUtils.push (Ljava/util/Deque;Ljava/lang/Object;)V\n" +
            "ALOAD 0\n" +
            "GETSTATIC traces/TLSTest.name : Ljava/lang/ThreadLocal;\n" +
            "SWAP\n" +
            "INVOKEVIRTUAL java/lang/ThreadLocal.set (Ljava/lang/Object;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 5"
        );
    }

    @Test
    public void exportTest() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("ExportTest");

        checkTrace(
            "public static Lcom/sun/btrace/BTraceRuntime; runtime\n" +
            "// access flags 0x19\n" +
            "public final static Ljava/util/Deque; entryTimes\n" +
            "// access flags 0x19\n" +
            "public final static Ljava/lang/String; name\n" +
            "// access flags 0x19\n" +
            "public final static I x\n" +
            "// access flags 0x19\n" +
            "public final static D y\n" +
            "public final static J z\n" +
            "\n" +
            "// access flags 0x49\n" +
            "public static volatile I $btrace$$level = 0\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/ExportTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "ACONST_NULL\n" +
            "LDC \"btrace.traces/ExportTest.name\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.putPerfString (Ljava/lang/String;Ljava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L0\n" +
            "LDC Ltraces/ExportTest;.class\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.forClass (Ljava/lang/Class;)Lcom/sun/btrace/BTraceRuntime;\n" +
            "PUTSTATIC traces/ExportTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "GETSTATIC traces/ExportTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L2\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L2\n" +
            "LDC \"btrace.traces/ExportTest.entryTimes\"\n" +
            "LDC \"Ljava/util/Deque;\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n" +
            "LDC \"btrace.traces/ExportTest.x\"\n" +
            "LDC \"I\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "ACONST_NULL\n" +
            "LDC \"btrace.traces/ExportTest.name\"\n" +
            "LDC \"Ljava/lang/String;\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "DCONST_0\n" +
            "INVOKESTATIC java/lang/Double.valueOf (D)Ljava/lang/Double;\n" +
            "LDC \"btrace.traces/ExportTest.y\"\n" +
            "LDC \"D\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "LDC 10\n" +
            "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n" +
            "LDC \"btrace.traces/ExportTest.z\"\n" +
            "LDC \"J\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.start ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "MAXSTACK = 3"
        );

        checkTransformation(
            "ALOAD 1\n" +
            "LLOAD 2\n" +
            "ALOAD 4\n" +
            "ALOAD 5\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$ExportTest$testArgs (Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "MAXSTACK = 5\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$ExportTest$testArgs(Ljava/lang/String;J[Ljava/lang/String;[I)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/ExportTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "ACONST_NULL\n" +
            "LLOAD 1\n" +
            "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceUtils.push (Ljava/util/Deque;Ljava/lang/Object;)V\n" +
            "ALOAD 0\n" +
            "LDC \"btrace.traces/ExportTest.name\"\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.putPerfString (Ljava/lang/String;Ljava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 3\n" +
            "MAXLOCALS = 5"
        );
    }

    @Test
    public void onprobeTest() throws Exception {
        loadTargetClass("OnMethodTest");
        System.err.println(asmify(originalBC));
        transform("OnProbeTest", false);
        System.err.println("\n" + asmify(transformedBC));
    }

    @Test
    public void onTimerTest() throws Exception {
        loadTargetClass("OnMethodTest");
        transform("OnTimerTest", false);

        checkTrace(
            "public static Lcom/sun/btrace/BTraceRuntime; runtime\n" +
            "\n" +
            "// access flags 0x49\n" +
            "public static volatile I $btrace$$level = 0\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/OnTimerTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "L0\n" +
            "LDC Ltraces/OnTimerTest;.class\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.forClass (Ljava/lang/Class;)Lcom/sun/btrace/BTraceRuntime;\n" +
            "PUTSTATIC traces/OnTimerTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "GETSTATIC traces/OnTimerTest.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L2\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L2\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.start ()V\n" +
            "L1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN"
        );
    }
}
