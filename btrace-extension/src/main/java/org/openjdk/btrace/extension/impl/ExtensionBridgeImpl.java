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
package org.openjdk.btrace.extension.impl;

import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.PermissionSet;
import org.openjdk.btrace.extension.ExtensionBridge;
import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionLoader;
import org.openjdk.btrace.extension.PermissionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ExtensionBridge for agent-side extension access.
 * Provides class loading for extension services accessed via invokedynamic.
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
      Class<?> indyClz = Class.forName("org.openjdk.btrace.runtime.ExtensionIndy");
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
      org.openjdk.btrace.extension.ExtensionRegistry.registerFailedExtension(serviceClassName, "No providing extension found");
      return null;
    }
    if (log.isDebugEnabled()) {
      log.debug("ExtensionBridge: service {} provided by extension {} at {} (loaded={})",
          serviceClassName, ext.getId(), ext.getJarPath(), ext.isLoaded());
    }

    // 2) Enforce policy (deny list / privileged requirements)
    PermissionPolicy policy = PermissionPolicy.get();
    if (policy.isExplicitlyDenied(ext.getId())) {
      return fallbackInterface(ext, serviceClassName, "Blocked by policy (denyExtensions)");
    }
    if (requiresPrivileged(ext) && !(policy.isAllowPrivileged() || policy.isExplicitlyAllowed(ext.getId()))) {
      log.warn("Blocking privileged extension {}. Allow via allowExtensions or allowPrivileged.", ext.getId());
      return fallbackInterface(ext, serviceClassName, "Blocked privileged extension. Required=" + ext.getRequiredPermissions());
    }

    // 3) Load extension if needed
    if (!ext.isLoaded()) {
      if (!loader.load(ext)) {
        log.error("Failed to load extension {} for service {}", ext.getId(), serviceClassName);
        org.openjdk.btrace.extension.ExtensionRegistry.registerFailedExtension(ext.getId(), "Failed to load extension");
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
    } catch (Throwable t) {
      log.debug("Context classloader fallback failed for {}", serviceClassName, t);
    }

    // 6) Fallback to service interface (runtime will shim as needed)
    if (log.isDebugEnabled()) {
      log.debug("ExtensionBridge: falling back to interface {} for service {}", serviceInterface.getName(), serviceClassName);
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

  private Class<?> fallbackInterface(ExtensionDescriptorDTO ext, String serviceClassName, String reason) {
    try {
      loader.ensureApiOnBootstrap(ext);
      Class<?> intf = Class.forName(serviceClassName, false, null);
      org.openjdk.btrace.extension.ExtensionRegistry.registerFailedExtension(ext.getId(), reason);
      return intf;
    } catch (ClassNotFoundException cnfe) {
      // Even if API interface is not on bootstrap in this environment, keep the original reason
      // to accurately reflect why the implementation was not linked.
      org.openjdk.btrace.extension.ExtensionRegistry.registerFailedExtension(ext.getId(), reason);
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
            log.debug("ExtensionBridge: using ServiceLoader provider {} for service {}", impl.getName(), ifaceName);
          }
          return impl;
        }
      }
    } catch (Throwable t) {
      log.debug("ServiceLoader lookup failed for {}", ifaceName, t);
    }

    // Conventional Impl naming: FooService -> FooServiceImpl
    String implCandidate = ifaceName + "Impl";
    try {
      Class<?> impl = cl.loadClass(implCandidate);
      if (serviceInterface.isAssignableFrom(impl)) {
        if (log.isDebugEnabled()) {
          log.debug("ExtensionBridge: using Impl candidate {} for service {}", implCandidate, ifaceName);
        }
        return impl;
      }
    } catch (ClassNotFoundException ignore) {
      // no-op
    }
    return null;
  }
}
