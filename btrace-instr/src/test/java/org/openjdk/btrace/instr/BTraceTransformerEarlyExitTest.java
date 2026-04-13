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
}
