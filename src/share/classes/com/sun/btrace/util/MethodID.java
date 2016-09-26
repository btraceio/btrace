/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.btrace.util;

import com.sun.btrace.com.carrotsearch.hppcrt.ObjectIntMap;
import com.sun.btrace.com.carrotsearch.hppcrt.maps.ObjectIntHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A factory class for shared method ids
 * @author Jaroslav Bachorik
 */
public class MethodID {
    private static final ObjectIntMap<String> methodIds = new ObjectIntHashMap<>();
    static final AtomicInteger lastMehodId = new AtomicInteger(1);

    /**
     * Generates a unique method id based on the provided method tag
     * @param methodTag The tag used to distinguish between methods
     * @return An ID belonging to the provided method tag
     */
    public static int getMethodId(String methodTag) {
        synchronized(methodIds) {
            if (!methodIds.containsKey(methodTag)) {
                methodIds.put(methodTag, lastMehodId.getAndIncrement());
            }
            return methodIds.get(methodTag);
        }
    }

    public static int getMethodId(String className, String method, String desc) {
        return getMethodId(className + "#" + method + "#" + desc);
    }
}
