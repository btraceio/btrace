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
package io.btrace.boot;

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
   * Finds a masked JAR file in the given classpath. A masked JAR is identified by containing
   * META-INF/btrace/shared/ directory.
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
   * Finds a masked JAR file in the given classpath entries. A masked JAR is identified by
   * containing META-INF/btrace/shared/ directory.
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
   * Checks if the given file is a masked JAR. A masked JAR is identified by containing
   * META-INF/btrace/shared/ directory.
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
