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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension classloader that loads from nested JAR structure.
 *
 * <p>Extension JARs contain two nested JARs:
 * <ul>
 *   <li>api.jar - API classes added to bootstrap classpath
 *   <li>impl.jar - Implementation classes loaded in this classloader
 * </ul>
 *
 * <p>This classloader extracts both nested JARs to a temp directory, adds the API JAR to
 * bootstrap classpath via Instrumentation, and loads implementation classes from impl.jar.
 */
public final class NestedJarExtensionClassLoader extends URLClassLoader {
  private static final Logger log = LoggerFactory.getLogger(NestedJarExtensionClassLoader.class);

  private final String extensionId;
  private final String version;
  private final Path apiJarPath;
  private final Path implJarPath;
  private final Path tempDir;

  /**
   * Create a nested JAR extension classloader.
   *
   * @param extensionId extension identifier
   * @param version extension version
   * @param extensionJar path to extension JAR containing api.jar and impl.jar
   * @param parent parent classloader (typically BTrace boot classloader)
   * @param instrumentation instrumentation instance for adding API to bootstrap classpath
   * @throws IOException if nested JARs cannot be extracted
   */
  public NestedJarExtensionClassLoader(
      String extensionId,
      String version,
      Path extensionJar,
      ClassLoader parent,
      Instrumentation instrumentation)
      throws IOException {
    super(new URL[0], parent);

    this.extensionId = extensionId;
    this.version = version;

    // Create temp directory for this extension
    this.tempDir = Files.createTempDirectory("btrace-ext-" + extensionId + "-");
    this.tempDir.toFile().deleteOnExit();

    log.debug("Extracting nested JARs from {} to {}", extensionJar, tempDir);

    // Extract nested JARs
    try (JarFile jar = new JarFile(extensionJar.toFile())) {
      apiJarPath = extractNestedJar(jar, "api.jar", tempDir);
      implJarPath = extractNestedJar(jar, "impl.jar", tempDir);
    }

    log.debug("Extracted api.jar to {} and impl.jar to {}", apiJarPath, implJarPath);

    // Add API JAR to bootstrap classpath
    if (instrumentation != null) {
      JarFile apiJar = new JarFile(apiJarPath.toFile());
      instrumentation.appendToBootstrapClassLoaderSearch(apiJar);
      log.info("Added extension API to bootstrap classpath: {} ({})", extensionId, apiJarPath.getFileName());
    } else {
      log.warn("Instrumentation not available, cannot add API JAR to bootstrap: {}", extensionId);
    }

    // Add impl JAR to this classloader's URLs
    addURL(implJarPath.toUri().toURL());
    log.debug("Added impl JAR to extension classloader: {}", implJarPath);
  }

  /**
   * Extract a nested JAR from the extension JAR.
   *
   * @param extensionJar the extension JAR file
   * @param entryName name of nested JAR entry (e.g., "api.jar")
   * @param targetDir directory to extract to
   * @return path to extracted JAR file
   * @throws IOException if extraction fails
   */
  private Path extractNestedJar(JarFile extensionJar, String entryName, Path targetDir)
      throws IOException {
    JarEntry entry = extensionJar.getJarEntry(entryName);
    if (entry == null) {
      throw new IOException(
          String.format(
              "Nested JAR not found: %s in extension %s (%s)",
              entryName, extensionId, extensionJar.getName()));
    }

    Path targetFile = targetDir.resolve(entryName);
    try (InputStream in = extensionJar.getInputStream(entry);
        OutputStream out = Files.newOutputStream(targetFile)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
    }

    targetFile.toFile().deleteOnExit();
    log.debug("Extracted {} ({} bytes) to {}", entryName, entry.getSize(), targetFile);

    return targetFile;
  }

  public String getExtensionId() {
    return extensionId;
  }

  public String getVersion() {
    return version;
  }

  public Path getApiJarPath() {
    return apiJarPath;
  }

  public Path getImplJarPath() {
    return implJarPath;
  }

  @Override
  public String toString() {
    return String.format(
        "NestedJarExtensionClassLoader[%s:%s, api=%s, impl=%s]",
        extensionId, version, apiJarPath.getFileName(), implJarPath.getFileName());
  }
}
