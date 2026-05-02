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
package org.openjdk.btrace.extension;

import java.util.List;

/**
 * Repository for discovering BTrace extensions. Implementations scan specific locations (file
 * system directories, remote repositories, etc.) for extension JARs and parse their metadata.
 */
public interface ExtensionRepository {

  /**
   * Scan this repository for available extensions.
   *
   * @return list of discovered extension descriptors
   */
  List<ExtensionDescriptorDTO> scan();

  /**
   * Get the location identifier for this repository (e.g., directory path, URL).
   *
   * @return repository location
   */
  String getLocation();

  /**
   * Get the priority of this repository. Higher priority repositories override lower priority ones
   * when resolving extension conflicts. Built-in repositories have lower priority than user
   * repositories.
   *
   * @return repository priority (higher = more important)
   */
  int getPriority();

  /** Priority constants for common repository types. */
  public static final class Priority {
    /** Built-in extensions in BTRACE_HOME/libs/ext/ */
    public static final int BUILTIN = 0;

    /** System-wide extensions in /etc/btrace/ext/ or %PROGRAMDATA%\btrace\ext\ */
    public static final int SYSTEM = 100;

    /** User extensions in ~/.btrace/ext/ */
    public static final int USER = 200;

    /** Environment variable BTRACE_EXT_PATH */
    public static final int ENVIRONMENT = 300;

    /** Command-line --ext-path argument */
    public static final int COMMAND_LINE = 400;

    /** Script-local ./.btrace/ext/ */
    public static final int SCRIPT_LOCAL = 500;

    private Priority() {}
  }
}
