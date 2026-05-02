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
 * <p>API classes remain as {@code .class} files and are loaded normally via bootstrap
 * (Boot-Class-Path). Implementation classes are stored as {@code .classdata} and loaded by this
 * custom classloader.
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
    this.resourceLoader = resourceLoader;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    // Check cache first
    Class<?> cached = loadedClasses.get(name);
    if (cached != null) {
      return cached;
    }

    String resourcePath = name.replace('.', '/') + CLASSDATA_SUFFIX;
    byte[] classBytes = loadClassData(resourcePath);
    if (classBytes == null) {
      throw new ClassNotFoundException(name + " (no .classdata resource found)");
    }

    // Validate bytecode before defining - ensures we're loading a valid class file
    if (!isValidClassFile(classBytes)) {
      throw new ClassNotFoundException(name + " (invalid class file format)");
    }

    Class<?> clazz = defineClass(name, classBytes, 0, classBytes.length);
    Class<?> existing = loadedClasses.putIfAbsent(name, clazz);
    return existing != null ? existing : clazz;
  }

  private byte[] loadClassData(String resourcePath) {
    try (InputStream is = resourceLoader.getResourceAsStream(resourcePath)) {
      if (is == null) {
        return null;
      }
      return readAllBytes(is);
    } catch (IOException e) {
      return null;
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
