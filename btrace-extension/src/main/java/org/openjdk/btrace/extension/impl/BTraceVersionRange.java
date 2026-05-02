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

/**
 * Parses and evaluates BTrace API version requirements declared by extensions.
 *
 * <p>Supported requirement formats:
 * <ul>
 *   <li>{@code "3.0+"} — requires BTrace >= 3.0 (any patch)</li>
 *   <li>{@code "3.0.1+"} — requires BTrace >= 3.0.1</li>
 *   <li>{@code "3.0.0"} — treated as a minimum (>= 3.0.0)</li>
 * </ul>
 *
 * <p>The actual running BTrace version may carry a qualifier (e.g. {@code "3.0.0-SNAPSHOT"});
 * qualifiers are stripped before comparison.
 *
 * <p>An empty or null requirement, or an actual version of {@code "unknown"}, skips the check.
 */
final class BTraceVersionRange {
  private final int major;
  private final int minor;
  private final int patch;

  private BTraceVersionRange(int major, int minor, int patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  /**
   * Parse a version requirement string.
   * A trailing {@code +} is stripped; missing minor/patch components default to 0.
   */
  static BTraceVersionRange parse(String requirement) {
    if (requirement == null || requirement.trim().isEmpty()) {
      return new BTraceVersionRange(0, 0, 0);
    }
    String s = requirement.trim();
    if (s.endsWith("+")) {
      s = s.substring(0, s.length() - 1);
    }
    return parseComponents(s);
  }

  /**
   * Returns true if {@code actualVersion} satisfies this minimum requirement.
   *
   * <p>Returns true unconditionally when {@code actualVersion} is null, empty, or
   * {@code "unknown"} — the check is skipped rather than failing safe, because an unknown
   * version typically means a development/test environment where the JAR manifest has not
   * been written yet.
   */
  boolean satisfiedBy(String actualVersion) {
    if (actualVersion == null || actualVersion.trim().isEmpty()
        || "unknown".equalsIgnoreCase(actualVersion.trim())) {
      return true;
    }
    BTraceVersionRange actual = parseActual(actualVersion);
    return compare(actual, this) >= 0;
  }

  private static BTraceVersionRange parseActual(String version) {
    String s = version.trim();
    int dash = s.indexOf('-');
    if (dash >= 0) {
      s = s.substring(0, dash);
    }
    return parseComponents(s);
  }

  private static BTraceVersionRange parseComponents(String s) {
    String[] parts = s.split("\\.", -1);
    int major = parts.length > 0 ? parseIntSafe(parts[0]) : 0;
    int minor = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
    int patch = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
    return new BTraceVersionRange(major, minor, patch);
  }

  private static int parseIntSafe(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static int compare(BTraceVersionRange a, BTraceVersionRange b) {
    if (a.major != b.major) return Integer.compare(a.major, b.major);
    if (a.minor != b.minor) return Integer.compare(a.minor, b.minor);
    return Integer.compare(a.patch, b.patch);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
