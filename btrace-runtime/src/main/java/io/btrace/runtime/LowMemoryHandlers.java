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

import io.btrace.core.handlers.LowMemoryHandler;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The {@code @OnLowMemory} handlers of one probe: which memory pool each handler watches, and the
 * usage thresholds armed on those pools.
 */
final class LowMemoryHandlers {
  private final Map<String, LowMemoryHandler> byPool = new HashMap<>();

  /**
   * @param handlers the probe's handlers
   * @param poolName resolves a handler's {@code pool} member (which may use probe arguments) to the
   *     pool name
   */
  LowMemoryHandlers(LowMemoryHandler[] handlers, Function<String, String> poolName) {
    for (LowMemoryHandler handler : handlers) {
      byPool.put(poolName.apply(handler.pool), handler);
    }
  }

  /** Sets the usage threshold of every pool a handler watches. */
  void arm(List<MemoryPoolMXBean> pools) {
    for (MemoryPoolMXBean pool : pools) {
      LowMemoryHandler handler = byPool.get(pool.getName());
      if (handler != null && pool.isUsageThresholdSupported()) {
        pool.setUsageThreshold(handler.threshold);
      }
    }
  }

  /** The handler watching the named pool, or {@code null}. */
  LowMemoryHandler handlerFor(String pool) {
    return byPool.get(pool);
  }
}
