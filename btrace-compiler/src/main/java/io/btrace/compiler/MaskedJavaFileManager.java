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
package io.btrace.compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JavaFileManager that can read .classdata files from a masked JAR.
 *
 * <p>This file manager wraps another JavaFileManager and intercepts requests for class files. When
 * javac requests a class file (e.g., to resolve an annotation), this manager first checks if the
 * class exists as a .classdata file in the masked JAR's shared section. If found, it returns a
 * ClassDataJavaFileObject that reads from the .classdata file. Otherwise, it delegates to the
 * wrapped file manager.
 *
 * <p>This allows javac to find and process annotation classes that are stored as .classdata files
 * instead of .class files.
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
   * @param maskedJar the masked JAR file containing .classdata files
   * @throws IOException if the JAR file cannot be opened
   */
  MaskedJavaFileManager(JavaFileManager fileManager, File maskedJar) throws IOException {
    super(fileManager);
    this.maskedJarFile = maskedJar;
    this.jarFile = new JarFile(maskedJar);
    debug("Created MaskedJavaFileManager for: " + maskedJar.getAbsolutePath());
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
      throws IOException {
    debug(
        "getJavaFileForInput: location="
            + location
            + ", className="
            + className
            + ", kind="
            + kind);

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
      Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
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
        boolean isDirectChild =
            !relativePath.substring(0, relativePath.lastIndexOf('.')).contains("/");

        if (recurse || isDirectChild) {
          // Convert path to class name
          String className =
              name.substring("META-INF/btrace/shared/".length())
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
