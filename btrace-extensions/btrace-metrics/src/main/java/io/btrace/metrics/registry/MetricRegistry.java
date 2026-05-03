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
package io.btrace.metrics.registry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import io.btrace.metrics.Metric;

/**
 * Thread-safe registry for storing metrics.
 *
 * <p>Supports grouping via String keys.
 */
public final class MetricRegistry {

  private final ConcurrentHashMap<String, Metric> metrics = new ConcurrentHashMap<>();

  /**
   * Get or create a metric.
   *
   * @param name metric name
   * @param key grouping key (null for ungrouped)
   * @param factory factory to create metric if not present
   * @param <T> metric type
   * @return metric instance
   */
  @SuppressWarnings("unchecked")
  public <T extends Metric> T getOrCreate(String name, String key, Supplier<T> factory) {
    String fullName = makeFullName(name, key);
    return (T) metrics.computeIfAbsent(fullName, k -> factory.get());
  }

  /** Reset all metrics. */
  public void reset() {
    for (Metric metric : metrics.values()) {
      metric.reset();
    }
  }

  /**
   * Get number of registered metrics.
   *
   * @return metric count
   */
  public int size() {
    return metrics.size();
  }

  /** Clear all metrics. */
  public void clear() {
    metrics.clear();
  }

  private String makeFullName(String name, String key) {
    if (key == null || key.isEmpty()) {
      return name;
    }
    return name + ":" + key;
  }
}
