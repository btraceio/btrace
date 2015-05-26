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

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.JAVA_LANG_OBJECT;

/**
 * @author A. Sundararajan
 */
public class InstrumentUtils {
    private static class BTraceClassWriter extends ClassWriter {
        /**
         * This delegating classloader allows to check whether a particular
         * class has already been loaded by any classloader in the hierarchy
         */
        private static final class CheckingClassLoader extends ClassLoader {
            public CheckingClassLoader(ClassLoader parent) {
                super(parent);
            }

            public boolean isClassLoaded(String name) {
                Class<?> clz = null;
                try {
                    clz = findClass(name);
                } catch (ClassNotFoundException e) {
                    clz = null;
                }
                return clz != null;
            }
        }

        private static final CheckingClassLoader CHECKING_CL =
                new CheckingClassLoader(ClassWriter.class.getClassLoader());

        public BTraceClassWriter(int flags) {
            super(flags);
        }

        public BTraceClassWriter(ClassReader reader, int flags) {
            super(reader, flags);
        }

        protected String getCommonSuperClass(String type1, String type2) {
            // FIXME: getCommonSuperClass is called by ClassWriter to merge two types
            // - persumably to compute stack frame attribute. We get LinkageError
            // when one of the types is the one being written/prepared by this ClassWriter
            // itself! So, I catch LinkageError and return "java/lang/Object" in such cases.
            // Revisit this for a possible better solution.
            try {
                type1 = getResolvedType(type1);
                type2 = getResolvedType(type2);
                return super.getCommonSuperClass(type1, type2);
            } catch (LinkageError le) {
                return JAVA_LANG_OBJECT;
            } catch (RuntimeException re) {
                return JAVA_LANG_OBJECT;
            }
        }

        private static String getResolvedType(String type) {
            return CHECKING_CL.isClassLoaded(type.replace('/', '.')) ? type : "java/lang/Object";
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
        return newClassWriter(null, ClassWriter.COMPUTE_FRAMES);
    }

    public static ClassWriter newClassWriter(byte[] code) {
        int flags = ClassWriter.COMPUTE_MAXS;
        if (isJDK16OrAbove(code)) {
            flags = ClassWriter.COMPUTE_FRAMES;
        }
        return newClassWriter(null, flags);
    }

    public static ClassWriter newClassWriter(ClassReader reader, int flags) {
        ClassWriter cw = null;
        cw = reader != null ? new BTraceClassWriter(reader, flags) :
                              new BTraceClassWriter(flags);

        return cw;
    }
}