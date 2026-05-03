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
 * This annotation specifies a particular "location" within a traced/probed java method for BTrace
 * probe specifications.
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
   * Specifies where do want to probe with respect to the location of interest.
   *
   * @see Where
   */
  Where where() default Where.BEFORE;

  /** Specifies the fully qualified class name for certain kind of probe locations. */
  String clazz() default "";

  /** Specifies the method name for certain kind of probe locations. */
  String method() default "";

  /**
   * Specifies the field name for Kind.FIELD_SET and Kind.FIELD_GET probes.
   *
   * @see Kind#FIELD_GET
   * @see Kind#FIELD_SET
   */
  String field() default "";

  /**
   * Specifies field or method type for certain kind of probe locations. The type is specified like
   * in Java source - except the method or field name and parameter names are not included.
   */
  String type() default "";

  /**
   * Specifies the line number for Kind.LINE probes.
   *
   * @see Kind#LINE
   */
  int line() default 0;
}
