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
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import io.btrace.core.extensions.PermissionSet;
import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension repository that discovers embedded extensions from classpath resources.
 *
 * <p>Embedded extensions are bundled inside the agent JAR at {@code META-INF/btrace-extensions/}
 * and discovered via manifest attribute or resource scanning.
 *
 * <p>Structure:
 *
 * <pre>
 * META-INF/
 *   MANIFEST.MF
 *     BTrace-Embedded-Extensions: ext1,ext2
 *   btrace-extensions/
 *     ext1/
 *       extension.properties
 *       probes/
 *         Probe1.class
 *     ext2/
 *       extension.properties
 * </pre>
 *
 * <p>API classes are flattened as {@code .class} files in the JAR root (loaded via bootstrap).
 * Implementation classes are stored as {@code .classdata} files and loaded by {@link
 * ClassDataLoader}.
 */
public final class EmbeddedExtensionRepository implements ExtensionRepository {
  private static final Logger log = LoggerFactory.getLogger(EmbeddedExtensionRepository.class);

  /** Priority for embedded extensions (lowest - can be overridden by filesystem extensions). */
  public static final int EMBEDDED_PRIORITY = -100;

  /**
   * Sentinel classloader used when the caller passes {@code null} (i.e. the bootstrap classloader).
   * Using a sentinel — rather than silently substituting the system classloader — makes
   * bootstrap-loading explicit: callers that need real resource look-up should pass a concrete
   * loader; callers that genuinely want bootstrap semantics get a loader that delegates to the
   * system classloader only as a last-resort fallback via the standard parent-delegation chain.
   */
  private static final ClassLoader BOOTSTRAP_SENTINEL = new ClassLoader(null) {};

  private static final Pattern VALID_CLASS_NAME_PATTERN =
      Pattern.compile(
          "^[a-zA-Z_][a-zA-Z0-9_]*+(\\.[a-zA-Z_][a-zA-Z0-9_$]*+)*+(\\$[a-zA-Z_][a-zA-Z0-9_$]*+)*+$");

  private static final String EXTENSIONS_BASE = "META-INF/btrace-extensions/";
  private static final String MANIFEST_ATTR = "BTrace-Embedded-Extensions";
  private static final String EXTENSION_PROPERTIES = "extension.properties";

  private final ClassLoader resourceLoader;

  /** True when the caller supplied {@code null}, indicating the bootstrap classloader. */
  private final boolean isBootstrap;

  /**
   * Creates an embedded extension repository.
   *
   * @param resourceLoader classloader to read resources from (typically agent classloader). Pass
   *     {@code null} to indicate the bootstrap classloader; in that case the repository uses {@link
   *     #BOOTSTRAP_SENTINEL} so that bootstrap-loading semantics are tracked explicitly rather than
   *     silently replaced by the system classloader.
   */
  public EmbeddedExtensionRepository(ClassLoader resourceLoader) {
    this.isBootstrap = resourceLoader == null;
    this.resourceLoader = isBootstrap ? BOOTSTRAP_SENTINEL : resourceLoader;
    if (isBootstrap) {
      log.debug(
          "EmbeddedExtensionRepository created with bootstrap classloader sentinel;"
              + " resource look-up will use the bootstrap delegation chain");
    }
  }

  @Override
  public List<ExtensionDescriptorDTO> scan() {
    List<ExtensionDescriptorDTO> extensions = new ArrayList<>();

    // Try manifest index first for fast enumeration
    List<String> extensionIds = readManifestIndex();

    if (extensionIds.isEmpty()) {
      log.debug("No embedded extensions declared in manifest, skipping embedded scan");
      return extensions;
    }

    log.debug("Found {} embedded extension(s) in manifest: {}", extensionIds.size(), extensionIds);

    for (String extId : extensionIds) {
      try {
        // Validate extension ID to prevent path traversal attacks
        if (!isValidExtensionId(extId)) {
          log.warn("Rejecting invalid extension ID (potential path traversal): {}", extId);
          continue;
        }
        ExtensionDescriptorDTO descriptor = parseEmbeddedExtension(extId);
        if (descriptor != null) {
          extensions.add(descriptor);
          log.debug(
              "Discovered embedded extension: {} version {}",
              descriptor.getId(),
              descriptor.getVersion());
        }
      } catch (Exception e) {
        log.warn("Failed to parse embedded extension {}: {}", extId, e.getMessage());
      }
    }

    log.info("Discovered {} embedded extension(s)", extensions.size());
    return extensions;
  }

  /**
   * Reads the list of embedded extension IDs from all JAR manifests visible to the classloader.
   *
   * <p>All manifests that carry a {@code BTrace-Embedded-Extensions} attribute are iterated;
   * extension IDs are accumulated in encounter order. Duplicate IDs (e.g. produced by a
   * shadow/shade merge of two JARs that each declared the same extension) are deduplicated and a
   * warning is logged so operators can investigate collisions.
   */
  private List<String> readManifestIndex() {
    // Use a LinkedHashSet to deduplicate while preserving encounter order.
    Set<String> seen = new LinkedHashSet<>();
    try {
      Enumeration<URL> manifests = resourceLoader.getResources("META-INF/MANIFEST.MF");
      while (manifests.hasMoreElements()) {
        URL url = manifests.nextElement();
        try (InputStream is = url.openStream()) {
          Manifest manifest = new Manifest(is);
          Attributes attrs = manifest.getMainAttributes();
          String embeddedExtensions = attrs.getValue(MANIFEST_ATTR);
          if (embeddedExtensions != null && !embeddedExtensions.trim().isEmpty()) {
            for (String entry : embeddedExtensions.split(",")) {
              String trimmed = entry.trim();
              if (!trimmed.isEmpty()) {
                if (!seen.add(trimmed)) {
                  log.warn(
                      "Duplicate embedded extension ID '{}' encountered in manifest {};"
                          + " keeping first occurrence — check for conflicting classpath JARs",
                      trimmed,
                      url);
                }
              }
            }
          }
        }
      }
    } catch (IOException e) {
      log.debug("Failed to read manifest: {}", e.getMessage());
    }
    return seen.isEmpty() ? Collections.emptyList() : new ArrayList<>(seen);
  }

