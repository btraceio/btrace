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
package io.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation specifies a BTrace probe point by specifying a java class (or classes), a method
 * (or methods in it) and a specific location within it. A BTrace trace action method annotated by
 * this annotation is called when matching the traced program reaches the specified location.
 *
 * @author A. Sundararajan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnMethod {
  /**
   * The probed (or traced) class name. This is either fully qualified name of the class or regular
   * expression within two forward slash characters [like /java\\.awt\\..+/]
   * or @annotation_of_the_class. i.e., specify a class indirectly as a class annotated by specified
   * annotation.
   */
  String clazz();

  /**
   * The probed (or traced) method name. This is either the name of the method or regular expression
   * within two forward slash characters [like /read.+/] or @annotation_of_the_method. i.e., specify
   * a method indirectly as a method annotated by specified annotation.
   */
  String method() default "";

  /**
   * When set to {@code true} type checks will not involve assignability checks based on class
   * hierarchy and only exactly matching types will be resolved as assignable.
   *
   * @return {@code true} if exact type matching is requested; {@code false} otherwise (default)
   */
  boolean exactTypeMatch() default false;

  /**
   * This is method type declaration. This is like Java method declaration but not including method
   * name, parameter names and throws clause. *
   *
   * <p>Eg. <b>public void myMethod(java.lang.String param)</b> will become <b><i>void
   * (java.lang.String)</i></b>
   */
  String type() default "";

  /** Identifies exact "location" or "point" of interest to probe within the set of methods. */
  Location location() default @Location;

  /**
   * Activate this probe according to instrumentation level.
   *
   * <p>It is possible to define enable/disable the handler according to the current instrumentation
   * level. Eg. {@code @OnMethod(clazz="class", method="method", enableAt=@Level(">1")}
   *
   * <p>The developer must make sure that all the handlers which are interconnected in any way (eg.
   * method entry/exit) will be enabled/disabled at a compatible instrumentation level.
   *
   * @return The instrumentation level (default {@code @Level("0")})
   * @see Level
   * @see io.btrace.core.BTraceUtils#getInstrumentationLevel()
   * @since 1.3.4
   */
  Level enableAt() default @Level;
}
