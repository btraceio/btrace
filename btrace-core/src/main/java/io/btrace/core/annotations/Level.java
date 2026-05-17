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

/**
 * Allows specifying a probe handler instrumentation level matching expression.
 *
 * <p>See {@linkplain Level#value()} for the allowed expression syntax.
 *
 * @author Jaroslav Bachorik
 */
public @interface Level {
  /**
   * The level check expression.<br>
   * Allowed syntax is one of the following
   *
   * <ul>
   *   <li>{@code @Level("NUMBER")} - the same as {@code @Level(">=NUMBER")}
   *   <li>{@code @Level("=NUMBER")} - handler is enabled when instrumentation level equals
   *       <b>NUMBER</b>
   *   <li>{@code @Level(">NUMBER")} - handler is enabled when instrumentation level is greater than
   *       <b>NUMBER</b>
   *   <li>{@code @Level(">=NUMBER")} - handler is enabled when instrumentation level is greater
   *       than or equal to <b>NUMBER</b>
   *   <li>{@code @Level("<NUMBER")} - handler is enabled when instrumentation level is less than
   *       <b>NUMBER</b>
   *   <li>{@code @Level("<=NUMBER")} - handler is enabled when instrumentation level is less than
   *       or equal to <b>NUMBER</b>
   * </ul>
   *
   * <p>Where <b>NUMBER</b> is a non-negative integer number.
   *
   * @return the level
   */
  String value() default "";
}
