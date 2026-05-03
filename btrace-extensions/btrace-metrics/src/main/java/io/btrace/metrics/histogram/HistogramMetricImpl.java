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
package io.btrace.metrics.histogram;

import io.btrace.metrics.Metric;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Histogram metric using HdrHistogram for accurate percentiles.
 *
 * <p>Uses Recorder pattern for lock-free writes in hot path.
 */
public final class HistogramMetricImpl implements HistogramMetric, Metric {

  private final String name;
  private final Recorder recorder;
  private final HistogramConfig config;

  public HistogramMetricImpl(String name, HistogramConfig config) {
    this.name = name;
    this.config = config;
    this.recorder =
        new Recorder(
            config.getLowestDiscernibleValue(),
            config.getHighestTrackableValue(),
            config.getNumberOfSignificantValueDigits());
  }

  /**
   * Record value - ZERO ALLOCATION in hot path.
   *
   * @param value value to record
   */
  public void record(long value) {
    recorder.recordValue(value);
  }

  /**
   * Record value with count - ZERO ALLOCATION.
   *
   * @param value value to record
   * @param count number of times to record
   */
  public void recordValueWithCount(long value, long count) {
    recorder.recordValueWithCount(value, count);
  }

  /**
   * Get snapshot for querying percentiles.
   *
   * <p>This allocates a new Histogram via getIntervalHistogram(). Call infrequently (e.g., on
   * OnEvent).
   *
   * @return immutable snapshot
   */
  @Nullable public HistogramSnapshot snapshot() {
    Histogram histogram = recorder.getIntervalHistogram();
    return new HistogramSnapshotImpl(name, histogram);
  }

  @Override
  public void reset() {
    recorder.reset();
  }

  @Override
  @NotNull public String getName() {
    return name;
  }
}
