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
package io.btrace.core.extensions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;

/**
 * Metadata describing an extension, as reported by extension inspection tooling.
 *
 * <p>Values come from the extension JAR's manifest, which the Gradle plugin writes and the
 * extension loader reads, so what is reported matches what the runtime will enforce. A
 * package-level {@link ExtensionDescriptor} fills any gap the manifest leaves.
 */
public final class ExtensionMeta {
  // Manifest attribute names, mirroring what the Gradle plugin emits and what the extension loader
  // reads. Kept in step with io.btrace.extension.impl.ExtensionMetadata.
  private static final String ATTR_ID = "BTrace-Extension-Id";
  private static final String ATTR_NAME = "BTrace-Extension-Name";
  private static final String ATTR_VERSION = "BTrace-Extension-Version";
  private static final String ATTR_DESCRIPTION = "BTrace-Extension-Description";
  private static final String ATTR_API_VERSION = "BTrace-API-Version";
  private static final String ATTR_PERMISSIONS = "BTrace-Extension-Permissions";

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
   * Extracts metadata from an extension class alone.
   *
   * <p>Only the package-level {@link ExtensionDescriptor} is available through this overload, and
   * that annotation is optional - most extensions declare their identity and permissions through
   * the Gradle plugin, which records them in the extension JAR's manifest. Prefer {@link
   * #from(Class, Attributes)} wherever the manifest can be reached, so that what is reported
   * matches what the extension loader will actually enforce.
   *
   * @param extensionClass the extension class
   * @return metadata derived from the package descriptor, with the class's simple name as the
   *     fallback identity
   */
  public static ExtensionMeta from(Class<? extends Extension> extensionClass) {
    return from(extensionClass, null);
  }

  /**
   * Extracts metadata from an extension class and its JAR manifest.
   *
   * <p>The manifest wins. It is what the Gradle plugin emits and what {@code ExtensionMetadata}
   * reads when the runtime loads and permission-checks an extension, so it is the only description
   * that is guaranteed to match the extension's real behaviour. The package-level {@link
   * ExtensionDescriptor} is consulted only to fill gaps: it is optional, several shipped extensions
   * do not carry one, and those that do may state a version that the build never propagates.
   *
   * @param extensionClass the extension class
   * @param manifestAttributes main attributes of the extension's API JAR manifest, or {@code null}
   *     when unavailable
   * @return metadata preferring manifest values over the package descriptor
   */
  public static ExtensionMeta from(
      Class<? extends Extension> extensionClass, Attributes manifestAttributes) {
    Package pkg = extensionClass.getPackage();
    ExtensionDescriptor pkgDesc = pkg != null ? pkg.getAnnotation(ExtensionDescriptor.class) : null;

    String name =
        firstNonBlank(
            attribute(manifestAttributes, ATTR_NAME),
            attribute(manifestAttributes, ATTR_ID),
            pkgDesc != null ? pkgDesc.name() : null,
            extensionClass.getSimpleName());
    String version =
        firstNonBlank(
            attribute(manifestAttributes, ATTR_VERSION),
            pkgDesc != null ? pkgDesc.version() : null);
    String description =
        firstNonBlank(
            attribute(manifestAttributes, ATTR_DESCRIPTION),
            pkgDesc != null ? pkgDesc.description() : null);
    String minBTraceVersion =
        firstNonBlank(
            attribute(manifestAttributes, ATTR_API_VERSION),
            pkgDesc != null ? pkgDesc.minBTraceVersion() : null);

    return new ExtensionMeta(
        extensionClass,
        name,
        version,
        description,
        minBTraceVersion,
        resolvePermissions(manifestAttributes, pkgDesc),
        Collections.emptySet());
  }

  /**
   * Resolves the permissions to report.
   *
   * <p>The manifest is authoritative when it declares the attribute at all - the Gradle plugin
   * already fails the build if a package descriptor requires a permission the manifest omits, so a
   * present manifest entry is the merged, verified set. The annotation is used only when no
   * manifest is available.
   */
  private static PermissionSet resolvePermissions(
      Attributes manifestAttributes, ExtensionDescriptor pkgDesc) {
    String declared = attribute(manifestAttributes, ATTR_PERMISSIONS);
    if (declared != null) {
      return PermissionSet.parse(declared);
    }
    if (pkgDesc == null) {
      return PermissionSet.empty();
    }
    Set<Permission> permissions = new HashSet<>();
    for (Permission p : pkgDesc.permissions()) {
      if (p != null) permissions.add(p);
    }
    return permissions.isEmpty()
        ? PermissionSet.empty()
        : PermissionSet.of(permissions.toArray(new Permission[0]));
  }

  private static String attribute(Attributes attributes, String name) {
    if (attributes == null) {
      return null;
    }
    String value = attributes.getValue(name);
    return (value == null || value.trim().isEmpty()) ? null : value;
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.trim().isEmpty()) {
        return candidate;
      }
    }
    return "";
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
