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

package com.sun.btrace.util;

import com.sun.btrace.org.objectweb.asm.ClassAdapter;
import com.sun.btrace.org.objectweb.asm.MethodAdapter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.sun.btrace.runtime.Constants.CONSTRUCTOR;

/**
 * Will generate a set of empty methods (with no body)
 * @author Jaroslav Bachorik
 */
public class EmptyMethodsEvaluator extends ClassAdapter {
    final private Set<String> emptyMethods = new HashSet<String>();

    public EmptyMethodsEvaluator() {
        super(new NullVisitor());
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, String[] exceptions) {
        final CodeSizeEvaluator cse = new CodeSizeEvaluator(super.visitMethod(access, desc, desc, desc, exceptions));
        return new MethodAdapter(cse) {

            @Override
            public void visitCode() {
                if (!CONSTRUCTOR.equals(name)) {
                    cse.setEnabled(true);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String mowner, String mname, String mdesc) {
                if (CONSTRUCTOR.equals(name)) {
                    if (!cse.isEnabled()) {
                        if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(mname)) {
                            cse.setEnabled(true);
                        }
                    }
                } else {
                    super.visitInsn(opcode);
                }
            }

            @Override
            public void visitInsn(int opcode) {
                if (!(opcode == Opcodes.RETURN ||
                      opcode == Opcodes.IRETURN ||
                      opcode == Opcodes.ARETURN ||
                      opcode == Opcodes.DRETURN ||
                      opcode == Opcodes.FRETURN ||
                      opcode == Opcodes.LRETURN)) {
                    super.visitInsn(opcode);
                }
            }


            @Override
            public void visitEnd() {
                if (cse.getMinSize() == 0) {
                    emptyMethods.add(MethodID.create(name, desc));
                }
            }
        };
    }

    public Set<String> getEmptyMethods() {
        return Collections.unmodifiableSet(emptyMethods);
    }
}
