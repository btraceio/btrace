package org.openjdk.btrace.core.extensions;

/**
 * Permissions that can be requested by BTrace extensions.
 *
 * <p>Permissions are organized into three tiers:
 *
 * <ul>
 *   <li><b>Default</b> - Always granted, core BTrace functionality
 *   <li><b>Standard</b> - Granted unless explicitly restricted
 *   <li><b>Privileged</b> - Require explicit user consent
 * </ul>
 */
public enum Permission {
  // Default permissions - always granted
  /** Permission to send messages to the BTrace client */
  MESSAGING,
  /** Permission to use aggregation functions */
  AGGREGATION,
  /** Permission to create and use JFR events */
  JFR_EVENTS,
  /** Permission to use profiling functions */
  PROFILING,

  // Standard permissions - granted unless restricted
  /** Permission to read files (limited to specific paths) */
  FILE_READ,
  /** Permission to read system properties */
  SYSTEM_PROPS,
  /** Permission to read thread information */
  THREAD_INFO,
  /** Permission to read memory and GC information */
  MEMORY_INFO,

  // Privileged permissions - require explicit consent
  /** Permission to write to files */
  FILE_WRITE,
  /** Permission for network I/O (sockets, HTTP) */
  NETWORK,
  /** Permission to create and manage threads */
  THREADS,
  /** Permission to call native code (JNI, Unsafe) */
  NATIVE,
  /** Permission to execute external processes */
  EXEC,
  /** Permission to use reflection */
  REFLECTION,
  /** Permission to access classloaders */
  CLASSLOADER,
  /** Permission for unlimited buffer allocation */
  UNLIMITED_MEMORY;

  /**
   * Returns whether this is a default permission.
   *
   * @return true if this permission is always granted
   */
  public boolean isDefault() {
    return ordinal() <= PROFILING.ordinal();
  }

  /**
   * Returns whether this is a standard permission.
   *
   * @return true if this permission is granted unless restricted
   */
  public boolean isStandard() {
    return ordinal() > PROFILING.ordinal() && ordinal() <= MEMORY_INFO.ordinal();
  }

  /**
   * Returns whether this is a privileged permission.
   *
   * @return true if this permission requires explicit consent
   */
  public boolean isPrivileged() {
    return ordinal() > MEMORY_INFO.ordinal();
  }
}
