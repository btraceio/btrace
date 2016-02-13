/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.DebugSupport;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.JAVA_LANG_OBJECT;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author A. Sundararajan
 * @author J. Bachorik
 */
public final class InstrumentUtils {
    /**
    * Collects the type hierarchy into the provided list, sorted from the actual type to root.
    * Common superclasses may be present multiple times (eg. {@code java.lang.Object})
    * It will use the associated classloader to locate the class file resources.
    * @param cl the associated classloader
    * @param type the type to compute the hierarchy closure for
    * @param closure the list to store the closure in
    */
    public static void collectHierarchyClosure(ClassLoader cl, String type, List<String> closure) {
        if (type == null || type.equals(JAVA_LANG_OBJECT)) {
           return;
        }
        try {
           InputStream typeIs = cl.getResourceAsStream(type + ".class");
           if (typeIs != null) {
                final List<String> mySupers = new LinkedList<>();
                ClassReader cr = new ClassReader(typeIs);
                cr.accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        mySupers.add(superName);
                        mySupers.addAll(Arrays.asList(interfaces));
                        super.visit(version, access, name, signature, superName, interfaces);
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                closure.addAll(mySupers);
                for (String superType : mySupers) {
                    collectHierarchyClosure(cl, superType, closure);
                }
            }
       } catch (IOException e) {
            DebugSupport.warning(e);
       }
    }

    private static class BTraceClassWriter extends ClassWriter {
        private final ClassLoader targetCL;
        public BTraceClassWriter(ClassLoader cl, int flags) {
            super(flags);
            this.targetCL = cl != null ? cl : ClassLoader.getSystemClassLoader();
        }

        public BTraceClassWriter(ClassLoader cl, ClassReader reader, int flags) {
            super(reader, flags);
            this.targetCL = cl != null ? cl : ClassLoader.getSystemClassLoader();
        }

        protected String getCommonSuperClass(String type1, String type2) {
            // Using type closures resolved via the associate classloader
            List<String> type1Closure = new LinkedList<>();
            List<String> type2Closure = new LinkedList<>();
            collectHierarchyClosure(targetCL, type1, type1Closure);
            collectHierarchyClosure(targetCL, type2, type2Closure);
            // basically, do intersection
            type1Closure.retainAll(type2Closure);

            // if the intersection is not empty the first element is the closest common ancestor
            Iterator<String> iter = type1Closure.iterator();
            if (iter.hasNext()) {
                String type = iter.next();
                return type;
            }

            return JAVA_LANG_OBJECT;
        }
    }
    public static String arrayDescriptorFor(int typeCode) {
        switch (typeCode) {
            case T_BOOLEAN:
                return "[Z";
            case T_CHAR:
                return "[C";
            case T_FLOAT:
                return "[F";
            case T_DOUBLE:
                return "[D";
            case T_BYTE:
                return "[B";
            case T_SHORT:
                return "[S";
            case T_INT:
                return "[I";
            case T_LONG:
                return "[J";
            default:
                throw new IllegalArgumentException();
        }
    }

    public static void accept(ClassReader reader, ClassVisitor visitor) {
        accept(reader, visitor, ClassReader.SKIP_FRAMES);
    }

    public static void accept(ClassReader reader, ClassVisitor visitor, int flags) {
        reader.accept(visitor, flags);
    }

    private static boolean isJDK16OrAbove(byte[] code) {
        // skip 0xCAFEBABE magic and minor version
        final int majorOffset = 4 + 2;
        int major = (((code[majorOffset] << 8) & 0xFF00) |
                ((code[majorOffset + 1]) & 0xFF));
        return major >= 50;
    }

    public static ClassWriter newClassWriter() {
        return newClassWriter(null, null, ClassWriter.COMPUTE_FRAMES);
    }

    public static ClassWriter newClassWriter(byte[] code) {
        int flags = ClassWriter.COMPUTE_MAXS;
        if (isJDK16OrAbove(code)) {
            flags = ClassWriter.COMPUTE_FRAMES;
        }
        return newClassWriter(null, code);
    }

    public static ClassWriter newClassWriter(ClassLoader cl, byte[] code) {
        int flags = ClassWriter.COMPUTE_MAXS;
        if (isJDK16OrAbove(code)) {
            flags = ClassWriter.COMPUTE_FRAMES;
        }
        return newClassWriter(cl, new ClassReader(code), flags);
    }

    public static ClassWriter newClassWriter(ClassLoader cl, ClassReader reader, int flags) {
        ClassWriter cw = null;
        cw = reader != null ? new BTraceClassWriter(cl, reader, flags) :
                              new BTraceClassWriter(cl, flags);

        return cw;
    }

    public static final String getActionPrefix(String className) {
        return Constants.BTRACE_METHOD_PREFIX + className.replace('/', '$') + "$";
    }
}