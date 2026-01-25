package org.openjdk.btrace.runtime;

import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.PermissionSet;

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
