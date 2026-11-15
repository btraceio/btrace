/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.handlers.LowMemoryHandler;
import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** {@link LowMemoryHandlers} against fake memory pools. */
class LowMemoryHandlersTest {
  /** A memory pool whose usage threshold the test controls; names are unique per test. */
  static final class FakePool {
    final String name;
    final boolean supported;
    final long max;
    long threshold;
    final MemoryPoolMXBean bean;

    FakePool(String name, boolean supported, long max, long threshold) {
      this.name = name;
      this.supported = supported;
      this.max = max;
      this.threshold = threshold;
      bean =
          (MemoryPoolMXBean)
              Proxy.newProxyInstance(
                  MemoryPoolMXBean.class.getClassLoader(),
                  new Class<?>[] {MemoryPoolMXBean.class},
                  (proxy, method, args) -> {
                    switch (method.getName()) {
                      case "getName":
                        return this.name;
                      case "isUsageThresholdSupported":
                        return this.supported;
                      case "getUsageThreshold":
                        return this.threshold;
                      case "setUsageThreshold":
                        long value = (Long) args[0];
                        if (!this.supported) {
                          throw new UnsupportedOperationException();
                        }
                        if (value < 0 || (this.max >= 0 && value > this.max)) {
                          throw new IllegalArgumentException("out of range: " + value);
                        }
                        this.threshold = value;
                        return null;
                      default:
                        throw new UnsupportedOperationException(method.getName());
                    }
                  });
    }
  }

  private static final Function<String, String> AS_IS = Function.identity();

  private static LowMemoryHandler handler(String method, String pool, long threshold) {
    return new LowMemoryHandler(method, pool, threshold, null, true);
  }

  @Test
  void armsWatchedPoolsAndReportsTheOnesItCannotArm() {
    FakePool armed = new FakePool("arm-test-armed", true, -1, 0);
    FakePool noThreshold = new FakePool("arm-test-no-threshold", false, -1, 0);
    FakePool tooSmall = new FakePool("arm-test-too-small", true, 100, 0);
    LowMemoryHandlers handlers =
        new LowMemoryHandlers(
            new LowMemoryHandler[] {
              handler("a", "arm-test-armed", 1000),
              handler("b", "arm-test-no-threshold", 1000),
              handler("c", "arm-test-too-small", 1000),
              handler("d", "arm-test-missing", 1000)
            },
            AS_IS);

    List<String> warnings =
        handlers.arm(Arrays.asList(armed.bean, noThreshold.bean, tooSmall.bean));

    assertEquals(1000, armed.threshold);
    assertEquals(3, warnings.size(), warnings.toString());
    assertTrue(warnings.get(0).contains("'arm-test-missing' does not exist"), warnings.get(0));
    assertTrue(
        warnings.get(1).contains("'arm-test-no-threshold' does not support usage thresholds"),
        warnings.get(1));
    assertTrue(warnings.get(2).contains("'arm-test-too-small' rejects the threshold 1000"));
    for (String warning : warnings) {
      assertTrue(
          warning.endsWith("[arm-test-armed, arm-test-too-small]"),
          "pools with thresholds not listed: " + warning);
    }
  }
}
