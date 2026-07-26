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

import java.util.EnumSet;
import java.util.Iterator;

/**
 * Immutable set of permissions.
 *
 * <p>This class provides an efficient, allocation-free way to check permissions and create derived
 * permission sets.
 */
public final class PermissionSet implements Iterable<Permission> {
  private static final PermissionSet EMPTY = new PermissionSet(EnumSet.noneOf(Permission.class));
  private static final PermissionSet ALL = new PermissionSet(EnumSet.allOf(Permission.class));
  private static final PermissionSet DEFAULT;
  private static final PermissionSet STANDARD;

  static {
    EnumSet<Permission> defaultPerms = EnumSet.noneOf(Permission.class);
    EnumSet<Permission> standardPerms = EnumSet.noneOf(Permission.class);
    for (Permission p : Permission.values()) {
      if (p.isDefault()) {
        defaultPerms.add(p);
      }
      if (p.isDefault() || p.isStandard()) {
        standardPerms.add(p);
      }
    }
    DEFAULT = new PermissionSet(defaultPerms);
    STANDARD = new PermissionSet(standardPerms);
  }

  private final EnumSet<Permission> permissions;

  private PermissionSet(EnumSet<Permission> permissions) {
    this.permissions = EnumSet.copyOf(permissions);
  }

  /**
   * Returns an empty permission set.
   *
   * @return empty permission set
   */
  public static PermissionSet empty() {
    return EMPTY;
  }

  /**
   * Returns a permission set containing all permissions.
   *
   * @return permission set with all permissions
   */
  public static PermissionSet all() {
    return ALL;
  }

  /**
   * Returns a permission set containing default permissions.
   *
   * @return permission set with default permissions
   */
  public static PermissionSet defaults() {
    return DEFAULT;
  }

  /**
   * Returns a permission set containing default and standard permissions.
   *
   * @return permission set with default and standard permissions
   */
  public static PermissionSet standard() {
    return STANDARD;
  }

  /**
   * Returns a permission set containing the specified permissions.
   *
   * @param permissions the permissions to include
   * @return new permission set
   */
  public static PermissionSet of(Permission... permissions) {
    if (permissions.length == 0) {
      return EMPTY;
    }
    EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
    for (Permission p : permissions) {
      set.add(p);
    }
    return new PermissionSet(set);
  }

  /**
   * Parses a permission set from its textual form.
   *
   * <p>Accepts the comma- or whitespace-separated list used by the {@code
   * BTrace-Extension-Permissions} manifest attribute and the {@code requires.permissions} property.
   * Unknown names are ignored rather than rejected, so an extension built against a newer BTrace
   * that names a permission this runtime does not know still loads with the permissions it does
   * understand.
   *
   * @param value the textual permission list; may be {@code null} or blank
   * @return the parsed set, empty if nothing recognisable was found
   */
  public static PermissionSet parse(String value) {
    if (value == null || value.trim().isEmpty()) {
      return EMPTY;
    }
    EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
    for (String part : value.split("[,\\s]+")) {
      try {
        set.add(Permission.valueOf(part.trim()));
      } catch (IllegalArgumentException ignored) {
        // lenient by design - see javadoc
      }
    }
    return set.isEmpty() ? EMPTY : new PermissionSet(set);
  }

  /**
   * Checks if this set contains the specified permission.
   *
   * @param permission the permission to check
   * @return true if the permission is present
   */
  public boolean has(Permission permission) {
    return permissions.contains(permission);
  }

  /**
   * Checks if this set contains all of the specified permissions.
   *
   * @param other the permission set to check
   * @return true if all permissions are present
   */
  public boolean hasAll(PermissionSet other) {
    return permissions.containsAll(other.permissions);
  }

  /**
   * Returns a new permission set with the specified permissions added.
   *
   * @param toAdd the permissions to add
   * @return new permission set
   */
  public PermissionSet with(Permission... toAdd) {
    if (toAdd.length == 0) {
      return this;
    }
    EnumSet<Permission> newSet = EnumSet.copyOf(permissions);
    for (Permission p : toAdd) {
      newSet.add(p);
    }
    return new PermissionSet(newSet);
  }

  /**
   * Returns a new permission set with the specified permissions removed.
   *
   * @param toRemove the permissions to remove
   * @return new permission set
   */
  public PermissionSet without(Permission... toRemove) {
    if (toRemove.length == 0) {
      return this;
    }
    EnumSet<Permission> newSet = EnumSet.copyOf(permissions);
    for (Permission p : toRemove) {
      newSet.remove(p);
    }
    return new PermissionSet(newSet);
  }

  /**
   * Returns the number of permissions in this set.
   *
   * @return permission count
   */
  public int size() {
    return permissions.size();
  }

  /**
   * Returns whether this set is empty.
   *
   * @return true if no permissions are present
   */
  public boolean isEmpty() {
    return permissions.isEmpty();
  }

  @Override
  public Iterator<Permission> iterator() {
    return permissions.iterator();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof PermissionSet)) {
      return false;
    }
    PermissionSet other = (PermissionSet) obj;
    return permissions.equals(other.permissions);
  }

  @Override
  public int hashCode() {
    return permissions.hashCode();
  }

  @Override
  public String toString() {
    return permissions.toString();
  }
}
