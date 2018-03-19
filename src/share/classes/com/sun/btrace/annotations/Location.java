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
 * This annotation specifies a particular "location" within a
 * traced/probed java method for BTrace probe specifications.
 *
 * @author A. Sundararajan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Location {
    /**
     * Kind of the location.
     *
     * @see Kind
     */
    Kind value() default Kind.ENTRY;

    /**
     * Specifies where do want to probe with
     * respect to the location of interest.
     *
     * @see Where
     */
    Where where() default Where.BEFORE;

    /**
     * Specifies the fully qualified class name for
     * certain kind of probe locations.
     *
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "MyClass", method = "myMethod", location = @Location(clazz = "${package}.OtherClass"))}
     * </pre>
     * </p>
     */
    String clazz() default "";

    /**
     * Specifies the method name for
     * certain kind of probe locations.
     *
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "MyClass", method = "myMethod", location = @Location(clazz = "OtherClass", method = "${method}"))}
     * </pre>
     * </p>
     */
    String method() default "";

    /**
     * Specifies the field name for Kind.FIELD_SET
     * and Kind.FIELD_GET probes.
     *
     * @see Kind#FIELD_GET
     * @see Kind#FIELD_SET
     *
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "MyClass", method = "myMethod", location = @Location(clazz = "OtherClass", field = "${field}"))}
     * </pre>
     * </p>
     */
    String field() default "";

    /**
     * Specifies field or method type for
     * certain kind of probe locations. The type
     * is specified like in Java source - except
     * the method or field name and parameter names
     * are not included.
     *
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "MyClass", method = "myMethod", location = @Location(clazz = "OtherClass", type = "${ret} ()"))}
     * </pre>
     * </p>
     */
    String type() default "";

    /**
     * Specifies the line number for Kind.LINE probes.
     *
     * @see Kind#LINE
     */
    int line() default 0;
}