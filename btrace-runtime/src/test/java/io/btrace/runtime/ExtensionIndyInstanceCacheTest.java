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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.btrace.extension.ExtensionBridge;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** One service instance per (probe class, service): shared by all call sites of the probe. */
class ExtensionIndyInstanceCacheTest {
  /** Stateful service: a per-instance counter. */
  public static class CountingService {
    private int count;

    public CountingService() {}

    public void bump() {
      count++;
    }

    public int count() {
      return count;
    }
  }

  /** Stand-ins for two distinct probe classes. */
  static final class ProbeA {
    static MethodHandles.Lookup lookup() {
      return MethodHandles.lookup();
    }
  }

  static final class ProbeB {
    static MethodHandles.Lookup lookup() {
      return MethodHandles.lookup();
    }
  }

  private ExtensionBridge previousBridge;

  @BeforeEach
  void installBridge() {
    previousBridge = ExtensionIndy.bridge;
    ExtensionIndy.bridge = name -> CountingService.class;
  }

  @AfterEach
  void restoreBridge() {
    ExtensionIndy.bridge = previousBridge;
  }

  private static Object link(MethodHandles.Lookup caller) throws Throwable {
    CallSite cs =
        ExtensionIndy.bootstrapFieldGet(
            caller,
            "svc",
            MethodType.methodType(CountingService.class),
            CountingService.class.getName(),
            "SIMPLE",
            "",
            0,
            "");
    return cs.getTarget().invokeWithArguments();
  }

  @Test
  void callSitesOfSameProbeShareOneInstance() throws Throwable {
    Object first = link(ProbeA.lookup());
    Object second = link(ProbeA.lookup());
    assertSame(first, second, "two call sites of the same probe must see the same service");
    ((CountingService) first).bump();
    assertEquals(1, ((CountingService) second).count());
  }

  @Test
  void differentProbesGetDifferentInstances() throws Throwable {
    assertNotSame(link(ProbeA.lookup()), link(ProbeB.lookup()));
  }

  @Test
  void invalidatingProbeYieldsFreshInstance() throws Throwable {
    Object before = link(ProbeA.lookup());
    Object other = link(ProbeB.lookup());
    ExtensionIndy.invalidateProbe(ProbeA.class.getName().replace('.', '/'));
    Object after = link(ProbeA.lookup());
    assertNotSame(before, after, "unregistered probe must get a fresh instance on redeploy");
    assertSame(other, link(ProbeB.lookup()), "other probes must be unaffected");
  }
}
