/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

/**
 * Tests that specifying a class by its associated annotation does not cause NPE
 * @author Jaroslav Bachorik
 */
public class BTRACE106Test extends InstrumentorTestBase {
    @Test
    public void annotatedClass() throws Exception {
        originalBC = loadTargetClass("issues/BTRACE106");
        transform("issues/BTRACE106");
        checkTransformation("ALOAD 0\n" +
            "LDC \"aMethod\"\n" +
            "INVOKESTATIC resources/issues/BTRACE106.$btrace$traces$issues$BTRACE106$o1 (Ljava/lang/Object;Ljava/lang/String;)V\n" +
            "LCONST_0\n" +
            "LSTORE 1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 3\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 3\n" +
            "LSUB\n" +
            "LSTORE 1\n" +
            "ALOAD 0\n" +
            "LDC \"bMethod\"\n" +
            "LLOAD 1\n" +
            "INVOKESTATIC resources/issues/BTRACE106.$btrace$traces$issues$BTRACE106$o2 (Ljava/lang/Object;Ljava/lang/String;J)V\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 5"
        );
    }
}
