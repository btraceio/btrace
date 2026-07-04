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

import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.ExtensionLoader;
import io.btrace.extension.ExtensionRepository;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages discovery, loading, and lifecycle of BTrace extensions. */
public final class ExtensionLoaderImpl extends ExtensionLoader implements java.io.Closeable {
  private static final Logger log = LoggerFactory.getLogger(ExtensionLoaderImpl.class);

  private final List<ExtensionRepository> repositories;
  private final ClassLoader parentClassLoader;
  private final ExtensionConfig config;
  private final Instrumentation instrumentation;
  private final String btraceVersion;
  // Written from concurrent load paths (indy bootstrap on app threads and per-client handler
  // threads), so it must be a concurrent map - plain HashMap.put racing HashMap.put can corrupt
  // the table.
  private final Map<String, ExtensionDescriptorDTO> loadedExtensions;
  // Populated by discoverExtensions() and read (and, on re-discovery, cleared) concurrently.
  private final Map<String, ExtensionDescriptorDTO> availableExtensions;

  // Bootstrap-append bookkeeping, all guarded by bootstrapLock. appendedApiJars dedups the
  // appendToBootstrapClassLoaderSearch calls so a hot path (one call per @Injected field per
  // submitted script) does not reopen the same JAR and grow the bootstrap search path without
  // bound; openApiJars keeps the JarFiles open for the loader's lifetime.
  private final Object bootstrapLock = new Object();
  private final Set<String> appendedApiJars = new HashSet<>();
  private final List<JarFile> openApiJars = new ArrayList<>();

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
      Instrumentation instrumentation,
      String btraceVersion) {
    this.repositories = new ArrayList<>(repositories);
    this.parentClassLoader = parentClassLoader;
    this.config = config != null ? config : ExtensionConfig.createDefault();
    this.instrumentation = instrumentation;
    this.btraceVersion = btraceVersion != null ? btraceVersion : "unknown";
    this.loadedExtensions = new ConcurrentHashMap<>();
    this.availableExtensions = new ConcurrentHashMap<>();
  }

  /**
   * Discover all available extensions from configured repositories. This should be called once
   * during agent startup.
   *
   * @return list of discovered extensions
   */
  @Override
  public List<ExtensionDescriptorDTO> discoverExtensions() {
    log.info(
        "Discovering extensions from {} repositories (config: {})", repositories.size(), config);

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

    log.info(
        "Discovered {} extension(s): {}",
        filtered.size(),
        filtered.stream()
            .map(e -> e.getId() + ":" + e.getVersion())
            .collect(Collectors.joining(", ")));

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
   * Load an extension and make its classes available. This is idempotent - loading an
   * already-loaded extension is a no-op.
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
    log.info(
        "Loading extension: {} version {} from {}",
        descriptor.getId(),
        descriptor.getVersion(),
        descriptor.getJarPath());

    // Reject extensions that require a newer BTrace than what is running.
    String requiredApi = descriptor.getBtraceApiVersion();
    if (requiredApi != null && !requiredApi.isEmpty()) {
      BTraceVersionRange requirement = BTraceVersionRange.parse(requiredApi);
      if (!requirement.satisfiedBy(btraceVersion)) {
        log.error(
            "Extension {} {} requires BTrace API {} but running version is {} — skipping",
            descriptor.getId(),
            descriptor.getVersion(),
            requiredApi,
            btraceVersion);
        return false;
      }
    }

    try {
      // Load any required extensions first
      for (String requiredId : descriptor.getRequiredExtensions()) {
        ExtensionDescriptorDTO required = availableExtensions.get(requiredId);
        if (required == null) {
          log.error("Required extension {} not found for {}", requiredId, descriptor.getId());
          return false;
        }
        if (!load(required)) {
          log.error("Failed to load required extension {} for {}", requiredId, descriptor.getId());
          return false;
        }
      }

      // Handle embedded vs filesystem extensions differently
      if (descriptor.isEmbedded()) {
        return loadEmbedded(descriptor);
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

      // Add API JAR to bootstrap classpath (deduplicated - see appendApiJarToBootstrap).
      if (!appendApiJarToBootstrap(apiJar)) {
        throw new IllegalStateException("Failed to add API JAR to bootstrap: " + apiJar);
      }
      log.debug("Added {} to bootstrap classpath", apiJar.getFileName());

      // Create classloader for implementation JAR
      URL implUrl = implJar.toUri().toURL();
      URLClassLoader classLoader = new URLClassLoader(new URL[] {implUrl}, parentClassLoader);

      descriptor.setClassLoader(classLoader);
      loadedExtensions.put(descriptor.getId(), descriptor);

      log.info(
          "Successfully loaded extension: {} version {} (api: {}, impl: {})",
          descriptor.getId(),
          descriptor.getVersion(),
          apiJar.getFileName(),
          implJar.getFileName());

      return true;

    } catch (Exception e) {
      log.error("Failed to load extension {}: {}", descriptor.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * Load an embedded extension using ClassDataLoader.
   *
   * <p>For embedded extensions: - API classes are already on bootstrap (flattened into agent JAR as
   * .class files) - Impl classes are stored as .classdata files and loaded via ClassDataLoader
   *
   * @param descriptor embedded extension descriptor
   * @return true if loaded successfully
   */
  private boolean loadEmbedded(ExtensionDescriptorDTO descriptor) {
    log.info(
        "Loading embedded extension: {} version {}", descriptor.getId(), descriptor.getVersion());

    // API classes are already on bootstrap via Boot-Class-Path manifest attribute
    // (they were flattened into the agent JAR at build time as .class files)
    log.debug("API classes for {} already on bootstrap classpath", descriptor.getId());

    // Create ClassDataLoader for implementation classes (.classdata resources).
    // parentClassLoader may be null when the caller wants the JVM bootstrap classloader
    // as the parent (e.g. the BTrace agent itself runs on the bootstrap classpath).
    // ClassDataLoader passes it directly to ClassLoader(ClassLoader), which accepts null
    // as a well-defined signal meaning "use the bootstrap classloader as parent" — no
    // NullPointerException is thrown. API classes are already on bootstrap via
    // Boot-Class-Path, so bootstrap delegation is the correct behaviour in that case.
    ClassLoader resourceLoader = ExtensionLoaderImpl.class.getClassLoader();
    ClassDataLoader classLoader =
        new ClassDataLoader(descriptor.getId(), resourceLoader, parentClassLoader);

    descriptor.setClassLoader(classLoader);
    loadedExtensions.put(descriptor.getId(), descriptor);

    log.info(
        "Successfully loaded embedded extension: {} version {}",
        descriptor.getId(),
        descriptor.getVersion());

    return true;
  }

  /**
   * Ensure the extension API JAR is appended to the bootstrap classpath without attempting to load
   * the implementation JAR. This enables BTrace to generate shims against the API when
   * implementation use is blocked (e.g., permissions).
   *
   * @param descriptor the extension descriptor
   * @return true if the API JAR was found and appended; false otherwise
   */
  @Override
  public boolean ensureApiOnBootstrap(ExtensionDescriptorDTO descriptor) {
    // For embedded extensions, API classes are already on bootstrap
    if (descriptor.isEmbedded()) {
      log.debug("Embedded extension {} has API already on bootstrap", descriptor.getId());
      return true;
    }

    try {
      Path extensionDir = descriptor.getJarPath();
      Path apiJar = findApiJar(extensionDir);
      if (apiJar == null) {
        log.warn("No API JAR found for extension {} in {}", descriptor.getId(), extensionDir);
        return false;
      }
      boolean ok = appendApiJarToBootstrap(apiJar);
      if (ok) {
        log.debug(
            "Ensured API on bootstrap for extension {} via {}",
            descriptor.getId(),
            apiJar.getFileName());
      }
      return ok;
    } catch (Exception e) {
      log.warn(
          "Failed to ensure API on bootstrap for {}: {}", descriptor.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * Append an extension API JAR to the bootstrap classloader search exactly once. Repeated calls
   * for the same JAR (a hot path: once per {@code @Injected} field per submitted script, plus from
   * {@code doLoad}) are no-ops, preventing unbounded file-descriptor accumulation and duplicate
   * bootstrap search entries. The opened {@link JarFile} is retained for the loader's lifetime
   * because HotSpot may need the descriptor to read class bytes after the append.
   *
   * @return true if the JAR is on the bootstrap search (appended now or on an earlier call)
   */
  private boolean appendApiJarToBootstrap(Path apiJar) {
    String key;
    try {
      key = apiJar.toAbsolutePath().normalize().toString();
    } catch (RuntimeException e) {
      key = apiJar.toString();
    }
    synchronized (bootstrapLock) {
      if (appendedApiJars.contains(key)) {
        return true;
      }
      try {
        JarFile apiJarFile = new JarFile(apiJar.toFile());
        instrumentation.appendToBootstrapClassLoaderSearch(apiJarFile);
        openApiJars.add(apiJarFile);
        appendedApiJars.add(key);
        return true;
      } catch (java.io.IOException e) {
        log.warn("Failed to append API jar {} to bootstrap: {}", apiJar, e.getMessage(), e);
        return false;
      }
    }
  }

  /** Find the API JAR in the extension directory. */
  private java.nio.file.Path findApiJar(java.nio.file.Path extensionDir)
      throws java.io.IOException {
    try (java.nio.file.DirectoryStream<java.nio.file.Path> stream =
        java.nio.file.Files.newDirectoryStream(extensionDir, "*-api.jar")) {
      for (java.nio.file.Path path : stream) {
        return path;
      }
    }
    return null;
  }

  /**
   * Find the implementation JAR. First try reading from API JAR manifest, then fall back to
   * scanning directory.
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

  @Override
  public void close() {
    // openApiJars were registered with the bootstrap classloader via
    // appendToBootstrapClassLoaderSearch and must not be closed; the JVM may
    // continue reading class bytes from them after registration. The OS reclaims
    // file descriptors at JVM exit.
    openApiJars.clear();
  }

  /**
   * Resolve conflicts when multiple versions of the same extension are discovered. Resolution
   * strategy: 1. Higher priority repository wins 2. Within same priority, latest version wins
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
      log.debug(
          "Resolving conflict for extension {}: {} candidates", entry.getKey(), candidates.size());

      ExtensionDescriptorDTO winner =
          candidates.stream()
              .max(
                  Comparator.comparingInt(
                          (ExtensionDescriptorDTO e) -> e.getRepository().getPriority())
                      .thenComparing(ExtensionDescriptorDTO::getVersion))
              .orElse(null);

      if (winner != null) {
        log.info(
            "Selected extension {} version {} from {} (priority {})",
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
