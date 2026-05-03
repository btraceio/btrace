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
package io.btrace.runtime;

import io.btrace.core.ArgsMap;
import io.btrace.core.comm.Command;
import io.btrace.core.extensions.Extension;
import io.btrace.core.extensions.ExtensionContext;
import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;

/**
 * Implementation of {@link ExtensionContext} backed by a {@link BTraceRuntimeImplBase}.
 *
 * <p>Provides extensions with access to the BTrace runtime services for their associated script.
 */
public final class ExtensionContextImpl implements ExtensionContext {
  private final BTraceRuntimeImplBase runtime;
  private final String scriptClassName;
  private final PermissionSet permissions;

  /**
   * Creates a new extension context.
   *
   * @param runtime the BTrace runtime instance
   * @param scriptClassName the script class name
   * @param permissions the granted permissions
   */
  public ExtensionContextImpl(
      BTraceRuntimeImplBase runtime, String scriptClassName, PermissionSet permissions) {
    this.runtime = runtime;
    this.scriptClassName = scriptClassName;
    this.permissions = permissions;
  }

  @Override
  public void send(String message) {
    runtime.send(message);
  }

  @Override
  public void send(Command command) {
    runtime.send(command);
  }

  @Override
  public ArgsMap getArgs() {
    return runtime.getArgsMap();
  }

  @Override
  public String getScriptClassName() {
    return scriptClassName;
  }

  @Override
  public PermissionSet getPermissions() {
    return permissions;
  }

  @Override
  public boolean hasPermission(Permission permission) {
    return permissions.has(permission);
  }

  void registerExtension(Extension ext) {
    runtime.registerExtension(ext);
  }
}
