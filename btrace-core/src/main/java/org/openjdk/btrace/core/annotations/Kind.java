/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.btrace.core.types.AnyType;

/**
 * This enum is specified in the Location
 * annotation to specify probe point kind.
 * This enum identifies various "points" of
 * interest within a Java method's bytecode.
 *
 * @author A. Sundararajan
 */
public enum Kind {
    /**
     * <h2>Array element load</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@code type[]} - the array instance</li>
     *   <li>{@link int int} - array index</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain Where#AFTER})</li>
     * </ul>
     */
    ARRAY_GET,

    /**
     * <h2>Array element store</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@code type[]} - the array instance</li>
     *   <li>{@link int int} - array index</li>
     *   <li>{@link java.lang.Object Object} - new value</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    ARRAY_SET,

    /**
     * <h2>Method call</h2>
     * <p>
     * The order and number of unannotated parameters (if provided) must
     * fully match the called method signature. Instead of specific parameter
     * types one can use {@linkplain AnyType} to match any type.
     * </p>
     * <p>
     * If the only unannotated parameter is of type {@link AnyType AnyType[]}
     * it will contain the called method parameters in the order defined by
     * its signature.
     * </p>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain TargetInstance} - the target instance of the method call
     *       or null if the method is static</li>
     *   <li>{@linkplain TargetMethodOrField} - the name of the method which is called</li>
     *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain Where#AFTER})</li>
     *   <li>{@linkplain Duration} - the method call duration in nanoseconds (only for {@linkplain Where#AFTER}</li>
     * </ul>
     */
    CALL,

    /**
     * <h2>Exception catch</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.Throwable Throwable} - caught throwable</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    CATCH,

    /**
     * <h2>Checkcast</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.String String} - type to cast to</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain TargetInstance} - the casted instance ({@linkplain AnyType})</li>
     * </ul>
     */
    CHECKCAST,

    /**
     * <h2>Method entry</h2>
     * <p>
     * The order and number of unannotated parameters (if provided) must
     * fully match the probed method signature. Instead of specific parameter
     * types one can use {@linkplain AnyType} to match any type.
     * </p>
     * <p>
     * If the only unannotated parameter is of type {@link AnyType AnyType[]}
     * it will contain the probed method parameters in the order defined by
     * its signature.
     * </p>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    ENTRY,

    /**
     * <h2>"return" because of no-catch</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.Throwable Throwable} - the thrown throwable</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain Duration} - the method call duration in nanoseconds (only for {@linkplain Where#AFTER}</li>
     * </ul>
     */
    ERROR,

    /**
     * <h2>Getting a field value</h2>
     *
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain TargetInstance} - the field owner instance or null
     *       if the field is static</li>
     *   <li>{@linkplain TargetMethodOrField} - the name of the method which is called</li>
     *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain Where#AFTER})</li>
     * </ul>
     */
    FIELD_GET,

    /**
     * <h2>Setting a field value</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.Object Object} - new field value</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that field is static</li>
     *   <li>{@linkplain TargetInstance} - the field owner instance or null
     *       if the field is static</li>
     *   <li>{@linkplain TargetMethodOrField} - the name of the method which is called</li>
     * </ul>
     */
    FIELD_SET,

    /**
     * <h2>instanceof check</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.String String} - type to check against</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain TargetInstance} - the checked instance ({@linkplain AnyType})</li>
     * </ul>
     */
    INSTANCEOF,

    /**
     * <h2>Source line number</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link int int} - line number</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    LINE,

    /**
     * <h2>New object created</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.String String} - object type name</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain Where#AFTER})</li>
     * </ul>
     */
    NEW,

    /**
     * <h2>New array created</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.String String} - array type name</li>
     *   <li>{@link int int} - number of dimensions</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain Where#AFTER})</li>
     * </ul>
     */
    NEWARRAY,

    /**
     * <h2>Return from method</h2>
     * <p>
     * The order and number of unannotated probe handler parameters (if provided)
     * must fully match the probed method signature. Instead of specific parameter
     * types one can use {@linkplain AnyType} to match any type.
     * </p>
     * <p>
     * If the only unannotated parameter is of type {@link AnyType AnyType[]}
     * it will contain the probed method parameters in the order defined by
     * its signature.
     * </p>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain Where#AFTER})</li>
     *   <li>{@linkplain Duration} - the method call duration in nanoseconds (only for {@linkplain Where#AFTER}</li>
     * </ul>
     */
    RETURN,

    /**
     * <h2>Entry into a synchronized block</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.Object Object} - lock object</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    SYNC_ENTRY,

    /**
     * <h2>Exit from a synchronized block</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@link java.lang.Object Object} - lock object</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    SYNC_EXIT,

    /**
     * <h2>Throwing an exception</h2>
     *
     * <h3>Unannotated probe handler parameters:</h3>
     * <ol>
     *   <li>{@linkplain java.lang.Throwable Throwable} - thrown exception</li>
     * </ol>
     * <h3>Allowed probe handler parameter annotations:</h3>
     * <ul>
     *   <li>{@linkplain ProbeClassName} - the name of the enclosing class</li>
     *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method</li>
     *   <li>{@linkplain Self} - the instance enclosing the declaring method or null
     *       if that method is static</li>
     * </ul>
     */
    THROW
}
