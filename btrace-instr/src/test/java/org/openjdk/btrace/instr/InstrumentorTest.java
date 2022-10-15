/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.btrace.instr;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Jaroslav Bachorik
 */
public class InstrumentorTest extends InstrumentorTestBase {
  @BeforeAll
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

    checkTransformation(
        "ALOAD 0\nALOAD 1\nALOAD 2\n"
            + "INVOKESTATIC resources/DerivedClass.$btrace$org$openjdk$btrace$runtime$auxiliary$MatchDerived$args (Lresources/AbstractClass;Ljava/lang/String;Ljava/util/Map;)V");
  }

  @Test
  public void matchAnnotatedClass() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MatchAnnotated");

    checkTransformation(
        "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MatchAnnotated$args (Ljava/lang/Object;)V\n"
            + "MAXSTACK = 1");
  }

  @Test
  public void matchAnnotatedRegexClass() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MatchAnnotatedRegex");

    checkTransformation(
        "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MatchAnnotatedRegex$args (Ljava/lang/Object;)V\n"
            + "MAXSTACK = 1");
  }

  @Test
  public void methodEntryCheckcastBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/CheckcastBefore");

    checkTransformation(
        "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$CheckcastBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "ASTORE 3\n"
            + "FRAME APPEND [java/util/Map java/util/HashMap java/util/HashMap]\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L2 L5 3\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");

    resetClassLoader();

    transform("onmethod/leveled/CheckcastBefore");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/CheckcastBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$CheckcastBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "FRAME FULL [resources/OnMethodTest java/util/HashMap] [java/util/HashMap]\n"
            + "CHECKCAST java/util/HashMap\n"
            + "ASTORE 3\n"
            + "L3\n"
            + "LINENUMBER 102 L3\n"
            + "IFEQ L4\n"
            + "L5\n"
            + "LINENUMBER 103 L5\n"
            + "L4\n"
            + "LINENUMBER 105 L4\n"
            + "FRAME FULL [resources/OnMethodTest java/util/Map T java/util/HashMap] []\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L3 L6 3\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryCheckcastAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/CheckcastAfter");

    checkTransformation(
        "DUP\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "ALOAD 2\n"
            + "LDC \"java.util.HashMap\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$CheckcastAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;)V\n"
            + "ASTORE 3\n"
            + "FRAME APPEND [java/util/Map java/util/HashMap java/util/HashMap]\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L2 L5 3\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");

    resetClassLoader();

    transform("onmethod/leveled/CheckcastAfter");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/CheckcastAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$CheckcastAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "FRAME FULL [resources/OnMethodTest java/util/HashMap] [java/util/HashMap]\n"
            + "ASTORE 3\n"
            + "L3\n"
            + "LINENUMBER 102 L3\n"
            + "IFEQ L4\n"
            + "L5\n"
            + "LINENUMBER 103 L5\n"
            + "L4\n"
            + "LINENUMBER 105 L4\n"
            + "FRAME FULL [resources/OnMethodTest java/util/Map T java/util/HashMap] []\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L3 L6 3\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryInstanceofBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/InstanceofBefore");

    checkTransformation(
        "DUP\n"
            + "ASTORE 3\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$InstanceofBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "FRAME APPEND [java/util/Map java/util/HashMap java/util/HashMap]\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");

    resetClassLoader();

    transform("onmethod/leveled/InstanceofBefore");

    checkTransformation(
        "DUP\n"
            + "ASTORE 3\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/InstanceofBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$InstanceofBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest java/util/HashMap java/util/HashMap java/util/HashMap] [java/util/HashMap]\n"
            + "IFEQ L4\n"
            + "L5\n"
            + "LINENUMBER 103 L5\n"
            + "L4\n"
            + "LINENUMBER 105 L4\n"
            + "FRAME FULL [resources/OnMethodTest java/util/Map java/util/HashMap java/util/HashMap] []\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L2 L6 2\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryInstanceofAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/InstanceofAfter");

    checkTransformation(
        "DUP\n"
            + "ASTORE 3\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$InstanceofAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "FRAME APPEND [java/util/Map java/util/HashMap java/util/HashMap]\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");

    resetClassLoader();

    transform("onmethod/leveled/InstanceofAfter");

    checkTransformation(
        "DUP\n"
            + "ASTORE 3\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/InstanceofAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC \"java.util.HashMap\"\n"
            + "ALOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$InstanceofAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest java/util/HashMap java/util/HashMap java/util/HashMap] [I]\n"
            + "IFEQ L4\n"
            + "L5\n"
            + "LINENUMBER 103 L5\n"
            + "L4\n"
            + "LINENUMBER 105 L4\n"
            + "FRAME FULL [resources/OnMethodTest java/util/Map java/util/HashMap java/util/HashMap] []\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L2 L6 2\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryCatch() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/Catch");

    checkTransformation(
        "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Catch$args (Ljava/lang/Object;Ljava/io/IOException;)V\n"
            + "FRAME FULL [resources/OnMethodTest java/io/IOException] [java/io/IOException]\n"
            + "ASTORE 2\n"
            + "L3\n"
            + "LINENUMBER 68 L3\n"
            + "ALOAD 2\n"
            + "L4\n"
            + "LINENUMBER 70 L4\n"
            + "L5\n"
            + "LOCALVARIABLE e Ljava/io/IOException; L3 L4 2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/Catch");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Catch.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Catch$args (Ljava/lang/Object;Ljava/io/IOException;)V\n"
            + "FRAME SAME1 java/io/IOException\n"
            + "ASTORE 2\n"
            + "L3\n"
            + "LINENUMBER 68 L3\n"
            + "ALOAD 2\n"
            + "L4\n"
            + "LINENUMBER 70 L4\n"
            + "L5\n"
            + "LOCALVARIABLE e Ljava/io/IOException; L3 L4 2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "MAXLOCALS = 3");
  }

  @Test
  public void methodEntryThrow() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/Throw");

    checkTransformation(
        "DUP\n"
            + "ASTORE 1\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"exception\"\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Throw$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V\n"
            + "ASTORE 2\n"
            + "ALOAD 2\n"
            + "LOCALVARIABLE e Ljava/io/IOException; L2 L3 2\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/Throw");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Throw.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP\n"
            + "ASTORE 1\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"exception\"\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Throw$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V\n"
            + "L2\n"
            + "FRAME SAME1 java/io/IOException\n"
            + "ASTORE 2\n"
            + "L3\n"
            + "LINENUMBER 68 L3\n"
            + "ALOAD 2\n"
            + "L4\n"
            + "LINENUMBER 70 L4\n"
            + "L5\n"
            + "LOCALVARIABLE e Ljava/io/IOException; L3 L4 2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 3");
  }

  @Test
  public void methodEntryError() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/Error");

    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 1\n"
            + "ALOAD 0\n"
            + "LDC \"uncaught\"\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 2");

    resetClassLoader();

    transform("onmethod/leveled/Error");

    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Error.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "LDC \"uncaught\"\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V\n"
            + "L2\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 2");
  }

  @Test
  public void methodEntryErrorCaught() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ErrorCaught");

    checkTransformation(
        "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n"
            + "L3\n"
            + "LINENUMBER 162 L3\n"
            + "L4\n"
            + "LINENUMBER 164 L4\n"
            + "RETURN\n"
            + "L2\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"caught\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ErrorCaught$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "LOCALVARIABLE e Ljava/lang/RuntimeException; L3 L4 1\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/ErrorCaught");

    checkTransformation(
        "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n"
            + "L3\n"
            + "LINENUMBER 162 L3\n"
            + "L4\n"
            + "LINENUMBER 164 L4\n"
            + "RETURN\n"
            + "L2\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ErrorCaught.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L5\n"
            + "ALOAD 0\n"
            + "LDC \"caught\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ErrorCaught$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest T java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE e Ljava/lang/RuntimeException; L3 L4 1\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 3");
  }

  @Test
  public void methodEntryErrorDuration() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ErrorDuration");

    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "LCONST_0\n"
            + "LSTORE 1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 3\n"
            + "FRAME FULL [resources/OnMethodTest J J] [java/lang/Throwable]\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 3\n"
            + "LSUB\n"
            + "LSTORE 1\n"
            + "DUP\n"
            + "ASTORE 5\n"
            + "ALOAD 0\n"
            + "LDC \"uncaught\"\n"
            + "LLOAD 1\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ErrorDuration$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 6");

    resetClassLoader();

    transform("onmethod/leveled/ErrorDuration");

    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "LCONST_0\n"
            + "LSTORE 1\n"
            + "LCONST_0\n"
            + "LSTORE 3\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ErrorDuration.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 5\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 3\n"
            + "FRAME APPEND [J J I]\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ILOAD 5\n"
            + "IFLE L2\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 3\n"
            + "LSUB\n"
            + "LSTORE 1\n"
            + "L2\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ErrorDuration.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LDC \"uncaught\"\n"
            + "LLOAD 1\n"
            + "ALOAD 6\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ErrorDuration$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Throwable;)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest J J I java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryLine() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/Line");

    checkTransformation(
        "LDC \"field\"\n"
            + "LDC 84\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Line$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0");

    resetClassLoader();

    transform("onmethod/leveled/Line");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Line.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "LDC \"field\"\n"
            + "LDC 84\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Line$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "ALOAD 0\n"
            + "L2\n"
            + "LINENUMBER 85 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0");
  }

  @Test
  public void methodEntryAllLines() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/AllLines");

    checkTransformation(
        "LDC \"<init>\"\n"
            + "LDC 39\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"<init>\"\n"
            + "LDC 40\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "ALOAD 0\n"
            + "LDC \"noargs\"\n"
            + "LDC 42\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ACONST_NULL\n"
            + "LDC \"noargs$static\"\n"
            + "LDC 43\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"args\"\n"
            + "LDC 44\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ACONST_NULL\n"
            + "LDC \"args$static\"\n"
            + "LDC 45\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ACONST_NULL\n"
            + "LDC \"callTopLevelStatic\"\n"
            + "LDC 48\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ACONST_NULL\n"
            + "LDC \"callTopLevelStatic\"\n"
            + "LDC 49\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ACONST_NULL\n"
            + "LDC \"callTargetStatic\"\n"
            + "LDC 53\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "LDC \"callTopLevel\"\n"
            + "LDC 57\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "ALOAD 0\n"
            + "LDC \"callTarget\"\n"
            + "LDC 61\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"exception\"\n"
            + "LDC 66\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"exception\"\n"
            + "LDC 67\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L2\n"
            + "L3\n"
            + "LINENUMBER 68 L3\n"
            + "ALOAD 0\n"
            + "LDC \"exception\"\n"
            + "LDC 68\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L4\n"
            + "LINENUMBER 70 L4\n"
            + "ALOAD 0\n"
            + "LDC \"exception\"\n"
            + "LDC 70\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L5\n"
            + "LOCALVARIABLE e Ljava/io/IOException; L3 L4 1\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "MAXSTACK = 4\n"
            + "ALOAD 0\n"
            + "LDC \"uncaught\"\n"
            + "LDC 73\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"array\"\n"
            + "LDC 77\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"array\"\n"
            + "LDC 79\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"array\"\n"
            + "LDC 80\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"array\"\n"
            + "LDC 81\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "LDC \"field\"\n"
            + "LDC 84\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "ALOAD 0\n"
            + "LDC \"field\"\n"
            + "LDC 85\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"newObject\"\n"
            + "LDC 88\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"newObject\"\n"
            + "LDC 89\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"newArray\"\n"
            + "LDC 92\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"newArray\"\n"
            + "LDC 93\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"newArray\"\n"
            + "LDC 94\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"newArray\"\n"
            + "LDC 95\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"newArray\"\n"
            + "LDC 96\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC 99\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC 100\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC 102\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC 103\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"casts\"\n"
            + "LDC 105\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L5\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE c Ljava/util/Map; L1 L6 1\n"
            + "LOCALVARIABLE d Ljava/util/HashMap; L2 L6 2\n"
            + "MAXSTACK = 3\n"
            + "LDC \"sync\"\n"
            + "LDC 108\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "ALOAD 0\n"
            + "LDC \"sync\"\n"
            + "LDC 109\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"sync\"\n"
            + "LDC 110\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"sync\"\n"
            + "LDC 111\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L7\n"
            + "L8\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L8 0\n"
            + "MAXSTACK = 3\n"
            + "LDC \"callTopLevel1\"\n"
            + "LDC 114\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "ALOAD 0\n"
            + "LDC \"callTopLevel1\"\n"
            + "LDC 115\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"calLTargetX\"\n"
            + "LDC 119\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"argsMultiReturn\"\n"
            + "LDC 123\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"argsMultiReturn\"\n"
            + "LDC 124\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"argsMultiReturn\"\n"
            + "LDC 127\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L3\n"
            + "IFLE L4\n"
            + "L5\n"
            + "LINENUMBER 128 L5\n"
            + "ALOAD 0\n"
            + "LDC \"argsMultiReturn\"\n"
            + "LDC 128\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L4\n"
            + "LINENUMBER 132 L4\n"
            + "ALOAD 0\n"
            + "LDC \"argsMultiReturn\"\n"
            + "LDC 132\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L6\n"
            + "L7\n"
            + "LINENUMBER 133 L7\n"
            + "ALOAD 0\n"
            + "LDC \"argsMultiReturn\"\n"
            + "LDC 133\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L8\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L8 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L8 1\n"
            + "LOCALVARIABLE b J L0 L8 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L8 4\n"
            + "LOCALVARIABLE d [I L0 L8 5\n"
            + "ALOAD 0\n"
            + "LDC \"staticField\"\n"
            + "LDC 143\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"staticField\"\n"
            + "LDC 144\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "LDC \"syncM\"\n"
            + "LDC 147\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "ALOAD 0\n"
            + "LDC \"syncM\"\n"
            + "LDC 148\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"syncM\"\n"
            + "LDC 149\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"syncM\"\n"
            + "LDC 150\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L7\n"
            + "L8\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L8 0\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"argsTypeMatch\"\n"
            + "LDC 155\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 3\n"
            + "ALOAD 0\n"
            + "LDC \"caught\"\n"
            + "LDC 160\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"caught\"\n"
            + "LDC 161\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L2\n"
            + "L3\n"
            + "LINENUMBER 162 L3\n"
            + "ALOAD 0\n"
            + "LDC \"caught\"\n"
            + "LDC 162\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L4\n"
            + "LINENUMBER 164 L4\n"
            + "ALOAD 0\n"
            + "LDC \"caught\"\n"
            + "LDC 164\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AllLines$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "RETURN\n"
            + "L5\n"
            + "LOCALVARIABLE e Ljava/lang/RuntimeException; L3 L4 1\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 2");
  }

  @Test
  public void methodEntryNewBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NewBefore");

    checkTransformation(
        "ALOAD 0\n"
            + "LDC \"java.util.HashMap\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewBefore$args (Ljava/lang/Object;Ljava/lang/String;)V");

    resetClassLoader();

    transform("onmethod/leveled/NewBefore");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "LDC \"java.util.HashMap\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewBefore$args (Ljava/lang/Object;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "L2\n"
            + "LINENUMBER 89 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE m Ljava/util/Map; L2 L3 1");
  }

  @Test
  public void methodEntryNewAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NewAfter");

    checkTransformation(
        "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LDC \"java.util.HashMap\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewAfter$args (Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;)V\n"
            + "ASTORE 2\n"
            + "LOCALVARIABLE m Ljava/util/Map; L1 L2 2\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/NewAfter");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LDC \"java.util.HashMap\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewAfter$args (Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;)V\n"
            + "FRAME SAME1 java/util/HashMap\n"
            + "ASTORE 2\n"
            + "L2\n"
            + "LINENUMBER 89 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE m Ljava/util/Map; L2 L3 2\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 3");
  }

  @Test
  public void methodEntrySyncEntry() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/SyncEntry");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"sync\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L6\n"
            + "LINENUMBER 110 L6\n"
            + "GOTO L7\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Object resources/OnMethodTest] [java/lang/Throwable]\n"
            + "ASTORE 3\n"
            + "ALOAD 3\n"
            + "L7\n"
            + "LINENUMBER 111 L7\n"
            + "FRAME FULL [resources/OnMethodTest T resources/OnMethodTest] []\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 4");

    resetClassLoader();

    transform("onmethod/leveled/SyncEntry");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/SyncEntry.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L6\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"sync\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L6\n"
            + "FRAME FULL [resources/OnMethodTest resources/OnMethodTest] [resources/OnMethodTest]\n"
            + "L7\n"
            + "LINENUMBER 110 L7\n"
            + "GOTO L8\n"
            + "ASTORE 3\n"
            + "ALOAD 3\n"
            + "L8\n"
            + "LINENUMBER 111 L8\n"
            + "L5\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntrySyncMEntry() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/SyncMEntry");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"syncM\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncMEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L6\n"
            + "LINENUMBER 149 L6\n"
            + "GOTO L7\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Object java/lang/Object] [java/lang/Throwable]\n"
            + "ASTORE 3\n"
            + "ALOAD 3\n"
            + "L7\n"
            + "LINENUMBER 150 L7\n"
            + "FRAME FULL [resources/OnMethodTest T java/lang/Object] []\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 4");

    resetClassLoader();

    transform("onmethod/leveled/SyncMEntry");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/SyncMEntry.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L6\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"syncM\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncMEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L6\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Object] [java/lang/Object]\n"
            + "L7\n"
            + "LINENUMBER 149 L7\n"
            + "GOTO L8\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ASTORE 3\n"
            + "ALOAD 3\n"
            + "L8\n"
            + "LINENUMBER 150 L8\n"
            + "L5\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntrySyncExit() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/SyncExit");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "L6\n"
            + "LINENUMBER 110 L6\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "GOTO L7\n"
            + "ASTORE 3\n"
            + "DUP\n"
            + "ASTORE 4\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "ALOAD 3\n"
            + "L7\n"
            + "LINENUMBER 111 L7\n"
            + "FRAME FULL [resources/OnMethodTest T resources/OnMethodTest] []\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 5");

    resetClassLoader();

    transform("onmethod/leveled/SyncExit");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "L6\n"
            + "LINENUMBER 110 L6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/SyncExit.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L7\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L7\n"
            + "FRAME FULL [resources/OnMethodTest resources/OnMethodTest] [resources/OnMethodTest]\n"
            + "GOTO L8\n"
            + "ASTORE 3\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/SyncExit.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L9\n"
            + "DUP\n"
            + "ASTORE 4\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L9\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Object T java/lang/Throwable] [java/lang/Object]\n"
            + "ALOAD 3\n"
            + "L8\n"
            + "LINENUMBER 111 L8\n"
            + "FRAME CHOP 3\n"
            + "L5\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 5");
  }

  @Test
  public void methodEntrySyncMExit() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/SyncMExit");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "L6\n"
            + "LINENUMBER 149 L6\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncMExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "GOTO L7\n"
            + "ASTORE 3\n"
            + "DUP\n"
            + "ASTORE 4\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncMExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "ALOAD 3\n"
            + "L7\n"
            + "LINENUMBER 150 L7\n"
            + "FRAME FULL [resources/OnMethodTest T java/lang/Object] []\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 5");

    resetClassLoader();

    transform("onmethod/leveled/SyncMExit");

    checkTransformation(
        "TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\n"
            + "L6\n"
            + "LINENUMBER 149 L6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/SyncMExit.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L7\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncMExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L7\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Object] [java/lang/Object]\n"
            + "GOTO L8\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ASTORE 3\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/SyncMExit.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L9\n"
            + "DUP\n"
            + "ASTORE 4\n"
            + "ALOAD 0\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$SyncMExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n"
            + "L9\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/Object T java/lang/Throwable] [java/lang/Object]\n"
            + "ALOAD 3\n"
            + "L8\n"
            + "LINENUMBER 150 L8\n"
            + "FRAME CHOP 3\n"
            + "L5\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 5");
  }

  @Test
  public void methodEntryNewArrayIntBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NewArrayIntBefore");

    checkTransformation(
        "ALOAD 0\n"
            + "LDC \"int\"\n"
            + "ICONST_1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"int\"\n"
            + "ICONST_2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 5");

    resetClassLoader();

    transform("onmethod/leveled/NewArrayIntBefore");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewArrayIntBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "LDC \"int\"\n"
            + "ICONST_1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L1\n"
            + "FRAME SAME1 I\n"
            + "L2\n"
            + "LINENUMBER 93 L2\n"
            + "ICONST_1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewArrayIntBefore.$btrace$$level : I\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LDC \"int\"\n"
            + "ICONST_2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest [I] [I I]\n"
            + "L4\n"
            + "LINENUMBER 94 L4\n"
            + "L5\n"
            + "LINENUMBER 95 L5\n"
            + "L6\n"
            + "LINENUMBER 96 L6\n"
            + "L7\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L7 0\n"
            + "LOCALVARIABLE a [I L2 L7 1\n"
            + "LOCALVARIABLE b [[I L4 L7 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L5 L7 3\n"
            + "LOCALVARIABLE d [[Ljava/lang/String; L6 L7 4\n"
            + "MAXSTACK = 5");
  }

  @Test
  public void methodEntryNewArrayStringBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NewArrayStringBefore");

    checkTransformation(
        "ALOAD 0\n"
            + "LDC \"java.lang.String\"\n"
            + "ICONST_1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "ALOAD 0\n"
            + "LDC \"java.lang.String\"\n"
            + "ICONST_2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 5");

    resetClassLoader();

    transform("onmethod/leveled/NewArrayStringBefore");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewArrayStringBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LDC \"java.lang.String\"\n"
            + "ICONST_1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest [I [[I] [I]\n"
            + "L4\n"
            + "LINENUMBER 95 L4\n"
            + "ICONST_1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewArrayStringBefore.$btrace$$level : I\n"
            + "IF_ICMPLT L5\n"
            + "ALOAD 0\n"
            + "LDC \"java.lang.String\"\n"
            + "ICONST_2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest [I [[I [Ljava/lang/String;] [I I]\n"
            + "L6\n"
            + "LINENUMBER 96 L6\n"
            + "L7\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L7 0\n"
            + "LOCALVARIABLE a [I L1 L7 1\n"
            + "LOCALVARIABLE b [[I L2 L7 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L4 L7 3\n"
            + "LOCALVARIABLE d [[Ljava/lang/String; L6 L7 4\n"
            + "MAXSTACK = 5");
  }

  @Test
  public void methodEntryNewArrayIntAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NewArrayIntAfter");

    checkTransformation(
        "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayIntAfter$args (Ljava/lang/Object;[I)V\n"
            + "ASTORE 2\n"
            + "ASTORE 3\n"
            + "ASTORE 4\n"
            + "ASTORE 5\n"
            + "LOCALVARIABLE a [I L1 L5 2\n"
            + "LOCALVARIABLE b [[I L2 L5 3\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L3 L5 4\n"
            + "LOCALVARIABLE d [[Ljava/lang/String; L4 L5 5\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6");

    resetClassLoader();

    transform("onmethod/leveled/NewArrayIntAfter");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewArrayIntAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayIntAfter$args (Ljava/lang/Object;[I)V\n"
            + "FRAME SAME1 [I\n"
            + "ASTORE 2\n"
            + "L2\n"
            + "LINENUMBER 93 L2\n"
            + "ASTORE 3\n"
            + "L3\n"
            + "LINENUMBER 94 L3\n"
            + "ASTORE 4\n"
            + "L4\n"
            + "LINENUMBER 95 L4\n"
            + "ASTORE 5\n"
            + "L5\n"
            + "LINENUMBER 96 L5\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE a [I L2 L6 2\n"
            + "LOCALVARIABLE b [[I L3 L6 3\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L4 L6 4\n"
            + "LOCALVARIABLE d [[Ljava/lang/String; L5 L6 5\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6");
  }

  @Test
  public void methodEntryNewArrayStringAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NewArrayStringAfter");

    checkTransformation(
        "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayStringAfter$args (Ljava/lang/Object;[Ljava/lang/String;)V\n"
            + "ASTORE 4\n"
            + "ASTORE 5\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L3 L5 4\n"
            + "LOCALVARIABLE d [[Ljava/lang/String; L4 L5 5\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6");

    resetClassLoader();

    transform("onmethod/leveled/NewArrayStringAfter");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NewArrayStringAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "DUP\n"
            + "ALOAD 0\n"
            + "ALOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NewArrayStringAfter$args (Ljava/lang/Object;[Ljava/lang/String;)V\n"
            + "FRAME FULL [resources/OnMethodTest [I [[I] [[Ljava/lang/String;]\n"
            + "ASTORE 4\n"
            + "L4\n"
            + "LINENUMBER 95 L4\n"
            + "ASTORE 5\n"
            + "L5\n"
            + "LINENUMBER 96 L5\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE a [I L1 L6 1\n"
            + "LOCALVARIABLE b [[I L2 L6 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L4 L6 4\n"
            + "LOCALVARIABLE d [[Ljava/lang/String; L5 L6 5\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6");
  }

  @Test
  public void methodEntryArrayGetBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArrayGetBefore");

    checkTransformation(
        "DUP2\n"
            + "ISTORE 3\n"
            + "ASTORE 4\n"
            + "ALOAD 0\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetBefore$args (Ljava/lang/Object;[II)V\n"
            + "ISTORE 5\n"
            + "LOCALVARIABLE b I L2 L4 5\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 6");

    resetClassLoader();

    transform("onmethod/leveled/ArrayGetBefore");

    checkTransformation(
        "DUP2\n"
            + "ASTORE 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArrayGetBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetBefore$args (Ljava/lang/Object;[II)V\n"
            + "FRAME FULL [resources/OnMethodTest I [I I [I] [[I I]\n"
            + "IALOAD\n"
            + "ISTORE 5\n"
            + "L3\n"
            + "LINENUMBER 80 L3\n"
            + "L4\n"
            + "LINENUMBER 81 L4\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a I L0 L5 1\n"
            + "LOCALVARIABLE arr [I L1 L5 2\n"
            + "LOCALVARIABLE b I L3 L5 5\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 6");
  }

  @Test
  public void methodEntryArrayGetAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArrayGetAfter");

    checkTransformation(
        "DUP2\n"
            + "ISTORE 3\n"
            + "ASTORE 4\n"
            + "DUP\n"
            + "ISTORE 5\n"
            + "ALOAD 0\n"
            + "ILOAD 5\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetAfter$args (Ljava/lang/Object;I[II)V\n"
            + "ISTORE 6\n"
            + "LOCALVARIABLE b I L2 L4 6\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArrayGetAfter");

    checkTransformation(
        "DUP2\n"
            + "ASTORE 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArrayGetAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 5\n"
            + "IFLT L2\n"
            + "FRAME FULL [resources/OnMethodTest I [I I [I I] [[I I]\n"
            + "IALOAD\n"
            + "ILOAD 5\n"
            + "IFLT L3\n"
            + "DUP\n"
            + "ISTORE 6\n"
            + "ALOAD 0\n"
            + "ILOAD 6\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetAfter$args (Ljava/lang/Object;I[II)V\n"
            + "L3\n"
            + "FRAME SAME1 I\n"
            + "ISTORE 7\n"
            + "L4\n"
            + "LINENUMBER 80 L4\n"
            + "L5\n"
            + "LINENUMBER 81 L5\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE a I L0 L6 1\n"
            + "LOCALVARIABLE arr [I L1 L6 2\n"
            + "LOCALVARIABLE b I L4 L6 7\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArraySetBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArraySetBefore");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetBefore$args (Ljava/lang/Object;[III)V\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArraySetBefore");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArraySetBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetBefore$args (Ljava/lang/Object;[III)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest I [I I I I [I] [[I I I]\n"
            + "L4\n"
            + "LINENUMBER 81 L4\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a I L0 L5 1\n"
            + "LOCALVARIABLE arr [I L1 L5 2\n"
            + "LOCALVARIABLE b I L2 L5 3\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryArraySetAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArraySetAfter");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetAfter$args (Ljava/lang/Object;[III)V\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArraySetAfter");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArraySetAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLT L3\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest I [I I I I [I I] [[I I I]\n"
            + "ILOAD 7\n"
            + "IFLT L4\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetAfter$args (Ljava/lang/Object;[III)V\n"
            + "L4\n"
            + "LINENUMBER 81 L4\n"
            + "FRAME SAME\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a I L0 L5 1\n"
            + "LOCALVARIABLE arr [I L1 L5 2\n"
            + "LOCALVARIABLE b I L2 L5 3\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArrayGetBeforeAny() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArrayGetBeforeAny");

    checkTransformation(
        "DUP2\n"
            + "ISTORE 3\n"
            + "ASTORE 4\n"
            + "ALOAD 0\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetBeforeAny$args (Ljava/lang/Object;Ljava/lang/Object;I)V\n"
            + "ISTORE 5\n"
            + "LOCALVARIABLE b I L2 L4 5\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 6");

    resetClassLoader();

    transform("onmethod/leveled/ArrayGetBeforeAny");

    checkTransformation(
        "DUP2\n"
            + "ASTORE 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArrayGetBeforeAny.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetBeforeAny$args (Ljava/lang/Object;Ljava/lang/Object;I)V\n"
            + "FRAME FULL [resources/OnMethodTest I [I I [I] [[I I]\n"
            + "IALOAD\n"
            + "ISTORE 5\n"
            + "L3\n"
            + "LINENUMBER 80 L3\n"
            + "L4\n"
            + "LINENUMBER 81 L4\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a I L0 L5 1\n"
            + "LOCALVARIABLE arr [I L1 L5 2\n"
            + "LOCALVARIABLE b I L3 L5 5\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 6");
  }

  @Test
  public void methodEntryArrayGetAfterAny() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArrayGetAfterAny");

    checkTransformation(
        "DUP2\n"
            + "ISTORE 3\n"
            + "ASTORE 4\n"
            + "DUP\n"
            + "ISTORE 5\n"
            + "ALOAD 0\n"
            + "ILOAD 5\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetAfterAny$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)V\n"
            + "ISTORE 6\n"
            + "LOCALVARIABLE b I L2 L4 6\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArrayGetAfterAny");

    checkTransformation(
        "DUP2\n"
            + "ASTORE 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArrayGetAfterAny.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 5\n"
            + "IFLT L2\n"
            + "FRAME FULL [resources/OnMethodTest I [I I [I I] [[I I]\n"
            + "IALOAD\n"
            + "ILOAD 5\n"
            + "IFLT L3\n"
            + "DUP\n"
            + "ISTORE 6\n"
            + "ALOAD 0\n"
            + "ILOAD 6\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "ALOAD 4\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArrayGetAfterAny$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)V\n"
            + "L3\n"
            + "FRAME SAME1 I\n"
            + "ISTORE 7\n"
            + "L4\n"
            + "LINENUMBER 80 L4\n"
            + "L5\n"
            + "LINENUMBER 81 L5\n"
            + "L6\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L6 0\n"
            + "LOCALVARIABLE a I L0 L6 1\n"
            + "LOCALVARIABLE arr [I L1 L6 2\n"
            + "LOCALVARIABLE b I L4 L6 7\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArraySetBeforeAny() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArraySetBeforeAny");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetBeforeAny$args (Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArraySetBeforeAny");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArraySetBeforeAny.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetBeforeAny$args (Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest I [I I I I [I] [[I I I]\n"
            + "L4\n"
            + "LINENUMBER 81 L4\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a I L0 L5 1\n"
            + "LOCALVARIABLE arr [I L1 L5 2\n"
            + "LOCALVARIABLE b I L2 L5 3\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryArraySetAfterAny() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArraySetAfterAny");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetAfterAny$args (Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArraySetAfterAny");

    checkTransformation(
        "ISTORE 4\n"
            + "DUP2\n"
            + "ISTORE 5\n"
            + "ASTORE 6\n"
            + "ILOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArraySetAfterAny.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLT L3\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest I [I I I I [I I] [[I I I]\n"
            + "ILOAD 7\n"
            + "IFLT L4\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "ILOAD 5\n"
            + "ILOAD 4\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArraySetAfterAny$args (Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V\n"
            + "L4\n"
            + "LINENUMBER 81 L4\n"
            + "FRAME SAME\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a I L0 L5 1\n"
            + "LOCALVARIABLE arr [I L1 L5 2\n"
            + "LOCALVARIABLE b I L2 L5 3\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryFieldStaticGetBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldGetBeforeStatic");

    checkTransformation(
        "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"sField\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetBeforeStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V");

    resetClassLoader();

    transform("onmethod/leveled/FieldGetBeforeStatic");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldGetBeforeStatic.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"sField\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetBeforeStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "L2\n"
            + "LINENUMBER 144 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0");
  }

  @Test
  public void methodEntryFieldGetBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldGetBefore");

    checkTransformation(
        "DUP\nASTORE 1\nALOAD 0\nALOAD 1\nLDC \"field\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V");

    resetClassLoader();

    transform("onmethod/leveled/FieldGetBefore");

    checkTransformation(
        "DUP\n"
            + "ASTORE 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldGetBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LDC \"field\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest resources/OnMethodTest] [resources/OnMethodTest resources/OnMethodTest]\n"
            + "L2\n"
            + "LINENUMBER 85 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 2");
  }

  @Test
  public void methodEntryFieldStaticGetAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldGetAfterStatic");

    checkTransformation(
        "DUP2\n"
            + "LSTORE 1\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"static field long resources.OnMethodTest#sField\"\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetAfterStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J)V\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/FieldGetAfterStatic");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldGetAfterStatic.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 1\n"
            + "IFLT L1\n"
            + "L1\n"
            + "FRAME APPEND [I]\n"
            + "ILOAD 1\n"
            + "IFLT L2\n"
            + "DUP2\n"
            + "LSTORE 2\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"static field long resources.OnMethodTest#sField\"\n"
            + "LLOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetAfterStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LINENUMBER 144 L3\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryFieldGetAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldGetAfter");

    checkTransformation(
        "DUP\n"
            + "ASTORE 1\n"
            + "DUP\n"
            + "ISTORE 2\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LDC \"field int resources.OnMethodTest#field\"\n"
            + "ILOAD 2\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/FieldGetAfter");

    checkTransformation(
        "DUP\n"
            + "ASTORE 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldGetAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 2\n"
            + "IFLT L1\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest resources/OnMethodTest I] [resources/OnMethodTest resources/OnMethodTest]\n"
            + "ILOAD 2\n"
            + "IFLT L2\n"
            + "DUP\n"
            + "ISTORE 3\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LDC \"field int resources.OnMethodTest#field\"\n"
            + "ILOAD 3\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldGetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L2\n"
            + "FRAME FULL [resources/OnMethodTest resources/OnMethodTest I] [resources/OnMethodTest I]\n"
            + "L3\n"
            + "LINENUMBER 85 L3\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryFieldStaticSetBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldSetBeforeStatic");

    checkTransformation(
        "LSTORE 1\n"
            + "LLOAD 1\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"sField\"\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetBeforeStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J)V\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/FieldSetBeforeStatic");

    checkTransformation(
        "LSTORE 1\n"
            + "LLOAD 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldSetBeforeStatic.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"sField\"\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetBeforeStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest J] [J]\n"
            + "L2\n"
            + "LINENUMBER 144 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 3");
  }

  @Test
  public void methodEntryFieldSetBefore() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldSetBefore");

    checkTransformation(
        "ISTORE 1\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ILOAD 1\n"
            + "ALOAD 0\n"
            + "ALOAD 2\n"
            + "LDC \"field\"\n"
            + "ILOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/FieldSetBefore");

    checkTransformation(
        "ISTORE 1\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ILOAD 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldSetBefore.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "ALOAD 2\n"
            + "LDC \"field\"\n"
            + "ILOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest I resources/OnMethodTest] [resources/OnMethodTest I]\n"
            + "L2\n"
            + "LINENUMBER 85 L2\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 3");
  }

  @Test
  public void methodEntryFieldStaticSetAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldSetAfterStatic");

    checkTransformation(
        "LSTORE 1\n"
            + "LLOAD 1\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"sField\"\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetAfterStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J)V\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 3");

    resetClassLoader();

    transform("onmethod/leveled/FieldSetAfterStatic");

    checkTransformation(
        "LSTORE 1\n"
            + "LLOAD 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldSetAfterStatic.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 3\n"
            + "IFLT L1\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest J I] [J]\n"
            + "ILOAD 3\n"
            + "IFLT L2\n"
            + "ALOAD 0\n"
            + "ACONST_NULL\n"
            + "LDC \"sField\"\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetAfterStatic$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J)V\n"
            + "L2\n"
            + "LINENUMBER 144 L2\n"
            + "FRAME SAME\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryFieldSetAfter() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/FieldSetAfter");

    checkTransformation(
        "ISTORE 1\nDUP\nASTORE 2\nILOAD 1\nALOAD 0\nALOAD 2\nLDC \"field\"\nILOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");

    resetClassLoader();

    transform("onmethod/leveled/FieldSetAfter");

    checkTransformation(
        "ISTORE 1\n"
            + "DUP\n"
            + "ASTORE 2\n"
            + "ILOAD 1\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/FieldSetAfter.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "ISUB\n"
            + "DUP\n"
            + "ISTORE 3\n"
            + "IFLT L1\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest I resources/OnMethodTest I] [resources/OnMethodTest I]\n"
            + "ILOAD 3\n"
            + "IFLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 2\n"
            + "LDC \"field\"\n"
            + "ILOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$FieldSetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V\n"
            + "L2\n"
            + "LINENUMBER 85 L2\n"
            + "FRAME SAME\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 4");
  }

  @Test
  public void methodEntryArgsNoSelf() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsNoSelf");

    checkTransformation(
        "LDC \"public long resources.OnMethodTest#args(java.lang.String, long, java.lang.String[], int[])\"\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsNoSelf$argsNoSelf (Ljava/lang/String;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 6");

    resetClassLoader();

    transform("onmethod/leveled/ArgsNoSelf");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsNoSelf.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "LDC \"public long resources.OnMethodTest#args(java.lang.String, long, java.lang.String[], int[])\"\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsNoSelf$argsNoSelf (Ljava/lang/String;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 6");
  }

  @Test
  public void methodEntryNoArgs() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NoArgs");

    checkTransformation(
        "ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgs$argsEmpty (Ljava/lang/Object;)V");

    resetClassLoader();

    transform("onmethod/leveled/NoArgs");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NoArgs.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgs$argsEmpty (Ljava/lang/Object;)V");
  }

  @Test
  public void methodEntryArgs() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/Args");
    checkTransformation(
        "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 6");

    resetClassLoader();

    transform("onmethod/leveled/Args");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Args.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 6");
  }

  @Test
  public void methodEntryArgsReload() throws Exception {
    enableUniqueClientClassNameCheck();
    loadTargetClass("OnMethodTest");
    loadTrace("onmethod/Args");
    transform("onmethod/Args");
    checkTransformation(
        "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args$1$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 6");

    resetClassLoader();

    loadTrace("onmethod/leveled/Args");
    transform("onmethod/leveled/Args");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Args$3.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args$3$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 6");
  }

  @Test
  public void methodEntryArgsShared() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsShared");
    checkTransformation(
        "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$args(Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"/.*\\\\.OnMethodTest/\", method=\"args\")\n"
            + "// annotable parameter count: 5 (visible)\n"
            + "@Lorg/openjdk/btrace/core/annotations/Self;() // parameter 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW java/lang/StringBuilder\n"
            + "DUP\n"
            + "INVOKESPECIAL java/lang/StringBuilder.<init> ()V\n"
            + "LDC \"this = \"\n"
            + "INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;\n"
            + "ALOAD 0\n"
            + "INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/Object;)Ljava/lang/StringBuilder;\n"
            + "INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "LDC \"args\"\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.cntr : Ljava/lang/ThreadLocal;\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n"
            + "CHECKCAST java/lang/Integer\n"
            + "INVOKEVIRTUAL java/lang/Integer.intValue ()I\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.str (I)Ljava/lang/String;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.cntr : Ljava/lang/ThreadLocal;\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n"
            + "CHECKCAST java/lang/Integer\n"
            + "INVOKEVIRTUAL java/lang/Integer.intValue ()I\n"
            + "ICONST_1\n"
            + "IADD\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.cntr : Ljava/lang/ThreadLocal;\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.set (Ljava/lang/Object;)V\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$dumpExported ()V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$dumpExported()V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ArgsShared.exported\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.getPerfLong (Ljava/lang/String;)J\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.str (J)Ljava/lang/String;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$incExported ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 2\n"
            + "MAXLOCALS = 0\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$incExported()V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ArgsShared.exported\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.getPerfLong (Ljava/lang/String;)J\n"
            + "LCONST_1\n"
            + "LADD\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X2\n"
            + "POP\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ArgsShared.exported\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.putPerfLong (JLjava/lang/String;)V\n"
            + "RETURN\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 0");

    resetClassLoader();

    transform("onmethod/leveled/ArgsShared");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$args(Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"/.*\\\\.OnMethodTest/\", method=\"args\", enableAt=@Lorg/openjdk/btrace/core/annotations/Level;(value=\">=1\"))\n"
            + "// annotable parameter count: 5 (visible)\n"
            + "@Lorg/openjdk/btrace/core/annotations/Self;() // parameter 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW java/lang/StringBuilder\n"
            + "DUP\n"
            + "INVOKESPECIAL java/lang/StringBuilder.<init> ()V\n"
            + "LDC \"this = \"\n"
            + "INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;\n"
            + "ALOAD 0\n"
            + "INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/Object;)Ljava/lang/StringBuilder;\n"
            + "INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "LDC \"args\"\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.cntr : Ljava/lang/ThreadLocal;\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n"
            + "CHECKCAST java/lang/Integer\n"
            + "INVOKEVIRTUAL java/lang/Integer.intValue ()I\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.str (I)Ljava/lang/String;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.cntr : Ljava/lang/ThreadLocal;\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n"
            + "CHECKCAST java/lang/Integer\n"
            + "INVOKEVIRTUAL java/lang/Integer.intValue ()I\n"
            + "ICONST_1\n"
            + "IADD\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.cntr : Ljava/lang/ThreadLocal;\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.set (Ljava/lang/Object;)V\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$dumpExported ()V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$dumpExported()V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ArgsShared.exported\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.getPerfLong (Ljava/lang/String;)J\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.str (J)Ljava/lang/String;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$incExported ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 2\n"
            + "MAXLOCALS = 0\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsShared$incExported()V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ArgsShared.exported\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.getPerfLong (Ljava/lang/String;)J\n"
            + "LCONST_1\n"
            + "LADD\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsShared.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X2\n"
            + "POP\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ArgsShared.exported\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.putPerfLong (JLjava/lang/String;)V\n"
            + "RETURN\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 0");
  }

  @Test
  public void methodEntryArgsSampledNoSampling() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsSampledNoSampling");
    checkTransformation(
        "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "ILOAD 6\n"
            + "IFEQ L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampledNoSampling$argsSampled (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME APPEND [I]\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampledNoSampling$argsNoSampling (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "LINENUMBER 44 L1\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L1 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L1 L2 1\n"
            + "LOCALVARIABLE b J L1 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L1 L2 4\n"
            + "LOCALVARIABLE d [I L1 L2 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArgsSampledNoSampling");
    checkTransformation(
        "ICONST_0\n"
            + "ISTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampledNoSampling.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLE L0\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "FRAME APPEND [I I]\n"
            + "ILOAD 7\n"
            + "IFLE L1\n"
            + "ILOAD 6\n"
            + "IFEQ L2\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampledNoSampling.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampledNoSampling$argsSampled (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampledNoSampling.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampledNoSampling$argsNoSampling (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L3\n"
            + "LINENUMBER 44 L3\n"
            + "FRAME SAME\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L3 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L3 L4 1\n"
            + "LOCALVARIABLE b J L3 L4 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L3 L4 4\n"
            + "LOCALVARIABLE d [I L3 L4 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsSampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsSampled");
    checkTransformation(
        "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "ILOAD 6\n"
            + "IFEQ L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME APPEND [I]\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryArgsSampledLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsSampled");
    checkTransformation(
        "ICONST_0\n"
            + "ISTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampled.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLE L0\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "FRAME APPEND [I I]\n"
            + "ILOAD 7\n"
            + "IFLE L1\n"
            + "ILOAD 6\n"
            + "IFEQ L2\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "LINENUMBER 44 L2\n"
            + "FRAME SAME\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L2 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L2 L3 1\n"
            + "LOCALVARIABLE b J L2 L3 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L2 L3 4\n"
            + "LOCALVARIABLE d [I L2 L3 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgs2Sampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/Args2Sampled");
    checkTransformation(
        "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "ILOAD 6\n"
            + "IFEQ L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args2Sampled$args2 (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME APPEND [I]\n"
            + "ILOAD 6\n"
            + "IFEQ L1\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args2Sampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "LINENUMBER 44 L1\n"
            + "FRAME SAME\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L1 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L1 L2 1\n"
            + "LOCALVARIABLE b J L1 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L1 L2 4\n"
            + "LOCALVARIABLE d [I L1 L2 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryArgs2SampledLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/Args2Sampled");
    checkTransformation(
        "ICONST_0\n"
            + "ISTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Args2Sampled.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLE L0\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "FRAME APPEND [I I]\n"
            + "ILOAD 7\n"
            + "IFLE L1\n"
            + "ILOAD 6\n"
            + "IFEQ L2\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Args2Sampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args2Sampled$args2 (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME\n"
            + "ILOAD 7\n"
            + "IFLE L3\n"
            + "ILOAD 6\n"
            + "IFEQ L4\n"
            + "L3\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Args2Sampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L4\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args2Sampled$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L4\n"
            + "LINENUMBER 44 L4\n"
            + "FRAME SAME\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L4 L5 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L4 L5 1\n"
            + "LOCALVARIABLE b J L4 L5 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L4 L5 4\n"
            + "LOCALVARIABLE d [I L4 L5 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsSampledAdaptive() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsSampledAdaptive");
    checkTransformation(
        "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitAdaptive (I)Z\n"
            + "ISTORE 6\n"
            + "ILOAD 6\n"
            + "IFEQ L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME APPEND [I]\n"
            + "ILOAD 6\n"
            + "IFEQ L1\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.updateEndTs (I)V\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryArgsSampledAdaptiveLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsSampledAdaptive");
    checkTransformation(
        "ICONST_0\n"
            + "ISTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampledAdaptive.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLE L0\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitAdaptive (I)Z\n"
            + "ISTORE 6\n"
            + "FRAME APPEND [I I]\n"
            + "ILOAD 7\n"
            + "IFLE L1\n"
            + "ILOAD 6\n"
            + "IFEQ L2\n"
            + "L1\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsSampledAdaptive.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "LINENUMBER 44 L2\n"
            + "FRAME SAME\n"
            + "ILOAD 6\n"
            + "IFEQ L3\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.updateEndTs (I)V\n"
            + "L3\n"
            + "FRAME SAME1 J\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L2 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L2 L4 1\n"
            + "LOCALVARIABLE b J L2 L4 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L2 L4 4\n"
            + "LOCALVARIABLE d [I L2 L4 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturn() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturn");
    checkTransformation(
        "DUP2\nLSTORE 6\nALOAD 0\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V");
  }

  @Test
  public void methodEntryArgsReturnVoid() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnVoid");
    checkTransformation(
        "ACONST_NULL\n"
            + "ASTORE 1\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnVoid$args (Ljava/lang/Object;Ljava/lang/Void;)V\n"
            + "MAXSTACK = 2\n"
            + "MAXLOCALS = 2\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnVoid$args(Ljava/lang/Object;Ljava/lang/Void;)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"/.*\\\\.OnMethodTest/\", method=\"noargs\", location=@Lorg/openjdk/btrace/core/annotations/Location;(value=Lorg/openjdk/btrace/core/annotations/Kind;.RETURN))\n"
            + "// annotable parameter count: 2 (visible)\n"
            + "@Lorg/openjdk/btrace/core/annotations/Self;() // parameter 0\n"
            + "@Lorg/openjdk/btrace/core/annotations/Return;() // parameter 1\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnVoid.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "LDC \"args\"\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnVoid.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnVoid.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnVoid.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 2");
  }

  @Test
  public void methodEntryArgsReturnLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsReturn");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "DUP2\n"
            + "LSTORE 6\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 10\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturnBoxed() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnBoxed");
    checkTransformation(
        "DUP2\n"
            + "LSTORE 6\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnBoxed$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturnAugmented() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnAugmented", true);
    checkTransformation(
        "LSTORE 6\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "LLOAD 6\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnAugmented$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[IJ)J\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturnAugmentedLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsReturnAugmented", true);
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnAugmented.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "LSTORE 6\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "LLOAD 6\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnAugmented$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[IJ)J\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturnAugmented1() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnAugmented1", true);
    checkTransformation(
        "LSTORE 6\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnAugmented1$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)J\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturnAugmented1Level() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsReturnAugmented1", true);
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnAugmented1.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "LSTORE 6\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnAugmented1$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)J\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodEntryArgsReturnSampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnSampled");
    checkTransformation(
        "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "ILOAD 6\n"
            + "IFEQ L1\n"
            + "DUP2\n"
            + "LSTORE 7\n"
            + "ALOAD 0\n"
            + "LLOAD 7\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnSampled$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I I] [J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 10\n"
            + "MAXLOCALS = 9");

    resetClassLoader();

    transform("onmethod/leveled/ArgsReturnSampled");
    checkTransformation(
        "ICONST_0\n"
            + "ISTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnSampled.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 7\n"
            + "IFLE L0\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 6\n"
            + "FRAME APPEND [I I]\n"
            + "ILOAD 7\n"
            + "IFLE L1\n"
            + "ILOAD 6\n"
            + "IFEQ L2\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsReturnSampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "ALOAD 0\n"
            + "LLOAD 8\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnSampled$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n"
            + "LOCALVARIABLE b J L0 L3 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n"
            + "LOCALVARIABLE d [I L0 L3 5\n"
            + "MAXSTACK = 10\n"
            + "MAXLOCALS = 10");
  }

  @Test
  public void methodEntryArgsDuration() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDuration");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "ALOAD 0\n"
            + "LLOAD 10\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 12");
  }

  @Test
  public void methodEntryArgsReturnTypeMatch() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnTypeMatch");
    checkTransformation(
        "DUP2\nLSTORE 6\nALOAD 0\nLLOAD 6\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsReturnTypeMatch$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V");
  }

  @Test
  public void methodEntryArgsReturnTypeNoMatch() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsReturnTypeNoMatch");
    checkTransformation("");
  }

  @Test
  public void methodEntryArgsDurationLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsDuration");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 10\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME APPEND [J J I]\n"
            + "ILOAD 10\n"
            + "IFLE L1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP2\n"
            + "LSTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 11\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n"
            + "LOCALVARIABLE b J L0 L3 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n"
            + "LOCALVARIABLE d [I L0 L3 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 13");
  }

  @Test
  public void methodEntryArgsDurationMultiReturn() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDurationMultiReturn");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "ALOAD 0\n"
            + "LLOAD 10\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME APPEND [J J]\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 12\n"
            + "ALOAD 0\n"
            + "LLOAD 12\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 14\n"
            + "ALOAD 0\n"
            + "LLOAD 14\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 16");
  }

  @Test
  public void methodEntryArgsDurationMultiReturnLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsDurationMultiReturn");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationMultiReturn.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 10\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME APPEND [J J I]\n"
            + "ILOAD 10\n"
            + "IFLE L3\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L3\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationMultiReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L4\n"
            + "DUP2\n"
            + "LSTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 11\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L4\n"
            + "FRAME SAME1 J\n"
            + "IFLE L5\n"
            + "L6\n"
            + "LINENUMBER 128 L6\n"
            + "ILOAD 10\n"
            + "IFLE L7\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L7\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationMultiReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L8\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "ALOAD 0\n"
            + "LLOAD 13\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L8\n"
            + "FRAME SAME1 J\n"
            + "L5\n"
            + "LINENUMBER 132 L5\n"
            + "L9\n"
            + "LINENUMBER 133 L9\n"
            + "ILOAD 10\n"
            + "IFLE L10\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L10\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationMultiReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L11\n"
            + "DUP2\n"
            + "LSTORE 15\n"
            + "ALOAD 0\n"
            + "LLOAD 15\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationMultiReturn$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L11\n"
            + "FRAME SAME1 J\n"
            + "L12\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L12 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L12 1\n"
            + "LOCALVARIABLE b J L0 L12 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L12 4\n"
            + "LOCALVARIABLE d [I L0 L12 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 17");
  }

  @Test
  public void methodEntryArgsDurationSampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDurationSampled");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "L2I\n"
            + "ISTORE 10\n"
            + "ILOAD 10\n"
            + "IFEQ L1\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 11\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationSampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I] [J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 13");
  }

  @Test
  public void methodEntryArgsDurationSampledLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsDurationSampled");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "ICONST_0\n"
            + "ISTORE 10\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationSampled.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 11\n"
            + "IFLE L0\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "L2I\n"
            + "ISTORE 10\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I I] []\n"
            + "ILOAD 11\n"
            + "IFLE L1\n"
            + "ILOAD 10\n"
            + "IFEQ L2\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationSampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP2\n"
            + "LSTORE 12\n"
            + "ALOAD 0\n"
            + "LLOAD 12\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationSampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n"
            + "LOCALVARIABLE b J L0 L3 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n"
            + "LOCALVARIABLE d [I L0 L3 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 14");
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
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 2\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 4\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 4\n"
            + "LSUB\n"
            + "LSTORE 2\n"
            + "ALOAD 0\n"
            + "LLOAD 2\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationConstructor$args (Ljava/lang/Object;JLjava/lang/String;)V\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 6");
  }

  @Test
  public void methodEntryArgsDurationConstructorLevel() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/leveled/ArgsDurationConstructor");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 2\n"
            + "LCONST_0\n"
            + "LSTORE 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationConstructor.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 6\n"
            + "IFLE L1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 4\n"
            + "L1\n"
            + "FRAME APPEND [J J I]\n"
            + "ILOAD 6\n"
            + "IFLE L2\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 4\n"
            + "LSUB\n"
            + "LSTORE 2\n"
            + "L2\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationConstructor.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LLOAD 2\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationConstructor$args (Ljava/lang/Object;JLjava/lang/String;)V\n"
            + "L3\n"
            + "FRAME SAME\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 7");
  }

  @Test
  // check for multiple timestamps
  public void methodEntryArgsDuration2() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDuration2");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "ALOAD 0\n"
            + "LLOAD 10\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "DUP2\n"
            + "LSTORE 12\n"
            + "ALOAD 0\n"
            + "LLOAD 12\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 14");

    resetClassLoader();

    transform("onmethod/leveled/ArgsDuration2");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 10\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME APPEND [J J I]\n"
            + "ILOAD 10\n"
            + "IFLE L1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP2\n"
            + "LSTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 11\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "ALOAD 0\n"
            + "LLOAD 13\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L3\n"
            + "FRAME SAME1 J\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n"
            + "LOCALVARIABLE b J L0 L4 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L4 4\n"
            + "LOCALVARIABLE d [I L0 L4 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 15");
  }

  @Test
  // check for multiple timestamps
  public void methodEntryArgsDuration2Sampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDuration2Sampled");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "L2I\n"
            + "ISTORE 10\n"
            + "ILOAD 10\n"
            + "IFEQ L1\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP2\n"
            + "LSTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 11\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Sampled$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I] [J]\n"
            + "ILOAD 10\n"
            + "IFEQ L2\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "ALOAD 0\n"
            + "LLOAD 13\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Sampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n"
            + "LOCALVARIABLE b J L0 L3 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n"
            + "LOCALVARIABLE d [I L0 L3 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 15");

    resetClassLoader();

    transform("onmethod/leveled/ArgsDuration2Sampled");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 6\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "L2I\n"
            + "ISTORE 10\n"
            + "ILOAD 10\n"
            + "IFEQ L1\n"
            + "ICONST_1\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2Sampled.$btrace$$level : I\n"
            + "ICONST_5\n"
            + "IF_ICMPGT L1\n"
            + "DUP2\n"
            + "LSTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 11\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Sampled$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I] [J]\n"
            + "ILOAD 10\n"
            + "IFEQ L2\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2Sampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "ALOAD 0\n"
            + "LLOAD 13\n"
            + "LLOAD 6\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Sampled$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n"
            + "LOCALVARIABLE b J L0 L3 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L3 4\n"
            + "LOCALVARIABLE d [I L0 L3 5\n"
            + "MAXSTACK = 12\n"
            + "MAXLOCALS = 15");
  }

  @Test
  public void methodEntryArgsDurationErr() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDurationErr");

    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "LCONST_0\n"
            + "LSTORE 6\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J] [java/lang/Throwable]\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP\n"
            + "ASTORE 10\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 10\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 11");

    resetClassLoader();

    transform("onmethod/leveled/ArgsDurationErr");

    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "LCONST_0\n"
            + "LSTORE 6\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationErr.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 10\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME APPEND [J J I]\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ILOAD 10\n"
            + "IFLE L2\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L2\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 11\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationErr.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 11\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "L3\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 12");
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
    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L2\n"
            + "LINENUMBER 39 L2\n"
            + "LCONST_0\n"
            + "LSTORE 1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 3\n"
            + "L0\n"
            + "FRAME FULL [U J J] [java/lang/Throwable]\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 3\n"
            + "LSUB\n"
            + "LSTORE 1\n"
            + "DUP\n"
            + "ASTORE 5\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 6\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L2\n"
            + "LINENUMBER 40 L2\n"
            + "LCONST_0\n"
            + "LSTORE 2\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 4\n"
            + "L0\n"
            + "FRAME FULL [U java/lang/String J J] [java/lang/Throwable]\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 4\n"
            + "LSUB\n"
            + "LSTORE 2\n"
            + "DUP\n"
            + "ASTORE 6\n"
            + "ALOAD 0\n"
            + "LLOAD 2\n"
            + "ALOAD 6\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L2 L1 1\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/ArgsDurationConstructorErr");
    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L2\n"
            + "LINENUMBER 39 L2\n"
            + "LCONST_0\n"
            + "LSTORE 1\n"
            + "LCONST_0\n"
            + "LSTORE 3\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationConstructorErr.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 5\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 3\n"
            + "L0\n"
            + "FRAME APPEND [J J I]\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ILOAD 5\n"
            + "IFLE L3\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 3\n"
            + "LSUB\n"
            + "LSTORE 1\n"
            + "L3\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationConstructorErr.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L4\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 6\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "L4\n"
            + "FRAME FULL [U J J I java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 7\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L2\n"
            + "LINENUMBER 40 L2\n"
            + "LCONST_0\n"
            + "LSTORE 2\n"
            + "LCONST_0\n"
            + "LSTORE 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationConstructorErr.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 6\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 4\n"
            + "L0\n"
            + "FRAME APPEND [J J I]\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ILOAD 6\n"
            + "IFLE L3\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 4\n"
            + "LSUB\n"
            + "LSTORE 2\n"
            + "L3\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 7\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDurationConstructorErr.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L4\n"
            + "ALOAD 0\n"
            + "LLOAD 2\n"
            + "ALOAD 7\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDurationConstructorErr$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "L4\n"
            + "FRAME FULL [U java/lang/String J J I java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L2 L1 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L2 L1 1\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 8");
  }

  @Test
  // check for multiple timestamps
  public void methodEntryArgsDuration2Err() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsDuration2Err");
    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n"
            + "LCONST_0\n"
            + "LSTORE 6\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J] [java/lang/Throwable]\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP\n"
            + "ASTORE 10\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 10\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Err$args2 (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "L2\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "DUP\n"
            + "ASTORE 11\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 11\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Err$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "ATHROW\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 12");

    resetClassLoader();

    transform("onmethod/leveled/ArgsDuration2Err");
    checkTransformation(
        "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n"
            + "LCONST_0\n"
            + "LSTORE 6\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2Err.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 10\n"
            + "IFLE L0\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 8\n"
            + "FRAME APPEND [J J I]\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "ILOAD 10\n"
            + "IFLE L3\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L3\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 11\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2Err.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L4\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 11\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Err$args2 (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "L4\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "L2\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I] [java/lang/Throwable]\n"
            + "ILOAD 10\n"
            + "IFLE L5\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 8\n"
            + "LSUB\n"
            + "LSTORE 6\n"
            + "L5\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "DUP\n"
            + "ASTORE 12\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ArgsDuration2Err.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L6\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "ALOAD 12\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsDuration2Err$args (Ljava/lang/Object;JLjava/lang/Throwable;)V\n"
            + "L6\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J [Ljava/lang/String; [I J J I T java/lang/Throwable] [java/lang/Throwable]\n"
            + "ATHROW\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 13");
  }

  @Test
  public void methodEntryAnytypeArgs() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/AnytypeArgs");
    checkTransformation(
        "ALOAD 0\n"
            + "ICONST_4\n"
            + "ANEWARRAY java/lang/Object\n"
            + "DUP\n"
            + "ICONST_0\n"
            + "ALOAD 1\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_1\n"
            + "LLOAD 2\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_2\n"
            + "ALOAD 4\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_3\n"
            + "ALOAD 5\n"
            + "AASTORE\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V\n"
            + "MAXSTACK = 6");

    resetClassLoader();

    transform("onmethod/leveled/AnytypeArgs");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/AnytypeArgs.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "ICONST_4\n"
            + "ANEWARRAY java/lang/Object\n"
            + "DUP\n"
            + "ICONST_0\n"
            + "ALOAD 1\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_1\n"
            + "LLOAD 2\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_2\n"
            + "ALOAD 4\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_3\n"
            + "ALOAD 5\n"
            + "AASTORE\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 6");
  }

  @Test
  public void methodEntryAnytypeArgsNoSelf() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/AnytypeArgsNoSelf");
    checkTransformation(
        "ICONST_4\n"
            + "ANEWARRAY java/lang/Object\n"
            + "DUP\n"
            + "ICONST_0\n"
            + "ALOAD 1\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_1\n"
            + "LLOAD 2\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_2\n"
            + "ALOAD 4\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_3\n"
            + "ALOAD 5\n"
            + "AASTORE\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V\n"
            + "MAXSTACK = 5");

    resetClassLoader();

    transform("onmethod/leveled/AnytypeArgsNoSelf");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/AnytypeArgsNoSelf.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ICONST_4\n"
            + "ANEWARRAY java/lang/Object\n"
            + "DUP\n"
            + "ICONST_0\n"
            + "ALOAD 1\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_1\n"
            + "LLOAD 2\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_2\n"
            + "ALOAD 4\n"
            + "AASTORE\n"
            + "DUP\n"
            + "ICONST_3\n"
            + "ALOAD 5\n"
            + "AASTORE\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 5");
  }

  @Test
  public void methodEntryStaticArgs() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticArgs");

    checkTransformation(
        "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticArgs$args (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5");

    resetClassLoader();

    transform("onmethod/leveled/StaticArgs");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/StaticArgs.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticArgs$args (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 5");
  }

  @Test
  public void methodEntryStaticArgsReturn() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticArgsReturn");

    checkTransformation(
        "DUP2\n"
            + "LSTORE 5\n"
            + "ALOAD 0\n"
            + "LLOAD 5\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticArgsReturn$args (Ljava/lang/String;JJ[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/StaticArgsReturn");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/StaticArgsReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "DUP2\n"
            + "LSTORE 5\n"
            + "ALOAD 0\n"
            + "LLOAD 5\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticArgsReturn$args (Ljava/lang/String;JJ[Ljava/lang/String;[I)V\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "L2\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 0\n"
            + "LOCALVARIABLE b J L0 L2 1\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 3\n"
            + "LOCALVARIABLE d [I L0 L2 4\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryStaticArgsSelf() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticArgsSelf");

    checkTransformation(
        "ACONST_NULL\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticArgsSelf$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 6");
  }

  @Test
  public void methodEntryStaticNoArgs() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticNoArgs");

    checkTransformation(
        "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticNoArgs$argsEmpty ()V\n");

    resetClassLoader();

    transform("onmethod/leveled/StaticNoArgs");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/StaticNoArgs.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticNoArgs$argsEmpty ()V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 2");
  }

  @Test
  public void methodEntryStaticNoArgsSelf() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticNoArgsSelf");

    checkTransformation(
        "ACONST_NULL\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticNoArgsSelf$argsEmpty (Ljava/lang/Object;)V\n"
            + "MAXSTACK = 1");
  }

  @Test
  public void methodCall() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCall");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCall$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 8");

    resetClassLoader();

    transform("onmethod/leveled/MethodCall");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCall.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCall$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME APPEND [J java/lang/String resources/OnMethodTest]\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void methodCallSampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallSampled");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 8\n"
            + "ILOAD 8\n"
            + "IFEQ L1\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallSampled$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest I] []\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 9");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallSampled");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ICONST_0\n"
            + "ISTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallSampled.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 9\n"
            + "IFLE L1\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hit (I)Z\n"
            + "ISTORE 8\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest I I] []\n"
            + "ILOAD 9\n"
            + "IFLE L2\n"
            + "ILOAD 8\n"
            + "IFEQ L3\n"
            + "L2\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallSampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallSampled$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L3\n"
            + "FRAME SAME\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n"
            + "LOCALVARIABLE b J L0 L4 2\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 10");
  }

  @Test
  public void methodCallSampledAdaptive() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallSampledAdaptive");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitAdaptive (I)Z\n"
            + "ISTORE 8\n"
            + "ILOAD 8\n"
            + "IFEQ L1\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest I] []\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 8\n"
            + "IFEQ L2\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.updateEndTs (I)V\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "L3\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L3 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 1\n"
            + "LOCALVARIABLE b J L0 L3 2\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 9");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallSampledAdaptive");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ICONST_0\n"
            + "ISTORE 8\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallSampledAdaptive.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 9\n"
            + "IFLE L1\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitAdaptive (I)Z\n"
            + "ISTORE 8\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest I I] []\n"
            + "ILOAD 9\n"
            + "IFLE L2\n"
            + "ILOAD 8\n"
            + "IFEQ L3\n"
            + "L2\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallSampledAdaptive.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallSampledAdaptive$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L3\n"
            + "FRAME SAME\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 8\n"
            + "IFEQ L4\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.updateEndTs (I)V\n"
            + "L4\n"
            + "FRAME SAME1 J\n"
            + "L5\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L5 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L5 1\n"
            + "LOCALVARIABLE b J L0 L5 2\n"
            + "MAXSTACK = 8\n"
            + "MAXLOCALS = 10");
  }

  @Test
  public void methodCallNoArgs() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallNoArgs");

    checkTransformation(
        "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallNoArgs$args ()V");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallNoArgs");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallNoArgs.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallNoArgs$args ()V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J] [resources/OnMethodTest java/lang/String J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXSTACK = 6");
  }

  @Test
  public void methodCallReturn() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallReturn");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallReturn$args (JLjava/lang/String;J)V\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 10");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallReturn");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "DUP2\n"
            + "LSTORE 8\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallReturn$args (JLjava/lang/String;J)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest] [J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXSTACK = 7\n"
            + "MAXLOCALS = 10");
  }

  @Test
  public void methodCallReturnAugmented() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallReturnAugmented", true);

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LSTORE 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LLOAD 8\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallReturnAugmented$args (Ljava/lang/String;JJ)J\n"
            + "MAXLOCALS = 10");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallReturnAugmented", true);

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallReturnAugmented.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "LSTORE 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LLOAD 8\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallReturnAugmented$args (Ljava/lang/String;JJ)J\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest] [J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXLOCALS = 10");
  }

  @Test
  public void methodCallReturnAugmented1() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallReturnAugmented1", true);

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LSTORE 8\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallReturnAugmented1$args (JLjava/lang/String;J)J\n"
            + "MAXLOCALS = 10");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallReturnAugmented1", true);

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallReturnAugmented1.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "LSTORE 8\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallReturnAugmented1$args (JLjava/lang/String;J)J\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest] [J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXLOCALS = 10");
  }

  @Test
  public void methodCallDuration() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallDuration");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 10\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 10\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "DUP2\n"
            + "LSTORE 12\n"
            + "LLOAD 12\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDuration$args (JJLjava/lang/String;J)V\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 14");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallDuration");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "LCONST_0\n"
            + "LSTORE 10\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDuration.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 12\n"
            + "IFLE L1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 10\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I] []\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 12\n"
            + "IFLE L2\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 10\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDuration.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "LLOAD 13\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDuration$args (JJLjava/lang/String;J)V\n"
            + "L3\n"
            + "FRAME SAME1 J\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n"
            + "LOCALVARIABLE b J L0 L4 2\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 15");
  }

  @Test
  public void methodCallDurationSampled() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallDurationSampled");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "L2I\n"
            + "ISTORE 12\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 12\n"
            + "IFEQ L1\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 10\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "LLOAD 13\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDurationSampled$args (JJLjava/lang/String;J)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I] [J]\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 15");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallDurationSampled");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "LCONST_0\n"
            + "LSTORE 10\n"
            + "ICONST_0\n"
            + "ISTORE 12\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDurationSampled.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 13\n"
            + "IFLE L1\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "L2I\n"
            + "ISTORE 12\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I I] []\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 13\n"
            + "IFLE L2\n"
            + "ILOAD 12\n"
            + "IFEQ L3\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 10\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDurationSampled.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "DUP2\n"
            + "LSTORE 14\n"
            + "LLOAD 14\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDurationSampled$args (JJLjava/lang/String;J)V\n"
            + "L3\n"
            + "FRAME SAME1 J\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n"
            + "LOCALVARIABLE b J L0 L4 2\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 16");
  }

  @Test
  public void methodCallDurationSampledMulti() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallDurationSampledMulti");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "L2I\n"
            + "ISTORE 12\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 12\n"
            + "IFEQ L1\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 10\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "DUP2\n"
            + "LSTORE 13\n"
            + "LLOAD 13\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I] [J]\n"
            + "LSTORE 15\n"
            + "ASTORE 17\n"
            + "ICONST_3\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 18\n"
            + "L2I\n"
            + "ISTORE 20\n"
            + "ALOAD 17\n"
            + "LLOAD 15\n"
            + "ILOAD 20\n"
            + "IFEQ L2\n"
            + "ICONST_3\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 18\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "DUP2\n"
            + "LSTORE 21\n"
            + "LLOAD 21\n"
            + "LLOAD 8\n"
            + "ALOAD 17\n"
            + "LLOAD 15\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n"
            + "L2\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I T T J java/lang/String J I] [J J]\n"
            + "LSTORE 23\n"
            + "L3\n"
            + "LINENUMBER 115 L3\n"
            + "LLOAD 23\n"
            + "L4\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L4 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L4 1\n"
            + "LOCALVARIABLE b J L0 L4 2\n"
            + "LOCALVARIABLE i J L3 L4 23\n"
            + "MAXSTACK = 11\n"
            + "MAXLOCALS = 25");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallDurationSampledMulti");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "LCONST_0\n"
            + "LSTORE 8\n"
            + "LCONST_0\n"
            + "LSTORE 10\n"
            + "ICONST_0\n"
            + "ISTORE 12\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDurationSampledMulti.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 13\n"
            + "IFLE L1\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 10\n"
            + "L2I\n"
            + "ISTORE 12\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I I] []\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ILOAD 13\n"
            + "IFLE L2\n"
            + "ILOAD 12\n"
            + "IFEQ L3\n"
            + "ICONST_2\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 10\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "L2\n"
            + "FRAME SAME1 J\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDurationSampledMulti.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L3\n"
            + "DUP2\n"
            + "LSTORE 14\n"
            + "LLOAD 14\n"
            + "LLOAD 8\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n"
            + "L3\n"
            + "FRAME SAME1 J\n"
            + "LSTORE 16\n"
            + "ASTORE 18\n"
            + "LCONST_0\n"
            + "LSTORE 19\n"
            + "ICONST_0\n"
            + "ISTORE 21\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDurationSampledMulti.$btrace$$level : I\n"
            + "DUP\n"
            + "ISTORE 22\n"
            + "IFLE L4\n"
            + "ICONST_3\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.hitTimed (I)J\n"
            + "DUP2\n"
            + "LSTORE 19\n"
            + "L2I\n"
            + "ISTORE 21\n"
            + "L4\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I I T T J java/lang/String J I I] [J]\n"
            + "ALOAD 18\n"
            + "LLOAD 16\n"
            + "ILOAD 22\n"
            + "IFLE L5\n"
            + "ILOAD 21\n"
            + "IFEQ L6\n"
            + "ICONST_3\n"
            + "INVOKESTATIC org/openjdk/btrace/instr/MethodTracker.getEndTs (I)J\n"
            + "LLOAD 19\n"
            + "LSUB\n"
            + "LSTORE 8\n"
            + "L5\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I I T T J java/lang/String J I I] [J J]\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallDurationSampledMulti.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L6\n"
            + "DUP2\n"
            + "LSTORE 23\n"
            + "LLOAD 23\n"
            + "LLOAD 8\n"
            + "ALOAD 18\n"
            + "LLOAD 16\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDurationSampledMulti$args (JJLjava/lang/String;J)V\n"
            + "L6\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String resources/OnMethodTest J J I I T T J java/lang/String J I I] [J J]\n"
            + "LSTORE 25\n"
            + "L7\n"
            + "LINENUMBER 115 L7\n"
            + "LLOAD 25\n"
            + "L8\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L8 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L8 1\n"
            + "LOCALVARIABLE b J L0 L8 2\n"
            + "LOCALVARIABLE i J L7 L8 25\n"
            + "MAXSTACK = 11\n"
            + "MAXLOCALS = 27");
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
  //                + "INVOKESTATIC
  // resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallDuration$args
  // (JJLjava/lang/String;J)V\n"
  //                + "LLOAD 10\nMAXSTACK = 7\nMAXLOCALS = 14\n");
  //    }

  @Test
  public void methodCallStatic() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/MethodCallStatic");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ACONST_NULL\n"
            + "LDC \"static long resources.OnMethodTest#callTargetStatic(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallStatic$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "MAXSTACK = 10\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/MethodCallStatic");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/MethodCallStatic.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LDC \"static long resources.OnMethodTest#callTargetStatic(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevel\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$MethodCallStatic$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L1\n"
            + "FRAME FULL [resources/OnMethodTest java/lang/String J J java/lang/String] [J]\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void staticMethodCall() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticMethodCall");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevelStatic\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticMethodCall$args (Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 8");

    resetClassLoader();

    transform("onmethod/leveled/StaticMethodCall");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/StaticMethodCall.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "ALOAD 7\n"
            + "LDC \"special long resources.OnMethodTest#callTarget(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevelStatic\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticMethodCall$args (Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L2\n"
            + "FRAME FULL [java/lang/String J resources/OnMethodTest J java/lang/String resources/OnMethodTest] [J]\n"
            + "ALOAD 7\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "L3\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 0\n"
            + "LOCALVARIABLE b J L0 L3 1\n"
            + "LOCALVARIABLE instance Lresources/OnMethodTest; L1 L3 3\n"
            + "MAXSTACK = 9\n"
            + "MAXLOCALS = 8");
  }

  @Test
  public void staticMethodCallStatic() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/StaticMethodCallStatic");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LDC \"static long resources.OnMethodTest#callTargetStatic(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevelStatic\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticMethodCallStatic$args (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "MAXLOCALS = 7");

    resetClassLoader();

    transform("onmethod/leveled/StaticMethodCallStatic");

    checkTransformation(
        "LSTORE 4\n"
            + "ASTORE 6\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/StaticMethodCallStatic.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L2\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "LDC \"static long resources.OnMethodTest#callTargetStatic(java.lang.String, long)\"\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "LDC \"callTopLevelStatic\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$StaticMethodCallStatic$args (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "L2\n"
            + "FRAME APPEND [resources/OnMethodTest J java/lang/String]\n"
            + "ALOAD 6\n"
            + "LLOAD 4\n"
            + "L3\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L3 0\n"
            + "LOCALVARIABLE b J L0 L3 1\n"
            + "LOCALVARIABLE instance Lresources/OnMethodTest; L1 L3 3\n"
            + "MAXLOCALS = 7");
  }

  @Test
  public void methodEntryNoArgsEntryReturn() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NoArgsEntryReturn");

    checkTransformation(
        "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgsEntryReturn$argsEmptyEntry (Ljava/lang/Object;)V\n"
            + "DUP2\n"
            + "LSTORE 6\n"
            + "ALOAD 0\n"
            + "LLOAD 6\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgsEntryReturn$argsEmptyReturn (Ljava/lang/Object;J)V\n"
            + "MAXSTACK = 5\n"
            + "MAXLOCALS = 8");

    resetClassLoader();

    transform("onmethod/leveled/NoArgsEntryReturn");

    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NoArgsEntryReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgsEntryReturn$argsEmptyEntry (Ljava/lang/Object;)V\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/NoArgsEntryReturn.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L1\n"
            + "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgsEntryReturn$argsEmptyReturn (Ljava/lang/Object;)V\n"
            + "L1\n"
            + "FRAME SAME1 J\n"
            + "L2\n"
            + "LOCALVARIABLE this Lresources/OnMethodTest; L0 L2 0\n"
            + "LOCALVARIABLE a Ljava/lang/String; L0 L2 1\n"
            + "LOCALVARIABLE b J L0 L2 2\n"
            + "LOCALVARIABLE c [Ljava/lang/String; L0 L2 4\n"
            + "LOCALVARIABLE d [I L0 L2 5\n"
            + "MAXSTACK = 4");
  }

  @Test
  public void methodEntryNoArgsEntryReturnNoCapture() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/NoArgsEntryReturnNoCapture");

    checkTransformation(
        "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgsEntryReturnNoCapture$argsEmptyEntry (Ljava/lang/Object;)V\n"
            + "ALOAD 0\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$NoArgsEntryReturnNoCapture$argsEmptyReturn (Ljava/lang/Object;)V");
  }

  @Test
  public void servicesTest() throws Exception {
    // a sanity test for the runtime verifier accepting the services method calls
    loadTargetClass("OnMethodTest");
    transform("ServicesTest");

    checkTransformation(
        "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSimpleService ()V\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testFieldInjection (Ljava/lang/String;)V\n"
            + "MAXSTACK = 1\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testRuntimeService (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSingletonService (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testRuntimeService(Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummyRuntimeService\n"
            + "DUP\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESPECIAL services/DummyRuntimeService.<init> (Lorg/openjdk/btrace/services/api/RuntimeContext;)V\n"
            + "ASTORE 5\n"
            + "ALOAD 5\n"
            + "BIPUSH 10\n"
            + "LDC \"hello\"\n"
            + "INVOKEVIRTUAL services/DummyRuntimeService.doit (ILjava/lang/String;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSimpleService()V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"noargs\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummySimpleService\n"
            + "DUP\n"
            + "INVOKESPECIAL services/DummySimpleService.<init> ()V\n"
            + "ASTORE 0\n"
            + "ALOAD 0\n"
            + "LDC \"hello\"\n"
            + "BIPUSH 10\n"
            + "INVOKEVIRTUAL services/DummySimpleService.doit (Ljava/lang/String;I)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 1\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSingletonService(Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args$static\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummySimpleService\n"
            + "DUP\n"
            + "LDC \"getInstance\"\n"
            + "INVOKESPECIAL services/DummySimpleService.<init> (Ljava/lang/String;)V\n"
            + "ASTORE 5\n"
            + "ALOAD 5\n"
            + "LDC \"hello\"\n"
            + "BIPUSH 10\n"
            + "INVOKEVIRTUAL services/DummySimpleService.doit (Ljava/lang/String;I)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testFieldInjection(Ljava/lang/String;)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"noargs$static\")\n"
            + "// annotable parameter count: 1 (visible)\n"
            + "@Lorg/openjdk/btrace/core/annotations/ProbeClassName;() // parameter 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummyRuntimeService\n"
            + "DUP\n"
            + "DUP\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESPECIAL services/DummyRuntimeService.<init> (Lorg/openjdk/btrace/services/api/RuntimeContext;)V\n"
            + "ASTORE 1\n"
            + "BIPUSH 10\n"
            + "LDC \"hey\"\n"
            + "INVOKEVIRTUAL services/DummyRuntimeService.doit (ILjava/lang/String;)V\n"
            + "ALOAD 1\n"
            + "BIPUSH 20\n"
            + "LDC \"ho\"\n"
            + "INVOKEVIRTUAL services/DummyRuntimeService.doit (ILjava/lang/String;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 2");
  }

  @Test
  public void statsdServiceTest() throws Exception {
    // a sanity test for the runtime verifier accepting the services method calls
    loadTargetClass("OnMethodTest");
    transform("ServicesTest");

    checkTransformation(
        "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSimpleService ()V\n"
            + "LDC \"resources.OnMethodTest\"\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testFieldInjection (Ljava/lang/String;)V\n"
            + "MAXSTACK = 1\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testRuntimeService (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "ALOAD 3\n"
            + "ALOAD 4\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSingletonService (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testRuntimeService(Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummyRuntimeService\n"
            + "DUP\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESPECIAL services/DummyRuntimeService.<init> (Lorg/openjdk/btrace/services/api/RuntimeContext;)V\n"
            + "ASTORE 5\n"
            + "ALOAD 5\n"
            + "BIPUSH 10\n"
            + "LDC \"hello\"\n"
            + "INVOKEVIRTUAL services/DummyRuntimeService.doit (ILjava/lang/String;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSimpleService()V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"noargs\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummySimpleService\n"
            + "DUP\n"
            + "INVOKESPECIAL services/DummySimpleService.<init> ()V\n"
            + "ASTORE 0\n"
            + "ALOAD 0\n"
            + "LDC \"hello\"\n"
            + "BIPUSH 10\n"
            + "INVOKEVIRTUAL services/DummySimpleService.doit (Ljava/lang/String;I)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 1\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testSingletonService(Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args$static\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummySimpleService\n"
            + "DUP\n"
            + "LDC \"getInstance\"\n"
            + "INVOKESPECIAL services/DummySimpleService.<init> (Ljava/lang/String;)V\n"
            + "ASTORE 5\n"
            + "ALOAD 5\n"
            + "LDC \"hello\"\n"
            + "BIPUSH 10\n"
            + "INVOKEVIRTUAL services/DummySimpleService.doit (Ljava/lang/String;I)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 6\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ServicesTest$testFieldInjection(Ljava/lang/String;)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"noargs$static\")\n"
            + "// annotable parameter count: 1 (visible)\n"
            + "@Lorg/openjdk/btrace/core/annotations/ProbeClassName;() // parameter 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "NEW services/DummyRuntimeService\n"
            + "DUP\n"
            + "DUP\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESPECIAL services/DummyRuntimeService.<init> (Lorg/openjdk/btrace/services/api/RuntimeContext;)V\n"
            + "ASTORE 1\n"
            + "BIPUSH 10\n"
            + "LDC \"hey\"\n"
            + "INVOKEVIRTUAL services/DummyRuntimeService.doit (ILjava/lang/String;)V\n"
            + "ALOAD 1\n"
            + "BIPUSH 20\n"
            + "LDC \"ho\"\n"
            + "INVOKEVIRTUAL services/DummyRuntimeService.doit (ILjava/lang/String;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ServicesTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 4\n"
            + "MAXLOCALS = 2");
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

    Files.write(Paths.get(System.getProperty("java.io.tmpdir"), "TLSTest.class"), traceCode);

    checkTrace(
        "public static Lorg/openjdk/btrace/core/BTraceRuntime; runtime\n"
            + "// access flags 0x19\n"
            + "// signature Ljava/lang/ThreadLocal<Ljava/util/Deque;>;\n"
            + "// declaration: java.lang.ThreadLocal<java.util.Deque>\n"
            + "public final static Ljava/lang/ThreadLocal; entryTimes\n"
            + "// access flags 0x19\n"
            + "// signature Ljava/lang/ThreadLocal<Ljava/lang/String;>;\n"
            + "// declaration: java.lang.ThreadLocal<java.lang.String>\n"
            + "public final static Ljava/lang/ThreadLocal; name\n"
            + "// access flags 0x19\n"
            + "// signature Ljava/lang/ThreadLocal<Ljava/lang/Integer;>;\n"
            + "// declaration: java.lang.ThreadLocal<java.lang.Integer>\n"
            + "public final static Ljava/lang/ThreadLocal; x\n"
            + "// access flags 0x19\n"
            + "// signature Ljava/lang/ThreadLocal<Ljava/lang/Double;>;\n"
            + "// declaration: java.lang.ThreadLocal<java.lang.Double>\n"
            + "public final static Ljava/lang/ThreadLocal; y\n"
            + "// signature Ljava/lang/ThreadLocal<Ljava/lang/Long;>;\n"
            + "// declaration: java.lang.ThreadLocal<java.lang.Long>\n"
            + "public final static Ljava/lang/ThreadLocal; z\n"
            + "\n"
            + "// access flags 0x49\n"
            + "public static volatile I $btrace$$level = 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC traces/TLSTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "GETSTATIC traces/TLSTest.entryTimes : Ljava/lang/ThreadLocal;\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n"
            + "CHECKCAST java/util/Deque\n"
            + "GETSTATIC traces/TLSTest.name : Ljava/lang/ThreadLocal;\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.set (Ljava/lang/Object;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L0\n"
            + "LDC Ltraces/TLSTest;.class\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.forClass (Ljava/lang/Class;)Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "PUTSTATIC traces/TLSTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "GETSTATIC traces/TLSTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L2\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN\n"
            + "L2\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n"
            + "PUTSTATIC traces/TLSTest.entryTimes : Ljava/lang/ThreadLocal;\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n"
            + "PUTSTATIC traces/TLSTest.x : Ljava/lang/ThreadLocal;\n"
            + "ACONST_NULL\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n"
            + "PUTSTATIC traces/TLSTest.name : Ljava/lang/ThreadLocal;\n"
            + "DCONST_0\n"
            + "INVOKESTATIC java/lang/Double.valueOf (D)Ljava/lang/Double;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n"
            + "PUTSTATIC traces/TLSTest.y : Ljava/lang/ThreadLocal;\n"
            + "LDC 10\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newThreadLocal (Ljava/lang/Object;)Ljava/lang/ThreadLocal;\n"
            + "PUTSTATIC traces/TLSTest.z : Ljava/lang/ThreadLocal;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.start ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "MAXSTACK = 2");

    checkTransformation(
        "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$TLSTest$testArgs (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$TLSTest$testArgs(Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/TLSTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/TLSTest.entryTimes : Ljava/lang/ThreadLocal;\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.get ()Ljava/lang/Object;\n"
            + "CHECKCAST java/util/Deque\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.push (Ljava/util/Deque;Ljava/lang/Object;)V\n"
            + "ALOAD 0\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/TLSTest.name : Ljava/lang/ThreadLocal;\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL java/lang/ThreadLocal.set (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/TLSTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/TLSTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/TLSTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 5");
  }

  @Test
  public void exportTest() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("ExportTest");

    checkTrace(
        "public static Lorg/openjdk/btrace/core/BTraceRuntime; runtime\n"
            + "// access flags 0x19\n"
            + "public final static Ljava/util/Deque; entryTimes\n"
            + "// access flags 0x19\n"
            + "public final static Ljava/lang/String; name\n"
            + "// access flags 0x19\n"
            + "public final static I x\n"
            + "// access flags 0x19\n"
            + "public final static D y\n"
            + "public final static J z\n"
            + "\n"
            + "// access flags 0x49\n"
            + "public static volatile I $btrace$$level = 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC traces/ExportTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "ACONST_NULL\n"
            + "LDC \"btrace.traces/ExportTest.name\"\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.putPerfString (Ljava/lang/String;Ljava/lang/String;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L0\n"
            + "LDC Ltraces/ExportTest;.class\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.forClass (Ljava/lang/Class;)Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "PUTSTATIC traces/ExportTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "GETSTATIC traces/ExportTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L2\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN\n"
            + "L2\n"
            + "LDC \"btrace.traces/ExportTest.entryTimes\"\n"
            + "LDC \"Ljava/util/Deque;\"\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\n"
            + "LDC \"btrace.traces/ExportTest.x\"\n"
            + "LDC \"I\"\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "ACONST_NULL\n"
            + "LDC \"btrace.traces/ExportTest.name\"\n"
            + "LDC \"Ljava/lang/String;\"\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "DCONST_0\n"
            + "INVOKESTATIC java/lang/Double.valueOf (D)Ljava/lang/Double;\n"
            + "LDC \"btrace.traces/ExportTest.y\"\n"
            + "LDC \"D\"\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "LDC 10\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "LDC \"btrace.traces/ExportTest.z\"\n"
            + "LDC \"J\"\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.newPerfCounter (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.start ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "MAXSTACK = 3");

    checkTransformation(
        "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ExportTest$testArgs (Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "MAXSTACK = 5\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$ExportTest$testArgs(Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"resources.OnMethodTest\", method=\"args\")\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ExportTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "ACONST_NULL\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.push (Ljava/util/Deque;Ljava/lang/Object;)V\n"
            + "ALOAD 0\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ExportTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "SWAP\n"
            + "LDC \"btrace.org/openjdk/btrace/runtime/auxiliary/ExportTest.name\"\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.putPerfString (Ljava/lang/String;Ljava/lang/String;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ExportTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ExportTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/ExportTest.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 5");
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
        "public static Lorg/openjdk/btrace/core/BTraceRuntime; runtime\n"
            + "\n"
            + "// access flags 0x49\n"
            + "public static volatile I $btrace$$level = 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC traces/OnTimerTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN\n"
            + "// access flags 0x9\n"
            + "public static dump(Ljava/lang/String;)V\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L0\n"
            + "LDC Ltraces/OnTimerTest;.class\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.forClass (Ljava/lang/Class;)Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "PUTSTATIC traces/OnTimerTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "GETSTATIC traces/OnTimerTest.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L2\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN\n"
            + "L2\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.start ()V\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeImpl.leave ()V\n"
            + "RETURN");
  }

  @Test
  public void methodEntryArgsSigMatch() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/ArgsSigMatch");
    checkTransformation(
        "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSigMatch$m3 (Ljava/lang/Object;Ljava/util/ArrayList;)V\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$ArgsSigMatch$m1 (Ljava/lang/Object;Ljava/util/List;)V\n"
            + "MAXSTACK = 2");

    resetClassLoader();

    transform("onmethod/leveled/Args");
    checkTransformation(
        "GETSTATIC org/openjdk/btrace/runtime/auxiliary/Args.$btrace$$level : I\n"
            + "ICONST_1\n"
            + "IF_ICMPLT L0\n"
            + "ALOAD 0\n"
            + "ALOAD 1\n"
            + "LLOAD 2\n"
            + "ALOAD 4\n"
            + "ALOAD 5\n"
            + "INVOKESTATIC resources/OnMethodTest.$btrace$org$openjdk$btrace$runtime$auxiliary$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V\n"
            + "FRAME SAME\n"
            + "MAXSTACK = 6");
  }
}
