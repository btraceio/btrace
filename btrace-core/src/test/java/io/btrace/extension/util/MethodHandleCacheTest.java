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
package io.btrace.extension.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MethodHandleCacheTest {
  @Test
  void cachesSuccessfulPublicLookupsAndRetriesFailuresWithoutLoaderSafetyClaim() throws Exception {
    MethodHandleCache cache = new MethodHandleCache();

    MethodHandle virtual = cache.findVirtual(Fixture.class, "value", int.class);
    assertSame(virtual, cache.findVirtual(Fixture.class, "value", int.class));
    MethodHandle statik = cache.findStatic(Fixture.class, "twice", int.class, int.class);
    assertSame(statik, cache.findStatic(Fixture.class, "twice", int.class, int.class));
    assertEquals(2, entries(cache));

    assertThrows(
        MethodHandleCache.LookupRuntimeException.class,
        () -> cache.findVirtual(Fixture.class, "missing", int.class));
    assertEquals(2, entries(cache), "failed lookup must not be stored");
    assertThrows(
        MethodHandleCache.LookupRuntimeException.class,
        () -> cache.findVirtual(Fixture.class, "missing", int.class));
    assertEquals(2, entries(cache), "a repeated failed lookup must remain retryable");
  }

  @SuppressWarnings("unchecked")
  private static int entries(MethodHandleCache cache) throws Exception {
    Field field = MethodHandleCache.class.getDeclaredField("cache");
    field.setAccessible(true);
    return ((Map<Object, MethodHandle>) field.get(cache)).size();
  }

  public static final class Fixture {
    public int value() {
      return 1;
    }

    public static int twice(int value) {
      return value * 2;
    }
  }
}
