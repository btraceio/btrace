package org.openjdk.btrace.metrics;

import org.jetbrains.annotations.NotNull;

/**
 * Base interface for all metrics.
 */
public interface Metric {
  /**
   * Get the metric name.
   *
   * @return metric name
   */
  @NotNull
  String getName();

  /**
   * Reset the metric to initial state.
   */
  void reset();
}

