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

import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodAdapter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.runtime.MethodReturnInstrumentor;

/**
 *
 * @author Jaroslav Bachorik
 */
public class TimeStampGenerator extends MethodAdapter {
    final public static String TIME_STAMP_NAME = "$btrace$time$stamp";
    
    private static final String CONSTRUCTOR = "<init>";

    private int[] ts_index;
    private int[] exitOpcodes;
    private int capturingIndex = -1;
    private boolean capturing = false;
    private boolean generatingIndex = false;
    private boolean entryCalled = false;

    private String methodName;
    private String className;
    final private LocalVariablesSorter lvs;

    public TimeStampGenerator(LocalVariablesSorter lvs, int[] tsindex, String className, int access, String name, String desc, MethodVisitor mv, int[] exitOpcodes) {
        super(mv);
        this.lvs = lvs;
        this.methodName = name;
        this.className = className;
        this.ts_index = tsindex;
        this.exitOpcodes = new int[exitOpcodes.length];
        System.arraycopy(exitOpcodes, 0, this.exitOpcodes, 0, exitOpcodes.length);
    }

    @Override
    public void visitCode() {
        ts_index[0] = -1;
        ts_index[1] = -1;
        capturing = false;
        capturingIndex = -1;
        entryCalled = false;
        
        super.visitCode();
    }


    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        if (!generatingIndex) {
            check();
        }
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        check();
        super.visitIincInsn(var, increment);
    }

    @Override
    public void visitInsn(int opcode) {
        check();
        if (ts_index[1] == -1) {
            for(int exitOpcode : exitOpcodes) {
                if (exitOpcode == opcode) {
                    if (ts_index[0] != -1 && ts_index[1] == -1) generateTS(1);
                    break;
                }
            }
        }
        super.visitInsn(opcode);
        if (ts_index[1] != -1) {
            switch (opcode) {
                case RETURN:
                case IRETURN:
                case FRETURN:
                case LRETURN:
                case DRETURN:
                case ARETURN:
                case ATHROW:
                    ts_index[1] = -1; // reset the exit time stamp as it gets invalidated
            }
        }
    }

    @Override
    public void visitIntInsn(int opcode, int var) {
        check();
        super.visitIntInsn(opcode, var);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        check();
        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLabel(Label label) {
        check();
        super.visitLabel(label);
    }

    @Override
    public void visitLdcInsn(Object cst) {
        check();
        super.visitLdcInsn(cst);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        check();
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        if (generatingIndex) {
            super.visitMethodInsn(opcode, owner, name, desc);
            return;
        }
        if (!capturing &&
            name.equals(TIME_STAMP_NAME) &&
            desc.equals("()J")
        ) {
            capturing = true;
            capturingIndex++;
        }
//        if (capturingIndex == -1) {
//            if (!CONSTRUCTOR.equals(methodName) || !CONSTRUCTOR.equals(name)) {
//                check();
//            } else if (entryCalled) {
//                check();
//            }
//        }
        check();
        super.visitMethodInsn(opcode, owner, name, desc);
        if (!entryCalled && CONSTRUCTOR.equals(name)) {
            entryCalled = true;
            if (capturingIndex == -1) {
                check();
            }
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        check();
        super.visitMultiANewArrayInsn(desc, dims);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels) {
        check();
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        check();
        super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        check();
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        check();
        super.visitLocalVariable(name, desc, signature, start, end, index);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (opcode == LSTORE) {
            if (!generatingIndex) {
                if (capturing) {
                    ts_index[capturingIndex] = var; //lvs.remap(var, Type.LONG_TYPE);
                }
                capturing = false;
            }
        }
        super.visitVarInsn(opcode, var);
    }

    private void check() {
        if (!entryCalled && CONSTRUCTOR.equals(methodName)) return; // wait for call to superconstructor

        capturing = false;
        generateTS(0);
    }

    private void generateTS(int index) {
        if (mv instanceof MethodReturnInstrumentor) {
            if (!((MethodReturnInstrumentor)mv).usesTimeStamp()) return; // the method return instrumentor is not using timestamp; no need to generate time stamp collectors
        }
        if (ts_index[index] > -1) return;
        try {
            generatingIndex = true;
            TimeStampHelper.generateTimeStampAccess(this, className);
            ts_index[index] = lvs.newLocal(Type.LONG_TYPE);
        } finally {
            generatingIndex = false;
        }
    }
}
