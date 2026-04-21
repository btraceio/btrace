/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package org.openjdk.btrace.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;

/** Regression tests for the hidden-class probe defineClass path on JDK 15+. */
class HiddenClassDefineRegressionTest {

  @Test
  void auxiliaryLookupHasFullPrivilegeAccess() throws Exception {
    MethodHandles.Lookup lookup = Auxiliary.lookup();
    assertEquals(Auxiliary.class, lookup.lookupClass(),
        "lookup must be anchored on Auxiliary");
    // hasFullPrivilegeAccess is JDK 14+; reflect so this test source set stays
    // compilable against older targets.
    Method hasFullPriv;
    try {
      hasFullPriv = MethodHandles.Lookup.class.getMethod("hasFullPrivilegeAccess");
    } catch (NoSuchMethodException tooOld) {
      return;
    }
    assertTrue((Boolean) hasFullPriv.invoke(lookup),
        "Auxiliary.lookup() must grant PRIVATE|MODULE (defineHiddenClass precondition)");
  }

  @Test
  void defineHiddenClassWithAuxiliaryLookupSucceeds() throws Throwable {
    assumeTrue(Runtime.version().feature() >= 15, "defineHiddenClass is JDK 15+");

    String internalName = Auxiliary.class.getPackage().getName().replace('.', '/')
        + "/HiddenClassProbe$Test";
    byte[] bytes = ProbeAnchor.generateAnchorBytes(internalName);

    MethodHandles.Lookup lookup = Auxiliary.lookup();
    Class<?> classOptionClass =
        Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
    Object emptyOptions = Array.newInstance(classOptionClass, 0);
    Method defineHidden = MethodHandles.Lookup.class.getMethod(
        "defineHiddenClass", byte[].class, boolean.class, emptyOptions.getClass());
    Object hiddenLookup = defineHidden.invoke(lookup, bytes, true, emptyOptions);
    Method lookupClassMtd = hiddenLookup.getClass().getMethod("lookupClass");
    Class<?> defined = (Class<?>) lookupClassMtd.invoke(hiddenLookup);

    assertNotNull(defined, "defineHiddenClass returned null");
    assertTrue(defined.isHidden(), "expected a hidden class");
    assertTrue(defined.getName().startsWith(internalName.replace('/', '.')),
        "hidden class name should start with declared name: " + defined.getName());
  }

  @Test
  void hiddenClassNameIsStrippedForRuntimeLookup() {
    String plain = "org.openjdk.btrace.runtime.auxiliary.SampleProbe";
    String hidden = plain + "/0x00000007ff0abcd0";
    assertEquals(plain, BTraceRuntimeAccessImpl.normalizeProbeName(hidden));
    assertEquals(plain, BTraceRuntimeAccessImpl.normalizeProbeName(plain));
  }
}
