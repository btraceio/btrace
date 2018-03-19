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
package com.sun.btrace.runtime;

import com.sun.btrace.ArgsMap;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.DebugSupport;
import static com.sun.btrace.runtime.ClassFilter.isSubTypeOf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class BTraceProbeSupport  {    
    private final List<OnMethod> onMethods;
    private final List<OnProbe> onProbes;
    private final Map<String, String> serviceFields;

    private final Object filterLock = new Object();
    private volatile ClassFilter filter;

    private boolean trustedScript = false;
    private boolean classRenamed = false;
    private String className, origName;

    private final DebugSupport debug;

    BTraceProbeSupport(DebugSupport dbg) {
        this.onMethods = new ArrayList<>();
        this.onProbes = new ArrayList<>();
        this.serviceFields = new HashMap<>();
        this.debug = dbg;
    }

    void setClassName(String name) {
        this.origName = name;
        this.className = BTraceRuntime.getClientName(name);
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
            return this.getClassName(true);
        }
        return owner;
    }

    boolean isTransforming() {
        return onMethods != null && onMethods.size() > 0;
    }

    Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        final Collection<OnMethod> applicables = new ArrayList<>(onMethods.size());
        final String targetName = cr.getJavaClassName();

        outer:
        for(OnMethod om : onMethods) {
            String probeClass = om.getClazz();
            if (probeClass == null && probeClass.isEmpty()) continue;

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
                    for(String annoType : annoTypes) {
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
        return new Iterable<OnMethod>() {
            @Override
            public Iterator<OnMethod> iterator() {
                return Collections.unmodifiableCollection(onMethods).iterator();
            }
        };
    }

    Iterable<OnProbe> onprobes() {
        return new Iterable<OnProbe>() {
            @Override
            public Iterator<OnProbe> iterator() {
                return onProbes.iterator();
            }
        };
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
        synchronized(filterLock) {
            if (filter == null) {
                this.filter = new ClassFilter(onmethods());
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

    Class defineClass(BTraceRuntime rt, byte[] code) {
        // This extra BTraceRuntime.enter is needed to
        // check whether we have already entered before.
        boolean enteredHere = BTraceRuntime.enter();
        try {
            // The trace class static initializer needs to be run
            // without BTraceRuntime.enter(). Please look at the
            // static initializer code of trace class.
            BTraceRuntime.leave();
            if (debug.isDebug()) {
                debug.debug("about to defineClass " + getClassName(true));
            }
            Class clz = rt.defineClass(code, isTransforming());
            if (debug.isDebug()) {
                debug.debug("defineClass succeeded for " + getClassName(false));
            }
            return clz;
        } finally {
            // leave BTraceRuntime enter state as it was before
            // we started executing this method.
            if (! enteredHere) BTraceRuntime.enter();
        }
    }

    void applyArgs(ArgsMap argsMap) {
        for (OnMethod om : onMethods) {
            om.applyArgs(argsMap);
        }
    }

    @Override
    public String toString() {
        return "BTraceProbeSupport{" + "onMethods=" + onMethods + ", onProbes=" + onProbes +
                                    ", trustedScript=" + trustedScript + ", serviceFields=" + serviceFields + '}';
    }
}