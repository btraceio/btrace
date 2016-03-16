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

import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.util.LocalVariableHelper;

/**
 * This class helps in inserting code whenever a type check
 * (instanceof or checkcact) is done. The code to insert on
 * type check may be decided by  derived class. By default,
 * this class inserts code to print message.
 *
 * @author A. Sundararajan
 */
public class TypeCheckInstrumentor extends MethodInstrumentor {
    public TypeCheckInstrumentor(LocalVariableHelper mv, String parentClz, String superClz,
        int access, String name, String desc) {
        super(mv, parentClz, superClz, access, name, desc);
    }

    public void visitTypeInsn(int opcode, String desc) {
        boolean typeCheck = (opcode == CHECKCAST || opcode == INSTANCEOF);
        if (typeCheck) {
            onBeforeTypeCheck(opcode, desc);
        }
        super.visitTypeInsn(opcode, desc);
        if (typeCheck) {
            onAfterTypeCheck(opcode, desc);
        }
    }

    protected void onBeforeTypeCheck(int opcode, String desc) {
        asm.println("before type checking for " + desc);
    }

    protected void onAfterTypeCheck(int opcode, String desc) {
       asm.println("after type checking for " + desc);
    }
}