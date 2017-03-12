/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime.instr;

import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.util.LocalVariableHelper;

/**
 * This visitor helps in inserting code whenever an field access
 * is done. The code to insert on field access may be decided by
 * derived class. By default, this class inserts code to print
 * the field access.
 *
 * @author A. Sundararajan
 */
public class FieldAccessInstrumentor extends MethodInstrumentor {
    protected boolean isStaticAccess = false;

    public FieldAccessInstrumentor(LocalVariableHelper mv, String parentClz, String superClz,
        int access, String name, String desc) {
        super(mv, parentClz, superClz, access, name, desc);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner,
        String name, String desc) {
        boolean get;
        // ignore any internal BTrace fields
        if (name.contains("$btrace$")) {
            super.visitFieldInsn(opcode, owner, name, desc);
            return;
        }

        get = opcode == GETFIELD || opcode == GETSTATIC;
        isStaticAccess = (opcode == GETSTATIC || opcode == PUTSTATIC);

        if (get) {
            onBeforeGetField(opcode, owner, name, desc);
        } else {
            onBeforePutField(opcode, owner, name, desc);
        }
        super.visitFieldInsn(opcode, owner, name, desc);
        if (get) {
            onAfterGetField(opcode, owner, name, desc);
        } else {
            onAfterPutField(opcode, owner, name, desc);
        }
    }

    protected void onBeforeGetField(int opcode,
        String owner, String name, String desc) {}

    protected void onAfterGetField(int opcode,
        String owner, String name, String desc) {}

    protected void onBeforePutField(int opcode,
        String owner, String name, String desc) {}

    protected void onAfterPutField(int opcode,
        String owner, String name, String desc) {}
}