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
package org.openjdk.btrace.core.extensions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Metadata extracted from an extension class.
 *
 * <p>This class holds immutable metadata parsed from package-level {@link ExtensionDescriptor} (if
 * available).
 */
public final class ExtensionMeta {
  private final Class<? extends Extension> extensionClass;
  private final String name;
  private final String version;
  private final String description;
  private final String minBTraceVersion;
  private final PermissionSet requiredPermissions;
  private final Set<Class<? extends Extension>> dependencies;

  private ExtensionMeta(
      Class<? extends Extension> extensionClass,
      String name,
      String version,
      String description,
      String minBTraceVersion,
      PermissionSet requiredPermissions,
      Set<Class<? extends Extension>> dependencies) {
    this.extensionClass = extensionClass;
    this.name = name;
    this.version = version;
    this.description = description;
    this.minBTraceVersion = minBTraceVersion;
    this.requiredPermissions = requiredPermissions;
    this.dependencies = dependencies;
  }

  /**
   * Extracts metadata from an extension class.
   *
   * @param extensionClass the extension class
   * @return extracted metadata Builds metadata from package-level {@link ExtensionDescriptor} when
   *     present.
   */
  public static ExtensionMeta from(Class<? extends Extension> extensionClass) {
    // Prefer package-level descriptor for identity and extension-level permissions
    Package pkg = extensionClass.getPackage();
    ExtensionDescriptor pkgDesc = pkg != null ? pkg.getAnnotation(ExtensionDescriptor.class) : null;

    String name =
        (pkgDesc != null && !pkgDesc.name().isEmpty())
            ? pkgDesc.name()
            : extensionClass.getSimpleName();
    String version = (pkgDesc != null) ? pkgDesc.version() : "";
    String description = (pkgDesc != null) ? pkgDesc.description() : "";
    String minBTraceVersion = (pkgDesc != null) ? pkgDesc.minBTraceVersion() : "";

    // Extract required permissions (pkg-level)
    Set<Permission> permissions = new HashSet<>();
    if (pkgDesc != null) {
      for (Permission p : pkgDesc.permissions()) {
        if (p != null) permissions.add(p);
      }
    }

    PermissionSet permissionSet =
        permissions.isEmpty()
            ? PermissionSet.empty()
            : PermissionSet.of(permissions.toArray(new Permission[0]));

    return new ExtensionMeta(
        extensionClass,
        name,
        version,
        description,
        minBTraceVersion,
        permissionSet,
        Collections.emptySet());
  }

  /**
   * Returns the extension class.
   *
   * @return extension class
   */
  public Class<? extends Extension> getExtensionClass() {
    return extensionClass;
  }

  /**
   * Returns the extension name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the extension version.
   *
   * @return version string
   */
  public String getVersion() {
    return version;
  }

  /**
   * Returns the extension description.
   *
   * @return description text
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the minimum required BTrace version.
   *
   * @return minimum BTrace version
   */
  public String getMinBTraceVersion() {
    return minBTraceVersion;
  }

  /**
   * Returns the required permissions.
   *
   * @return permission set
   */
  public PermissionSet getRequiredPermissions() {
    return requiredPermissions;
  }

  /**
   * Returns the extension dependencies.
   *
   * @return set of required extension classes
   */
  public Set<Class<? extends Extension>> getDependencies() {
    return dependencies;
  }

  @Override
  public String toString() {
    return "ExtensionMeta{"
        + "name='"
        + name
        + '\''
        + ", version='"
        + version
        + '\''
        + ", permissions="
        + requiredPermissions
        + '}';
  }
}
