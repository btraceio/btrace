/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openjdk.btrace.core.annotations;

import org.openjdk.btrace.core.types.AnyType;

/**
 * This enum is specified in the Location annotation to specify probe point kind. This enum
 * identifies various "points" of interest within a Java method's bytecode.
 *
 * @author A. Sundararajan
 */
public enum Kind {
  /**
   *
   *
   * <h2>Array element load</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@code type[]} - the array instance
   *   <li>{@link int int} - array index
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain
   *       Where#AFTER})
   * </ul>
   */
  ARRAY_GET,

  /**
   *
   *
   * <h2>Array element store</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@code type[]} - the array instance
   *   <li>{@link int int} - array index
   *   <li>{@link java.lang.Object Object} - new value
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  ARRAY_SET,

  /**
   *
   *
   * <h2>Method call</h2>
   *
   * <p>The order and number of unannotated parameters (if provided) must fully match the called
   * method signature. Instead of specific parameter types one can use {@linkplain AnyType} to match
   * any type.
   *
   * <p>If the only unannotated parameter is of type {@link AnyType AnyType[]} it will contain the
   * called method parameters in the order defined by its signature.
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain TargetInstance} - the target instance of the method call or null if the
   *       method is static
   *   <li>{@linkplain TargetMethodOrField} - the name of the method which is called
   *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain
   *       Where#AFTER})
   *   <li>{@linkplain Duration} - the method call duration in nanoseconds (only for {@linkplain
   *       Where#AFTER}
   * </ul>
   */
  CALL,

  /**
   *
   *
   * <h2>Exception catch</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.Throwable Throwable} - caught throwable
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  CATCH,

  /**
   *
   *
   * <h2>Checkcast</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.String String} - type to cast to
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain TargetInstance} - the casted instance ({@linkplain AnyType})
   * </ul>
   */
  CHECKCAST,

  /**
   *
   *
   * <h2>Method entry</h2>
   *
   * <p>The order and number of unannotated parameters (if provided) must fully match the probed
   * method signature. Instead of specific parameter types one can use {@linkplain AnyType} to match
   * any type.
   *
   * <p>If the only unannotated parameter is of type {@link AnyType AnyType[]} it will contain the
   * probed method parameters in the order defined by its signature.
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  ENTRY,

  /**
   *
   *
   * <h2>"return" because of no-catch</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.Throwable Throwable} - the thrown throwable
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain Duration} - the method call duration in nanoseconds (only for {@linkplain
   *       Where#AFTER}
   * </ul>
   */
  ERROR,

  /**
   *
   *
   * <h2>Getting a field value</h2>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain TargetInstance} - the field owner instance or null if the field is static
   *   <li>{@linkplain TargetMethodOrField} - the name of the method which is called
   *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain
   *       Where#AFTER})
   * </ul>
   */
  FIELD_GET,

  /**
   *
   *
   * <h2>Setting a field value</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.Object Object} - new field value
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that field is
   *       static
   *   <li>{@linkplain TargetInstance} - the field owner instance or null if the field is static
   *   <li>{@linkplain TargetMethodOrField} - the name of the method which is called
   * </ul>
   */
  FIELD_SET,

  /**
   *
   *
   * <h2>instanceof check</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.String String} - type to check against
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain TargetInstance} - the checked instance ({@linkplain AnyType})
   * </ul>
   */
  INSTANCEOF,

  /**
   *
   *
   * <h2>Source line number</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link int int} - line number
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  LINE,

  /**
   *
   *
   * <h2>New object created</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.String String} - object type name
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain
   *       Where#AFTER})
   * </ul>
   */
  NEW,

  /**
   *
   *
   * <h2>New array created</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.String String} - array type name
   *   <li>{@link int int} - number of dimensions
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain
   *       Where#AFTER})
   * </ul>
   */
  NEWARRAY,

  /**
   *
   *
   * <h2>Return from method</h2>
   *
   * <p>The order and number of unannotated probe handler parameters (if provided) must fully match
   * the probed method signature. Instead of specific parameter types one can use {@linkplain
   * AnyType} to match any type.
   *
   * <p>If the only unannotated parameter is of type {@link AnyType AnyType[]} it will contain the
   * probed method parameters in the order defined by its signature.
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   *   <li>{@linkplain Return} - the return value of the method call (only for {@linkplain
   *       Where#AFTER})
   *   <li>{@linkplain Duration} - the method call duration in nanoseconds (only for {@linkplain
   *       Where#AFTER}
   * </ul>
   */
  RETURN,

  /**
   *
   *
   * <h2>Entry into a synchronized block</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.Object Object} - lock object
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  SYNC_ENTRY,

  /**
   *
   *
   * <h2>Exit from a synchronized block</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@link java.lang.Object Object} - lock object
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  SYNC_EXIT,

  /**
   *
   *
   * <h2>Throwing an exception</h2>
   *
   * <h3>Unannotated probe handler parameters:</h3>
   *
   * <ol>
   *   <li>{@linkplain java.lang.Throwable Throwable} - thrown exception
   * </ol>
   *
   * <h3>Allowed probe handler parameter annotations:</h3>
   *
   * <ul>
   *   <li>{@linkplain ProbeClassName} - the name of the enclosing class
   *   <li>{@linkplain ProbeMethodName} - the name of the enclosing method
   *   <li>{@linkplain Self} - the instance enclosing the declaring method or null if that method is
   *       static
   * </ul>
   */
  THROW
}
