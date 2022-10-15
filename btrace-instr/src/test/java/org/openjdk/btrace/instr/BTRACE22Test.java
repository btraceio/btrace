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
public class BTRACE22Test extends InstrumentorTestBase {
  @Test
  public void bytecodeValidation() throws Exception {
    originalBC = loadTargetClass("issues/BTRACE22");
    transform("issues/BTRACE22");
    checkTransformation(
        "LCONST_0\n"
            + "LSTORE 1\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LSTORE 3\n"
            + "DSTORE 5\n"
            + "DLOAD 5\n"
            + "DLOAD 5\n"
            + "INVOKESTATIC java/lang/System.nanoTime ()J\n"
            + "LLOAD 3\n"
            + "LSUB\n"
            + "LSTORE 1\n"
            + "ALOAD 0\n"
            + "LLOAD 1\n"
            + "INVOKESTATIC resources/issues/BTRACE22.$btrace$org$openjdk$btrace$runtime$auxiliary$BTRACE22$tracker (Ljava/lang/Object;J)V\n"
            + "LOCALVARIABLE d D L1 L3 5\n"
            + "MAXSTACK = 6\n"
            + "MAXLOCALS = 7");
  }
}
