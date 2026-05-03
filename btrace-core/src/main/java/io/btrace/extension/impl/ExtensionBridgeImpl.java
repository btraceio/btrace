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
package io.btrace.extension.impl;

import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;
import io.btrace.extension.ExtensionBridge;
import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.ExtensionLoader;
import io.btrace.extension.PermissionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ExtensionBridge for agent-side extension access. Provides class loading for
 * extension services accessed via invokedynamic.
 */
public final class ExtensionBridgeImpl implements ExtensionBridge {
  private static final Logger log = LoggerFactory.getLogger(ExtensionBridgeImpl.class);

  private final ExtensionLoader loader;

  public ExtensionBridgeImpl(ExtensionLoader loader) {
    this.loader = loader;
  }

  /** Initialize the invokedynamic bridge with a live ExtensionLoader. */
  public static void initialize(ExtensionLoader loader) {
    try {
      Class<?> indyClz = Class.forName("io.btrace.runtime.ExtensionIndy", false, null);
      ExtensionBridge bridge = new ExtensionBridgeImpl(loader);
      indyClz.getField("bridge").set(null, bridge);
      log.debug("ExtensionIndy.bridge initialized");
    } catch (ClassNotFoundException e) {
      log.debug("ExtensionIndy not available (expected for older Java versions)");
    } catch (Throwable t) {
      log.warn("Unable to initialize ExtensionIndy.bridge", t);
    }
  }

  @Override
  public Class<?> getExtensionClass(String serviceClassName) throws Exception {
    // 1) Locate the providing extension
    ExtensionDescriptorDTO ext = loader.findExtensionForService(serviceClassName);
    if (ext == null) {
      log.error("No extension found providing service: {}", serviceClassName);
      io.btrace.extension.ExtensionRegistry.registerFailedExtension(
          serviceClassName, "No providing extension found");
      return null;
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "ExtensionBridge: service {} provided by extension {} at {} (loaded={})",
          serviceClassName,
          ext.getId(),
          ext.getJarPath(),
          ext.isLoaded());
    }

    // 2) Enforce policy (deny list / privileged requirements)
    PermissionPolicy policy = PermissionPolicy.get();
    if (policy.isExplicitlyDenied(ext.getId())) {
      return fallbackInterface(ext, serviceClassName, "Blocked by policy (denyExtensions)");
    }
    if (requiresPrivileged(ext)
        && !(policy.isAllowPrivileged() || policy.isExplicitlyAllowed(ext.getId()))) {
      log.warn(
          "Blocking privileged extension {}. Allow via allowExtensions or allowPrivileged.",
          ext.getId());
      return fallbackInterface(
          ext,
          serviceClassName,
          "Blocked privileged extension. Required=" + ext.getRequiredPermissions());
    }

    // 3) Load extension if needed
    if (!ext.isLoaded()) {
      if (!loader.load(ext)) {
        log.error("Failed to load extension {} for service {}", ext.getId(), serviceClassName);
        io.btrace.extension.ExtensionRegistry.registerFailedExtension(
            ext.getId(), "Failed to load extension");
        return null;
      }
    }

    // 4) Resolve implementation class from extension classloader
    ClassLoader extCl = ext.getClassLoader();
    if (extCl == null) {
      log.error("Extension {} has no classloader", ext.getId());
      return null;
    }
    Class<?> serviceInterface = extCl.loadClass(serviceClassName);
    Class<?> impl = findImplementationClass(serviceInterface, extCl);
    if (impl != null) return impl;

    // 5) Try context classloader as a relaxed fallback (useful in tests)
    try {
      ClassLoader tccl = Thread.currentThread().getContextClassLoader();
      if (tccl != null && tccl != extCl) {
        Class<?> altIface = tccl.loadClass(serviceInterface.getName());
        Class<?> altImpl = findImplementationClass(altIface, tccl);
        if (altImpl != null) return altImpl;
      }
    } catch (Throwable ignore) {
      // ignore and fall back to interface
    }

    // 6) Fallback to service interface (runtime will shim as needed)
    if (log.isDebugEnabled()) {
      log.debug(
          "ExtensionBridge: falling back to interface {} for service {}",
          serviceInterface.getName(),
          serviceClassName);
    }
    return serviceInterface;
  }

  private boolean requiresPrivileged(ExtensionDescriptorDTO ext) {
    PermissionSet required = ext.getRequiredPermissions();
    if (required == null) return false;
    for (Permission p : required) {
      if (p.isPrivileged()) return true;
    }
    return false;
  }

  private Class<?> fallbackInterface(
      ExtensionDescriptorDTO ext, String serviceClassName, String reason) {
    try {
      loader.ensureApiOnBootstrap(ext);
      Class<?> intf = Class.forName(serviceClassName, false, null);
      io.btrace.extension.ExtensionRegistry.registerFailedExtension(ext.getId(), reason);
      return intf;
    } catch (ClassNotFoundException cnfe) {
      // Even if API interface is not on bootstrap in this environment, keep the original reason
      // to accurately reflect why the implementation was not linked.
      io.btrace.extension.ExtensionRegistry.registerFailedExtension(ext.getId(), reason);
      return null;
    }
  }

  private Class<?> findImplementationClass(Class<?> serviceInterface, ClassLoader cl) {
    String ifaceName = serviceInterface.getName();
    // Prefer ServiceLoader to allow multiple providers and custom impl names
    try {
      java.util.ServiceLoader<?> sl = java.util.ServiceLoader.load(serviceInterface, cl);
      for (Object prov : sl) {
        Class<?> impl = prov.getClass();
        if (serviceInterface.isAssignableFrom(impl)) {
          if (log.isDebugEnabled()) {
            log.debug(
                "ExtensionBridge: using ServiceLoader provider {} for service {}",
                impl.getName(),
                ifaceName);
          }
          return impl;
        }
      }
    } catch (Throwable t) {
      // ignore and continue
    }

    // Conventional Impl naming: FooService -> FooServiceImpl
    String implCandidate = ifaceName + "Impl";
    try {
      Class<?> impl = cl.loadClass(implCandidate);
      if (serviceInterface.isAssignableFrom(impl)) {
        if (log.isDebugEnabled()) {
          log.debug(
              "ExtensionBridge: using Impl candidate {} for service {}", implCandidate, ifaceName);
        }
        return impl;
      }
    } catch (ClassNotFoundException ignore) {
      // no-op
    }
    return null;
  }
}
