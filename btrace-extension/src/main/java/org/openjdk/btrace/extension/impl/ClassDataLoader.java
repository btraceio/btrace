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

/**
 * ClassLoader that loads classes from {@code .classdata} resources with a configurable path prefix.
 *
 * <p>This provides per-extension classloader isolation: each embedded extension gets its own
 * {@code ClassDataLoader} with a prefix pointing to its section in the masked JAR (e.g.
 * {@code META-INF/btrace-extensions/spark/impl/}). The {@code resourceLoader} (typically the
 * agent's {@code MaskedClassLoader}) reads the bytes via its resource fallback path.
 *
 * <p>API classes remain as regular {@code .class} files in the JAR's bootstrap section and are
 * resolved via parent delegation. Only implementation classes use {@code .classdata}.
 */
public final class ClassDataLoader extends ClassLoader {

  private static final String CLASSDATA_SUFFIX = ".classdata";
  private static final int BUFFER_SIZE = 8192;

  private final String extensionId;
  private final ClassLoader resourceLoader;
  private final String resourcePrefix;

  /**
   * Creates a ClassDataLoader for loading embedded extension implementation classes.
   *
   * @param extensionId extension identifier for diagnostics
   * @param resourceLoader classloader to read {@code .classdata} resources from (typically
   *     the agent's MaskedClassLoader)
   * @param parent parent classloader for delegation (API classes visible via bootstrap)
   * @param resourcePrefix path prefix prepended to class resource paths (e.g.
   *     {@code "META-INF/btrace-extensions/spark/impl/"})
   */
  public ClassDataLoader(
      String extensionId, ClassLoader resourceLoader, ClassLoader parent, String resourcePrefix) {
    super(parent);
    this.extensionId = extensionId;
    this.resourceLoader = resourceLoader;
    this.resourcePrefix = resourcePrefix != null ? resourcePrefix : "";
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    String resourcePath = resourcePrefix + name.replace('.', '/') + CLASSDATA_SUFFIX;
    byte[] classBytes = loadClassData(resourcePath);
    if (classBytes == null) {
      throw new ClassNotFoundException(name + " (no .classdata resource at " + resourcePath + ")");
    }

    if (!isValidClassFile(classBytes)) {
      throw new ClassNotFoundException(name + " (invalid class file format)");
    }

    return defineClass(name, classBytes, 0, classBytes.length);
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

  private static boolean isValidClassFile(byte[] classBytes) {
    if (classBytes == null || classBytes.length < 10) {
      return false;
    }
    return classBytes[0] == (byte) 0xCA
        && classBytes[1] == (byte) 0xFE
        && classBytes[2] == (byte) 0xBA
        && classBytes[3] == (byte) 0xBE;
  }

  public String getExtensionId() {
    return extensionId;
  }

  @Override
  public String toString() {
    return "ClassDataLoader{extension='" + extensionId + "', prefix='" + resourcePrefix + "'}";
  }
}
