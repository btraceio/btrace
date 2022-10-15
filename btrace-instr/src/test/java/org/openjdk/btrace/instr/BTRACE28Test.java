/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

import org.junit.jupiter.api.Test;

/**
 * @author Jaroslav Bachorik
 */
public class BTRACE28Test extends InstrumentorTestBase {
  @Test
  public void bytecodeValidation() throws Exception {
    originalBC = loadTargetClass("issues/BTRACE28");
    transform("issues/BTRACE28");
    checkTransformation(
        "LDC \"resources.issues.BTRACE28\"\n"
            + "LDC \"<init>\"\n"
            + "INVOKESTATIC resources/issues/BTRACE28.$btrace$org$openjdk$btrace$runtime$auxiliary$BTRACE28$tracker (Ljava/lang/String;Ljava/lang/String;)V\n"
            + "MAXSTACK = 2\n"
            + "ASTORE 5\n"
            + "ASTORE 6\n"
            + "ASTORE 7\n"
            + "ALOAD 7\n"
            + "FRAME FULL [resources/issues/BTRACE28 java/lang/String java/lang/String java/lang/String java/lang/String [B [B java/lang/StringBuilder] [java/lang/Throwable]\n"
            + "ASTORE 8\n"
            + "ALOAD 8\n"
            + "LDC \"resources.issues.BTRACE28\"\n"
            + "LDC \"serveResource\"\n"
            + "INVOKESTATIC resources/issues/BTRACE28.$btrace$org$openjdk$btrace$runtime$auxiliary$BTRACE28$tracker (Ljava/lang/String;Ljava/lang/String;)V\n"
            + "LOCALVARIABLE e Ljava/lang/Throwable; L10 L9 8\n"
            + "LOCALVARIABLE mainArr [B L6 L11 5\n"
            + "LOCALVARIABLE byteArr [B L7 L11 6\n"
            + "LOCALVARIABLE sb Ljava/lang/StringBuilder; L0 L11 7\n"
            + "MAXLOCALS = 9\n"
            + "\n"
            + "// access flags 0xA\n"
            + "private static $btrace$org$openjdk$btrace$runtime$auxiliary$BTRACE28$tracker(Ljava/lang/String;Ljava/lang/String;)V\n"
            + "@Lorg/openjdk/btrace/core/annotations/OnMethod;(clazz=\"/.*\\\\.BTRACE28/\", method=\"/.*/\", location=@Lorg/openjdk/btrace/core/annotations/Location;(value=Lorg/openjdk/btrace/core/annotations/Kind;.RETURN))\n"
            + "// annotable parameter count: 2 (visible)\n"
            + "@Lorg/openjdk/btrace/core/annotations/ProbeClassName;() // parameter 0\n"
            + "@Lorg/openjdk/btrace/core/annotations/ProbeMethodName;() // parameter 1\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/BTRACE28.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKESTATIC org/openjdk/btrace/runtime/BTraceRuntimeAccess.enter (Lorg/openjdk/btrace/core/BTraceRuntime$Impl;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "FRAME SAME\n"
            + "ALOAD 0\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "ALOAD 1\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "LDC \"args empty\"\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceUtils.println (Ljava/lang/Object;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/BTRACE28.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "FRAME SAME1 java/lang/Throwable\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/BTRACE28.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "DUP_X1\n"
            + "SWAP\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.handleException (Ljava/lang/Throwable;)V\n"
            + "GETSTATIC org/openjdk/btrace/runtime/auxiliary/BTRACE28.runtime : Lorg/openjdk/btrace/runtime/BTraceRuntimeImplBase;\n"
            + "INVOKEVIRTUAL org/openjdk/btrace/runtime/BTraceRuntimeImplBase.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 3\n"
            + "MAXLOCALS = 2");
  }
}
