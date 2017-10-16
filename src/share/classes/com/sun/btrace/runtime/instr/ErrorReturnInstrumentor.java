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

import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.runtime.MethodInstrumentorHelper;

/**
 * This visitor helps in inserting code whenever a method
 * "returns" because of an exception (i.e., no exception
 * handler in the method and so it's frame poped). The code
 * to insert on method error return may be decided by derived
 * class. By default, this class inserts code to print message
 * to say "no handler here".
 *
 * @author A. Sundararajan
 */
public class ErrorReturnInstrumentor extends MethodReturnInstrumentor {
    private final Label start = new Label();
    private final Label end = new Label();

    public ErrorReturnInstrumentor(MethodVisitor mv, MethodInstrumentorHelper mHelper, String parentClz, String superClz,
        int access, String name, String desc) {
        super(mv, mHelper, parentClz, superClz, access, name, desc);
    }

    @Override
    protected void visitMethodPrologue() {
        visitTryCatchBlock(start, end, end, THROWABLE_INTERNAL);
        visitLabel(start);
        super.visitMethodPrologue();
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        visitLabel(end);
        insertFrameReplaceStack(end, THROWABLE_TYPE);
        onErrorReturn();
        visitInsn(ATHROW);
        super.visitMaxs(maxStack, maxLocals);
    }

    @Override
    protected void onMethodEntry() {}

    @Override
    protected void onMethodReturn(int opcode) {}

    protected void onErrorReturn() {
        asm.println("error return from " + getName() + getDescriptor());
    }
}