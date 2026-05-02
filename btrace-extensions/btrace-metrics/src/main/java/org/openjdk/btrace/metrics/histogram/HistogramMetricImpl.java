package org.openjdk.btrace.metrics.histogram;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openjdk.btrace.metrics.Metric;

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
  @Nullable
  public HistogramSnapshot snapshot() {
    Histogram histogram = recorder.getIntervalHistogram();
    return new HistogramSnapshotImpl(name, histogram);
  }

  @Override
  public void reset() {
    recorder.reset();
  }

  @Override
  @NotNull
  public String getName() {
    return name;
  }
}

