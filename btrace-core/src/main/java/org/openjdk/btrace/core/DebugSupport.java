/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
