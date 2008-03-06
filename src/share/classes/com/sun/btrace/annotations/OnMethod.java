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
     */
    String clazz();

    /**
     * The probed (or traced) method name. This is either
     * the name of the method or regular expression
     * within two forward slash characters [like /read.+/]	
     * or @annotation_of_the_method. i.e., specify a method indirectly 
     * as a method annotated by specified annotation.
     */
    String method() default "";

    /**
     * This is method type declaration. This is like Java method
     * declaration but not including method name, parameter
     * names and throws clause.
     */
    String type() default "";

    /**
     * Identifies exact "location" or "point" of interest to
     * probe within the set of methods.
     */
    Location location() default @Location();
}
