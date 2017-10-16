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
public class BTRACE69Test extends InstrumentorTestBase {
    @Test
    public void bytecodeValidation() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("issues/BTRACE69");
        checkTransformation(
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "TRYCATCHBLOCK L0 L2 L2 java/lang/Throwable\n" +
            "TRYCATCHBLOCK L3 L4 L5 null\n" +
            "TRYCATCHBLOCK L5 L6 L5 null\n" +
            "L0\n" +
            "LINENUMBER 108 L0\n" +
            "DUP\n" +
            "ASTORE 2\n" +
            "ALOAD 2\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$issues$BTRACE69$onSyncEntry (Ljava/lang/Object;)V\n" +
            "L3\n" +
            "LINENUMBER 109 L3\n" +
            "L7\n" +
            "LINENUMBER 110 L7\n" +
            "ALOAD 1\n" +
            "DUP\n" +
            "ASTORE 3\n" +
            "ALOAD 3\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$issues$BTRACE69$onSyncExit (Ljava/lang/Object;)V\n" +
            "MONITOREXIT\n" +
            "L4\n" +
            "GOTO L8\n" +
            "FRAME FULL [resources/OnMethodTest java/lang/Object resources/OnMethodTest] [java/lang/Throwable]\n" +
            "ASTORE 3\n" +
            "DUP\n" +
            "ASTORE 4\n" +
            "ALOAD 4\n" +
            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$issues$BTRACE69$onSyncExit (Ljava/lang/Object;)V\n" +
            "L6\n" +
            "ALOAD 3\n" +
            "ATHROW\n" +
            "L8\n" +
            "LINENUMBER 111 L8\n" +
            "FRAME FULL [resources/OnMethodTest resources/OnMethodTest resources/OnMethodTest] []\n" +
            "RETURN\n" +
            "FRAME FULL [resources/OnMethodTest] [java/lang/Throwable]\n" +
            "ATHROW\n" +
            "FRAME SAME1 java/lang/Throwable\n" +
            "LOCALVARIABLE this Lresources/OnMethodTest; L0 L1 0\n" +
            "MAXLOCALS = 5"
        );
    }
}
