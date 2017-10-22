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

import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.runtime.InstrumentUtils;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.runtime.MethodInstrumentorHelper;

/**
 * This visitor helps in inserting code whenever an array
 * is allocated. The code to insert on method entry may be
 * decided by derived class. By default, this class inserts
 * code to print allocated array objects.
 *
 * @author A. Sundararajan
 */
public class ArrayAllocInstrumentor extends MethodInstrumentor {
    public ArrayAllocInstrumentor(ClassLoader cl, MethodVisitor mv, MethodInstrumentorHelper mHelper,
                                    String parentClz, String superClz, int access, String name, String desc) {
        super(cl, mv, mHelper, parentClz, superClz, access, name, desc);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        String desc = null;
        if (opcode == NEWARRAY) {
            desc = InstrumentUtils.arrayDescriptorFor(operand);
            onBeforeArrayNew(getPlainType(desc), 1);
        }
        super.visitIntInsn(opcode, operand);
        if (opcode == NEWARRAY) {
            onAfterArrayNew(getPlainType(desc), 1);
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String desc) {
        if (opcode == ANEWARRAY) {
            onBeforeArrayNew("L" + desc + ";", 1);
        }
        super.visitTypeInsn(opcode, desc);
        if (opcode == ANEWARRAY) {
            onAfterArrayNew("L" + desc + ";", 1);
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        String type = getPlainType(desc);
        onBeforeArrayNew(type, dims);
        super.visitMultiANewArrayInsn(desc, dims);
        onAfterArrayNew(type, dims);
    }

    protected void onBeforeArrayNew(String desc, int dims) {
        asm.println("before allocating " + desc);
    }

    protected void onAfterArrayNew(String desc, int dims) {
        asm.dup()
           .printObject();
    }

    private String getPlainType(String desc) {
        int index = desc.lastIndexOf('[') + 1;
        if (index > 0) {
            return desc.substring(index);
        }
        return desc;
    }
}