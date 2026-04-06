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
package org.openjdk.btrace.boot;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

/**
 * Utility methods for working with masked JAR files.
 *
 * @author Jaroslav Bachorik
 */
public final class MaskedJarUtils {
    private MaskedJarUtils() {
        // Utility class
    }

    /**
     * Finds a masked JAR file in the given classpath.
     * A masked JAR is identified by containing META-INF/btrace/shared/ directory.
     *
     * @param classPath the classpath string (File.pathSeparator separated)
     * @return the masked JAR file, or null if not found
     */
    public static File findMaskedJarInClasspath(String classPath) {
        if (classPath == null) {
            return null;
        }

        for (String entry : classPath.split(File.pathSeparator)) {
            File file = new File(entry);
            if (isMaskedJar(file)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Finds a masked JAR file in the given classpath entries.
     * A masked JAR is identified by containing META-INF/btrace/shared/ directory.
     *
     * @param cpEntries the classpath entries array
     * @return the masked JAR file, or null if not found
     */
    public static File findMaskedJarInClasspath(String[] cpEntries) {
        if (cpEntries == null) {
            return null;
        }

        for (String entry : cpEntries) {
            File file = new File(entry);
            if (isMaskedJar(file)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Checks if the given file is a masked JAR.
     * A masked JAR is identified by containing META-INF/btrace/shared/ directory.
     *
     * @param file the file to check
     * @return true if the file is a masked JAR, false otherwise
     */
    public static boolean isMaskedJar(File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.getName().endsWith(".jar")) {
            return false;
        }

        try (JarFile jar = new JarFile(file)) {
            // Look for the shared section marker
            return jar.getEntry("META-INF/btrace/shared/") != null;
        } catch (IOException e) {
            return false;
        }
    }
}
