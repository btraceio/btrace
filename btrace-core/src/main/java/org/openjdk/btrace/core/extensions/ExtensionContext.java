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

import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.comm.Command;

/**
 * Context provided to extensions at initialization.
 *
 * <p>Extensions use this context to access BTrace runtime services such as messaging, script
 * arguments, and permission checking.
 */
public interface ExtensionContext {
  /**
   * Sends a message to the BTrace client.
   *
   * @param message the message text
   */
  void send(String message);

  /**
   * Sends a command to the BTrace client.
   *
   * @param command the command to send
   */
  void send(Command command);

  /**
   * Returns the script arguments.
   *
   * @return script arguments map
   */
  ArgsMap getArgs();

  /**
   * Returns the fully qualified name of the script class using this extension.
   *
   * @return script class name
   */
  String getScriptClassName();

  /**
   * Returns the permissions granted to the script.
   *
   * @return permission set
   */
  PermissionSet getPermissions();

  /**
   * Checks if the script has the specified permission.
   *
   * @param permission the permission to check
   * @return true if the permission is granted
   */
  boolean hasPermission(Permission permission);
}
