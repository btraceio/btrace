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

import com.sun.btrace.BTraceRuntime;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This class holds the supporting data and functions for sampled duration
 * measuring.
 *
 * @author Jaroslav Bachorik
 */
final public class SamplingSupport {
    private static interface RandomIntProvider {
        int nextInt(int bound);
    }

    private static final class SharedRandomIntProvider implements RandomIntProvider {
        private final Random rnd = new Random(System.nanoTime());

        public int nextInt(int bound) {
            return rnd.nextInt(bound);
        }
    }

    private static final class ThreadLocalRandomIntProvider implements RandomIntProvider {
        public int nextInt(int bound) {
            return ThreadLocalRandom.current().nextInt(bound);
        }
    }


    private static final ThreadLocal<int[]> iCounters = new ThreadLocal<int[]>();

    private static Method nextIntMtd;
    private static Object threadLocalRandom;

    private static final RandomIntProvider rndIntProvider;

    static {
        boolean entered = false;
        try {
            entered = BTraceRuntime.enter();
            Class clz = null;
            try {
                clz = Class.forName("java.util.concurrent.ThreadLocalRandom");
            } catch (Throwable e) {
                // ThreadLocalRandom not accessible -> pre JDK8
            }
            if (clz != null) {
                rndIntProvider = new ThreadLocalRandomIntProvider();
            } else {
                rndIntProvider = new SharedRandomIntProvider();
            }
        } finally {
            if (entered) BTraceRuntime.leave();
        }
    }

    /**
     * Used from the injected code to figure out whether it should record the invocation.
     * @param rate On average each "rate"-th invocation will be recorded
     * @param methodId A unique method id
     * @return Returns {@code true} if the invocation is to be recorded
     */
    public static boolean sampleHit(int rate, int methodId) {
        boolean entered = false;
        try {
            entered = BTraceRuntime.enter();
            int[] sampleCntrs = iCounters.get();
            if (sampleCntrs == null) {
                sampleCntrs = new int[(int) (MethodID.lastMehodId.get() * 1.5)];
                Arrays.fill(sampleCntrs, 0);
                iCounters.set(sampleCntrs);
            } else {
                if (methodId >= sampleCntrs.length) {
                    sampleCntrs = Arrays.copyOf(sampleCntrs, (int) (MethodID.lastMehodId.get() * 1.5));
                }
            }
            if (sampleCntrs[methodId] == 0) {
                sampleCntrs[methodId] = rndIntProvider.nextInt(rate);
                return true;
            }

            sampleCntrs[methodId]--;
            return false;
        } finally {
            if (entered) {
                BTraceRuntime.leave();
            }
        }
    }
}
