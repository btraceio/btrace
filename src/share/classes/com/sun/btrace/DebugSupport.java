/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Centralized support for logging various debug information.
 * @author Jaroslav Bachorik
 */
final public class DebugSupport {
    private final SharedSettings settings;

    public DebugSupport(SharedSettings s) {
        settings = s != null ? s : new SharedSettings();
    }

    public boolean isDebug() {
        return settings.isDebug();
    }

    public boolean isDumpClasses() {
        return settings.isDumpClasses();
    }

    public void dumpClass(String className, byte[] code) {
        if (settings.isDumpClasses()) {
            try {
                className = className.replace(".", File.separator).replace("/", File.separator);
                int index = className.lastIndexOf(File.separatorChar);
                StringBuilder buf = new StringBuilder();
                if (!settings.getDumpDir().equals(".")) {
                    buf.append(settings.getDumpDir());
                    buf.append(File.separatorChar);
                }
                String dir = buf.toString();
                if (index != -1) {
                    dir += className.substring(0, index);
                }
                new File(dir).mkdirs();
                String file;
                if (index != -1) {
                    file = className.substring(index+1);
                } else {
                    file = className;
                }
                file += ".class";
                new File(dir).mkdirs();
                File out = new File(dir, file);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(code);
                }
            } catch (Exception exp) {
                exp.printStackTrace();
            }
        }
    }

    public static void info(String msg) {
        System.out.println("btrace INFO: " + msg);
    }

    public void debug(String msg) {
        if (settings.isDebug()) {
            System.out.println("btrace DEBUG: " + msg);
        }
    }

    public void debug(Throwable th) {
        if (settings.isDebug()) {
            System.out.println("btrace DEBUG: " + th);
            th.printStackTrace(System.out);
        }
    }

    public static void warning(String msg) {
        System.err.println("btrace WARNING: " + msg);
    }

    public static void warning(Throwable th) {
        System.err.println("btrace WARNING: " + th);
        th.printStackTrace(System.out);
    }
}
