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

import org.openjdk.btrace.core.extensions.LocatorContext;
import org.openjdk.btrace.core.extensions.ProvidedDependencyLocator;
import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionLoader;
import org.openjdk.btrace.extension.ExtensionRepository;
import org.openjdk.btrace.extension.provided.LocatorContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Manages discovery, loading, and lifecycle of BTrace extensions.
 */
public final class ExtensionLoaderImpl extends ExtensionLoader {
  private static final Logger log = LoggerFactory.getLogger(ExtensionLoaderImpl.class);

  private final List<ExtensionRepository> repositories;
  private final ClassLoader parentClassLoader;
  private final ExtensionConfig config;
  private final Instrumentation instrumentation;
  private final Map<String, ExtensionDescriptorDTO> loadedExtensions;
  private final Map<String, ExtensionDescriptorDTO> availableExtensions;

  /**
   * Create an extension loader.
   *
   * @param repositories extension repositories to scan
   * @param parentClassLoader parent classloader for extensions (typically BTrace boot classloader)
   * @param config extension configuration
   * @param instrumentation instrumentation instance for adding to boot classpath
   */
  public ExtensionLoaderImpl(
      List<ExtensionRepository> repositories,
      ClassLoader parentClassLoader,
      ExtensionConfig config,
      Instrumentation instrumentation) {
    this.repositories = new ArrayList<>(repositories);
    this.parentClassLoader = parentClassLoader;
    this.config = config != null ? config : ExtensionConfig.createDefault();
    this.instrumentation = instrumentation;
    this.loadedExtensions = new HashMap<>();
    this.availableExtensions = new HashMap<>();
  }

  /**
   * Discover all available extensions from configured repositories.
   * This should be called once during agent startup.
   *
   * @return list of discovered extensions
   */
  @Override
  public List<ExtensionDescriptorDTO> discoverExtensions() {
    log.info("Discovering extensions from {} repositories (config: {})",
        repositories.size(), config);

    List<ExtensionDescriptorDTO> discovered = new ArrayList<>();

    for (ExtensionRepository repository : repositories) {
      log.debug("Scanning repository: {}", repository.getLocation());
      try {
        List<ExtensionDescriptorDTO> extensions = repository.scan();
        discovered.addAll(extensions);
      } catch (Exception e) {
        log.error("Failed to scan repository {}: {}", repository.getLocation(), e.getMessage(), e);
      }
    }

    // Resolve conflicts (higher priority repository wins, or latest version within same priority)
    List<ExtensionDescriptorDTO> resolved = resolveConflicts(discovered);

    // Filter based on configuration (enabled/disabled lists)
    List<ExtensionDescriptorDTO> filtered = new ArrayList<>();
    for (ExtensionDescriptorDTO ext : resolved) {
      if (config.isEnabled(ext.getId())) {
        filtered.add(ext);
      } else {
        log.info("Extension {} disabled by configuration", ext.getId());
      }
    }

    // Store in available extensions map
    availableExtensions.clear();
    for (ExtensionDescriptorDTO ext : filtered) {
      availableExtensions.put(ext.getId(), ext);
    }

    log.info("Discovered {} extension(s): {}", filtered.size(),
        filtered.stream().map(e -> e.getId() + ":" + e.getVersion()).collect(Collectors.joining(", ")));

    // Load all extensions immediately to add them to bootstrap classpath
    // This is required so BTrace scripts can reference extension classes
    log.info("Loading discovered extensions to bootstrap classpath");
    for (ExtensionDescriptorDTO ext : filtered) {
      if (!load(ext)) {
        log.warn("Failed to load extension {}, scripts may not be able to use it", ext.getId());
      }
    }

    return filtered;
  }

  @Override
  public ExtensionDescriptorDTO findExtensionForService(String serviceClassName) {
    for (ExtensionDescriptorDTO ext : availableExtensions.values()) {
      if (ext.providesService(serviceClassName)) {
        return ext;
      }
    }
    return null;
  }

  /**
   * Load an extension and make its classes available.
   * This is idempotent - loading an already-loaded extension is a no-op.
   *
   * @param descriptor extension to load
   * @return true if loaded successfully, false otherwise
   */
  @Override
  public boolean load(ExtensionDescriptorDTO descriptor) {
    // Synchronize on descriptor to prevent concurrent loading of the same extension
    synchronized (descriptor) {
      if (descriptor.isLoaded()) {
        log.debug("Extension {} is already loaded", descriptor.getId());
        return true;
      }
      return doLoad(descriptor);
    }
  }

