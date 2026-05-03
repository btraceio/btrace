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
package io.btrace.metrics;

import io.btrace.core.extensions.Extension;
import io.btrace.metrics.histogram.HistogramConfig;
import io.btrace.metrics.histogram.HistogramConfigBuilder;
import io.btrace.metrics.histogram.HistogramConfigBuilderImpl;
import io.btrace.metrics.histogram.HistogramConfigImpl;
import io.btrace.metrics.histogram.HistogramMetric;
import io.btrace.metrics.histogram.HistogramMetricImpl;
import io.btrace.metrics.registry.MetricRegistry;
import io.btrace.metrics.stats.StatsMetric;
import io.btrace.metrics.stats.StatsMetricImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
                new io.btrace.metrics.histogram.HistogramConfigImpl(1L, 3_600_000_000_000L, 3)));
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
    return registry.getOrCreate(name, key, () -> new io.btrace.metrics.stats.StatsMetricImpl(name));
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
