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

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;
import java.io.*;

/**
 * This adapter renames a given .class with given
 * name. Not all cases are handled/checked. But, this
 * is used to rename a client supplied BTrace program
 * to make it unique (because multiple clients could
 * submit the same named BTrace class).
 *
 * @author A. Sundararajan
 */
public class ClassRenamer extends ClassAdapter {
    private String oldName;
    private String newName;
    private String oldNameDesc;
    private String newNameDesc;

    public ClassRenamer(String newName, ClassVisitor visitor) {  
        super(visitor);
        newName = newName.replace('.', '/');
        this.newName = newName;
        this.newNameDesc = "L" + newName + ";";
    }

    public void visit(int version, int access, String name, 
        String signature, String superName, String[] interfaces) {
        oldName = name;
        oldNameDesc = "L" + oldName + ";";
        if (signature != null) {
            signature = signature.replace(oldNameDesc, newNameDesc);
        }
        super.visit(version, access, newName, 
                    signature, superName, interfaces);
    }

    public FieldVisitor visitField(int access, String name, 
        String desc, String signature, Object value) {
        desc = desc.replace(oldNameDesc, newNameDesc);
        if (signature != null) {
            signature = signature.replace(oldNameDesc, newNameDesc);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    public MethodVisitor visitMethod(int access, String name, 
            String desc, String signature, String[] exceptions) {
        if (signature != null) {
            signature = signature.replace(oldNameDesc, newNameDesc);
        }
        desc = desc.replace(oldNameDesc, newNameDesc);
        MethodVisitor adaptee = super.visitMethod(access, name, 
                                   desc, signature, exceptions);
        return new MethodAdapter(adaptee) {
            public void visitFieldInsn(int opcode,
                          String owner,
                          String name,
                          String desc) {
                if (owner.equals(oldName)) {
                    owner = newName;
                }
                desc = desc.replace(oldNameDesc, newNameDesc);
                super.visitFieldInsn(opcode, owner, name, desc);
            }

            public void visitMethodInsn(int opcode, String owner,
                String name, String desc) {
                if (owner.equals(oldName)) {
                    owner = newName;
                }
                desc = desc.replace(oldNameDesc, newNameDesc);
                super.visitMethodInsn(opcode, owner, name, desc);
            }

            public void visitLdcInsn(Object cst) {
                if (cst instanceof Type) {
                    String name = ((Type)cst).getInternalName();
                    if (name.equals(oldName)) {
                        cst = Type.getType(newNameDesc);
                    }
                }
                super.visitLdcInsn(cst);
            }

            public void visitTypeInsn(int opcode, String desc) {
                if (desc.equals(oldName)) {
                    desc = newName;
                }
                if (desc.equals(oldNameDesc)) {
                    desc = newNameDesc;
                }
                desc = desc.replace(oldNameDesc, newNameDesc);
                super.visitTypeInsn(opcode, desc);
            }

            public void visitMultiANewArrayInsn(String desc, int dims) {
                desc = desc.replace(oldNameDesc, newNameDesc);
                super.visitMultiANewArrayInsn(desc, dims);
            }

            public void visitLocalVariable(String name,
                        String desc,  String signature,
                        Label start, Label end, int index) {
                desc = desc.replace(oldNameDesc, newNameDesc);
                if (signature != null) {
                    signature = signature.replace(oldNameDesc, newNameDesc);
                }
                super.visitLocalVariable(name, desc, signature,
                        start, end, index);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java com.sun.btrace.runtime.ClassRenamer <class> <new-class-name>");
            System.exit(1);
        }

        args[0] = args[0].replace('.', '/');
        args[1] = args[1].replace('.', '/');
        FileInputStream fis = new FileInputStream(args[0] + ".class");
        ClassReader reader = new ClassReader(new BufferedInputStream(fis));
        FileOutputStream fos = new FileOutputStream(args[1] + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();
        InstrumentUtils.accept(reader, new ClassRenamer(args[1], writer));
        fos.write(writer.toByteArray());
    } 
}