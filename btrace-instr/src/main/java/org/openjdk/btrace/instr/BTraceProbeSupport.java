/*
 * Copyright (c) 2017,2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */
package org.openjdk.btrace.instr;

import static org.openjdk.btrace.instr.ClassFilter.isSubTypeOf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BTraceProbeSupport {
  private static final Logger log = LoggerFactory.getLogger(BTraceProbeSupport.class);

  private final List<OnMethod> onMethods;
  private final List<OnProbe> onProbes;
  private final Map<String, String> serviceFields;

  private final Object filterLock = new Object();
  private volatile ClassFilter filter;
  private boolean trustedScript = false;
  private boolean classRenamed = false;
  private String className, origName;

  BTraceProbeSupport() {
    onMethods = new ArrayList<>();
    onProbes = new ArrayList<>();
    serviceFields = new HashMap<>();
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
    return onMethods.size() > 0;
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
        Pattern p = Pattern.compile(probeClass);
        if (p.matcher(targetName).matches()) {
          applicables.add(om);
          continue;
        }
      }
      if (om.isClassAnnotationMatcher()) {
        Collection<String> annoTypes = cr.getAnnotationTypes();
        if (om.isClassRegexMatcher()) {
          Pattern p = Pattern.compile(probeClass);
          for (String annoType : annoTypes) {
            if (p.matcher(annoType).matches()) {
              applicables.add(om);
              continue outer;
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
    return serviceFields.containsValue(typeName);
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
      Class<?> clz = rt.defineClass(code, isTransforming());
      if (log.isDebugEnabled()) {
        log.debug("defineClass succeeded for {}", getClassName(false));
      }
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
