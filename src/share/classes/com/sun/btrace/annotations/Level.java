/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Allows specifying a probe handler instrumentation level matching expression.
 * <p>
 * See {@linkplain Level#value()} for the allowed expression syntax.
 *
 * @author Jaroslav Bachorik
 */
public @interface Level {
    /**
     * The level check expression.
     * <p>Allowed syntax is one of the following
     * <ul>
     * <li>{@code @Level("NUMBER")} - the same as {@code @Level(">=NUMBER")}
     * <li>{@code @Level("=NUMBER")} - handler is enabled when instrumentation level
     *     equals <b>NUMBER</b></li>
     * <li>{@code @Level(">NUMBER")} - handler is enabled when instrumentation level
     *     is greater than <b>NUMBER</b></li>
     * <li>{@code @Level(">=NUMBER")} - handler is enabled when instrumentation level
     *     is greater than or equal to <b>NUMBER</b></li>
     * <li>{@code @Level("<NUMBER")} - handler is enabled when instrumentation level
     *     is less than <b>NUMBER</b></li>
     * <li>{@code @Level("<=NUMBER")} - handler is enabled when instrumentation level
     *     is less than or equal to <b>NUMBER</b></li>
     * </ul>
     * <p>Where <b>NUMBER</b> is a non-negative integer number.
     */
    String value() default "";
}
