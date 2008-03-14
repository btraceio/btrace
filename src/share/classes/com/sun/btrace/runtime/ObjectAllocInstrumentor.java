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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;

/**
 * This visitor helps in inserting code whenever an object 
 * is allocated. The code to insert on object alloc may be 
 * decided by  derived class. By default, this class inserts 
 * code to print a message.
 *
 * @author A. Sundararajan
 */
public class ObjectAllocInstrumentor extends MethodInstrumentor {
    public ObjectAllocInstrumentor(MethodVisitor mv, int access,
        String name, String desc) {
        super(mv, access, name, desc);
    }

    public void visitTypeInsn(int opcode, String desc) {
        if (opcode == NEW) {            
            onObjectNew(desc);
        }
        super.visitTypeInsn(opcode, desc);
    }

    protected void onObjectNew(String desc) {
        println("before allocating an object of " + desc);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java com.sun.btrace.runtime.ObjectAllocInstrumentor <class>");
            System.exit(1);
        }

        args[0] = args[0].replace('.', '/');
        FileInputStream fis = new FileInputStream(args[0] + ".class");
        ClassReader reader = new ClassReader(new BufferedInputStream(fis));
        FileOutputStream fos = new FileOutputStream(args[0] + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();
        InstrumentUtils.accept(reader,
            new ClassAdapter(writer) {
                 public MethodVisitor visitMethod(int access, String name, String desc, 
                     String signature, String[] exceptions) {
                     MethodVisitor mv = super.visitMethod(access, name, desc, 
                             signature, exceptions);
                     return new ObjectAllocInstrumentor(mv, access, name, desc);
                 }
            });
        fos.write(writer.toByteArray());
    }
}