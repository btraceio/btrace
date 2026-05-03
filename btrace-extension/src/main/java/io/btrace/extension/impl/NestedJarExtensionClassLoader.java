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

import java.io.IOException;
import java.io.InputStream;
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
 *
 * <ul>
 *   <li>api.jar - API classes added to bootstrap classpath
 *   <li>impl.jar - Implementation classes loaded in this classloader
 * </ul>
 *
 * <p>This classloader extracts both nested JARs to a temp directory, adds the API JAR to bootstrap
 * classpath via Instrumentation, and loads implementation classes from impl.jar.
 */
public final class NestedJarExtensionClassLoader extends URLClassLoader {
  private static final Logger log = LoggerFactory.getLogger(NestedJarExtensionClassLoader.class);

  private final String extensionId;
  private final String version;
  private final Path apiJarPath;
  private final Path implJarPath;
  private final Path tempDir;
  // Kept open for the lifetime of the classloader; the JVM reads classes from it via bootstrap.
  private final JarFile apiJar;

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

    // Add API JAR to bootstrap classpath; keep the JarFile open (JVM reads from it).
    if (instrumentation != null) {
      apiJar = new JarFile(apiJarPath.toFile());
      instrumentation.appendToBootstrapClassLoaderSearch(apiJar);
      log.info(
          "Added extension API to bootstrap classpath: {} ({})",
          extensionId,
          apiJarPath.getFileName());
    } else {
      apiJar = null;
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

    Path targetFile = targetDir.resolve(entryName).normalize();
    if (!targetFile.startsWith(targetDir)) {
      throw new IOException("Zip Slip: entry would extract outside target dir: " + entryName);
    }
    try (InputStream in = extensionJar.getInputStream(entry)) {
      Files.copy(in, targetFile);
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
  public void close() throws IOException {
    try {
      super.close();
    } finally {
      if (apiJar != null) {
        apiJar.close();
      }
    }
  }

  @Override
  public String toString() {
    return String.format(
        "NestedJarExtensionClassLoader[%s:%s, api=%s, impl=%s]",
        extensionId, version, apiJarPath.getFileName(), implJarPath.getFileName());
  }
}
