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
package org.openjdk.btrace.extension;

import org.openjdk.btrace.core.extensions.PermissionSet;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Descriptor for a BTrace extension loaded from an extension JAR.
 * Contains metadata about the extension including identity, version,
 * compatibility requirements, and provided services.
 */
public final class ExtensionDescriptorDTO {
  private final String id;
  private final String version;
  private final String name;
  private final String description;
  private final Path jarPath;
  private final String btraceApiVersion;
  private final String javaVersion;
  private final List<String> services;
  private final List<String> requiredExtensions;
  private final ExtensionRepository repository;
  private final PermissionSet requiredPermissions;

  // Embedded extension support
  private final boolean embedded;
  private final String resourceBasePath;
  private final String configuratorClass;
  private final List<String> bundledProbes;

  private volatile boolean loaded = false;
  private volatile ClassLoader classLoader = null;

  ExtensionDescriptorDTO(
      String id,
      String version,
      String name,
      String description,
      Path jarPath,
      String btraceApiVersion,
      String javaVersion,
      List<String> services,
      List<String> requiredExtensions,
      ExtensionRepository repository,
      PermissionSet requiredPermissions,
      boolean embedded,
      String resourceBasePath,
      String configuratorClass,
      List<String> bundledProbes) {
    this.id = Objects.requireNonNull(id, "Extension id cannot be null");
    this.version = Objects.requireNonNull(version, "Extension version cannot be null");
    this.name = name != null ? name : id;
    this.description = description != null ? description : "";
    this.jarPath = Objects.requireNonNull(jarPath, "Extension jar path cannot be null");
    this.btraceApiVersion = btraceApiVersion != null ? btraceApiVersion : "3.0+";
    this.javaVersion = javaVersion != null ? javaVersion : "8+";
    this.services = services != null ? Collections.unmodifiableList(services) : Collections.emptyList();
    this.requiredExtensions = requiredExtensions != null ? Collections.unmodifiableList(requiredExtensions) : Collections.emptyList();
    this.repository = repository;
    this.requiredPermissions = requiredPermissions != null ? requiredPermissions : PermissionSet.empty();
    this.embedded = embedded;
    this.resourceBasePath = resourceBasePath;
    this.configuratorClass = configuratorClass;
    this.bundledProbes = bundledProbes != null ? Collections.unmodifiableList(bundledProbes) : Collections.emptyList();
  }

  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Path getJarPath() {
    return jarPath;
  }

  public String getBtraceApiVersion() {
    return btraceApiVersion;
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public List<String> getServices() {
    return services;
  }

  public List<String> getRequiredExtensions() {
    return requiredExtensions;
  }

  public ExtensionRepository getRepository() {
    return repository;
  }

  /**
   * Returns permissions required by this extension (from manifest or properties),
   * or an empty set if none were declared.
   */
  public PermissionSet getRequiredPermissions() {
    return requiredPermissions;
  }

  /**
   * Returns whether this is an embedded extension (bundled in agent JAR).
   */
  public boolean isEmbedded() {
    return embedded;
  }

  /**
   * Returns the resource base path for embedded extensions (e.g., "META-INF/btrace-extensions/ext-id").
   * Returns null for filesystem extensions.
   */
  public String getResourceBasePath() {
    return resourceBasePath;
  }

  /**
   * Returns the configurator class name for environment-aware probe selection.
   * Returns null if no configurator is defined.
   */
  public String getConfiguratorClass() {
    return configuratorClass;
  }

  /**
   * Returns the list of bundled probe class names.
   */
  public List<String> getBundledProbes() {
    return bundledProbes;
  }

  public boolean isLoaded() {
    return loaded;
  }

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
    this.loaded = true;
  }

  /**
   * Check if this extension provides the given service class.
   *
   * @param serviceClassName fully qualified service class name
   * @return true if this extension provides the service
   */
  public boolean providesService(String serviceClassName) {
    return services.contains(serviceClassName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExtensionDescriptorDTO that = (ExtensionDescriptorDTO) o;
    return id.equals(that.id) && version.equals(that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, version);
  }

  @Override
  public String toString() {
    return "ExtensionDescriptor{"
        + "id='"
        + id
        + '\''
        + ", version='"
        + version
        + '\''
        + ", name='"
        + name
        + '\''
        + ", jarPath="
        + jarPath
        + ", loaded="
        + loaded
        + '}';
  }

  /** Builder for creating ExtensionDescriptor instances. */
  public static final class Builder {
    private String id;
    private String version;
    private String name;
    private String description;
    private Path jarPath;
    private String btraceApiVersion;
    private String javaVersion;
    private List<String> services;
    private List<String> requiredExtensions;
    private ExtensionRepository repository;
    private PermissionSet requiredPermissions;
    private boolean embedded;
    private String resourceBasePath;
    private String configuratorClass;
    private List<String> bundledProbes;

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder jarPath(Path jarPath) {
      this.jarPath = jarPath;
      return this;
    }

    public Builder btraceApiVersion(String btraceApiVersion) {
      this.btraceApiVersion = btraceApiVersion;
      return this;
    }

    public Builder javaVersion(String javaVersion) {
      this.javaVersion = javaVersion;
      return this;
    }

    public Builder services(List<String> services) {
      this.services = services;
      return this;
    }

    public Builder requiredExtensions(List<String> requiredExtensions) {
      this.requiredExtensions = requiredExtensions;
      return this;
    }

    public Builder repository(ExtensionRepository repository) {
      this.repository = repository;
      return this;
    }

    public Builder requiredPermissions(PermissionSet permissions) {
      this.requiredPermissions = permissions;
      return this;
    }

    public Builder embedded(boolean embedded) {
      this.embedded = embedded;
      return this;
    }

    public Builder resourceBasePath(String resourceBasePath) {
      this.resourceBasePath = resourceBasePath;
      return this;
    }

    public Builder configuratorClass(String configuratorClass) {
      this.configuratorClass = configuratorClass;
      return this;
    }

    public Builder bundledProbes(List<String> bundledProbes) {
      this.bundledProbes = bundledProbes;
      return this;
    }

    public ExtensionDescriptorDTO build() {
      return new ExtensionDescriptorDTO(
          id,
          version,
          name,
          description,
          jarPath,
          btraceApiVersion,
          javaVersion,
          services,
          requiredExtensions,
          repository,
          requiredPermissions,
          embedded,
          resourceBasePath,
          configuratorClass,
          bundledProbes);
    }
  }
}
