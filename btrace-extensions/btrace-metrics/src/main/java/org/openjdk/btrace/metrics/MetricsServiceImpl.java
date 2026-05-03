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
package org.openjdk.btrace.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.metrics.histogram.HistogramConfig;
import org.openjdk.btrace.metrics.histogram.HistogramConfigBuilder;
import org.openjdk.btrace.metrics.histogram.HistogramConfigBuilderImpl;
import org.openjdk.btrace.metrics.histogram.HistogramConfigImpl;
import org.openjdk.btrace.metrics.histogram.HistogramMetric;
import org.openjdk.btrace.metrics.histogram.HistogramMetricImpl;
import org.openjdk.btrace.metrics.registry.MetricRegistry;
import org.openjdk.btrace.metrics.stats.StatsMetric;
import org.openjdk.btrace.metrics.stats.StatsMetricImpl;

/**
 * High-performance metrics service built on HdrHistogram.
 *
 * <p>Provides: - Histograms with accurate percentile queries (p50, p95, p99, etc.) - Basic
 * statistics (count, sum, min, max, avg, stddev)
 *
 * <p>Thread-safe and designed for low-latency hot paths.
 */
public final class MetricsServiceImpl extends Extension implements MetricsService {

  private final MetricRegistry registry = new MetricRegistry();

  public MetricsServiceImpl() {}

  // ========== Histogram Creation ==========

  @Override
  public HistogramConfigBuilder newHistogramConfig() {
    return new HistogramConfigBuilderImpl();
  }

  /**
   * Create histogram with default configuration.
   *
   * @param name metric name
   * @return histogram metric
   */
  @Nullable public HistogramMetric histogram(@NotNull String name) {
    // Default: 1ns .. 1h, 3 significant digits
    return histogram(name, new HistogramConfigImpl(1L, 3_600_000_000_000L, 3));
  }

  /**
   * Create histogram with custom configuration.
   *
   * @param name metric name
   * @param config histogram configuration
   * @return histogram metric
   */
  @Nullable public HistogramMetric histogram(@NotNull String name, @NotNull HistogramConfig config) {
    return registry.getOrCreate(name, null, () -> new HistogramMetricImpl(name, config));
  }

  /**
   * Create grouped histogram.
   *
   * @param name metric name
   * @param key grouping key
   * @return histogram metric
   */
  @Nullable public HistogramMetric histogram(@NotNull String name, @NotNull String key) {
    return registry.getOrCreate(
        name,
        key,
        () ->
            new HistogramMetricImpl(
                name,
                new org.openjdk.btrace.metrics.histogram.HistogramConfigImpl(
                    1L, 3_600_000_000_000L, 3)));
  }

  // Convenience creators for micros/millis ranges (match prior constants)
  @Override
  @Nullable public HistogramMetric histogramMicros(@NotNull String name) {
    return histogram(name, new HistogramConfigImpl(1L, 60_000_000L, 3));
  }

  @Override
  @Nullable public HistogramMetric histogramMicros(@NotNull String name, @NotNull String key) {
    return registry.getOrCreate(
        name,
        key,
        () -> new HistogramMetricImpl(name, new HistogramConfigImpl(1L, 60_000_000L, 3)));
  }

  @Override
  @Nullable public HistogramMetric histogramMillis(@NotNull String name) {
    return histogram(name, new HistogramConfigImpl(1L, 600_000L, 3));
  }

  @Override
  @Nullable public HistogramMetric histogramMillis(@NotNull String name, @NotNull String key) {
    return registry.getOrCreate(
        name, key, () -> new HistogramMetricImpl(name, new HistogramConfigImpl(1L, 600_000L, 3)));
  }

  /**
   * Create grouped histogram with custom configuration.
   *
   * @param name metric name
   * @param key grouping key
   * @param config histogram configuration
   * @return histogram metric
   */
  @Nullable public HistogramMetric histogram(
      @NotNull String name, @NotNull String key, @NotNull HistogramConfig config) {
    return registry.getOrCreate(name, key, () -> new HistogramMetricImpl(name, config));
  }

  // ========== Statistics Creation ==========

  /**
   * Create statistics metric (count/sum/min/max/avg/stddev).
   *
   * @param name metric name
   * @return statistics metric
   */
  @Nullable public StatsMetric stats(@NotNull String name) {
    return registry.getOrCreate(name, null, () -> new StatsMetricImpl(name));
  }

  /**
   * Create grouped statistics metric.
   *
   * @param name metric name
   * @param key grouping key
   * @return statistics metric
   */
  @Nullable public StatsMetric stats(@NotNull String name, @NotNull String key) {
    return registry.getOrCreate(
        name, key, () -> new org.openjdk.btrace.metrics.stats.StatsMetricImpl(name));
  }

  // ========== Query Operations ==========

  /** Reset all metrics. */
  public void reset() {
    registry.reset();
  }

  /** Clear all metrics. */
  public void clear() {
    registry.clear();
  }

  /**
   * Get number of registered metrics.
   *
   * @return metric count
   */
  public int size() {
    return registry.size();
  }
}
