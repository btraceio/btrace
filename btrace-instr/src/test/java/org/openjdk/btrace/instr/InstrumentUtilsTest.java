package org.openjdk.btrace.instr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Type;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstrumentUtilsTest {
    @ParameterizedTest(name = "Left: {0}, Right: {1}, Exact match: {2}, Assignable: {3}")
    @MethodSource("signaturesArguments")
    void testIsAssignableSignature(Type[] left, Type[] right, boolean exactMatch, boolean isAssignable) {
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
                Arguments.of(Constants.ANYTYPE_TYPE, Type.INT_TYPE, true, true)
        );
    }

    private static Stream<Arguments> signaturesArguments() {
        return Stream.of(
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        true, true
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        false, true
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(String.class), Type.INT_TYPE},
                        true, false
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(String.class), Type.INT_TYPE},
                        false, true
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(Object.class), Type.getType(String.class)},
                        true, false
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(Object.class), Type.getType(String.class)},
                        false, false
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE, Type.getType(Object.class)},
                        true, false
                ),
                Arguments.of(
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE},
                        new Type[] {Type.getType(Object.class), Type.INT_TYPE, Type.getType(Object.class)},
                        false, false
                )
        );
    }
}
