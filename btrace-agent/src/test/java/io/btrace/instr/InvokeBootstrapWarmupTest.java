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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

class InvokeBootstrapWarmupTest {

  @Test
  void warmupCompletesWithoutThrowing() {
    assertDoesNotThrow(InvokeBootstrapWarmup::warmup);
  }

  @Test
  void warmupIsIdempotentAcrossRepeatedCalls() {
    assertDoesNotThrow(
        () -> {
          InvokeBootstrapWarmup.warmup();
          InvokeBootstrapWarmup.warmup();
          InvokeBootstrapWarmup.warmup();
        });
  }

  @Test
  void warmupLeavesTheJvmAbleToLinkFreshIndyCallSites() throws Throwable {
    // Not a reproduction of the race itself (that needs concurrent JVM startup, which this
    // fast unit test cannot simulate) - just confirms warmup() doesn't leave java.lang.invoke
    // in a broken state, by exercising a fresh indy-backed call site (a MethodHandle invocation
    // through the public Lookup API) immediately afterward.
    InvokeBootstrapWarmup.warmup();
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle mh =
        lookup.findStatic(
            InvokeBootstrapWarmupTest.class, "trivialTarget", MethodType.methodType(int.class));
    int result = (int) mh.invoke();
    assertTrue(result == 42);
  }

  static int trivialTarget() {
    return 42;
  }
}
