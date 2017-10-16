/*
 * Copyright (c) 2008-2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime.instr;

import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.runtime.MethodInstrumentorHelper;

/**
 * This visitor helps in inserting code whenever a method call
 * is done. The code to insert on method calls may be decided by
 * derived class. By default, this class inserts code to print
 * the called method.
 *
 * @author A. Sundararajan
 */
public class MethodCallInstrumentor extends MethodInstrumentor {
    private int callId = 0;

    public MethodCallInstrumentor(MethodVisitor mv, MethodInstrumentorHelper mHelper, String parentClz, String superClz,
        int access, String name, String desc) {
        super(mv, mHelper, parentClz, superClz, access, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner,
        String name, String desc, boolean iface) {
        if (name.startsWith("$btrace")) {
            super.visitMethodInsn(opcode, owner, name, desc, iface);
            return;
        }

        callId++;

        onBeforeCallMethod(opcode, owner, name, desc);
        super.visitMethodInsn(opcode, owner, name, desc, iface);
        onAfterCallMethod(opcode, owner, name, desc);
    }

    protected void onBeforeCallMethod(int opcode,
        String owner, String name, String desc) {
        asm.println("before call: " + owner + "." + name + desc);
    }

    protected void onAfterCallMethod(int opcode,
        String owner, String name, String desc) {
        asm.println("after call: " + owner + "." + name + desc);
    }

    protected int getCallId() {
        return callId;
    }
}