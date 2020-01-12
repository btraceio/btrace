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

package org.openjdk.btrace.instr;

import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * This visitor helps in inserting code whenever a synchronized
 * method or block is about to be entered/exited. The code to insert on
 * synchronized method or block entry/exit may be decided by derived class.
 * By default, this class inserts code to print a message.
 *
 * @author A. Sundararajan
 */
public class SynchronizedInstrumentor extends MethodEntryExitInstrumentor {

    protected final boolean isStatic;
    protected final boolean isSyncMethod;

    public SynchronizedInstrumentor(ClassLoader cl, MethodVisitor mv, MethodInstrumentorHelper mHelper,
                                    String parentClz, String superClz, int access, String name, String desc) {
        super(cl, mv, mHelper, parentClz, superClz, access, name, desc);

        isStatic = (access & ACC_STATIC) != 0;
        isSyncMethod = (access & ACC_SYNCHRONIZED) != 0;
    }

    @Override
    protected void onMethodEntry() {
        if (isSyncMethod) {
            onAfterSyncEntry();
        }
    }

    @Override
    protected void onMethodReturn(int opcode) {
        onErrorReturn();
    }

    @Override
    protected void onErrorReturn() {
        if (isSyncMethod) {
            onBeforeSyncExit();
        }
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == MONITORENTER) {
            onBeforeSyncEntry();
        } else if (opcode == MONITOREXIT) {
            onBeforeSyncExit();
        }
        super.visitInsn(opcode);
        if (opcode == MONITORENTER) {
            onAfterSyncEntry();
        } else if (opcode == MONITOREXIT) {
            onAfterSyncExit();
        }
    }

    protected void onBeforeSyncEntry() {
        asm.println("before synchronized entry");
    }

    protected void onAfterSyncEntry() {
        asm.println("after synchronized entry");
    }

    protected void onBeforeSyncExit() {
        asm.println("before synchronized exit");
    }

    protected void onAfterSyncExit() {
        asm.println("after synchronized exit");
    }
}