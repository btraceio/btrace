/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import static com.sun.btrace.runtime.Constants.OBJECT_INTERNAL;

/**
 * @author A. Sundararajan
 * @author J. Bachorik
 */
public final class InstrumentUtils {
    private static final int CW_FLAGS = 0; // ClassWriter.COMPUTE_MAXS;
    
    /**
    * Collects the type hierarchy into the provided list, sorted from the actual type to root.
    * Common superclasses may be present multiple times (eg. {@code java.lang.Object})
    * It will use the associated classloader to locate the class file resources.
    * @param cl the associated classloader
    * @param type the type to compute the hierarchy closure for (either Java or internal name format)
    * @param closure the ordered set to store the closure in
    * @param useInternal should internal types names be used in the closure
    */
    public static void collectHierarchyClosure(ClassLoader cl, String type,
                                               Set<String> closure, boolean useInternal) {
        if (type == null || type.equals(OBJECT_INTERNAL)) {
           return;
        }
        ClassInfo ci = ClassCache.getInstance().get(cl, type);

        Set<ClassInfo> ciSet = new LinkedHashSet<>();

        // add self
        ciSet.add(ci);
        for(ClassInfo sci : ci.getSupertypes(false)) {
            if (!sci.isInterface() && !sci.getClassName().equals(OBJECT_INTERNAL)) {
                ciSet.add(sci);
            }
        }

        for (ClassInfo sci : ciSet) {
            closure.add(useInternal ? sci.getClassName() : sci.getJavaClassName());
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

    public static void accept(BTraceClassReader reader, ClassVisitor visitor) {
        accept(reader, visitor, 0);
    }

    public static void accept(BTraceClassReader reader, ClassVisitor visitor, int flags) {
        if (reader == null || visitor == null) return;

        reader.accept(visitor, flags);
    }

    private static boolean isJDK16OrAbove(byte[] code) {
        return isJDK16OrAbove(getMajor(code));
    }

    private static boolean isJDK16OrAbove(BTraceClassReader cr) {
        return isJDK16OrAbove(getMajor(cr));
    }

    private static boolean isJDK16OrAbove(int major) {
        return major >= 50;
    }

    private static int getMajor(BTraceClassReader cr) {
        return cr.getClassVersion();
    }

    private static int getMajor(byte[] code) {
        // skip 0xCAFEBABE magic and minor version
        final int majorOffset = 4 + 2;
        return (((code[majorOffset] << 8) & 0xFF00) |
               ((code[majorOffset + 1]) & 0xFF));
    }

    public static ClassWriter newClassWriter() {
        return newClassWriter(false);
    }

    public static ClassWriter newClassWriter(boolean computeFrames) {
        return newClassWriter(null, CW_FLAGS | ClassWriter.COMPUTE_FRAMES);
    }

    static BTraceClassWriter newClassWriter(ClassLoader cl, byte[] code) {
//        if (isJDK16OrAbove(code)) {
//            flags = ClassWriter.COMPUTE_FRAMES;
//        }
        return newClassWriter(new BTraceClassReader(cl, code), CW_FLAGS);
    }

    static BTraceClassWriter newClassWriter(BTraceClassReader cr) {

//        if (isJDK16OrAbove(cr)) {
//            flags = ClassWriter.COMPUTE_FRAMES;
//        }
        return newClassWriter(cr, CW_FLAGS);
    }

    static BTraceClassWriter newClassWriter(BTraceClassReader reader, int flags) {
        BTraceClassWriter cw = null;
        cw = reader != null ? new BTraceClassWriter(reader.getClassLoader(), reader, flags) :
                              new BTraceClassWriter(null, flags);

        return cw;
    }

    static BTraceClassReader newClassReader(byte[] code) {
        return new BTraceClassReader(ClassLoader.getSystemClassLoader(), code);
    }

    static BTraceClassReader newClassReader(ClassLoader cl, byte[] code) {
        return new BTraceClassReader(cl, code);
    }

    static BTraceClassReader newClassReader(InputStream is)
    throws IOException{
        return new BTraceClassReader(ClassLoader.getSystemClassLoader(), is);
    }

    static BTraceClassReader newClassReader(ClassLoader cl, InputStream is)
    throws IOException{
        return new BTraceClassReader(cl, is);
    }

    static final String getActionPrefix(String className) {
        return Constants.BTRACE_METHOD_PREFIX + className.replace('/', '$') + "$";
    }
}