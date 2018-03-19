/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BTrace methods annotated by this annotation are called when
 * the traced JVM's specified memory pool exceeds specified 
 * threshold size.
 *
 * @author A. Sundararajan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnLowMemory {
    /**
     * The memory pool name.
     *
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the handler will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnLowMemory(pool = "${firstPool}")}
     * </pre>
     * </p>
     */
    String pool();

    /**
     * The threashold size to watch for.
     */
    long threshold();

    /**
     * If specified the threshold will be taken from btrace arguments.
     * The format is Ant-like property reference - eg. {@code thresholdFrom = "${threshold}"}
     * @return btrace argument name holding the threshold
     * @since 1.3.11
     */
    String thresholdFrom() default "";
}
