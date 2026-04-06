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
package org.openjdk.btrace.compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JavaFileManager that can read .classdata files from a masked JAR.
 * <p>
 * This file manager wraps another JavaFileManager and intercepts requests for class files.
 * When javac requests a class file (e.g., to resolve an annotation), this manager first
 * checks if the class exists as a .classdata file in the masked JAR's shared section.
 * If found, it returns a ClassDataJavaFileObject that reads from the .classdata file.
 * Otherwise, it delegates to the wrapped file manager.
 * <p>
 * This allows javac to find and process annotation classes that are stored as .classdata
 * files instead of .class files.
 *
 * @author Jaroslav Bachorik
 */
class MaskedJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private static final Logger log = LoggerFactory.getLogger(MaskedJavaFileManager.class);
    private static final boolean DEBUG = Boolean.getBoolean("btrace.compiler.debug");

    private final File maskedJarFile;
    private final JarFile jarFile;

    /**
     * Creates a new MaskedJavaFileManager.
     *
     * @param fileManager the file manager to wrap
     * @param maskedJar   the masked JAR file containing .classdata files
     * @throws IOException if the JAR file cannot be opened
     */
    MaskedJavaFileManager(JavaFileManager fileManager, File maskedJar) throws IOException {
        super(fileManager);
        this.maskedJarFile = maskedJar;
        this.jarFile = new JarFile(maskedJar);
        debug("Created MaskedJavaFileManager for: " + maskedJar.getAbsolutePath());
    }

    @Override
    public JavaFileObject getJavaFileForInput(
            Location location, String className, Kind kind) throws IOException {
        debug("getJavaFileForInput: location=" + location + ", className=" + className + ", kind=" + kind);

        // Only intercept CLASS kind requests (not SOURCE)
        if (kind == Kind.CLASS) {
            // Check if this class exists as .classdata in the shared section
            String classDataPath = "META-INF/btrace/shared/" + className.replace('.', '/') + ".classdata";
            JarEntry entry = jarFile.getJarEntry(classDataPath);

            if (entry != null) {
                debug("Found .classdata for: " + className);
                return new ClassDataJavaFileObject(className, jarFile, entry);
            }
        }

        // Fall back to standard file manager
        return super.getJavaFileForInput(location, className, kind);
    }

    @Override
    public Iterable<JavaFileObject> list(
            Location location,
            String packageName,
            Set<Kind> kinds,
            boolean recurse)
            throws IOException {
        debug("list: location=" + location + ", package=" + packageName + ", kinds=" + kinds);

        // Get the standard list first
        Iterable<JavaFileObject> standardFiles = super.list(location, packageName, kinds, recurse);

        // If not looking for CLASS files, just return standard list
        if (!kinds.contains(Kind.CLASS)) {
            return standardFiles;
        }

        List<JavaFileObject> result = new ArrayList<>();
        if (standardFiles != null) {
            for (JavaFileObject jfo : standardFiles) {
                result.add(jfo);
            }
        }

        // Add .classdata files from the shared section for this package
        String packagePath = packageName.replace('.', '/');
        String sharedPackagePath = "META-INF/btrace/shared/" + packagePath;

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            // Check if this entry is in the requested package
            if (name.startsWith(sharedPackagePath + "/") && name.endsWith(".classdata")) {
                // Extract the simple name and check if it's a direct child (not recursive)
                String relativePath = name.substring(sharedPackagePath.length() + 1);
                boolean isDirectChild = !relativePath.substring(0, relativePath.lastIndexOf('.')).contains("/");

                if (recurse || isDirectChild) {
                    // Convert path to class name
                    String className = name.substring("META-INF/btrace/shared/".length())
                            .replace('/', '.')
                            .replace(".classdata", "");
                    debug("Found .classdata in list(): " + className);
                    result.add(new ClassDataJavaFileObject(className, jarFile, entry));
                }
            }
        }

        return result;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        // If it's our custom ClassDataJavaFileObject, we know the binary name
        if (file instanceof ClassDataJavaFileObject) {
            ClassDataJavaFileObject cdfo = (ClassDataJavaFileObject) file;
            String binaryName = cdfo.inferBinaryName();
            debug("inferBinaryName: " + binaryName + " for " + file.getName());
            return binaryName;
        }

        // Otherwise delegate to parent
        return super.inferBinaryName(location, file);
    }

    @Override
    public void close() throws IOException {
        try {
            jarFile.close();
        } finally {
            super.close();
        }
    }

    private void debug(String msg) {
        if (DEBUG) {
            log.debug("[MaskedFileManager] {}", msg);
        }
    }
}
