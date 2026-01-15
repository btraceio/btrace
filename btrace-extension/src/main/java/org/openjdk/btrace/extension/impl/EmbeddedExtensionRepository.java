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
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.openjdk.btrace.core.extensions.PermissionSet;
import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension repository that discovers embedded extensions from classpath resources.
 *
 * <p>Embedded extensions are bundled inside the agent JAR at {@code META-INF/btrace-extensions/}
 * and discovered via manifest attribute or resource scanning.
 *
 * <p>Structure:
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
 * Implementation classes are stored as {@code .classdata} files and loaded by {@link ClassDataLoader}.
 */
public final class EmbeddedExtensionRepository implements ExtensionRepository {
  private static final Logger log = LoggerFactory.getLogger(EmbeddedExtensionRepository.class);

  /** Priority for embedded extensions (lowest - can be overridden by filesystem extensions). */
  public static final int EMBEDDED_PRIORITY = -100;

  private static final String EXTENSIONS_BASE = "META-INF/btrace-extensions/";
  private static final String MANIFEST_ATTR = "BTrace-Embedded-Extensions";
  private static final String EXTENSION_PROPERTIES = "extension.properties";

  private final ClassLoader resourceLoader;

  /**
   * Creates an embedded extension repository.
   *
   * @param resourceLoader classloader to read resources from (typically agent classloader)
   */
  public EmbeddedExtensionRepository(ClassLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
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
        ExtensionDescriptorDTO descriptor = parseEmbeddedExtension(extId);
        if (descriptor != null) {
          extensions.add(descriptor);
          log.debug("Discovered embedded extension: {} version {}",
              descriptor.getId(), descriptor.getVersion());
        }
      } catch (Exception e) {
        log.warn("Failed to parse embedded extension {}: {}", extId, e.getMessage());
      }
    }

    log.info("Discovered {} embedded extension(s)", extensions.size());
    return extensions;
  }

  /**
   * Reads the list of embedded extension IDs from the agent JAR manifest.
   */
  private List<String> readManifestIndex() {
    try {
      Enumeration<URL> manifests = resourceLoader.getResources("META-INF/MANIFEST.MF");
      while (manifests.hasMoreElements()) {
        URL url = manifests.nextElement();
        try (InputStream is = url.openStream()) {
          Manifest manifest = new Manifest(is);
          Attributes attrs = manifest.getMainAttributes();
          String embeddedExtensions = attrs.getValue(MANIFEST_ATTR);
          if (embeddedExtensions != null && !embeddedExtensions.trim().isEmpty()) {
            return Arrays.asList(embeddedExtensions.split(","));
          }
        }
      }
    } catch (IOException e) {
      log.debug("Failed to read manifest: {}", e.getMessage());
    }
    return Collections.emptyList();
  }

  /**
   * Parses an embedded extension from its properties file.
   */
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
      String version = props.getProperty("version", "0.0.0");
      String name = props.getProperty("name", id);
      String description = props.getProperty("description", "");
      String btraceApiVersion = props.getProperty("btrace.api.version", "2.0+");
      String javaVersion = props.getProperty("java.version", "8+");
      String servicesStr = props.getProperty("services", "");
      String configurator = props.getProperty("configurator");

      List<String> services = servicesStr.isEmpty()
          ? Collections.emptyList()
          : Arrays.asList(servicesStr.split(","));

      // Discover bundled probes
      List<String> bundledProbes = discoverBundledProbes(extensionId);

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
   * Discovers bundled probe class files in the extension's probes/ directory.
   */
  private List<String> discoverBundledProbes(String extensionId) {
    // Note: Discovering resources without filesystem access is tricky.
    // The probes list should be declared in extension.properties instead.
    // For now, return empty and rely on extension.properties "probes" property.
    return Collections.emptyList();
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
}
