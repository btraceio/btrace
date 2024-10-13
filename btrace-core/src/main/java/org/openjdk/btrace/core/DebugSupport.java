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
package org.openjdk.btrace.core;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.impl.SimpleLogger;

/**
 * Centralized support for logging various debug information.
 *
 * @author Jaroslav Bachorik
 */
public final class DebugSupport {
  public static void initLoggers(boolean debug, Logger logger) {
    String logFile = System.getProperty("org.slf4j.simpleLogger.logFile");
    System.setProperty("org.slf4j.simpleLogger.logFile", logFile != null ? logFile : "System.out");
    String defaultLevel =
        System.getProperty("org.slf4j.simpleLogger.log.org.openjdk.btrace", "info");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", debug ? "debug" : defaultLevel);
    try {
      Method mthd = SimpleLogger.class.getDeclaredMethod("init");
      mthd.setAccessible(true);
      mthd.invoke(null);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      System.err.println("[btrace] Unable to reload logger config");
    }
    if (logger != null) {
      try {
        Field fld = logger.getClass().getDeclaredField("currentLogLevel");
        fld.setAccessible(true);
        fld.set(
            logger,
            debug
                ? 10
                : 20); // 10 is the 'debug' level for SLF4J SimpleLogger, 20 is the info level
      } catch (NoSuchFieldException | IllegalAccessException e) {
        System.err.println("[btrace] Unable to set debug log level");
        e.printStackTrace(System.err);
      }
    }
  }

  private final SharedSettings settings;

  public DebugSupport(SharedSettings s) {
    settings = s != null ? s : SharedSettings.GLOBAL;
  }

  public boolean isDebug() {
    return settings.isDebug();
  }

  public boolean isDumpClasses() {
    return settings.isDumpClasses();
  }

  public String getDumpClassDir() {
    return settings.getDumpDir();
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
          file = className.substring(index + 1);
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
}
