/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Support for the Java version deprecation policy introduced in BTrace 3.0.
 *
 * <p>BTrace 3.x fully supports Java 8 and newer, but running against a JVM older than {@link
 * #DEPRECATION_FLOOR Java 17} is deprecated: a warning is emitted once per JVM and support will be
 * removed in the next major release.
 *
 * <p>This class must stay Java 8 compatible ({@code Runtime.version()} is not available there) —
 * version detection is based on parsing {@code java.specification.version} / {@code java.version}
 * style strings.
 *
 * @since 3.0
 */
public final class JavaVersionCheck {
  /** The lowest Java feature version that is not deprecated. */
  public static final int DEPRECATION_FLOOR = 17;

  /** System property suppressing the deprecation warning when set to {@code true}. */
  public static final String SUPPRESS_PROP = "btrace.suppressJavaDeprecationWarning";

  private static final AtomicBoolean WARNED = new AtomicBoolean(false);

  private JavaVersionCheck() {}

  /**
   * The feature version of the currently running JVM, e.g. {@code 8}, {@code 11}, {@code 17}.
   *
   * @return the feature version, or {@code -1} if it can not be determined
   */
  public static int javaFeatureVersion() {
    int version = parseFeatureVersion(System.getProperty("java.specification.version", ""));
    if (version == -1) {
      version = parseFeatureVersion(System.getProperty("java.version", ""));
    }
    return version;
  }

  /**
   * Parses the Java feature version from a version string.
   *
   * <p>Accepts both legacy ({@code 1.8}, {@code 1.8.0_392}) and modern ({@code 9}, {@code
   * 11.0.21}, {@code 17-ea}, {@code 21+35}) version formats.
   *
   * @param versionString value of {@code java.specification.version} or {@code java.version}
   * @return the feature version, or {@code -1} if the string can not be parsed
   */
  public static int parseFeatureVersion(String versionString) {
    if (versionString == null || versionString.isEmpty()) {
      return -1;
    }
    String str = versionString.trim();
    if (str.startsWith("1.")) {
      str = str.substring(2);
    }
    int end = 0;
    while (end < str.length() && Character.isDigit(str.charAt(end))) {
      end++;
    }
    if (end == 0) {
      return -1;
    }
    try {
      return Integer.parseInt(str.substring(0, end));
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  /**
   * Whether the given feature version falls under the deprecation policy.
   *
   * @param featureVersion a Java feature version as returned by {@link #parseFeatureVersion}
   * @return {@code true} for known versions older than {@link #DEPRECATION_FLOOR}
   */
  public static boolean isDeprecated(int featureVersion) {
    return featureVersion > 0 && featureVersion < DEPRECATION_FLOOR;
  }

  /**
   * The deprecation warning text for the given Java feature version.
   *
   * @param featureVersion the deprecated Java feature version
   * @return the warning message
   */
  public static String deprecationWarning(int featureVersion) {
    return "[BTrace] WARNING: This JVM is Java "
        + featureVersion
        + ". Running BTrace on Java versions older than "
        + DEPRECATION_FLOOR
        + " is deprecated and support will be removed in the next major release. "
        + "Please upgrade to Java "
        + DEPRECATION_FLOOR
        + " or newer. Suppress this warning with -D"
        + SUPPRESS_PROP
        + "=true.";
  }

  /**
   * Emits the deprecation warning to {@code stderr} if the current JVM is older than {@link
   * #DEPRECATION_FLOOR}, at most once per JVM, unless suppressed via {@link #SUPPRESS_PROP}.
   *
   * <p>Never throws.
   */
  public static void warnIfDeprecatedJvm() {
    try {
      int version = javaFeatureVersion();
      if (isDeprecated(version)
          && !Boolean.getBoolean(SUPPRESS_PROP)
          && WARNED.compareAndSet(false, true)) {
        System.err.println(deprecationWarning(version));
      }
    } catch (Throwable ignored) {
      // the deprecation warning must never interfere with agent/client startup
    }
  }
}
