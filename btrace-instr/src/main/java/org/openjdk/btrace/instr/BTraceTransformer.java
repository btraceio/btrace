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
package org.openjdk.btrace.instr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * The single entry point for class transformation.
 * <p>
 * When a class is to be transformed all the registered {@linkplain BTraceProbe} instances are
 * asked for the appropriate instrumentation. When there are no registered probes or none of
 * the registered probes is able to instrument the class it will not be transformed.
 *
 * @author Jaroslav Bachorik
 * @since 1.3.5
 */
public final class BTraceTransformer implements ClassFileTransformer {
    private final DebugSupport debug;
    private final ReentrantReadWriteLock setupLock = new ReentrantReadWriteLock();
    private final Collection<BTraceProbe> probes = new ArrayList<>(3);
    private final Filter filter = new Filter();
    private final Collection<MethodNode> cushionMethods = new HashSet<>();

    public BTraceTransformer(DebugSupport d) {
        debug = d;
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
        return ClassFilter.isSensitiveClass(name);
    }

    public final void register(BTraceProbe p) {
        try {
            setupLock.writeLock().lock();
            probes.add(p);
            for (OnMethod om : p.onmethods()) {
                filter.add(om);
            }
        } finally {
            setupLock.writeLock().unlock();
        }
    }

    public final void unregister(BTraceProbe p) {
        try {
            setupLock.writeLock().lock();
            probes.remove(p);
            for (OnMethod om : p.onmethods()) {
                filter.remove(om);
                cushionMethods.add(new MethodNode(Opcodes.ASM7, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, Instrumentor.getActionMethodName(p, om.getTargetName()), om.getTargetDescriptor(), null, null));
            }

        } finally {
            setupLock.writeLock().unlock();
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            setupLock.readLock().lock();
            if (probes.isEmpty()) return null;

            className = className != null ? className : "<anonymous>";

            if ((loader == null || loader.equals(ClassLoader.getSystemClassLoader())) && isSensitiveClass(className)) {
                if (isDebug()) {
                    debugPrint("skipping transform for BTrace class " + className); // NOI18N
                }
                return null;
            }

            if (filter.matchClass(className) == Filter.Result.FALSE) return null;

            boolean entered = BTraceRuntime.enter();
            try {
                if (isDebug()) {
                    debug.dumpClass(className.replace('.', '/') + "_orig", classfileBuffer);
                }
                BTraceClassReader cr = InstrumentUtils.newClassReader(loader, classfileBuffer);
                BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
                cw.addCushionMethods(cushionMethods);
                for (BTraceProbe p : probes) {
                    p.notifyTransform(className);
                    cw.addInstrumentor(p, loader);
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
        } finally {
            setupLock.readLock().unlock();
        }
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

    static class Filter {
        private final Map<String, Integer> nameMap = new HashMap<>();
        private final Map<Pattern, Integer> nameRegexMap = new HashMap<>();
        private boolean isFast = true;
        private boolean isRegex = false;

        private static <K> void addToMap(Map<K, Integer> map, K name) {
            synchronized (map) {
                Integer i = map.get(name);
                if (i == null) {
                    map.put(name, 1);
                } else {
                    map.put(name, i + 1);
                }
            }
        }

        private static <K> void removeFromMap(Map<K, Integer> map, K name) {
            synchronized (map) {
                Integer i = map.get(name);
                if (i == null) {
                    return;
                }
                int freq = i - 1;
                if (freq == 0) {
                    map.remove(name);
                }
            }
        }

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

        public Result matchClass(String className) {
            if (isFast) {
                synchronized (nameMap) {
                    if (nameMap.containsKey(className)) {
                        return Result.TRUE;
                    }
                }
                if (isRegex) {
                    synchronized (nameRegexMap) {
                        for (Pattern p : nameRegexMap.keySet()) {
                            if (p.matcher(className).matches()) {
                                return Result.TRUE;
                            }
                        }
                    }
                }
                return Result.FALSE;
            }
            return Result.MAYBE;
        }

        enum Result {
            TRUE, FALSE, MAYBE
        }
    }
}
