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

import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;

/**
 * This adapter removes the methods that are
 * annotated by @OnMethod or @OnProbe from a 
 * BTrace class.
 *
 * @author A. Sundararajan
 */
public class MethodRemover extends ClassVisitor {
    public MethodRemover(ClassVisitor visitor) {  
        super(Opcodes.ASM4, visitor);
    }

    private MethodVisitor addMethod(int access, String name, 
            String desc, String signature, String[] exceptions) {
        return super.visitMethod(access, name, 
                              desc, signature, exceptions);
    }

    public MethodVisitor visitMethod(final int access, final String name, 
            final String desc, final String signature, final String[] exceptions) {
        if (name.equals(CONSTRUCTOR) ||
            name.equals(CLASS_INITIALIZER)) {
            return super.visitMethod(access, name, 
                              desc, signature, exceptions);
        } else {
            return new MethodVisitor(Opcodes.ASM4) {
                private boolean include = true;
                private MethodVisitor adaptee = null;

                private MethodVisitor getAdaptee() {
                    if (include && adaptee == null) {
                        adaptee = addMethod(access, name, desc, signature, exceptions);
                    }
                    return adaptee;
                }

                public AnnotationVisitor visitAnnotation(String annoDesc,
                                  boolean visible) {
                    if (annoDesc.equals(ONMETHOD_DESC)) {
                        include = false;
                        return new AnnotationVisitor(Opcodes.ASM4) {};
                    } else if (annoDesc.equals(ONPROBE_DESC)) {
                        include = false;
                        return new AnnotationVisitor(Opcodes.ASM4) {};
                    } else {
                        return getAdaptee().visitAnnotation(annoDesc, visible);
                    }
                }

                public void visitAttribute(Attribute attr) {
                    if (include) {
                        getAdaptee().visitAttribute(attr);
                    } 
                }

                public void visitCode() {
                    if (include) {
                        getAdaptee().visitCode();
                    }
                }

                public void visitFrame(int type, int nLocal, 
                    Object[] local, int nStack, Object[] stack) {
                    if (include) {
                        getAdaptee().visitFrame(type, nLocal, local, nStack, stack);
                    }
                }

                public void visitInsn(int opcode) {
                    if (include) {
                        getAdaptee().visitInsn(opcode);
                    }
                }

                public void visitIntInsn(int opcode, int operand) {
                    if (include) {
                        getAdaptee().visitIntInsn(opcode, operand);
                    }
                }

                public void visitVarInsn(int opcode, int var) {
                    if (include) {
                        getAdaptee().visitVarInsn(opcode, var);
                    }
                }

                public void visitTypeInsn(int opcode, String desc) {
                    if (include) {
                        getAdaptee().visitTypeInsn(opcode, desc);
                    }
                }

                public void visitFieldInsn(int opcode, String owner, 
                    String name, String desc) {
                    if (include) {
                        getAdaptee().visitFieldInsn(opcode, owner, name, desc);
                    }
                }

                public void visitMethodInsn(int opcode, String owner, 
                    String name, String desc) {
                    if (include) {
                        getAdaptee().visitMethodInsn(opcode, owner, name, desc);
                    }
                }

                public void visitJumpInsn(int opcode, Label label) {
                    if (include) {
                        getAdaptee().visitJumpInsn(opcode, label);
                    }
                }

                public void visitLabel(Label label) {
                    if (include) {
                        getAdaptee().visitLabel(label);
                    }
                }

                public void visitLdcInsn(Object cst) {
                    if (include) {
                        getAdaptee().visitLdcInsn(cst);
                    }
                }

                public void visitIincInsn(int var, int increment) {
                    if (include) {
                        getAdaptee().visitIincInsn(var, increment);
                    }
                }

                public void visitTableSwitchInsn(int min, int max, 
                    Label dflt, Label[] labels) {
                    if (include) {
                        getAdaptee().visitTableSwitchInsn(min, max, dflt, labels);
                    }
                }

                public void visitLookupSwitchInsn(Label dflt, int[] keys, 
                    Label[] labels) {
                    if (include) {
                        getAdaptee().visitLookupSwitchInsn(dflt, keys, labels);
                    }
                }

                public void visitMultiANewArrayInsn(String desc, int dims) {
                    if (include) {
                        getAdaptee().visitMultiANewArrayInsn(desc, dims);
                    }
                }

                public void visitTryCatchBlock(Label start, Label end, 
                    Label handler, String type) {
                    if (include) {
                        getAdaptee().visitTryCatchBlock(start, end, handler, type);
                    }
                }

                public void visitLocalVariable(String name, String desc, 
                    String signature, Label start, Label end, int index) {
                    if (include) {
                        getAdaptee().visitLocalVariable(name, desc, signature,
                            start, end, index);
                    }
                }

                public void visitLineNumber(int line, Label start) {
                    if (include) {
                        getAdaptee().visitLineNumber(line, start);
                    }
                }

                public void visitMaxs(int maxStack, int maxLocals) {
                    if (include) {
                        getAdaptee().visitMaxs(maxStack, maxLocals);
                    }
                }

                public void visitEnd() {
                    if (include) {
                        getAdaptee().visitEnd();
                    }
                }
            };
        }
    }
}
