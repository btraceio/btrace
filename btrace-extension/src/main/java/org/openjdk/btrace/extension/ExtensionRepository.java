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
package org.openjdk.btrace.extension;

import java.util.List;

/**
 * Repository for discovering BTrace extensions.
 * Implementations scan specific locations (file system directories,
 * remote repositories, etc.) for extension JARs and parse their metadata.
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
   * Get the priority of this repository. Higher priority repositories
   * override lower priority ones when resolving extension conflicts.
   * Built-in repositories have lower priority than user repositories.
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