  private boolean doLoad(ExtensionDescriptorDTO descriptor) {
    log.info("Loading extension: {} version {} from {}",
        descriptor.getId(), descriptor.getVersion(),
        descriptor.isEmbedded() ? "embedded" : descriptor.getJarPath());

    try {
      // Load any required extensions first
      for (String requiredId : descriptor.getRequiredExtensions()) {
        ExtensionDescriptorDTO required = availableExtensions.get(requiredId);
        if (required == null) {
          log.error("Required extension {} not found for {}",
              requiredId, descriptor.getId());
          return false;
        }
        if (!load(required)) {
          log.error("Failed to load required extension {} for {}",
              requiredId, descriptor.getId());
          return false;
        }
      }

      if (descriptor.isEmbedded()) {
        return doLoadEmbedded(descriptor);
      }

      // Load extension from directory structure:
      // extensions/extension-name/
      //   extension-name-api.jar  (added to bootstrap classpath)
      //   extension-name-impl.jar (loaded via extension classloader)

      Path extensionDir = descriptor.getJarPath();
      Path apiJar = findApiJar(extensionDir);
      Path implJar = findImplJar(extensionDir, apiJar);

      if (apiJar == null) {
        throw new IllegalStateException("No API JAR found in " + extensionDir);
      }
      if (implJar == null) {
        throw new IllegalStateException("No implementation JAR found in " + extensionDir);
      }

      // Add API JAR to bootstrap classpath
      // Note: JarFile must be closed after appendToBootstrapClassLoaderSearch as the
      // instrumentation API does not take ownership of the file handle
      try (JarFile apiJarFile = new JarFile(apiJar.toFile())) {
        instrumentation.appendToBootstrapClassLoaderSearch(apiJarFile);
        log.debug("Added {} to bootstrap classpath", apiJar.getFileName());
      }

      // Resolve provided dependencies and create classloader chain
      ClassLoader implParent = parentClassLoader;
      List<Path> providedJars = resolveProvidedDeps(descriptor);
      if (!providedJars.isEmpty()) {
        URL[] providedUrls = new URL[providedJars.size()];
        for (int i = 0; i < providedJars.size(); i++) {
          providedUrls[i] = providedJars.get(i).toUri().toURL();
        }
        implParent = new URLClassLoader(providedUrls, parentClassLoader);
        log.info("Injected {} provided dependency JARs for extension {}",
            providedUrls.length, descriptor.getId());
      }

      // Create classloader for implementation JAR
      URL implUrl = implJar.toUri().toURL();
      URLClassLoader classLoader = new URLClassLoader(new URL[] {implUrl}, implParent);

      descriptor.setClassLoader(classLoader);
      loadedExtensions.put(descriptor.getId(), descriptor);

      log.info("Successfully loaded extension: {} version {} (api: {}, impl: {})",
          descriptor.getId(), descriptor.getVersion(),
          apiJar.getFileName(), implJar.getFileName());

      return true;

    } catch (Exception e) {
      log.error(
          "Failed to load extension {}: {}", descriptor.getId(), e.getMessage(), e);
      return false;
    }
  }

  private boolean doLoadEmbedded(ExtensionDescriptorDTO descriptor) {
    // For embedded extensions:
    // - API classes are already in bootstrap (flattened .class files in the JAR root)
    // - Impl classes are loaded via ClassDataLoader with an extension-specific prefix
    ClassLoader implParent = parentClassLoader;
    List<Path> providedJars = resolveProvidedDeps(descriptor);
    if (!providedJars.isEmpty()) {
      try {
        URL[] providedUrls = new URL[providedJars.size()];
        for (int i = 0; i < providedJars.size(); i++) {
          providedUrls[i] = providedJars.get(i).toUri().toURL();
        }
        implParent = new URLClassLoader(providedUrls, parentClassLoader);
        log.info("Injected {} provided dependency JARs for embedded extension {}",
            providedUrls.length, descriptor.getId());
      } catch (Exception e) {
        log.error("Failed to create provided deps classloader for {}: {}",
            descriptor.getId(), e.getMessage(), e);
      }
    }

    String prefix = descriptor.getResourceBasePath() + "/impl/";
    ClassDataLoader classLoader =
        new ClassDataLoader(descriptor.getId(), parentClassLoader, implParent, prefix);

    descriptor.setClassLoader(classLoader);
    loadedExtensions.put(descriptor.getId(), descriptor);

    log.info(
        "Successfully loaded embedded extension: {} version {}", descriptor.getId(), descriptor.getVersion());
    return true;
  }

