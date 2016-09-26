/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace.runtime;

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.com.carrotsearch.hppcrt.ObjectIntMap;
import com.sun.btrace.com.carrotsearch.hppcrt.cursors.ObjectCursor;
import com.sun.btrace.com.carrotsearch.hppcrt.maps.ObjectIntHashMap;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Pattern;

/**
 * The single entry point for class transformation.
 * <p>
 * When a class is to be transformed all the registered {@linkplain BTraceProbe} instances are
 * asked for the appropriate instrumentation. When there are no registered probes or none of
 * the registered probes is able to instrument the class it will not be transformed.
 * </p>
 *
 * @since 1.3.5
 * @author Jaroslav Bachorik
 */
public final class BTraceTransformer implements ClassFileTransformer {
    static class Filter {
        static enum Result {
            TRUE, FALSE, MAYBE
        }
        private boolean isFast = true;
        private boolean isRegex = false;


        private final ObjectIntMap<String> nameMap = new ObjectIntHashMap<>();
        private final ObjectIntMap<Pattern> nameRegexMap = new ObjectIntHashMap<>();

        void add(OnMethod om) {
            if (om.isSubtypeMatcher() || om.isClassAnnotationMatcher()) {
                isFast = false;
            } else {
                if (om.isClassRegexMatcher()) {
                    isRegex = true;
                    String name = om.getClazz().replace("\\.", "/");
                    addToMap(nameRegexMap, Pattern.compile(name));
                } else {
                    String name = om.getClazz().replace('.', '/');
                    addToMap(nameMap, name);
                }
            }

        }

        void remove(OnMethod om) {
            String name = om.getClazz().replace('.', '/');
            if (!(om.isSubtypeMatcher() || om.isClassAnnotationMatcher())) {
                if (om.isClassRegexMatcher()) {
                    removeFromMap(nameRegexMap, Pattern.compile(name));
                } else {
                    removeFromMap(nameMap, name);
                }
            }
        }

        private static <K> void addToMap(ObjectIntMap<K> map, K name) {
            synchronized(map) {
                map.putOrAdd(name, 1, 1);
            }
        }

        private static <K> void removeFromMap(ObjectIntMap<K> map, K name) {
            synchronized(map) {
                int freq = map.addTo(name, -1);
                if (freq == 0) {
                    map.remove(name);
                }
            }
        }

        public Result matchClass(String className) {
            if (isFast) {
                synchronized(nameMap) {
                    if (nameMap.containsKey(className)) {
                        return Result.TRUE;
                    }
                }
                if (isRegex) {
                    synchronized(nameRegexMap) {
                        for(ObjectCursor<Pattern> p : nameRegexMap.keys()) {
                            if (p.value.matcher(className).matches()) {
                                return Result.TRUE;
                            }
                        }
                    }
                }
                return Result.FALSE;
            }
            return Result.MAYBE;
        }
    }
    private final DebugSupport debug;
    private final Collection<BTraceProbe> probes = new LinkedList<>();
    private final Filter filter = new Filter();

    public BTraceTransformer(DebugSupport d) {
        debug = d;
    }

    public final synchronized void register(BTraceProbe p) {
        probes.add(p);
        for(OnMethod om : p.onmethods()) {
            filter.add(om);
        }
    }

    public final synchronized void unregister(BTraceProbe p) {
        probes.remove(p);
        for(OnMethod om : p.onmethods()) {
            filter.remove(om);
        }
    }

    Filter getFilter() {
        return filter;
    }

    @Override
    public synchronized byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (probes.isEmpty()) return null;

        className = className != null ? className : "<anonymous>";

        if (loader == null && (isBTraceClass(className) || isSensitiveClass(className))) {
            if (isDebug()) {
                debugPrint("skipping transform for BTrace class " + className); // NOI18N
            }
            return null;
        }

        if (filter.matchClass(className) == Filter.Result.FALSE) return null;

        boolean entered = BTraceRuntime.enter();
        try {
            BTraceClassReader cr = InstrumentUtils.newClassReader(loader, classfileBuffer);
            BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
            for(BTraceProbe p : probes) {
                p.notifyTransform(className);
                cw.addInstrumentor(p);
            }
            byte[] transformed = cw.instrument();
            if (transformed == null) {
                // no instrumentation necessary
                if (isDebug()) {
                    debugPrint("skipping class " + cr.getJavaClassName());
                }
                return classfileBuffer;
            } else {
                if (isDebug()) {
                    debugPrint("transformed class " + cr.getJavaClassName());
                }
                if (debug.isDumpClasses()) {
                    debug.dumpClass(className.replace('.', '/'), transformed);
                }
            }
            return transformed;
        } catch (Throwable th) {
            debugPrint(th);
            throw th;
        } finally {
            if (entered) {
                BTraceRuntime.leave();
            }
        }
    }

    private static boolean isBTraceClass(String name) {
        return name.startsWith("com/sun/btrace/");
    }

    /*
     * Certain classes like java.lang.ThreadLocal and it's
     * inner classes, java.lang.Object cannot be safely
     * instrumented with BTrace. This is because BTrace uses
     * ThreadLocal class to check recursive entries due to
     * BTrace's own functions. But this leads to infinite recursions
     * if BTrace instruments java.lang.ThreadLocal for example.
     * For now, we avoid such classes till we find a solution.
     */
    private static boolean isSensitiveClass(String name) {
        if (name.startsWith("java/lang/")) {
            return name.equals("java/lang/Object") || // NOI18N
                   name.startsWith("java/lang/ThreadLocal") || // NOI18N
                   name.equals("java/lang/VerifyError"); // NOI18N
        } else if (name.startsWith("sun/")) {
            return name.startsWith("sun/reflect") || // NOI18N
                   name.equals("sun/misc/Unsafe")  || // NOI18N
                   name.startsWith("sun/security/"); // NOI18N
        }
        return false;
    }

    private boolean isDebug() {
        return debug.isDebug();
    }

    private void debugPrint(String msg) {
        debug.debug(msg);
    }

    private void debugPrint(Throwable th) {
        debug.debug(th);
    }
}
