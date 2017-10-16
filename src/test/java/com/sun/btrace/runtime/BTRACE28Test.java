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

package com.sun.btrace.runtime;

import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTRACE28Test extends InstrumentorTestBase {
    @Test
    public void bytecodeValidation() throws Exception {
        originalBC = loadTargetClass("issues/BTRACE28");
        transform("issues/BTRACE28");
        checkTransformation(
            "LDC \"resources.issues.BTRACE28\"\n" +
            "LDC \"<init>\"\n" +
            "INVOKESTATIC resources/issues/BTRACE28.$btrace$traces$issues$BTRACE28$tracker (Ljava/lang/String;Ljava/lang/String;)V\n" +
            "MAXSTACK = 2\n" +
            "ASTORE 5\n" +
            "ASTORE 6\n" +
            "ASTORE 7\n" +
            "ALOAD 7\n" +
            "FRAME FULL [resources/issues/BTRACE28 java/lang/String java/lang/String java/lang/String java/lang/String [B [B java/lang/StringBuilder] [java/lang/Throwable]\n" +
            "ASTORE 8\n" +
            "ALOAD 8\n" +
            "LDC \"resources.issues.BTRACE28\"\n" +
            "LDC \"serveResource\"\n" +
            "INVOKESTATIC resources/issues/BTRACE28.$btrace$traces$issues$BTRACE28$tracker (Ljava/lang/String;Ljava/lang/String;)V\n" +
            "LOCALVARIABLE e Ljava/lang/Throwable; L10 L9 8\n" +
            "LOCALVARIABLE mainArr [B L6 L11 5\n" +
            "LOCALVARIABLE byteArr [B L7 L11 6\n" +
            "LOCALVARIABLE sb Ljava/lang/StringBuilder; L0 L11 7\n" +
            "MAXLOCALS = 9"
        );
    }
}
