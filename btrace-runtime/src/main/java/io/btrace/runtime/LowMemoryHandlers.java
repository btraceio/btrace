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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

  /**
   * Sets the usage threshold of every pool a handler watches.
   *
   * @return one message per watched pool that could not be armed (no pool has that name, the pool
   *     does not support usage thresholds, or it rejects the threshold), naming the pool and
   *     listing the pools that support usage thresholds
   */
  List<String> arm(List<MemoryPoolMXBean> pools) {
    Map<String, String> problems = new TreeMap<>();
    for (String name : byPool.keySet()) {
      problems.put(name, "does not exist");
    }
    List<String> armable = new ArrayList<>();
    for (MemoryPoolMXBean pool : pools) {
      String name = pool.getName();
      LowMemoryHandler handler = byPool.get(name);
      if (!pool.isUsageThresholdSupported()) {
        if (handler != null) {
          problems.put(name, "does not support usage thresholds");
        }
        continue;
      }
      armable.add(name);
      if (handler != null) {
        try {
          pool.setUsageThreshold(handler.threshold);
          problems.remove(name);
        } catch (IllegalArgumentException e) {
          problems.put(
              name, "rejects the threshold " + handler.threshold + " (" + e.getMessage() + ")");
        }
      }
    }
    List<String> warnings = new ArrayList<>();
    for (Map.Entry<String, String> problem : problems.entrySet()) {
      warnings.add(
          "@OnLowMemory pool '"
              + problem.getKey()
              + "' "
              + problem.getValue()
              + "; its handlers will not run. Pools with usage thresholds: "
              + armable);
    }
    return warnings;
  }

  /** The handler watching the named pool, or {@code null}. */
  LowMemoryHandler handlerFor(String pool) {
    return byPool.get(pool);
  }
}
