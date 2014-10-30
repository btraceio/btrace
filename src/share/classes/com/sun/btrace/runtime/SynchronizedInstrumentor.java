/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.btrace.org.objectweb.asm.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.util.LocalVariableHelperImpl;
import com.sun.btrace.util.LocalVariableHelper;

/**
 * This visitor helps in inserting code whenever a synchronized
 * method or block is about to be entered/exited. The code to insert on
 * synchronized method or block entry/exit may be decided by derived class.
 * By default, this class inserts code to print a message.
 *
 * @author A. Sundararajan
 */
public class SynchronizedInstrumentor extends MethodEntryExitInstrumentor {

    private final boolean isStatic;
    private final boolean isSyncMethod;

    public SynchronizedInstrumentor(
            LocalVariableHelper mv, String parentClz, String superClz, int access, String name, String desc) {
        super(mv, parentClz, superClz, access, name, desc);

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

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java com.sun.btrace.runtime.SynchronizedInstrumentor <class>");
            System.exit(1);
        }

        args[0] = args[0].replace('.', '/');
        FileInputStream fis = new FileInputStream(args[0] + ".class");
        ClassReader reader = new ClassReader(new BufferedInputStream(fis));
        FileOutputStream fos = new FileOutputStream(args[0] + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();
        InstrumentUtils.accept(reader,
            new ClassVisitor(Opcodes.ASM4, writer) {
                 private String className;
                 public void visit(int version, int access, String name,
                     String signature, String superName, String[] interfaces) {
                     super.visit(version, access, name, signature,
                             superName, interfaces);
                     className = name;
                 }

                 public MethodVisitor visitMethod(int access, String name, String desc,
                     String signature, String[] exceptions) {
                     MethodVisitor mv = super.visitMethod(access, name, desc,
                             signature, exceptions);
                     return new SynchronizedInstrumentor(new LocalVariableHelperImpl(mv, access, desc), className, className, access, name, desc);
                 }
            });
        fos.write(writer.toByteArray());
    }
}