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
package io.btrace.core.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.management.MemoryUsage;
import org.junit.jupiter.api.Test;

class LowMemoryHandlerTest {
  static int calls;

  public static void onLowMemory(MemoryUsage usage) {
    calls++;
  }

  /**
   * Persisted probe packs, 2.x ones included, hold class bytes the preprocessor already wrote, and
   * their static initializer calls the five-argument constructor. It must keep working.
   */
  @Test
  void legacyConstructorFromPersistedProbesStillBuildsAWorkingHandler() throws Exception {
    LowMemoryHandler handler =
        LowMemoryHandler.class
            .getConstructor(String.class, String.class, long.class, String.class, boolean.class)
            .newInstance("onLowMemory", "Metaspace", 1048576L, null, true);
    assertEquals("Metaspace", handler.pool);
    assertEquals(1048576L, handler.threshold);

    calls = 0;
    handler.invoke(LowMemoryHandlerTest.class, new MemoryUsage(0, 1, 1, -1));
    assertEquals(1, calls);
  }
}
