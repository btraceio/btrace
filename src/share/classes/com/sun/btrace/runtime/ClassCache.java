/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

import com.sun.btrace.runtime.ClassInfo.ClassName;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A simple class cache holding {@linkplain ClassInfo} instances and being
 * searchable either by {@linkplain Class} or a tuple of {@code (className, classLoader)}
 *
 * @author Jaroslav Bachorik
 */
public final class ClassCache {
    private static final class Singleton {
        private static final ClassCache INSTANCE = new ClassCache();
    }

    private final Map<ClassLoader, Map<ClassName, ClassInfo>> cacheMap = new WeakHashMap<>();
    private final Map<ClassName, ClassInfo> bootstrapInfos = new HashMap<>(500);

    public static ClassCache getInstance() {
        return Singleton.INSTANCE;
    }

    public ClassInfo get(Class clz) {
        return get(clz.getClassLoader(), clz.getName());
    }

    /**
     * Returns a cached {@linkplain ClassInfo} value.
     * If the corresponding value has not been cached yet then it is
     * created and put into the cache.
     * @param cl The associated {@linkplain ClassLoader}
     * @param className The Java class name or internal class name
     */
    public ClassInfo get(ClassLoader cl, String className) {
        return get(cl, new ClassName(className));
    }

    ClassInfo get(ClassLoader cl, ClassName className) {
        Map<ClassName, ClassInfo> infos = getInfos(cl);

        ClassInfo ci = infos.get(className);
        if (ci == null) {
            ci = new ClassInfo(this, cl, className);
            infos.put(className, ci);
        }
        return ci;
    }

    private Map<ClassName, ClassInfo> getInfos(ClassLoader cl) {
        if (cl == null) {
            return bootstrapInfos;
        }
        Map<ClassName, ClassInfo> infos = cacheMap.get(cl);
        if (infos == null) {
            infos = new HashMap<>(500);
            cacheMap.put(cl, infos);
        }
        return infos;
    }
}
