/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 */
package org.openjdk.btrace.instr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the sensitive-class filter entries that protect against the JDK 8
 * reflection-inflation StackOverflowError. When the agent makes any reflective
 * Method.invoke() past the inflation threshold, the JVM defines a synthetic
 * accessor class under sun/reflect/Generated* (JDK 8) or jdk/internal/reflect/
 * Generated* (JDK 9-16). If those classes are not filtered, BTraceTransformer
 * will instrument them, ASM frame computation will issue more reflective calls,
 * the cascade recurses, and the agent crashes during testTraceAll on JDK 8.
 *
 * Coverage is loader-independent because synthetic accessors are defined in
 * sun.reflect.DelegatingClassLoader (not bootstrap, not system); the
 * structural early-exit in BTraceTransformer.transform() ALSO does not depend
 * on loader, but this test pins the SENSITIVE_CLASSES list as defense-in-depth
 * against future refactors of the transformer entry path.
 */
class ClassFilterSensitiveTest {

    @Test
    void sunReflectGeneratedAccessorIsSensitive() {
        assertTrue(ClassFilter.isSensitiveClass("sun/reflect/GeneratedMethodAccessor42"),
                "sun/reflect/GeneratedMethodAccessor* must be sensitive — instrumenting it triggers JDK 8 inflation cascade");
        assertTrue(ClassFilter.isSensitiveClass("sun/reflect/GeneratedConstructorAccessor1"),
                "sun/reflect/GeneratedConstructorAccessor* must be sensitive — same cascade source via constructor inflation");
        assertTrue(ClassFilter.isSensitiveClass("sun/reflect/GeneratedSerializationConstructorAccessor7"),
                "sun/reflect/GeneratedSerializationConstructorAccessor* must be sensitive — same cascade source");
    }

    @Test
    void jdkInternalReflectIsSensitive() {
        assertTrue(ClassFilter.isSensitiveClass("jdk/internal/reflect/GeneratedMethodAccessor3"),
                "JDK 9-16 renames the synthetic accessors under jdk/internal/reflect — must be sensitive too");
    }

    @Test
    void appClassesAreNotSensitive() {
        assertFalse(ClassFilter.isSensitiveClass("com/example/MyApp"),
                "Ordinary application classes must not be filtered");
        assertFalse(ClassFilter.isSensitiveClass("org/junit/jupiter/api/Test"),
                "Library classes outside the sensitive prefixes must not be filtered");
    }
}
