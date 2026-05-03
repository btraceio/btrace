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
package io.btrace.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import io.btrace.runtime.auxiliary.Auxiliary;

/** Regression tests for the hidden-class probe defineClass path on JDK 15+. */
class HiddenClassDefineRegressionTest {

  @Test
  void auxiliaryLookupHasFullPrivilegeAccess() throws Exception {
    MethodHandles.Lookup lookup = Auxiliary.lookup();
    assertEquals(Auxiliary.class, lookup.lookupClass(), "lookup must be anchored on Auxiliary");
    // hasFullPrivilegeAccess is JDK 14+; reflect so this test source set stays
    // compilable against older targets.
    Method hasFullPriv;
    try {
      hasFullPriv = MethodHandles.Lookup.class.getMethod("hasFullPrivilegeAccess");
    } catch (NoSuchMethodException tooOld) {
      return;
    }
    assertTrue(
        (Boolean) hasFullPriv.invoke(lookup),
        "Auxiliary.lookup() must grant PRIVATE|MODULE (defineHiddenClass precondition)");
  }

  @Test
  void defineHiddenClassWithAuxiliaryLookupSucceeds() throws Throwable {
    assumeTrue(Runtime.version().feature() >= 15, "defineHiddenClass is JDK 15+");

    String internalName =
        Auxiliary.class.getPackage().getName().replace('.', '/') + "/HiddenClassProbe$Test";
    byte[] bytes = ProbeAnchor.generateAnchorBytes(internalName);

    MethodHandles.Lookup lookup = Auxiliary.lookup();
    Class<?> classOptionClass = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
    Object emptyOptions = Array.newInstance(classOptionClass, 0);
    Method defineHidden =
        MethodHandles.Lookup.class.getMethod(
            "defineHiddenClass", byte[].class, boolean.class, emptyOptions.getClass());
    Object hiddenLookup = defineHidden.invoke(lookup, bytes, true, emptyOptions);
    Method lookupClassMtd = hiddenLookup.getClass().getMethod("lookupClass");
    Class<?> defined = (Class<?>) lookupClassMtd.invoke(hiddenLookup);

    assertNotNull(defined, "defineHiddenClass returned null");
    assertTrue(defined.isHidden(), "expected a hidden class");
    assertTrue(
        defined.getName().startsWith(internalName.replace('/', '.')),
        "hidden class name should start with declared name: " + defined.getName());
  }

  @Test
  void hiddenClassNameIsStrippedForRuntimeLookup() {
    String plain = "io.btrace.runtime.auxiliary.SampleProbe";
    String hidden = plain + "/0x00000007ff0abcd0";
    assertEquals(plain, BTraceRuntimeAccessImpl.normalizeProbeName(hidden));
    assertEquals(plain, BTraceRuntimeAccessImpl.normalizeProbeName(plain));
  }
}
