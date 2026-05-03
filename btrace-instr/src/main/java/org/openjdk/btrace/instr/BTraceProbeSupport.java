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
package org.openjdk.btrace.instr;

import static org.openjdk.btrace.instr.ClassFilter.isSubTypeOf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.regex.Pattern;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BTraceProbeSupport {
  private static final Logger log = LoggerFactory.getLogger(BTraceProbeSupport.class);

  private volatile String actionPrefix = null;
  private static final AtomicReferenceFieldUpdater<BTraceProbeSupport, String> actionPrefixUpdate =
      AtomicReferenceFieldUpdater.newUpdater(
          BTraceProbeSupport.class, String.class, "actionPrefix");
  private final List<OnMethod> onMethods;
  private final List<OnProbe> onProbes;
  private final Map<String, String> serviceFields;
  private final EnumSet<Permission> requiredPermissions;

  private final Object filterLock = new Object();
  private volatile ClassFilter filter;
  private boolean trustedScript = false;
  private boolean classRenamed = false;
  private String className, origName;
  private volatile Class<?> probeClass;

  /**
   * Per-probe handler {@link MethodHandle} cache. Holding it on the probe means it dies with the
   * probe object — no cross-probe scan on unregister, and no accidental retention of the probe's
   * defined {@code Class<?>} / {@code ClassLoader} via a static map outlasting the probe.
   *
   * <p>The key omits the probe name (redundant given the cache is per-probe). Sized small on
   * purpose: a typical probe has O(10) handlers.
   */
  private final Map<HandlerSubKey, MethodHandle> handlerCache = new ConcurrentHashMap<>();

  BTraceProbeSupport() {
    onMethods = new ArrayList<>();
    onProbes = new ArrayList<>();
    serviceFields = new HashMap<>();
    requiredPermissions = EnumSet.noneOf(Permission.class);
  }

  void setClassName(String name) {
    origName = name;
    String clientName = BTraceRuntimeAccess.getClientName(name);
    className = clientName != null ? clientName : name;
    classRenamed = !className.equals(name);
  }

  String getClassName(boolean internal) {
    return internal ? className : className.replace("/", ".");
  }

  String getOrigName() {
    return origName;
  }

  boolean isClassRenamed() {
    return classRenamed;
  }

  String translateOwner(String owner) {
    if (owner.equals(origName)) {
      return getClassName(true);
    }
    return owner;
  }

  boolean isTransforming() {
    return !onMethods.isEmpty();
  }

  Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
    Collection<OnMethod> applicables = new ArrayList<>(onMethods.size());
    String targetName = cr.getJavaClassName();

    outer:
    for (OnMethod om : onMethods) {
      String probeClass = om.getClazz();
      if (probeClass == null || probeClass.isEmpty()) continue;

      if (probeClass.equals(targetName)) {
        applicables.add(om);
        continue;
      }
      // Check regex match
      if (om.isClassRegexMatcher() && !om.isClassAnnotationMatcher()) {
        Pattern p = om.getClassPattern();
        if (p != null && p.matcher(targetName).matches()) {
          applicables.add(om);
          continue;
        }
      }
      if (om.isClassAnnotationMatcher()) {
        Collection<String> annoTypes = cr.getAnnotationTypes();
        if (om.isClassRegexMatcher()) {
          Pattern p = om.getClassPattern();
          if (p != null) {
            for (String annoType : annoTypes) {
              if (p.matcher(annoType).matches()) {
                applicables.add(om);
                continue outer;
              }
            }
          }
        } else {
          if (annoTypes.contains(probeClass)) {
            applicables.add(om);
            continue;
          }
        }
      }
      // And, finally, check the class hierarchy
      if (om.isSubtypeMatcher()) {
        // internal name of super type.
        if (isSubTypeOf(cr.getClassName(), cr.getClassLoader(), probeClass)) {
          applicables.add(om);
        }
      }
    }
    return applicables;
  }

  Collection<OnMethod> getOnMethods() {
    return Collections.unmodifiableCollection(onMethods);
  }

  Collection<OnProbe> getOnProbes() {
    return Collections.unmodifiableCollection(onProbes);
  }

  Iterable<OnMethod> onmethods() {
    return () -> Collections.unmodifiableCollection(onMethods).iterator();
  }

  Iterable<OnProbe> onprobes() {
    return () -> onProbes.iterator();
  }

  Map<String, String> serviceFields() {
    return Collections.unmodifiableMap(serviceFields);
  }

  boolean isServiceType(String typeName) {
    // First check if it's a direct service type (e.g., MetricsService)
    if (serviceFields.containsValue(typeName)) {
      return true;
    }
    // Check if it's in a sub-package of any service type's package
    // This allows return types from service methods (e.g., HistogramMetric, HistogramSnapshot)
    // without hardcoding specific packages - we infer allowed packages from injected services
    for (String serviceType : serviceFields.values()) {
      // Extract package by removing the class name (everything after the last '/')
      int lastSlash = serviceType.lastIndexOf('/');
      if (lastSlash > 0) {
        String servicePackage = serviceType.substring(0, lastSlash);
        // Allow classes in the same package or sub-packages of the service
        // This makes the verifier cooperate with the extension system automatically
        if (typeName.startsWith(servicePackage + "/")) {
          return true;
        }
      }
    }
    return false;
  }

  boolean isFieldInjected(String name) {
    return serviceFields.containsKey(name);
  }

  void addOnMethod(OnMethod om) {
    onMethods.add(om);
  }

  void addOnProbe(OnProbe op) {
    onProbes.add(op);
  }

  void addServiceField(String fldName, String svcType) {
    serviceFields.put(fldName, svcType);
  }

  void addRequiredPermission(Permission permission) {
    requiredPermissions.add(permission);
  }

  Set<Permission> getRequiredPermissions() {
    return Collections.unmodifiableSet(requiredPermissions);
  }

  boolean willInstrument(Class clz) {
    return getClassFilter().isCandidate(clz);
  }

  private ClassFilter getClassFilter() {
    synchronized (filterLock) {
      if (filter == null) {
        filter = new ClassFilter(onmethods());
      }
      return filter;
    }
  }

  void setTrusted() {
    trustedScript = true;
  }

  boolean isTrusted() {
    return trustedScript;
  }

  Class<?> getProbeClass() {
    return probeClass;
  }

  void clearProbeClass() {
    this.probeClass = null;
    // Drop cached MethodHandles — they resolve into the now-released probe Class and would
    // otherwise keep it (and its ClassLoader) reachable across a detach.
    handlerCache.clear();
  }

  MethodHandle getCachedHandler(String handlerName, MethodType type) {
    return handlerCache.get(new HandlerSubKey(handlerName, type));
  }

  void cacheHandler(String handlerName, MethodType type, MethodHandle mh) {
    handlerCache.put(new HandlerSubKey(handlerName, type), mh);
  }

  /**
   * Cache key for the per-probe handler cache. The probe component is implicit (the map lives on
   * the probe) so we only carry {@code (handlerName, type)}. Hash is precomputed because the key is
   * recomputed on every resolve.
   */
  private static final class HandlerSubKey {
    final String handler;
    final MethodType type;
    private final int hash;

    HandlerSubKey(String handler, MethodType type) {
      this.handler = handler;
      this.type = type;
      this.hash = handler.hashCode() * 31 + type.hashCode();
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof HandlerSubKey)) return false;
      HandlerSubKey k = (HandlerSubKey) o;
      return hash == k.hash && handler.equals(k.handler) && type.equals(k.type);
    }
  }

  Class<?> defineClass(BTraceRuntime.Impl rt, byte[] code) {
    // This extra BTraceRuntime.enter is needed to
    // check whether we have already entered before.
    boolean enteredHere = BTraceRuntime.enter();
    try {
      // The trace class static initializer needs to be run
      // without BTraceRuntime.enter(). Please look at the
      // static initializer code of trace class.
      BTraceRuntime.leave();
      if (log.isDebugEnabled()) {
        log.debug("about to defineClass {}", getClassName(false));
      }
      Class<?> clz = rt.defineClass(code);
      if (log.isDebugEnabled()) {
        log.debug("defineClass succeeded for {}", getClassName(false));
      }
      this.probeClass = clz;
      return clz;
    } finally {
      // leave BTraceRuntime enter state as it was before
      // we started executing this method.
      if (!enteredHere) BTraceRuntime.enter();
    }
  }

  void applyArgs(ArgsMap argsMap) {
    for (OnMethod om : onMethods) {
      om.applyArgs(argsMap);
    }
  }

  String getActionPrefix() {
    return actionPrefixUpdate.updateAndGet(
        this,
        prefix -> {
          if (prefix == null) {
            prefix = Constants.BTRACE_METHOD_PREFIX + getClassName(true).replace('/', '$') + "$";
          }
          return prefix;
        });
  }

  @Override
  public String toString() {
    return "BTraceProbeSupport{"
        + "onMethods="
        + onMethods
        + ", onProbes="
        + onProbes
        + ", trustedScript="
        + trustedScript
        + ", serviceFields="
        + serviceFields
        + '}';
  }
}