  /** Parses an embedded extension from its properties file. */
  private ExtensionDescriptorDTO parseEmbeddedExtension(String extensionId) {
    String propsPath = EXTENSIONS_BASE + extensionId + "/" + EXTENSION_PROPERTIES;

    try (InputStream is = resourceLoader.getResourceAsStream(propsPath)) {
      if (is == null) {
        log.debug("No extension.properties found for embedded extension: {}", extensionId);
        return null;
      }

      Properties props = new Properties();
      props.load(is);

      String id = props.getProperty("id", extensionId);
      // Validate ID from properties file (in case it differs from manifest)
      if (!isValidExtensionId(id)) {
        log.warn("Extension {} has invalid ID in properties: {}", extensionId, id);
        id = extensionId; // Fall back to validated ID
      }
      String version = props.getProperty("version", "0.0.0");
      String name = props.getProperty("name", id);
      String description = props.getProperty("description", "");
      String btraceApiVersion = props.getProperty("btrace.api.version", "3.0+");
      String javaVersion = props.getProperty("java.version", "8+");
      String servicesStr = props.getProperty("services", "");
      String configurator = props.getProperty("configurator");

      List<String> services =
          servicesStr.isEmpty()
              ? Collections.emptyList()
              : validateClassNames(Arrays.asList(servicesStr.split(",")), "service");

      // Validate configurator class name if present
      if (configurator != null && !isValidClassName(configurator)) {
        log.warn("Extension {} has invalid configurator class name: {}", extensionId, configurator);
        configurator = null;
      }

      // Discover bundled probes (declared via the 'probes' property)
      List<String> bundledProbes = discoverBundledProbes(extensionId, props);

      // For embedded extensions, jarPath points to a virtual path
      // The actual loading happens via ClassDataLoader
      return new ExtensionDescriptorDTO.Builder()
          .id(id)
          .version(version)
          .name(name)
          .description(description)
          .jarPath(Paths.get("embedded:" + extensionId))
          .btraceApiVersion(btraceApiVersion)
          .javaVersion(javaVersion)
          .services(services)
          .repository(this)
          .requiredPermissions(PermissionSet.empty())
          .embedded(true)
          .resourceBasePath(EXTENSIONS_BASE + extensionId)
          .configuratorClass(configurator)
          .bundledProbes(bundledProbes)
          .build();
    } catch (IOException e) {
      log.warn("Failed to read extension.properties for {}: {}", extensionId, e.getMessage());
      return null;
    }
  }

  /**
   * Discovers bundled probe class names for an extension by reading the {@code probes} property
   * from its {@code extension.properties}. The value is a comma-separated list of fully-qualified
   * probe class names; entries are trimmed and validated.
   */
  private List<String> discoverBundledProbes(String extensionId, Properties props) {
    String probesStr = props.getProperty("probes", "");
    if (probesStr.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> raw = new ArrayList<>();
    for (String entry : probesStr.split(",")) {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty()) {
        raw.add(trimmed);
      }
    }
    return validateClassNames(raw, "probe");
  }

  @Override
  public String getLocation() {
    return "embedded:" + EXTENSIONS_BASE;
  }

  @Override
  public int getPriority() {
    return EMBEDDED_PRIORITY;
  }

  @Override
  public String toString() {
    return "EmbeddedExtensionRepository{priority=" + EMBEDDED_PRIORITY + "}";
  }

  /**
   * Validates that an extension ID is safe and cannot be used for path traversal.
   *
   * <p>Valid extension IDs must:
   *
   * <ul>
   *   <li>Not be null or empty
   *   <li>Not contain path separators (/ or \)
   *   <li>Not contain parent directory references (..)
   *   <li>Only contain safe characters: alphanumeric, hyphen, underscore, dot
   * </ul>
   *
   * @param extensionId the extension ID to validate
   * @return true if the ID is safe, false otherwise
   */
  static boolean isValidExtensionId(String extensionId) {
    if (extensionId == null || extensionId.isEmpty()) {
      return false;
    }
    // Reject path separators
    if (extensionId.contains("/") || extensionId.contains("\\")) {
      return false;
    }
    // Reject parent directory references
    if (extensionId.contains("..")) {
      return false;
    }
    // Only allow safe characters: alphanumeric, hyphen, underscore, dot
    // This pattern matches valid Maven artifact IDs
    return extensionId.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
  }

  /**
   * Validates that a string is a valid Java class name.
   *
   * @param className the class name to validate
   * @return true if the class name appears valid
   */
  static boolean isValidClassName(String className) {
    if (className == null || className.isEmpty()) {
      return false;
    }
    // Basic validation: must be a valid Java identifier pattern
    // Allows: package.Class, package.Class$Inner
    return VALID_CLASS_NAME_PATTERN.matcher(className).matches();
  }

  /**
   * Validates and filters a list of class names.
   *
   * @param classNames list of class names to validate
   * @param type description of the class type (for logging)
   * @return list of valid class names
   */
  private List<String> validateClassNames(List<String> classNames, String type) {
    List<String> valid = new ArrayList<>();
    for (String className : classNames) {
      String trimmed = className.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (isValidClassName(trimmed)) {
        valid.add(trimmed);
      } else {
        log.warn("Ignoring invalid {} class name: {}", type, trimmed);
      }
    }
    return valid;
  }
}
