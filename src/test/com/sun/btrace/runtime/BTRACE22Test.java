/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.runtime;

import support.InstrumentorTestBase;
import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTRACE22Test extends InstrumentorTestBase {
    @Test
    public void bytecodeValidation() throws Exception {
        originalBC = loadTargetClass("issues/BTRACE22");
        transform("issues/BTRACE22");
        checkTransformation("INVOKESTATIC resources/issues/BTRACE22.$btrace$time$stamp ()J\nLSTORE 1\n" +
                            "DSTORE 3\nDLOAD 3\nDLOAD 3\n" +
                            "INVOKESTATIC resources/issues/BTRACE22.$btrace$time$stamp ()J\nLSTORE 5\n" +
                            "ALOAD 0\nLLOAD 5\nLLOAD 1\nLSUB\n" +
                            "INVOKESTATIC resources/issues/BTRACE22.$btrace$traces$issues$BTRACE22$tracker (Ljava/lang/Object;J)V\n" +
                            "MAXSTACK");
    }
}
