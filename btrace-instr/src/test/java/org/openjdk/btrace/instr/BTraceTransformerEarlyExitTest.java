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
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the load-bearing structural early-exit in {@link BTraceTransformer#transform}
 * for JVM-synthesized reflective accessor classes. This is the primary defense
 * against the JDK 8 reflection-inflation StackOverflowError observed in
 * testTraceAll on btraceio/btrace#830.
 *
 * <p>The early-exit must trigger regardless of class loader because synthetic
 * accessor classes are defined by {@code sun.reflect.DelegatingClassLoader} (JDK 8)
 * — neither null nor the system class loader — so the loader-gated
 * {@code isSensitiveClass()} branch in the same method does NOT filter them.
 * This test pins the loader-independence so a future refactor that accidentally
 * moves the early-exit below the loader gate will fail loudly here, even on
 * JDK 11+ build JVMs where the SOE itself is not reproducible.
 */
class BTraceTransformerEarlyExitTest {

    private BTraceTransformer newTransformer() {
        return new BTraceTransformer(new DebugSupport(new SharedSettings()));
    }

    /** A class loader that is neither {@code null} nor the system CL — emulates DelegatingClassLoader. */
    private ClassLoader nonBootstrapNonSystemLoader() {
        return new ClassLoader(null) {};
    }

    @Test
    void sunReflectGeneratedAccessorReturnsNull() throws Exception {
        BTraceTransformer transformer = newTransformer();
        byte[] result = transformer.transform(
                nonBootstrapNonSystemLoader(),
                "sun/reflect/GeneratedMethodAccessor42",
                null,
                null,
                new byte[]{0, 0});
        assertNull(result, "synthetic JDK 8 accessor must NOT be transformed (returns null)");
    }

    @Test
    void sunReflectGeneratedConstructorAccessorReturnsNull() throws Exception {
        BTraceTransformer transformer = newTransformer();
        byte[] result = transformer.transform(
                nonBootstrapNonSystemLoader(),
                "sun/reflect/GeneratedConstructorAccessor7",
                null,
                null,
                new byte[]{0, 0});
        assertNull(result, "synthetic JDK 8 constructor accessor must NOT be transformed (returns null)");
    }

    @Test
    void jdkInternalReflectGeneratedAccessorReturnsNull() throws Exception {
        BTraceTransformer transformer = newTransformer();
        byte[] result = transformer.transform(
                nonBootstrapNonSystemLoader(),
                "jdk/internal/reflect/GeneratedMethodAccessor3",
                null,
                null,
                new byte[]{0, 0});
        assertNull(result, "synthetic JDK 9-16 accessor must NOT be transformed (returns null)");
    }

    @Test
    void nullClassNameReturnsNull() throws Exception {
        BTraceTransformer transformer = newTransformer();
        byte[] result = transformer.transform(
                nonBootstrapNonSystemLoader(),
                null,
                null,
                null,
                new byte[]{0, 0});
        assertNull(result, "classes with no binary name (JDK 8 host-anonymous, JDK 15+ hidden) must NOT be transformed");
    }

    @Test
    void jdk8LambdaWrapperReturnsNull() throws Exception {
        BTraceTransformer transformer = newTransformer();
        byte[] result = transformer.transform(
                nonBootstrapNonSystemLoader(),
                "org/openjdk/btrace/agent/Main$$Lambda$36",
                null,
                null,
                new byte[]{0, 0});
        assertNull(result, "JDK 8 synthetic lambda wrapper (Main$$Lambda$N) must NOT be transformed");
    }

    @Test
    void jdk11LambdaWrapperReturnsNull() throws Exception {
        BTraceTransformer transformer = newTransformer();
        byte[] result = transformer.transform(
                nonBootstrapNonSystemLoader(),
                "org/openjdk/btrace/agent/Main$$Lambda$12/0x00000008000ab040",
                null,
                null,
                new byte[]{0, 0});
        assertNull(result, "JDK 11+ synthetic lambda wrapper (Main$$Lambda$N/0x...) must NOT be transformed");
    }

    @Test
    void userClassWithDoubleDollarInNameIsNotSkipped() {
        // A class whose internal name merely contains "$$" but NOT the specific
        // "$$Lambda$<digit>" pattern is a legitimate user class (e.g. Kotlin,
        // Scala, or Groovy synthetic) and must remain eligible for tracing.
        // Bypassing the short-circuit ensures the regex is anchored correctly.
        org.junit.jupiter.api.Assertions.assertFalse(
                BTraceTransformer.isSyntheticLambda("com/example/My$$Bridge"),
                "user class without Lambda$<digit> must NOT match the synthetic-lambda predicate");
        org.junit.jupiter.api.Assertions.assertFalse(
                BTraceTransformer.isSyntheticLambda("com/example/My$$Lambda$"),
                "malformed name with no digit after $$Lambda$ must NOT match");
        org.junit.jupiter.api.Assertions.assertFalse(
                BTraceTransformer.isSyntheticLambda("com/example/My$$LambdaBridge"),
                "substring match without the trailing $ must NOT match");
        org.junit.jupiter.api.Assertions.assertTrue(
                BTraceTransformer.isSyntheticLambda("Foo$$Lambda$0"),
                "JDK 8-style Lambda wrapper must match");
        org.junit.jupiter.api.Assertions.assertTrue(
                BTraceTransformer.isSyntheticLambda("Foo$$Lambda$99/0xdeadbeef"),
                "JDK 11+ named-hidden Lambda wrapper must match");
    }
}
