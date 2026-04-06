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

import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Discovers extensions embedded inside the agent JAR via manifest attributes and resource scanning.
 *
 * <p>Embedded extensions are stored under {@code META-INF/btrace-extensions/{ext-id}/} in the
 * agent JAR. Discovery is driven by the {@code BTrace-Embedded-Extensions} manifest attribute
 * which lists comma-separated extension IDs.
 *
 * <p>Each extension directory contains an {@code extension.properties} file with metadata and
 * optionally an {@code impl/} subdirectory with {@code .classdata} files and a {@code probes/}
 * subdirectory with pre-compiled probe classes.
 */
public final class EmbeddedExtensionRepository implements ExtensionRepository {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedExtensionRepository.class);

  private static final String EXTENSIONS_BASE = "META-INF/btrace-extensions/";
  private static final String MANIFEST_ATTR = "BTrace-Embedded-Extensions";
  private static final String PROPS_FILE = "extension.properties";

  /** Lowest priority — filesystem extensions override embedded ones. */
  private static final int EMBEDDED_PRIORITY = -100;

  private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
  private static final Pattern VALID_CLASS =
      Pattern.compile(
          "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_$]*)*(\\$[a-zA-Z_][a-zA-Z0-9_$]*)*$");

  private final ClassLoader resourceLoader;

  /**
   * @param resourceLoader classloader for reading JAR resources (typically the agent's
   *     MaskedClassLoader)
   */
  public EmbeddedExtensionRepository(ClassLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public List<ExtensionDescriptorDTO> scan() {
    List<String> extensionIds = readExtensionIndex();
    if (extensionIds.isEmpty()) {
      log.debug("No embedded extensions found (no {} manifest attribute)", MANIFEST_ATTR);
      return Collections.emptyList();
    }

    List<ExtensionDescriptorDTO> result = new ArrayList<>();
    for (String extId : extensionIds) {
      if (!isValidExtensionId(extId)) {
        log.warn("Skipping invalid embedded extension id: '{}'", extId);
        continue;
      }

      ExtensionDescriptorDTO descriptor = loadDescriptor(extId);
      if (descriptor != null) {
        result.add(descriptor);
        log.debug("Discovered embedded extension: {} v{}", descriptor.getId(), descriptor.getVersion());
      }
    }

    log.info("Found {} embedded extension(s)", result.size());
    return result;
  }

  @Override
  public String getLocation() {
    return "embedded";
  }

  @Override
  public int getPriority() {
    return EMBEDDED_PRIORITY;
  }

  private List<String> readExtensionIndex() {
    try {
      Enumeration<URL> manifests = resourceLoader.getResources("META-INF/MANIFEST.MF");
      while (manifests.hasMoreElements()) {
        URL url = manifests.nextElement();
        try (InputStream is = url.openStream()) {
          Manifest manifest = new Manifest(is);
          Attributes attrs = manifest.getMainAttributes();
          String value = attrs.getValue(MANIFEST_ATTR);
          if (value != null && !value.trim().isEmpty()) {
            String[] ids = value.split(",");
            List<String> result = new ArrayList<>();
            for (String id : ids) {
              String trimmed = id.trim();
              if (!trimmed.isEmpty()) {
                result.add(trimmed);
              }
            }
            return result;
          }
        }
      }
    } catch (IOException e) {
      log.debug("Failed to read manifests for embedded extension index", e);
    }
    return Collections.emptyList();
  }

  private ExtensionDescriptorDTO loadDescriptor(String extId) {
    String basePath = EXTENSIONS_BASE + extId;
    String propsPath = basePath + "/" + PROPS_FILE;

    try (InputStream is = resourceLoader.getResourceAsStream(propsPath)) {
      if (is == null) {
        log.warn("Embedded extension '{}' has no {}", extId, PROPS_FILE);
        return null;
      }

      Properties props = new Properties();
      props.load(is);

      String id = props.getProperty("id", extId);
      String version = props.getProperty("version", "0.0.0");
      String name = props.getProperty("name", id);
      String description = props.getProperty("description", "");
      String configurator = props.getProperty("configurator");
      String probesStr = props.getProperty("probes");
      String servicesStr = props.getProperty("services");

      if (configurator != null && !isValidClassName(configurator)) {
        log.warn("Invalid configurator class name '{}' in extension '{}'", configurator, extId);
        configurator = null;
      }

      List<String> probes = parseList(probesStr);
      validateClassNames(probes, extId);

      List<String> services = parseList(servicesStr);
      validateClassNames(services, extId);

      return new ExtensionDescriptorDTO.Builder()
          .id(id)
          .version(version)
          .name(name)
          .description(description)
          .jarPath(Paths.get("embedded:" + id))
          .services(services)
          .repository(this)
          .embedded(true)
          .resourceBasePath(basePath)
          .configuratorClass(configurator)
          .bundledProbes(probes)
          .build();

    } catch (IOException e) {
      log.warn("Failed to read properties for embedded extension '{}'", extId, e);
      return null;
    }
  }

  private static List<String> parseList(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptyList();
    }
    String[] parts = value.split(",");
    List<String> result = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  private static boolean isValidExtensionId(String id) {
    return id != null && VALID_ID.matcher(id).matches() && !id.contains("..");
  }

  private static boolean isValidClassName(String name) {
    return name != null && VALID_CLASS.matcher(name).matches();
  }

  private static void validateClassNames(List<String> names, String extId) {
    names.removeIf(
        name -> {
          if (!isValidClassName(name)) {
            LoggerFactory.getLogger(EmbeddedExtensionRepository.class)
                .warn("Removing invalid class name '{}' from extension '{}'", name, extId);
            return true;
          }
          return false;
        });
  }
}
