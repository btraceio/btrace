/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime;

import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;

/**
 * This class adapter injects a freshly loaded class with instructions to invoke
 * BTrace class retransformation upon invoking its static initializer.
 * @author Jaroslav Bachorik
 * @since 1.2.1
 */
public class ClinitInjector extends ClassVisitor {
    private static final String CLINIT = "<clinit>";

    private boolean clinitFound = false;
    private boolean transformed = false;
    private final String runtime;
    private final String cname;

    public ClinitInjector(ClassVisitor cv, String runtime, String cname) {
        super(Opcodes.ASM4, cv);
        this.runtime = runtime;
        this.cname = cname;
    }

    public boolean isTransformed() {
        return transformed;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String supername, String[] interfaces) {
        if (!(((access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) != 0) ||
            (supername.equals("java/lang/Object") && interfaces.length == 0))) {
            transformed = true;
        }
        int major = version & 0x0000ffff;
        super.visit(major < Opcodes.V1_5 ? Opcodes.V1_5 : version, access, name, signature, supername, interfaces);
    }



    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (transformed && CLINIT.equals(name)) {
            visitor = new MethodVisitor(Opcodes.ASM4, visitor) {
                private boolean exitFound = false;
                private int requiredStack = 0;
                @Override
                public void visitCode() {
                    requiredStack = generateClinit(mv);
                    super.visitCode();
                }

                @Override
                public void visitMaxs(int maxStack, int maxLocals) {
                    super.visitMaxs(maxStack < requiredStack ? requiredStack : maxStack, maxLocals);
                }

                @Override
                public void visitInsn(int i) {
                    // checking for existence of a proper exit instruction
                    if (i == Opcodes.RETURN || i == Opcodes.IRETURN ||
                        i == Opcodes.LRETURN || i == Opcodes.DRETURN ||
                        i == Opcodes.FRETURN || i == Opcodes.ATHROW) {
                        exitFound = true;
                    }
                    super.visitInsn(i);
                }

                @Override
                public void visitEnd() {
                    if (!exitFound) {
                        // no proper exit instruction found; generate RETURN
                        super.visitInsn(Opcodes.RETURN);
                    }
                    super.visitEnd();
                }
            };
            clinitFound = true;
        }
        return visitor;
    }

    @Override
    public void visitEnd() {
        if (transformed && !clinitFound) {
            MethodVisitor mv = visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, CLINIT, "()V", null, null); // NOI18N
            // this will call this method's visitMethod method, effectively generating the clinit content
            mv.visitCode();
            // need to call visitMaxs explicitely
            mv.visitMaxs(0, 0);
            // properly close the method body
            mv.visitEnd();
        }
        super.visitEnd();
    }

    private int generateClinit(MethodVisitor mv) {
        Type clazz = Type.getType("L" + cname + ";");

        // the client name (the BTrace script class name)
        mv.visitLdcInsn(runtime);
        // the name of the currently processed class
        mv.visitLdcInsn(clazz); // NOI18N
        // invocatio nof BTraceRuntime.retransform() method
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/sun/btrace/BTraceRuntime", "retransform", "(Ljava/lang/String;Ljava/lang/Class;)V"); // NOI18N

        return clazz.getSize() + Type.getType(String.class).getSize();
    }
}
