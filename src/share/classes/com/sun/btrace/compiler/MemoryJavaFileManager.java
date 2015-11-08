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

/*
 * MemoryJavaFileManager.java
 * @author A. Sundararajan
 */
package com.sun.btrace.compiler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.CharBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

/**
 * JavaFileManager that keeps compiled .class bytes in memory.
 * And also can expose input .java "files" from Strings.
 *
 * @author A. Sundararajan
 */
public final class MemoryJavaFileManager extends ForwardingJavaFileManager {

    private List<String> includeDirs;
    private Map<String, byte[]> classBytes;

    public MemoryJavaFileManager(JavaFileManager fileManager, List<String> includeDirs) {
        super(fileManager);
        this.includeDirs = includeDirs;
        classBytes = new HashMap<String, byte[]>();
    }

    public Map<String, byte[]> getClassBytes() {
        return classBytes;
    }

    public void close() throws IOException {
        classBytes = new HashMap<String, byte[]>();
    }

    public void flush() throws IOException {
    }

    /**
     * A file object used to represent Java source coming from a string.
     */
    private static class StringInputBuffer extends SimpleJavaFileObject {

        final String code;

        StringInputBuffer(String name, String code) {
            super(toURI(name), Kind.SOURCE);
            this.code = code;
        }

        public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
            return CharBuffer.wrap(code);
        }

        public Reader openReader() {
            return new StringReader(code);
        }
    }

    /**
     * A file object that stores Java bytecode into the classBytes map.
     */
    private class ClassOutputBuffer extends SimpleJavaFileObject {

        private String name;

        ClassOutputBuffer(String name) {
            super(toURI(name), Kind.CLASS);
            this.name = name;
        }

        public OutputStream openOutputStream() {
            return new FilterOutputStream(new ByteArrayOutputStream()) {

                public void close() throws IOException {
                    out.close();
                    ByteArrayOutputStream bos = (ByteArrayOutputStream) out;
                    classBytes.put(name, bos.toByteArray());
                }
            };
        }
    }

    public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location,
            String className,
            Kind kind,
            FileObject sibling) throws IOException {
        if (kind == Kind.CLASS) {
            return new ClassOutputBuffer(className);
        } else {
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }
    }

    public JavaFileObject getJavaFileForInput(JavaFileManager.Location location,
            String className,
            Kind kind)
            throws IOException {
        JavaFileObject result = super.getJavaFileForInput(location, className, kind);
        if (kind == Kind.SOURCE) {
            return preprocessedFileObject(result, includeDirs);
        } else {
            return result;
        }
    }

    static JavaFileObject preprocessedFileObject(JavaFileObject fo, List<String> includeDirs)
            throws IOException {
        if (includeDirs != null) {
            PCPP pcpp = new PCPP(includeDirs);
            StringWriter out = new StringWriter();
            pcpp.setOut(out);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fo.openInputStream()));
            pcpp.run(reader, fo.getName());
            return new StringInputBuffer(fo.getName(), out.toString());
        } else {
            return fo;
        }
    }

    static JavaFileObject makeStringSource(String name, String code, List<String> includeDirs) {
        if (includeDirs != null) {
            PCPP pcpp = new PCPP(includeDirs);
            StringWriter out = new StringWriter();
            pcpp.setOut(out);
            try {
                pcpp.run(new StringReader(code), name);
            } catch (IOException exp) {
                throw new RuntimeException(exp);
            }
            return new StringInputBuffer(name, out.toString());
        } else {
            return new StringInputBuffer(name, code);
        }
    }

    static URI toURI(String name) {
        File file = new File(name);
        if (file.exists()) {
            return file.toURI();
        } else {
            try {
//                return URI.create("mfm:///" + name.replace('.', '/'));
                return URI.create("mfm:///" + name);
            } catch (Exception exp) {
                return URI.create("mfm:///com/sun/script/java/java_source");
            }
        }
    }
}
