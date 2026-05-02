/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.btrace.extension.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ClassLoader that loads classes from {@code .classdata} resources.
 *
 * <p>This approach is inspired by dd-trace-java: implementation classes are renamed from {@code
 * .class} to {@code .classdata} at build time, allowing them to be flattened into the agent JAR
 * without polluting the bootstrap classloader or requiring temp file extraction.
 *
 * <p>API classes remain as {@code .class} files and are loaded normally via bootstrap (Boot-Class-Path).
 * Implementation classes are stored as {@code .classdata} and loaded by this custom classloader.
 *
 * <p>Benefits:
 *
 * <ul>
 *   <li>Single flat JAR (no nested JARs)
 *   <li>No temp file extraction (works in restricted environments)
 *   <li>No bootstrap pollution with implementation details
 *   <li>Clean classloader isolation
 * </ul>
 */
public final class ClassDataLoader extends ClassLoader {

  static {
    // Register as parallel-capable so getClassLoadingLock(name) works correctly.
    // Canonical pattern — see ClassLoader.registerAsParallelCapable() javadoc:
    // "This method should be called during class initialization."
    ClassLoader.registerAsParallelCapable();
  }

  private static final String CLASSDATA_SUFFIX = ".classdata";
  private static final int BUFFER_SIZE = 8192;

  private final ClassLoader resourceLoader;
  private final String extensionId;
  private final ConcurrentMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

  /**
   * Creates a ClassDataLoader for loading embedded extension implementation classes.
   *
   * @param extensionId extension identifier for diagnostics
   * @param resourceLoader classloader to read {@code .classdata} resources from
   * @param parent parent classloader (typically BTrace boot classloader with API classes)
   */
  public ClassDataLoader(String extensionId, ClassLoader resourceLoader, ClassLoader parent) {
    super(parent);
    this.extensionId = extensionId;
    this.resourceLoader = resourceLoader != null ? resourceLoader : ClassLoader.getSystemClassLoader();
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    // Lock-free fast path for warm cache: avoids taking the per-name class-loading lock
    // when the class has already been defined.
    Class<?> cached = loadedClasses.get(name);
    if (cached != null) return cached;

    // Serialize the defineClass path per name so two threads racing on the same class
    // cannot both reach defineClass and trigger a LinkageError.
    synchronized (getClassLoadingLock(name)) {
      cached = loadedClasses.get(name);
      if (cached != null) return cached;
      String resourcePath = name.replace('.', '/') + CLASSDATA_SUFFIX;
      byte[] classBytes;
      try {
        classBytes = loadClassData(resourcePath);
      } catch (IOException e) {
        throw new ClassNotFoundException(name + " (error reading .classdata resource)", e);
      }
      if (classBytes == null) {
        throw new ClassNotFoundException(name + " (no .classdata resource found)");
      }
      if (!isValidClassFile(classBytes)) {
        throw new ClassNotFoundException(name + " (invalid class file format)");
      }
      Class<?> clazz = defineClass(name, classBytes, 0, classBytes.length);
      loadedClasses.put(name, clazz);
      return clazz;
    }
  }

  private byte[] loadClassData(String resourcePath) throws IOException {
    try (InputStream is = resourceLoader.getResourceAsStream(resourcePath)) {
      if (is == null) {
        return null;
      }
      return readAllBytes(is);
    }
  }

  private static byte[] readAllBytes(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[BUFFER_SIZE];
    int read;
    while ((read = is.read(buffer)) != -1) {
      baos.write(buffer, 0, read);
    }
    return baos.toByteArray();
  }

  /**
   * Returns the extension identifier.
   *
   * @return extension ID
   */
  public String getExtensionId() {
    return extensionId;
  }

  @Override
  public String toString() {
    return "ClassDataLoader{extension='" + extensionId + "', classes=" + loadedClasses.size() + "}";
  }

  /**
   * Validates that the byte array represents a valid Java class file.
   *
   * <p>This performs basic structural validation:
   * <ul>
   *   <li>Minimum length for a class file header</li>
   *   <li>Magic number 0xCAFEBABE at the start</li>
   * </ul>
   *
   * @param classBytes the bytes to validate
   * @return true if the bytes appear to be a valid class file
   */
  private static boolean isValidClassFile(byte[] classBytes) {
    // Minimum class file size: magic(4) + version(4) + constant_pool_count(2) = 10 bytes
    // In practice, smallest valid class is larger, but this catches obvious corruption
    if (classBytes == null || classBytes.length < 10) {
      return false;
    }
    // Check for Java class file magic number: 0xCAFEBABE
    return classBytes[0] == (byte) 0xCA
        && classBytes[1] == (byte) 0xFE
        && classBytes[2] == (byte) 0xBA
        && classBytes[3] == (byte) 0xBE;
  }
}
