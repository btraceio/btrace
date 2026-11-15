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
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The {@code @OnLowMemory} handlers of one probe: which memory pool each handler watches, and the
 * usage thresholds armed on those pools.
 *
 * <p>A pool has one usage threshold, so a pool watched by several handlers is armed with the lowest
 * of their thresholds, and a notification runs only the handlers whose own threshold the reported
 * usage has reached. The JVM notifies once per crossing of the armed threshold, so a handler with a
 * higher threshold runs only if usage is already at its threshold when that crossing is reported.
 */
final class LowMemoryHandlers {
  private final Map<String, List<LowMemoryHandler>> byPool = new HashMap<>();

  /**
   * @param handlers the probe's handlers
   * @param poolName resolves a handler's {@code pool} member (which may use probe arguments) to the
   *     pool name
   */
  LowMemoryHandlers(LowMemoryHandler[] handlers, Function<String, String> poolName) {
    for (LowMemoryHandler handler : handlers) {
      byPool.computeIfAbsent(poolName.apply(handler.pool), k -> new ArrayList<>()).add(handler);
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
      List<LowMemoryHandler> handlers = byPool.get(name);
      if (!pool.isUsageThresholdSupported()) {
        if (handlers != null) {
          problems.put(name, "does not support usage thresholds");
        }
        continue;
      }
      armable.add(name);
      if (handlers != null) {
        long threshold = lowestThreshold(handlers);
        try {
          pool.setUsageThreshold(threshold);
          problems.remove(name);
        } catch (IllegalArgumentException e) {
          problems.put(name, "rejects the threshold " + threshold + " (" + e.getMessage() + ")");
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

  /**
   * The handlers to run for a threshold notification from the named pool: those whose threshold the
   * reported usage has reached.
   */
  List<LowMemoryHandler> handlersFor(String pool, MemoryUsage usage) {
    List<LowMemoryHandler> handlers = byPool.get(pool);
    if (handlers == null) {
      return Collections.emptyList();
    }
    List<LowMemoryHandler> due = new ArrayList<>(handlers.size());
    for (LowMemoryHandler handler : handlers) {
      if (usage.getUsed() >= handler.threshold) {
        due.add(handler);
      }
    }
    return due;
  }

  private static long lowestThreshold(List<LowMemoryHandler> handlers) {
    long lowest = Long.MAX_VALUE;
    for (LowMemoryHandler handler : handlers) {
      lowest = Math.min(lowest, handler.threshold);
    }
    return lowest;
  }
}
