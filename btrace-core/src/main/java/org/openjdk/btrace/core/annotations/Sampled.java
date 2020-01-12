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

package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@linkplain OnMethod} handler as a sampled one.
 * When a handler is sampled not all events will be traced - only a statistical
 * sample with the given mean.
 * <p>
 * By default an adaptive sampling is used. BTrace will increase or decrease the
 * number of invocations between samples to keep the mean time window, thus
 * decreasing the overall overhead.
 *
 * @author Jaroslav Bachorik
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Sampled {
    int MEAN_DEFAULT = 10;

    /**
     * The sampler kind
     *
     * @return The sampler kind
     */
    Sampler kind() default Sampler.Const;

    /**
     * The sampler mean.
     * <p>
     * For {@code Sampler.Const} it is the average number of events between samples.<br>
     * For {@code Sampler.Adaptive} it is the average time (in ns) between samples<br>
     * </p>
     *
     * @return The sampler mean
     */
    int mean() default MEAN_DEFAULT;

    /**
     * Specifies the sampler kind
     * <ul>
     * <li>{@code None} - no sampling</li>
     * <li>{@code Const} - keeps the average number of events between samples</li>
     * <li>{@code Adaptive} - increases or decreases the average number of events between samples to lower overhead</li>
     * </ul>
     */
    enum Sampler {
        /**
         * No Sampling
         */
        None,
        /**
         * Keeps the average number of events between samples
         */
        Const,
        /**
         * Increases or decreases the average number of events between samples to lower overhead
         */
        Adaptive
    }
}
