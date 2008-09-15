/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package net.java.btrace.visualvm.compiler;

import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Jaroslav Bachorik
 */
final public class BCompiler {
    private Object compilerDelegate;
    private Method compileMethod;
    private String toolsJarPath;
    private String clientPath;

    Class stringClz;

    private static Pattern classNamePattern = Pattern.compile("@BTrace\\s*.+?\\s*class\\s*(.*?)\\s+\\{", Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNIX_LINES);

    public BCompiler(String clientPath, String toolsJarPath) throws InstantiationException {
        this.clientPath = clientPath;
        this.toolsJarPath = toolsJarPath;
        
        ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader cl = new ClassLoaderEx(clientPath);

            Thread.currentThread().setContextClassLoader(cl);

            Class compilerClz = cl.loadClass("com.sun.btrace.compiler.Compiler");
            Constructor compilerNew = compilerClz.getConstructor();
            compilerDelegate = compilerNew.newInstance();
            compileMethod = compilerClz.getMethod("compile", String.class, String.class, Writer.class, String.class, String.class);
        } catch (Exception e) {
            throw new InstantiationException(e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(origLoader);
        }
    }

    public byte[] compile(String source, String classPath) {
        return compile(source, classPath, null);
    }

    public byte[] compile(String source, String classPath, Writer errorWriter) {
        try {
            Matcher matcher = classNamePattern.matcher(source);
            if (matcher.find()) {
                if (errorWriter == null) {
                    errorWriter = new PrintWriter(System.out);
                }
                String fileName = matcher.group(1) + ".java";
                String completeCP = toolsJarPath + File.pathSeparator + clientPath + File.pathSeparator + classPath;
                Map<String, byte[]> compilationMap = (Map<String, byte[]>) compileMethod.invoke(compilerDelegate, fileName, source, errorWriter, ".", completeCP);
                if (compilationMap != null) {
                    return compilationMap.values().iterator().next();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
}
