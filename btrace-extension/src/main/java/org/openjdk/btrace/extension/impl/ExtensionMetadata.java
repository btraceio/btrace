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

import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.PermissionSet;
import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Parser for extension metadata from MANIFEST.MF attributes,
 * btrace-extension.properties (legacy), and META-INF/services files.
 */
final class ExtensionMetadata {
  private static final String METADATA_FILE = "META-INF/btrace-extension.properties";
  private static final String SERVICES_DIR = "META-INF/services/";

  // MANIFEST.MF attribute names
  private static final String ATTR_EXTENSION_ID = "BTrace-Extension-Id";
  private static final String ATTR_EXTENSION_VERSION = "BTrace-Extension-Version";
  private static final String ATTR_EXTENSION_NAME = "BTrace-Extension-Name";
  private static final String ATTR_EXTENSION_DESC = "BTrace-Extension-Description";
  private static final String ATTR_API_VERSION = "BTrace-API-Version";
  private static final String ATTR_JAVA_VERSION = "BTrace-Java-Version";
  private static final String ATTR_SERVICES = "BTrace-Extension-Services";
  private static final String ATTR_REQUIRES = "BTrace-Extension-Requires";
  private static final String ATTR_PERMISSIONS = "BTrace-Extension-Permissions";

  private ExtensionMetadata() {}

  /**
   * Parse extension metadata from a JAR file.
   * Priority: MANIFEST.MF attributes > btrace-extension.properties > inference
   *
   * @param jarPath path to extension JAR
   * @param repository repository this extension was discovered in
   * @return extension descriptor, or null if not a valid extension
   */
  static ExtensionDescriptorDTO parse(Path jarPath, ExtensionRepository repository) {
    return parse(jarPath, jarPath, repository);
  }

  /**
   * Parse extension metadata from an API JAR in an extension directory.
   * Priority: MANIFEST.MF attributes > btrace-extension.properties > inference
   *
   * @param apiJarPath path to extension API JAR
   * @param extensionDirPath path to extension directory (used as jarPath in descriptor)
   * @param repository repository this extension was discovered in
   * @return extension descriptor, or null if not a valid extension
   */
  static ExtensionDescriptorDTO parse(Path apiJarPath, Path extensionDirPath, ExtensionRepository repository) {
    try (JarFile jar = new JarFile(apiJarPath.toFile())) {
      // Try MANIFEST.MF first
      ExtensionDescriptorDTO fromManifest = parseFromManifest(extensionDirPath, jar, repository);
      if (fromManifest != null) {
        return fromManifest;
      }

      // Fall back to btrace-extension.properties
      ExtensionDescriptorDTO fromProperties = parseFromProperties(extensionDirPath, jar, repository);
      if (fromProperties != null) {
        return fromProperties;
      }

      // Last resort: infer from JAR name and services
      return inferMetadata(extensionDirPath, jar, repository);

    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Parse extension metadata from MANIFEST.MF attributes.
   */
  private static ExtensionDescriptorDTO parseFromManifest(
      Path jarPath, JarFile jar, ExtensionRepository repository) throws IOException {
    Manifest manifest = jar.getManifest();
    if (manifest == null) {
      return null;
    }

    Attributes attrs = manifest.getMainAttributes();
    String id = attrs.getValue(ATTR_EXTENSION_ID);
    String version = attrs.getValue(ATTR_EXTENSION_VERSION);

    if (id == null || version == null) {
      // Not a BTrace extension (missing required attributes)
      return null;
    }

    // Parse services from both manifest attribute and META-INF/services
    List<String> services = new ArrayList<>();
    String servicesAttr = attrs.getValue(ATTR_SERVICES);
    if (servicesAttr != null && !servicesAttr.trim().isEmpty()) {
      services.addAll(parseList(servicesAttr));
    }
    // Also scan META-INF/services directory
    services.addAll(scanServicesDirectory(jar));

    return new ExtensionDescriptorDTO.Builder()
        .id(id)
        .version(version)
        .name(attrs.getValue(ATTR_EXTENSION_NAME) != null ?
              attrs.getValue(ATTR_EXTENSION_NAME) : id)
        .description(attrs.getValue(ATTR_EXTENSION_DESC) != null ?
                     attrs.getValue(ATTR_EXTENSION_DESC) : "")
        .jarPath(jarPath)
        .btraceApiVersion(attrs.getValue(ATTR_API_VERSION) != null ?
                          attrs.getValue(ATTR_API_VERSION) : "2.0+")
        .javaVersion(attrs.getValue(ATTR_JAVA_VERSION) != null ?
                     attrs.getValue(ATTR_JAVA_VERSION) : "8+")
        .services(services)
        .requiredExtensions(parseList(attrs.getValue(ATTR_REQUIRES)))
        .requiredPermissions(parsePermissions(attrs.getValue(ATTR_PERMISSIONS)))
        .repository(repository)
        .build();
  }

  /**
   * Parse extension metadata from btrace-extension.properties file (legacy).
   */
  private static ExtensionDescriptorDTO parseFromProperties(
      Path jarPath, JarFile jar, ExtensionRepository repository) throws IOException {
    Properties props = loadMetadata(jar);
    if (props == null) {
      return null;
    }

    String id = props.getProperty("extension.id");
    String version = props.getProperty("extension.version");

    if (id == null || version == null) {
      // Invalid metadata
      return null;
    }

    List<String> services = parseServices(jar, props);

    return new ExtensionDescriptorDTO.Builder()
        .id(id)
        .version(version)
        .name(props.getProperty("extension.name", id))
        .description(props.getProperty("extension.description", ""))
        .jarPath(jarPath)
        .btraceApiVersion(props.getProperty("btrace.api.version", "2.0+"))
        .javaVersion(props.getProperty("java.version", "8+"))
        .services(services)
        .requiredExtensions(parseList(props.getProperty("requires.extensions", "")))
        .requiredPermissions(parsePermissions(props.getProperty("requires.permissions", "")))
        .repository(repository)
        .build();
  }

  private static Properties loadMetadata(JarFile jar) throws IOException {
    JarEntry entry = jar.getJarEntry(METADATA_FILE);
    if (entry == null) {
      return null;
    }

    Properties props = new Properties();
    try (InputStream in = jar.getInputStream(entry)) {
      props.load(in);
    }
    return props;
  }

  /**
   * Scan META-INF/services directory for service implementations.
   */
  private static List<String> scanServicesDirectory(JarFile jar) throws IOException {
    List<String> services = new ArrayList<>();
    Enumeration<JarEntry> entries = jar.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      if (entry.getName().startsWith(SERVICES_DIR) && !entry.isDirectory()) {
        try (InputStream in = jar.getInputStream(entry);
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Skip comments and empty lines
            if (!line.isEmpty() && !line.startsWith("#")) {
              // Add the implementation class
              if (!services.contains(line)) {
                services.add(line);
              }
            }
          }
        }
      }
    }
    return services;
  }

