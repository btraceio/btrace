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

package com.sun.btrace.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@linkplain OnMethod} handler as a sampled one.
 * When a handler is sampled not all events will be traced - only a statistical
 * sample with the given mean.
 *
 *
 *
 * @author Jaroslav Bachorik
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Sampled {
    public static final int MEAN_DEFAULT = 10;

    /**
     * Specifies the sampler kind
     * <ul>
     * <li>{@code None} - no sampling</li>
     * <li>{@code Avg} - keeps the average number of events between samples</li>
     * <li>{@code Adaptive} - increases or decreases the average number of events between samples to lower overhead</li>
     * </ul>
     */
    public static enum Sampler {
        /**
         * No Sampling
         */
        None,
        /**
         * Keeps the average number of events between samples
         */
        Avg,
        /**
         * Increases or decreases the average number of events between samples to lower overhead
         */
        Adaptive
    }

    /**
     * The sampler kind
     * @return The sampler kind
     */
    Sampler kind() default Sampler.Avg;
    /**
     * The sampler mean.
     * It is the average number of events between taking sample
     * @return The sampler mean
     */
    int mean() default MEAN_DEFAULT;
}
