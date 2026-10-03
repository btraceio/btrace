/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.instr.BTraceTransformer.Filter;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BTraceTransformerFilterTest {
  private static OnMethod onMethod(String clazz) {
    OnMethod om = new OnMethod();
    om.setClazz(clazz);
    return om;
  }

  @Test
  void exactNameStaysMatchedUntilItsLastRegistrationIsRemoved() {
    Filter filter = new Filter();
    filter.add(onMethod("com.example.Target"));
    filter.add(onMethod("com.example.Target"));

    filter.remove(onMethod("com.example.Target"));
    assertEquals(Filter.Result.TRUE, filter.matchClass("com/example/Target"));

    filter.remove(onMethod("com.example.Target"));
    assertEquals(Filter.Result.FALSE, filter.matchClass("com/example/Target"));
  }

  @Test
  void regexStaysMatchedUntilItsLastRegistrationIsRemoved() {
    Filter filter = new Filter();
    filter.add(onMethod("/com\\.example\\..*/"));
    filter.add(onMethod("/com\\.example\\..*/"));

    filter.remove(onMethod("/com\\.example\\..*/"));
    assertEquals(Filter.Result.TRUE, filter.matchClass("com/example/Target"));

    filter.remove(onMethod("/com\\.example\\..*/"));
    assertEquals(Filter.Result.FALSE, filter.matchClass("com/example/Target"));
  }

  /**
   * Regression for #932: matchClass runs for every class definition in the JVM (JDK-internal hidden
   * classes defined mid socket write included) and must not block on the filter maps' monitors. A
   * captured exit-path wedge had the command-queue drain BLOCKED right there.
   */
  @ParameterizedTest
  @ValueSource(strings = {"nameMap", "nameRegexMap"})
  void matchClassDoesNotBlockOnAHeldMapMonitor(String mapField) throws Exception {
    Filter filter = new Filter();
    filter.add(onMethod("com.example.Target"));
    filter.add(onMethod("/org\\.example\\..*/"));
    Field field = Filter.class.getDeclaredField(mapField);
    field.setAccessible(true);
    Object map = field.get(filter);

    CountDownLatch monitorHeld = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder =
        new Thread(
            () -> {
              synchronized (map) {
                monitorHeld.countDown();
                try {
                  release.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
            },
            "filter-map-monitor-holder");
    holder.setDaemon(true);
    AtomicReference<Filter.Result> result = new AtomicReference<>();
    // An unmatched name walks both the exact map and the regex map.
    Thread matcher =
        new Thread(() -> result.set(filter.matchClass("net/example/Other")), "class-definition");
    matcher.setDaemon(true);
    holder.start();
    try {
      assertTrue(monitorHeld.await(10, TimeUnit.SECONDS), "monitor holder never started");
      matcher.start();
      matcher.join(TimeUnit.SECONDS.toMillis(10));
      assertFalse(matcher.isAlive(), "matchClass blocked on the " + mapField + " monitor");
      assertEquals(Filter.Result.FALSE, result.get());
    } finally {
      release.countDown();
      holder.join(TimeUnit.SECONDS.toMillis(10));
      matcher.join(TimeUnit.SECONDS.toMillis(10));
    }
  }
}