  private static List<String> parseServices(JarFile jar, Properties props) throws IOException {
    List<String> services = new ArrayList<>();

    // First check if services are listed in metadata
    String servicesProp = props.getProperty("services");
    if (servicesProp != null && !servicesProp.trim().isEmpty()) {
      services.addAll(parseList(servicesProp));
    }

    // Also scan META-INF/services directory
    services.addAll(scanServicesDirectory(jar));

    return services;
  }

  private static ExtensionDescriptorDTO inferMetadata(
      Path jarPath, JarFile jar, ExtensionRepository repository) {
    // Try to infer metadata from JAR name
    String fileName = jarPath.getFileName().toString();
    if (!fileName.endsWith(".jar")) {
      return null;
    }

    // Remove .jar extension
    String baseName = fileName.substring(0, fileName.length() - 4);

    // Try to parse name-version pattern
    String id = baseName;
    String version = "unknown";

    int dashIndex = baseName.lastIndexOf('-');
    if (dashIndex > 0 && dashIndex < baseName.length() - 1) {
      String potentialVersion = baseName.substring(dashIndex + 1);
      // Check if it looks like a version (starts with digit)
      if (!potentialVersion.isEmpty() && Character.isDigit(potentialVersion.charAt(0))) {
        id = baseName.substring(0, dashIndex);
        version = potentialVersion;
      }
    }

    try {
      List<String> services = scanServicesDirectory(jar);
      if (services.isEmpty()) {
        // No services found, not a valid extension
        return null;
      }

      return new ExtensionDescriptorDTO.Builder()
          .id(id)
          .version(version)
          .name(id)
          .description("")
          .jarPath(jarPath)
          .services(services)
          .repository(repository)
          .build();
    } catch (IOException e) {
      return null;
    }
  }

  private static List<String> parseList(String value) {
    if (value == null || value.trim().isEmpty()) {
      return new ArrayList<>();
    }
    return Arrays.asList(value.split("[,\\s]+"));
  }

  private static PermissionSet parsePermissions(String value) {
    if (value == null || value.trim().isEmpty()) {
      return PermissionSet.empty();
    }
    String[] parts = value.split("[,\\s]+");
    EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
    for (String p : parts) {
      try {
        set.add(Permission.valueOf(p.trim()));
      } catch (IllegalArgumentException iae) {
        // ignore unknown permission names to be lenient
      }
    }
    if (set.isEmpty()) {
      return PermissionSet.empty();
    }
    return PermissionSet.of(set.toArray(new Permission[0]));
  }
}
