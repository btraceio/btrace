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

package com.sun.btrace.util;

import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.runtime.MethodInstrumentor;

/**
 *
 * @author Jaroslav Bachorik
 */
public class TimeStampGenerator extends MethodVisitor {
    final public static String TIME_STAMP_NAME = "$btrace$time$stamp";
    
    private static final String CONSTRUCTOR = "<init>";

    private int[] tsIndex;
    private int[] exitOpcodes;
    private boolean generatingIndex = false;
    private boolean entryCalled = false;

    private String methodName;
    private String className;
    private String superName;
    final private LocalVariablesSorter lvs;

    public TimeStampGenerator(LocalVariablesSorter lvs, final int[] tsIndex, String className, String superName, int access, String name, String desc, MethodVisitor mv, int[] exitOpcodes) {
        super(Opcodes.ASM4, mv);
        this.lvs = lvs;
        this.methodName = name;
        this.className = className;
        this.superName = superName;
        this.tsIndex = tsIndex;
        this.exitOpcodes = new int[exitOpcodes.length];
        System.arraycopy(exitOpcodes, 0, this.exitOpcodes, 0, exitOpcodes.length);
    }

    @Override
    public void visitCode() {
        entryCalled = false;
        
        if (!CONSTRUCTOR.equals(methodName)) {
            generateTS(0);
        }
        
        super.visitCode();
    }

    @Override
    public void visitInsn(int opcode) {
        if (tsIndex[1] == -1) {
            for(int exitOpcode : exitOpcodes) {
                if (exitOpcode == opcode) {
                    if (tsIndex[0] != -1 && tsIndex[1] == -1) generateTS(1);
                    break;
                }
            }
        }
        super.visitInsn(opcode);
        if (tsIndex[1] != -1) {
            switch (opcode) {
                case RETURN:
                case IRETURN:
                case FRETURN:
                case LRETURN:
                case DRETURN:
                case ARETURN:
                case ATHROW:
                    tsIndex[1] = -1; // reset the exit time stamp as it gets invalidated
            }
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        if (generatingIndex) {
            super.visitMethodInsn(opcode, owner, name, desc);
            return;
        }
        super.visitMethodInsn(opcode, owner, name, desc);
        if (!entryCalled && CONSTRUCTOR.equals(name) && (owner.equals(className) || (superName != null && owner.equals(superName)))) {
            entryCalled = true;
            generateTS(0);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        super.visitVarInsn(opcode, var);
    }

    private void generateTS(int index) {
        if (tsIndex != null && tsIndex[index] != -1) return;
        
        if (!((MethodInstrumentor)mv).usesTimeStamp()) return; // the method instrumentor is not using timestamp; no need to generate time stamp collectors
        
        if (tsIndex[index] > -1) return;
        try {
            generatingIndex = true;
            TimeStampHelper.generateTimeStampAccess(this, className);
            tsIndex[index] = lvs.newLocal(Type.LONG_TYPE);
        } finally {
            generatingIndex = false;
        }
    }
}
