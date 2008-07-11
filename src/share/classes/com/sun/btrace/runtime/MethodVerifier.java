/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodAdapter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Type;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.*;

/**
 * This class verifies that the BTrace "action" method is
 * safe - boundedness and read-only rules are checked
 * such as no backward jumps (loops), no throw/new/invoke etc.
 *
 * @author A. Sundararajan
 */
public class MethodVerifier extends MethodAdapter {

    private static Set<String> primitiveWrapperTypes;
    private static Set<String> unboxMethods;

    static {
        primitiveWrapperTypes = new HashSet<String>();
        unboxMethods = new HashSet<String>();

        primitiveWrapperTypes.add("java/lang/Boolean");
        primitiveWrapperTypes.add("java/lang/Byte");
        primitiveWrapperTypes.add("java/lang/Character");
        primitiveWrapperTypes.add("java/lang/Short");
        primitiveWrapperTypes.add("java/lang/Integer");
        primitiveWrapperTypes.add("java/lang/Long");
        primitiveWrapperTypes.add("java/lang/Float");
        primitiveWrapperTypes.add("java/lang/Double");
        unboxMethods.add("booleanValue");
        unboxMethods.add("byteValue");
        unboxMethods.add("charValue");
        unboxMethods.add("shortValue");
        unboxMethods.add("intValue");
        unboxMethods.add("longValue");
        unboxMethods.add("floatValue");
        unboxMethods.add("doubleValue");
    }
    private String className;
    private Map<Label, Label> labels;

    public MethodVerifier(MethodVisitor mv, String className) {
        super(mv);
        this.className = className;
        labels = new HashMap<Label, Label>();
    }

    public void visitEnd() {
        labels.clear();
        super.visitEnd();
    }

    public void visitFieldInsn(int opcode, String owner,
            String name, String desc) {
        if (opcode == PUTFIELD) {
            reportError("no.assignment");
        }

        if (opcode == PUTSTATIC) {
            if (!owner.equals(className)) {
                reportError("no.assignment");
            }
        }
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    public void visitInsn(int opcode) {
        switch (opcode) {
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
                reportError("no.assignment");
                break;
            case ATHROW:
                reportError("no.throw");
                break;
            case MONITORENTER:
            case MONITOREXIT:
                reportError("no.synchronized.blocks");
                break;
        }
        super.visitInsn(opcode);
    }

    public void visitIntInsn(int opcode, int operand) {
        if (opcode == NEWARRAY) {
            reportError("no.array.creation");
        }
        super.visitIntInsn(opcode, operand);
    }

    public void visitJumpInsn(int opcode, Label label) {
        if (labels.get(label) != null) {
            reportError("no.loops");
        }
        super.visitJumpInsn(opcode, label);
    }

    public void visitLabel(Label label) {
        labels.put(label, label);
        super.visitLabel(label);
    }

    public void visitLdcInsn(Object cst) {
        if (cst instanceof Type) {
            reportError("no.class.literals", cst.toString());
        }
        super.visitLdcInsn(cst);
    }

    public void visitMethodInsn(int opcode, String owner,
            String name, String desc) {
        switch (opcode) {
            case INVOKEVIRTUAL:
                if (isPrimitiveWrapper(owner) && unboxMethods.contains(name)) {
                    // allow primitive type unbox methods.
                    // These calls are generated by javac for auto-unboxing
                    // and can't be caught sourc AST analyzer as well.
                } else {
                    reportError("no.method.calls", owner + "." + name + desc);
                }
                break;
            case INVOKEINTERFACE:
                reportError("no.method.calls", owner + "." + name + desc);
                break;
            case INVOKESPECIAL:
                if (owner.equals(JAVA_LANG_OBJECT) && name.equals(CONSTRUCTOR)) {
                } else {
                    reportError("no.method.calls", owner + "." + name + desc);
                }
                break;
            case INVOKESTATIC:
                if (!owner.equals(BTRACE_UTILS)) {
                    if ("valueOf".equals(name) && isPrimitiveWrapper(owner)) {
                        // allow primitive wrapper boxing methods.
                        // These calls are generated by javac for autoboxing
                        // and can't be caught sourc AST analyzer as well.
                    } else {
                        reportError("no.method.calls", owner + "." + name + desc);
                    }
                }
                break;
        }
        super.visitMethodInsn(opcode, owner, name, desc);
    }

    public void visitMultiANewArrayInsn(String desc, int dims) {
        reportError("no.array.creation");
    }

    public void visitTryCatchBlock(Label start, Label end,
            Label handler, String type) {
        reportError("no.catch");
    }

    public void visitTypeInsn(int opcode, String desc) {
        if (opcode == ANEWARRAY) {
            reportError("no.array.creation", desc);
        }
        if (opcode == NEW) {
            reportError("no.new.object", desc);
        }
        super.visitTypeInsn(opcode, desc);
    }

    public void visitVarInsn(int opcode, int var) {
        if (opcode == RET) {
            reportError("no.try");
        }
        super.visitVarInsn(opcode, var);
    }

    private void reportError(String err) {
        reportError(err, null);
    }

    private void reportError(String err, String msg) {
        Verifier.reportError(err, msg);
    }

    private static boolean isPrimitiveWrapper(String type) {
        return primitiveWrapperTypes.contains(type);
    }
}