  /**
   * Ensure the extension API JAR is appended to the bootstrap classpath without
   * attempting to load the implementation JAR. This enables BTrace to generate
   * shims against the API when implementation use is blocked (e.g., permissions).
   *
   * @param descriptor the extension descriptor
   * @return true if the API JAR was found and appended; false otherwise
   */
  @Override
  public boolean ensureApiOnBootstrap(ExtensionDescriptorDTO descriptor) {
    if (descriptor.isEmbedded()) {
      // Embedded extension API classes are already in the bootstrap section of the masked JAR
      log.debug("API already on bootstrap for embedded extension {}", descriptor.getId());
      return true;
    }
    try {
      Path extensionDir = descriptor.getJarPath();
      Path apiJar = findApiJar(extensionDir);
      if (apiJar == null) {
        log.warn("No API JAR found for extension {} in {}", descriptor.getId(), extensionDir);
        return false;
      }
      // Note: JarFile must be closed after appendToBootstrapClassLoaderSearch as the
      // instrumentation API does not take ownership of the file handle
      try (JarFile apiJarFile = new JarFile(apiJar.toFile())) {
        instrumentation.appendToBootstrapClassLoaderSearch(apiJarFile);
        log.debug("Ensured API on bootstrap for extension {} via {}", descriptor.getId(), apiJar.getFileName());
      }
      return true;
    } catch (Exception e) {
      log.warn("Failed to ensure API on bootstrap for {}: {}", descriptor.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * Find the API JAR in the extension directory.
   */
  private static final String BUILTIN_LOCATOR_PACKAGE = "org.openjdk.btrace.extension.provided.";

  /**
   * Resolve provided dependencies for an extension using its declared locator.
   * Returns empty list if no locator is configured or if resolution finds nothing.
   */
  private List<Path> resolveProvidedDeps(ExtensionDescriptorDTO descriptor) {
    String locatorClassName = descriptor.getProvidedLocatorClass();
    if (locatorClassName == null || locatorClassName.trim().isEmpty()) {
      return Collections.emptyList();
    }

    // Resolve [ShortName] syntax to built-in package
    if (locatorClassName.startsWith("[") && locatorClassName.endsWith("]")) {
      String shortName = locatorClassName.substring(1, locatorClassName.length() - 1);
      locatorClassName = BUILTIN_LOCATOR_PACKAGE + shortName;
    }

    try {
      Class<?> locatorClass = Class.forName(locatorClassName, true,
          ExtensionLoaderImpl.class.getClassLoader());
      ProvidedDependencyLocator locator =
          (ProvidedDependencyLocator) locatorClass.getDeclaredConstructor().newInstance();

      LocatorContext ctx = new LocatorContextImpl(
          descriptor.getId(), descriptor.getProvidedLocatorProperties());

      List<Path> resolved = locator.locate(ctx);
      if (resolved == null) {
        resolved = Collections.emptyList();
      }

      // Filter out non-existent files
      List<Path> valid = new ArrayList<>();
      for (Path p : resolved) {
        if (Files.isRegularFile(p)) {
          valid.add(p);
        } else {
          log.debug("Provided dep path is not a regular file, skipping: {}", p);
        }
      }

      if (valid.isEmpty() && descriptor.isProvidedRequired()) {
        throw new RuntimeException(
            "Required provided dependencies not found for extension '"
                + descriptor.getId() + "' (locator: " + locatorClassName + ")");
      }

      if (!valid.isEmpty()) {
        log.info("Resolved {} provided dependency JAR(s) for extension '{}'",
            valid.size(), descriptor.getId());
      }

      return valid;

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to resolve provided dependencies for '{}': {}",
          descriptor.getId(), e.getMessage(), e);
      if (descriptor.isProvidedRequired()) {
        throw new RuntimeException("Provided dependency resolution failed for " + descriptor.getId(), e);
      }
      return Collections.emptyList();
    }
  }

  private java.nio.file.Path findApiJar(java.nio.file.Path extensionDir) throws java.io.IOException {
    try (java.nio.file.DirectoryStream<java.nio.file.Path> stream =
        java.nio.file.Files.newDirectoryStream(extensionDir, "*-api.jar")) {
      for (java.nio.file.Path path : stream) {
        return path;
      }
    }
    return null;
  }

  /**
   * Find the implementation JAR. First try reading from API JAR manifest,
   * then fall back to scanning directory.
   */
  private java.nio.file.Path findImplJar(java.nio.file.Path extensionDir, java.nio.file.Path apiJar)
      throws java.io.IOException {
    if (apiJar != null) {
      try (java.util.jar.JarFile jar = new java.util.jar.JarFile(apiJar.toFile())) {
        java.util.jar.Manifest manifest = jar.getManifest();
        if (manifest != null) {
          String implJarName = manifest.getMainAttributes().getValue("BTrace-Extension-Impl");
          if (implJarName != null) {
            java.nio.file.Path implPath = extensionDir.resolve(implJarName);
            if (java.nio.file.Files.exists(implPath)) {
              return implPath;
            }
          }
        }
      }
    }

    // Fallback: scan directory for *-impl.jar
    try (java.nio.file.DirectoryStream<java.nio.file.Path> stream =
        java.nio.file.Files.newDirectoryStream(extensionDir, "*-impl.jar")) {
      for (java.nio.file.Path path : stream) {
        return path;
      }
    }
    return null;
  }

  /**
   * Get all loaded extensions.
   *
   * @return collection of loaded extension descriptors
   */
  public Collection<ExtensionDescriptorDTO> getLoadedExtensions() {
    return new ArrayList<>(loadedExtensions.values());
  }

  /**
   * Get all available (discovered) extensions.
   *
   * @return collection of available extension descriptors
   */
  @Override
  public Collection<ExtensionDescriptorDTO> getAvailableExtensions() {
    return new ArrayList<>(availableExtensions.values());
  }

  /**
   * Get a specific extension by ID.
   *
   * @param extensionId extension identifier
   * @return extension descriptor, or null if not found
   */
  public ExtensionDescriptorDTO getExtension(String extensionId) {
    return availableExtensions.get(extensionId);
  }

  /**
   * Resolve conflicts when multiple versions of the same extension are discovered.
   * Resolution strategy:
   * 1. Higher priority repository wins
   * 2. Within same priority, latest version wins
   *
   * @param discovered list of all discovered extensions
   * @return list of extensions with conflicts resolved
   */
  private List<ExtensionDescriptorDTO> resolveConflicts(List<ExtensionDescriptorDTO> discovered) {
    // Group by extension ID
    Map<String, List<ExtensionDescriptorDTO>> byId = new HashMap<>();
    for (ExtensionDescriptorDTO ext : discovered) {
      byId.computeIfAbsent(ext.getId(), k -> new ArrayList<>()).add(ext);
    }

    List<ExtensionDescriptorDTO> resolved = new ArrayList<>();

    for (Map.Entry<String, List<ExtensionDescriptorDTO>> entry : byId.entrySet()) {
      List<ExtensionDescriptorDTO> candidates = entry.getValue();

      if (candidates.size() == 1) {
        resolved.add(candidates.get(0));
        continue;
      }

      // Multiple versions - resolve conflict
      log.debug("Resolving conflict for extension {}: {} candidates",
          entry.getKey(), candidates.size());

      ExtensionDescriptorDTO winner =
          candidates.stream()
              .max(
                  Comparator.comparingInt((ExtensionDescriptorDTO e) -> e.getRepository().getPriority())
                      .thenComparing(ExtensionDescriptorDTO::getVersion))
              .orElse(null);

      if (winner != null) {
        log.info("Selected extension {} version {} from {} (priority {})",
            winner.getId(),
            winner.getVersion(),
            winner.getRepository().getLocation(),
            winner.getRepository().getPriority());

        resolved.add(winner);
      }
    }

    return resolved;
  }
}
