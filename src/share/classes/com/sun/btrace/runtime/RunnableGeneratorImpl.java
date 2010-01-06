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

import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import com.sun.btrace.RunnableGenerator;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Type;

/**
 * This class generates a java.lang.Runnable implementation 
 * that calls the given public static Method object that
 * accepts no arguments.
 *
 * @author A. Sundararajan
 */
public class RunnableGeneratorImpl implements RunnableGenerator {
    /**
     * Generate class bytes for java.lang.Runnable
     * implementation and return the same.
     */
    public byte[] generate(Method method, String className) {
        int modifiers = method.getModifiers();
        // make sure that the method is public static
        // and accepts no arguments
        if (!Modifier.isStatic(modifiers) ||
            !Modifier.isPublic(modifiers) ||
            method.getParameterTypes().length != 0) {
            throw new IllegalArgumentException();
        }
        Class clazz = method.getDeclaringClass();
        modifiers = clazz.getModifiers();
        // make sure that the class is public as well
        if (!Modifier.isPublic(modifiers)) {
            throw new IllegalArgumentException();
        }

        ClassWriter cw = InstrumentUtils.newClassWriter();
        cw.visit(V1_1, ACC_PUBLIC, className, null, "java/lang/Object", 
                 new String[] { "java/lang/Runnable" });
        // creates a MethodWriter for the (implicit) constructor
        MethodVisitor mw = cw.visitMethod(ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        // pushes the 'this' variable
        mw.visitVarInsn(ALOAD, 0);
        // invokes the super class constructor
        mw.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        mw.visitInsn(RETURN);     
        mw.visitMaxs(1, 1);
        mw.visitEnd();

        // creates a MethodWriter for the 'main' method
        mw = cw.visitMethod(ACC_PUBLIC,
                "run",
                "()V",
                null,
                null);
        // invokes the given method 
        mw.visitMethodInsn(INVOKESTATIC,
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method));
        mw.visitInsn(RETURN);        
        mw.visitMaxs(1, 1);
        mw.visitEnd();        
        return cw.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java com.sun.btrace.runtime.RunnableGenartor <class>");
            System.exit(1);
        }

        Class clazz = Class.forName(args[0]);
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            int index = 0;
            RunnableGenerator gen = new RunnableGeneratorImpl();
            if (Modifier.isStatic(modifiers) ||
                Modifier.isPublic(modifiers) ||
                method.getParameterTypes().length == 0) {
                try {
                    final String className = "Runnable$" + index;
                    final byte[] bytes = gen.generate(method, className);
                    ClassLoader loader = new ClassLoader() {
                        public Class findClass(String name) 
                            throws ClassNotFoundException {
                            if (name.equals(className)) {
                                return defineClass(className, bytes, 0, bytes.length);
                            }
                            throw new ClassNotFoundException(name);       
                        }
                    };
                    Runnable r = (Runnable) loader.loadClass(className).newInstance();
                    new Thread(r).start();
                } catch (Exception exp) {
                    exp.printStackTrace();
                }
            }
            index++;
        }
    }
}