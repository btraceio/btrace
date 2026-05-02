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

  /**
   * Returns a description of the security risk associated with this permission.
   *
   * @return risk description for user warning
   */
  public String getRiskDescription() {
    switch (this) {
      case MESSAGING:
        return "Send messages to BTrace client. Low risk.";
      case AGGREGATION:
        return "Use aggregation functions. Low risk.";
      case JFR_EVENTS:
        return "Create JFR events. Low risk.";
      case PROFILING:
        return "Use profiling functions. Low risk.";
      case FILE_READ:
        return "Read files from disk. Risk: Information disclosure.";
      case SYSTEM_PROPS:
        return "Read system properties. Risk: Information disclosure.";
      case THREAD_INFO:
        return "Read thread information. Risk: Information disclosure.";
      case MEMORY_INFO:
        return "Read memory/GC information. Risk: Information disclosure.";
      case FILE_WRITE:
        return "Write files to disk. Risk: Data modification, disk exhaustion.";
      case NETWORK:
        return "Network I/O (sockets, HTTP). Risk: Data exfiltration, remote connections.";
      case THREADS:
        return "Create and manage threads. Risk: Resource exhaustion, concurrent operations.";
      case NATIVE:
        return "Call native code (JNI, Unsafe). Risk: JVM crashes, memory corruption.";
      case EXEC:
        return "Execute external processes. Risk: Arbitrary command execution.";
      case REFLECTION:
        return "Use reflection. Risk: Bypass access controls.";
      case CLASSLOADER:
        return "Access classloaders. Risk: Load arbitrary code.";
      case UNLIMITED_MEMORY:
        return "Unlimited buffer allocation. Risk: Memory exhaustion.";
      default:
        return "Unknown permission.";
    }
  }
}
