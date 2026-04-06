package org.openjdk.btrace.core.extensions;

/**
 * Provides runtime environment information to {@link ExtensionConfigurator} implementations.
 *
 * <p>Configurators use this interface to detect which frameworks or libraries are present in the
 * target application, read system properties and environment variables, and obtain classloader
 * references for reflective access.
 */
public interface RuntimeEnvironment {

  /**
   * Check whether the given class is loadable in the target application.
   *
   * <p>Typically used for framework detection (e.g. {@code hasClass("org.apache.spark.SparkConf")}).
   *
   * @param className fully qualified class name
   * @return {@code true} if the class can be loaded
   */
  boolean hasClass(String className);

  /**
   * Read a system property from the target JVM.
   *
   * @param key property name
   * @return property value, or {@code null} if not set
   */
  String getSystemProperty(String key);

  /**
   * Read an environment variable from the target JVM.
   *
   * @param name variable name
   * @return variable value, or {@code null} if not set
   */
  String getEnv(String name);

  /**
   * Return a classloader suitable for loading application classes. Typically the thread context
   * classloader or the system classloader.
   *
   * @return application classloader, never {@code null}
   */
  ClassLoader getClassLoader();

  /**
   * Return the main class name of the target application, if available.
   *
   * @return main class name, or {@code null} if it cannot be determined
   */
  String getMainClassName();
}
