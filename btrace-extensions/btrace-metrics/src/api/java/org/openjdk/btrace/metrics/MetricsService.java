package org.openjdk.btrace.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.ServiceDescriptor;
import org.openjdk.btrace.metrics.histogram.HistogramConfig;
import org.openjdk.btrace.metrics.histogram.HistogramConfigBuilder;
import org.openjdk.btrace.metrics.histogram.HistogramMetric;
import org.openjdk.btrace.metrics.stats.StatsMetric;

/**
 * High-performance metrics service API.
 */
@ServiceDescriptor(permissions = { Permission.THREADS })
public interface MetricsService {
  @Nullable HistogramConfigBuilder newHistogramConfig();
  @Nullable HistogramMetric histogram(@NotNull String name);
  @Nullable HistogramMetric histogram(@NotNull String name, @NotNull HistogramConfig config);
  @Nullable HistogramMetric histogram(@NotNull String name, @NotNull String key);
  @Nullable HistogramMetric histogram(@NotNull String name, @NotNull String key, @NotNull HistogramConfig config);
  // Convenience creators for common units (matches prior constants):
  @Nullable HistogramMetric histogramMicros(@NotNull String name);
  @Nullable HistogramMetric histogramMicros(@NotNull String name, @NotNull String key);
  @Nullable HistogramMetric histogramMillis(@NotNull String name);
  @Nullable HistogramMetric histogramMillis(@NotNull String name, @NotNull String key);
  @Nullable StatsMetric stats(@NotNull String name);
  @Nullable StatsMetric stats(@NotNull String name, @NotNull String key);
  void reset();
  void clear();
  int size();
}
