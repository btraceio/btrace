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
package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Type;

public class InstrumentUtilsTest {
  @ParameterizedTest(name = "Left: {0}, Right: {1}, Exact match: {2}, Assignable: {3}")
  @MethodSource("signaturesArguments")
  void testIsAssignableSignature(
      Type[] left, Type[] right, boolean exactMatch, boolean isAssignable) {
    assertEquals(isAssignable, InstrumentUtils.isAssignable(left, right, null, exactMatch));
  }

  @ParameterizedTest(name = "Left: {0}, Right: {1}, Exact match: {2}, Assignable: {3}")
  @MethodSource("typeAssignments")
  void testAssignableTypes(Type left, Type right, boolean exactMatch, boolean isAssignable) {
    assertEquals(isAssignable, InstrumentUtils.isAssignable(left, right, null, exactMatch));
  }

  private static Stream<Arguments> typeAssignments() {
    return Stream.of(
        Arguments.of(Type.INT_TYPE, Type.INT_TYPE, false, true),
        Arguments.of(Type.INT_TYPE, Type.INT_TYPE, true, true),
        Arguments.of(Type.INT_TYPE, Type.LONG_TYPE, false, false),
        Arguments.of(Type.INT_TYPE, Type.LONG_TYPE, true, false),
        Arguments.of(Type.getType(Object.class), Type.getType(Object.class), false, true),
        Arguments.of(Type.getType(Object.class), Type.getType(Object.class), true, true),
        Arguments.of(Type.getType(Object.class), Type.getType(String.class), false, true),
        Arguments.of(Type.getType(Object.class), Type.getType(String.class), true, false),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.getType(Object.class), false, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.getType(Object.class), true, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.getType(String.class), false, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.getType(String.class), true, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.getType(String[].class), false, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.getType(String[].class), true, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.INT_TYPE, false, true),
        Arguments.of(Constants.ANYTYPE_TYPE, Type.INT_TYPE, true, true));
  }

  private static Stream<Arguments> signaturesArguments() {
    return Stream.of(
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            true,
            true),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            false,
            true),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(String.class), Type.INT_TYPE},
            true,
            false),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(String.class), Type.INT_TYPE},
            false,
            true),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(Object.class), Type.getType(String.class)},
            true,
            false),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(Object.class), Type.getType(String.class)},
            false,
            false),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(Object.class), Type.INT_TYPE, Type.getType(Object.class)},
            true,
            false),
        Arguments.of(
            new Type[] {Type.getType(Object.class), Type.INT_TYPE},
            new Type[] {Type.getType(Object.class), Type.INT_TYPE, Type.getType(Object.class)},
            false,
            false));
  }
}
