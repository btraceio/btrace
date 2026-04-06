package org.openjdk.btrace.core.extensions;

import java.util.Map;

/**
 * Context provided to {@link ProvidedDependencyLocator} implementations during dependency
 * resolution.
 *
 * <p>Locators use this to read their configuration properties (from {@code extension.properties} or
 * manifest attributes) and to inspect the runtime environment.
 */
public interface LocatorContext {

  /** Extension ID that declared this provided dependency. */
  String getExtensionId();

  /**
   * Locator configuration properties. Keys are stripped of the {@code locator.} prefix — e.g.,
   * {@code provided.locator.envVar=SPARK_HOME} in the properties file becomes key {@code envVar}.
   */
  Map<String, String> getProperties();

  /** Convenience: get a single locator property by key, or {@code null} if not set. */
  default String getProperty(String key) {
    return getProperties().get(key);
  }

  /** Convenience: get a locator property with a default value. */
  default String getProperty(String key, String defaultValue) {
    String v = getProperties().get(key);
    return v != null ? v : defaultValue;
  }

  /** Check whether a class is loadable in the target application. */
  boolean hasClass(String className);

  /** Read a JVM system property. */
  String getSystemProperty(String key);

  /** Read an environment variable. */
  String getEnv(String name);
}
