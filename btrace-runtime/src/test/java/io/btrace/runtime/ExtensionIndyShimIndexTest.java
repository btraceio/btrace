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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;
import test.shim.ShimService;

class ExtensionIndyShimIndexTest {

  @Test
  void resolvesNoopShimFromIndex() throws Throwable {
    MethodHandles.Lookup lk = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(ShimService.class);
    // Force fallback path by using a bogus service name; optional=1 and mode=SHIM
    CallSite cs =
        ExtensionIndy.bootstrapFieldGet(lk, "svc", mt, "no.such.Service", "SIMPLE", "", 1, "SHIM");
    Object o = cs.getTarget().invokeWithArguments();
    assertNotNull(o, "Expected shim instance for bogus service in SHIM mode");
    assertTrue(
        o instanceof ShimService, "Resolved shim is not a ShimService: " + o.getClass().getName());
    assertEquals(42, ((ShimService) o).value(), "Noop shim should return 42");
  }

  @Test
  void resolvesThrowShimFromIndex() throws Throwable {
    MethodHandles.Lookup lk = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(ShimService.class);
    // Force fallback path by using a bogus service name; optional=1 and mode=THROW
    CallSite cs =
        ExtensionIndy.bootstrapFieldGet(lk, "svc", mt, "no.such.Service", "SIMPLE", "", 1, "THROW");
    ShimService shim = (ShimService) cs.getTarget().invokeWithArguments();
    assertNotNull(shim, "Expected shim instance for bogus service in THROW mode");
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            shim::value,
            "Expected IllegalStateException when invoking throw-shim");
    assertEquals("shim-throw", ex.getMessage(), "Throw shim message mismatch");
  }
}
