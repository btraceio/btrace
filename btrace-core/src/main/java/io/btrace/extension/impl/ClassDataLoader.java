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
package io.btrace.extension.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ClassLoader that loads classes from {@code .classdata} resources.
 *
 * <p>This approach is inspired by dd-trace-java: implementation classes are renamed from {@code
 * .class} to {@code .classdata} at build time, allowing them to be stored in the agent JAR without
 * polluting the bootstrap classloader or requiring temp file extraction.
 *
 * <p>API classes remain as {@code .class} files and are loaded normally via bootstrap
 * (Boot-Class-Path). Implementation classes are stored as {@code .classdata} under the extension's
 * own prefix, {@code META-INF/btrace-extensions/<id>/<package path>.classdata}, and loaded by this
 * custom classloader. The prefix is what keeps embedded extensions apart: two extensions may each
 * ship a class of the same name, and every loader only ever sees the class data of the extension it
 * was created for. A {@code .classdata} resource outside the prefix is never consulted.
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

  /** Root of the per-extension class data trees inside an embedding JAR. */
  public static final String EXTENSIONS_BASE = "META-INF/btrace-extensions/";

  private static final String CLASSDATA_SUFFIX = ".classdata";
  private static final int BUFFER_SIZE = 8192;

  private final ClassLoader resourceLoader;
  private final String extensionId;
  private final ConcurrentMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

  /**
   * Creates a ClassDataLoader for loading embedded extension implementation classes.
   *
   * @param extensionId extension identifier; selects the {@code META-INF/btrace-extensions/<id>/}
   *     tree this loader reads class data from
   * @param resourceLoader classloader to read {@code .classdata} resources from
   * @param parent parent classloader (typically BTrace boot classloader with API classes)
   * @throws IllegalArgumentException if {@code extensionId} is null, empty, or contains a path
   *     separator (an id is a single path segment of the resource prefix)
   */
  public ClassDataLoader(String extensionId, ClassLoader resourceLoader, ClassLoader parent) {
    super(parent);
    if (extensionId == null || extensionId.isEmpty()) {
      throw new IllegalArgumentException("extensionId must not be null or empty");
    }
    if (extensionId.indexOf('/') >= 0 || extensionId.indexOf('\\') >= 0) {
      throw new IllegalArgumentException(
          "extensionId must be a single path segment, got '" + extensionId + "'");
    }
    this.extensionId = extensionId;
    this.resourceLoader =
        resourceLoader != null ? resourceLoader : ClassLoader.getSystemClassLoader();
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
      String resourcePath = resourcePath(extensionId, name);
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

  /**
   * Returns the resource path a class of an embedded extension is stored at.
   *
   * @param extensionId extension identifier
   * @param className binary class name
   * @return {@code META-INF/btrace-extensions/<id>/<package path>.classdata}
   */
  public static String resourcePath(String extensionId, String className) {
    return EXTENSIONS_BASE + extensionId + '/' + className.replace('.', '/') + CLASSDATA_SUFFIX;
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
   *
   * <ul>
   *   <li>Minimum length for a class file header
   *   <li>Magic number 0xCAFEBABE at the start
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
