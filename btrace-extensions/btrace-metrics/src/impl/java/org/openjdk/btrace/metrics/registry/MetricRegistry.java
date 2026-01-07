package org.openjdk.btrace.metrics.registry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.openjdk.btrace.metrics.Metric;

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

  /**
   * Reset all metrics.
   */
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

  /**
   * Clear all metrics.
   */
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

