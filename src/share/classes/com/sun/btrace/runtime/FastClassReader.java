/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author Jaroslav Bachorik
 */
public class FastClassReader extends ClassReader {
    /**
     * Dummy, non-stack-collecting runtime exception. It is used for execution control in ClassReader instances in order
     * to avoid processing the complete class file when the relevant info is available right at the beginning of
     * parsing.
     */
    private static final class BailoutException extends RuntimeException {
        /**
         * Shared instance to optimize the cost of throwing
         */
        private static final BailoutException INSTANCE = new BailoutException();

        private BailoutException() {
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // we don't need the stack here
            return this;
        }
    }

    public FastClassReader(byte[] bytes) {
        super(bytes);
    }

    public FastClassReader(byte[] bytes, int i, int i1) {
        super(bytes, i, i1);
    }

    public FastClassReader(InputStream in) throws IOException {
        super(in);
    }

    public FastClassReader(String string) throws IOException {
        super(string);
    }

    public String[] readClassSupers() {
        int u = header; // current offset in the class file
        char[] c = new char[getMaxStringLength()]; // buffer used to read strings

        // reads the class declaration
        int ifcsLen = readUnsignedShort(u + 6);
        String[] info = new String[ifcsLen + 1];
        // supertype name
        info[0] = readClass(u + 4, c);
        u += 8;
        // interfaces, if any
        for (int i = 0; i < ifcsLen; ++i) {
            info[1 + i] = readClass(u, c);
            u += 2;
        }
        return info;
    }

    @Override
    public void accept(ClassVisitor cv, Attribute[] atrbts, int i) {
        try {
            super.accept(cv, atrbts, i);
        } catch (BailoutException e) {
            // expected; ignore
        }
    }

    @Override
    public void accept(ClassVisitor cv, int i) {
        try {
            super.accept(cv, i);
        } catch (BailoutException e) {
            // expected; ignore
        }
    }

    public static void bailout() {
        throw BailoutException.INSTANCE;
    }
}
