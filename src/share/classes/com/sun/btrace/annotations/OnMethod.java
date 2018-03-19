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
 * 
 * Copyright (c) 2017, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */

package com.sun.btrace.annotations;

import com.sun.btrace.BTraceUtils;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation specifies a BTrace probe point by specifying
 * a java class (or classes), a method (or methods in it) and
 * a specific location within it. A BTrace trace action method
 * annotated by this annotation is called when matching the traced
 * program reaches the specified location.
 *
 * @author A. Sundararajan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnMethod {
    /**
     * The probed (or traced) class name. This is either
     * fully qualified name of the class or regular expression
     * within two forward slash characters [like /java\\.awt\\..+/]
     * or @annotation_of_the_class. i.e., specify a class indirectly
     * as a class annotated by specified annotation.
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "${package}.MyClass")}
     * </pre>
     * </p>
     */
    String clazz();

    /**
     * The probed (or traced) method name. This is either
     * the name of the method or regular expression
     * within two forward slash characters [like /read.+/]
     * or @annotation_of_the_method. i.e., specify a method indirectly
     * as a method annotated by specified annotation.
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "MyClass", method = "${method}")}
     * </pre>
     * </p>
     */
    String method() default "";

    /**
     * When set to {@code true} type checks will not involve assignability
     * checks based on class hierarchy and only exactly matching types will
     * be resolved as assignable.
     * @return {@code true} if exact type matching is requested; {@code false} otherwise (default)
     */
    boolean exactTypeMatch() default false;

    /**
     * This is method type declaration. This is like Java method
     * declaration but not including method name, parameter
     * names and throws clause.
     * <p>
     * Eg. <b>public void myMethod(java.lang.String param)</b> will become
     * <b><i>void (java.lang.String)</i></b>
     *
     * <p>
     * <h3>Since 1.3.11</h3>
     * The specification can contain references to user arguments.
     * These references are using Ant style substitution patterns.
     * If a reference is not resolvable the whole probe point will be effectively disabled.
     * <br>
     * <pre>
     * {@code @OnMethod(clazz = "MyClass", method = "method", type = "${retType} ()")}
     * </pre>
     * </p>
     */
    String type() default "";

    /**
     * Identifies exact "location" or "point" of interest to
     * probe within the set of methods.
     */
    Location location() default @Location();

    /**
     * Activate this probe according to instrumentation level.
     *
     * <p>
     * It is possible to define enable/disable the handler according to the
     * current instrumentation level. Eg. {@code @OnMethod(clazz="class",
     * method="method", enableAt=@Level(">1")}
     * <p>
     * The developer must make sure that all the handlers which are interconnected
     * in any way (eg. method entry/exit) will be enabled/disabled at a compatible
     * instrumentation level.
     *
     * @return The instrumentation level (default {@code @Level("0")})
     * @see Level
     * @see BTraceUtils#getInstrumentationLevel()
     * @since 1.3.4
     */
    Level enableAt() default @Level;
}
