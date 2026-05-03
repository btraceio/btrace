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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.ServiceDescriptor;
import io.btrace.metrics.histogram.HistogramConfig;
import io.btrace.metrics.histogram.HistogramConfigBuilder;
import io.btrace.metrics.histogram.HistogramMetric;
import io.btrace.metrics.stats.StatsMetric;

/** High-performance metrics service API. */
@ServiceDescriptor(permissions = {Permission.THREADS})
public interface MetricsService {
  @Nullable HistogramConfigBuilder newHistogramConfig();

  @Nullable HistogramMetric histogram(@NotNull String name);

  @Nullable HistogramMetric histogram(@NotNull String name, @NotNull HistogramConfig config);

  @Nullable HistogramMetric histogram(@NotNull String name, @NotNull String key);

  @Nullable HistogramMetric histogram(
      @NotNull String name, @NotNull String key, @NotNull HistogramConfig config);

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
