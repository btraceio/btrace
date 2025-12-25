package org.openjdk.btrace.core.extensions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Metadata extracted from an extension class.
 *
 * <p>This class holds immutable metadata parsed from {@link ExtensionDescriptor} and {@link
 * RequiresPermission} annotations.
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
   * @return extracted metadata
   * @throws ExtensionException if the class is missing required annotations or is invalid
   */
  public static ExtensionMeta from(Class<? extends Extension> extensionClass) {
    ExtensionDescriptor descriptor = extensionClass.getAnnotation(ExtensionDescriptor.class);
    if (descriptor == null) {
      throw new ExtensionException(
          "Extension class " + extensionClass.getName() + " missing @ExtensionDescriptor");
    }

    // Extract required permissions
    Set<Permission> permissions = new HashSet<>();
    RequiresPermission single = extensionClass.getAnnotation(RequiresPermission.class);
    if (single != null) {
      permissions.add(single.value());
    }
    RequiresPermissions multiple = extensionClass.getAnnotation(RequiresPermissions.class);
    if (multiple != null) {
      for (RequiresPermission req : multiple.value()) {
        permissions.add(req.value());
      }
    }

    PermissionSet permissionSet =
        permissions.isEmpty()
            ? PermissionSet.empty()
            : PermissionSet.of(permissions.toArray(new Permission[0]));

    // Extract dependencies
    Set<Class<? extends Extension>> deps = new HashSet<>();
    for (Class<? extends Extension> dep : descriptor.dependencies()) {
      deps.add(dep);
    }

    return new ExtensionMeta(
        extensionClass,
        descriptor.name(),
        descriptor.version(),
        descriptor.description(),
        descriptor.minBTraceVersion(),
        permissionSet,
        Collections.unmodifiableSet(deps));
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
