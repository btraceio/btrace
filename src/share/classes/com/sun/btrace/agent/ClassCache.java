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
package com.sun.btrace.agent;

import java.util.HashMap;
import java.util.Map;

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

    private final Map<String, ClassInfo> classMap = new HashMap<>();

    public static ClassCache getInstance() {
        return Singleton.INSTANCE;
    }

    public ClassInfo get(Class clz) {
        String id = getId(clz);
        ClassInfo ci = classMap.get(id);
        if (ci == null) {
            ci = new ClassInfo(this, clz);
            classMap.put(id, ci);
        }
        return ci;
    }

    public ClassInfo get(ClassLoader cl, String className) {
        String id = getId(cl, className);
        ClassInfo ci = classMap.get(id);
        if (ci == null) {
            ci = new ClassInfo(this, cl, className);
            classMap.put(id, ci);
        }
        return ci;
    }

    private static String getId(Class clz) {
        return getId(clz.getClassLoader(), clz.getName());
    }

    private static String getId(ClassLoader cl, String className) {
        return (cl != null ? cl.toString() : "<null>") + className.replace("/", ".");
    }
}
