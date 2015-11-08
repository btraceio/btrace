/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime;

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.org.objectweb.asm.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import static com.sun.btrace.runtime.Constants.CLASS_INITIALIZER;

/**
 * This class copies a set of methods from one class
 * to another class. While copying methods can be
 * renamed as well as access bits can be changed.
 * No validation (like target already has such method)
 * is done.
 *
 * @author A. Sundararajan
 */
public class MethodCopier extends ClassVisitor {
    private final ClassReader fromClass;
    private final Iterable<MethodInfo> methods;

    public static class MethodInfo {
        String name;
        String desc;
        String newName;
        int newAccess;

        public MethodInfo(String name, String desc,
                          String newName, int newAccess) {
            this.name = name;
            this.desc = desc;
            this.newName = newName;
            this.newAccess = newAccess;
        }
    }

    public MethodCopier(ClassReader fromClass, ClassVisitor toClass,
                       Iterable<MethodInfo> methods) {
        super(Opcodes.ASM5, toClass);
        this.fromClass = fromClass;
        this.methods = methods;
    }

    protected MethodVisitor addMethod(int access, String name, String desc,
                        String signature, String[] exceptions) {
        return super.visitMethod(access, name, desc,
                signature, exceptions);
    }

    private MethodInfo getMethodInfo(String name, String desc) {
        Iterator<MethodInfo> itr = methods.iterator();
        while (itr.hasNext()) {
            MethodInfo mi = itr.next();
            if (mi.name.equals(name) &&
                mi.desc.equals(desc)) {
                itr.remove();
                return mi;
            }
        }
        return null;
    }

    @Override
    public void visitEnd() {
        fromClass.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(int version, int access, String name,
                String signature, String superName, String[] interfaces) {
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                return new AnnotationVisitor(Opcodes.ASM5) {};
            }

            @Override
            public void visitAttribute(Attribute attr) {
            }

            @Override
            public void visitEnd() {
            }

            @Override
            public FieldVisitor visitField(int access, String name,
                String desc, String signature, Object value) {
                return null;
            }

            @Override
            public void visitInnerClass(String name, String outerName,
                String innerName, int access) {
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
                MethodInfo mi = getMethodInfo(name, desc);
                if (mi != null) {
                    return addMethod(mi.newAccess, mi.newName, desc,
                                       signature, exceptions);
                } else {
                    return null;
                }
            }

            @Override
            public void visitOuterClass(String owner,
                String name, String desc) {
            }

            @Override
            public void visitSource(String source, String debug) {
            }
        }, 0);
        super.visitEnd();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java com.sun.btrace.runtime.MethodCopier <class-1> <class-2>");
            System.exit(1);
        }

        Class clazz = Class.forName(args[0]);
        Method[] methods = clazz.getDeclaredMethods();
        List<MethodInfo> miList = new ArrayList<>();
        for (Method m : methods) {
            // skip the class initializer method
            if (! m.getName().equals(CLASS_INITIALIZER)) {
                MethodInfo mi = new MethodInfo(m.getName(),
                             Type.getMethodDescriptor(m),
                             m.getName(), m.getModifiers());
                miList.add(mi);
            }
        }
        args[0] = args[0].replace('.', '/');
        args[1] = args[1].replace('.', '/');
        FileInputStream fis1 = new FileInputStream(args[0] + ".class");
        ClassReader reader1 = new ClassReader(new BufferedInputStream(fis1));
        FileInputStream fis2 = new FileInputStream(args[1] + ".class");
        ClassReader reader2 = new ClassReader(new BufferedInputStream(fis2));
        FileOutputStream fos = new FileOutputStream(args[1] + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();
        MethodCopier copier = new MethodCopier(reader1, writer, miList);
        InstrumentUtils.accept(reader2, copier);
        fos.write(writer.toByteArray());
    }
}
