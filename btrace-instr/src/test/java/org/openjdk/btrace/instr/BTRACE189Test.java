/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

/**
 * Tests that shared methods have properly extended public access
 *
 * @author Jaroslav Bachorik
 */
public class BTRACE189Test extends InstrumentorTestBase {
  @Test
  public void annotatedClass() throws Exception {
    originalBC = loadTargetClass("Main");
    transform("issues/BTRACE189");
    checkTrace(
        "// access flags 0x9\n"
            + "public static Lorg/openjdk/btrace/core/BTraceRuntime; runtime\n"
            + "\n"
            + "// access flags 0x49\n"
            + "public static volatile I $btrace$$level = 0\n"
            + "\n"
            + "// access flags 0x9\n"
            + "public static <clinit>()V\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "L0\n"
            + "LDC Ltraces/issues/BTRACE189;.class\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.forClass (Ljava/lang/Class;)Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "PUTSTATIC traces/issues/BTRACE189.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "GETSTATIC traces/issues/BTRACE189.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L2\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.leave ()V\n"
            + "RETURN\n"
            + "L2\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.start ()V\n"
            + "RETURN\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.leave ()V\n"
            + "RETURN\n"
            + "MAXSTACK = 1\n"
            + "MAXLOCALS = 0\n"
            + "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n"
            + "GETSTATIC traces/issues/BTRACE189.runtime : Lorg/openjdk/btrace/core/BTraceRuntime;\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.enter (Lorg/openjdk/btrace/core/BTraceRuntime;)Z\n"
            + "IFNE L0\n"
            + "RETURN\n"
            + "L0\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.leave ()V\n"
            + "L1\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n"
            + "INVOKESTATIC org/openjdk/btrace/core/BTraceRuntime.leave ()V\n"
            + "RETURN\n"
            + "// access flags 0x9\n"
            + "public static sharedMethod(Ljava/lang/String;)V");
  }
}
