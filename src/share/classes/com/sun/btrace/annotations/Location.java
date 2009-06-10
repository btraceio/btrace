/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
     */
    String clazz() default "";

    /**
     * Specifies the method name for
     * certain kind of probe locations.
     */
    String method() default "";

    /**
     * Specifies the field name for Kind.FIELD_SET
     * and Kind.FIELD_GET probes.
     *
     * @see Kind#FIELD_GET
     * @see Kind#FIELD_SET
     */
    String field() default "";

    /**
     * Specifies field or method type for
     * certain kind of probe locations. The type
     * is specified like in Java source - except 
     * the method or field name and parameter names
     * are not included.
     */
    String type() default "";

    /**
     * Specifies the line number for Kind.LINE probes.
     *
     * @see Kind#LINE
     */
    int line() default 0;
}