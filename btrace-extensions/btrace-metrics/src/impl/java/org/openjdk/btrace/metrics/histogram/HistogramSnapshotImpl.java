package org.openjdk.btrace.metrics.histogram;

import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of histogram for percentile queries.
 */
public final class HistogramSnapshotImpl implements HistogramSnapshot {

  private final String name;
  private final Histogram histogram;

  HistogramSnapshotImpl(String name, Histogram histogram) {
    this.name = name;
    this.histogram = histogram;
  }

  @NotNull
  public String getName() {
    return name;
  }

  // ========== Percentiles ==========

  public long p50() {
    return histogram.getValueAtPercentile(50.0);
  }

  public long p75() {
    return histogram.getValueAtPercentile(75.0);
  }

  public long p90() {
    return histogram.getValueAtPercentile(90.0);
  }

  public long p95() {
    return histogram.getValueAtPercentile(95.0);
  }

  public long p99() {
    return histogram.getValueAtPercentile(99.0);
  }

  public long p999() {
    return histogram.getValueAtPercentile(99.9);
  }

  public long p9999() {
    return histogram.getValueAtPercentile(99.99);
  }

  public long percentile(double percentile) {
    return histogram.getValueAtPercentile(percentile);
  }

  // ========== Statistics ==========

  public long count() {
    return histogram.getTotalCount();
  }

  public long min() {
    return histogram.getMinValue();
  }

  public long max() {
    return histogram.getMaxValue();
  }

  public double mean() {
    return histogram.getMean();
  }

  public double stddev() {
    return histogram.getStdDeviation();
  }

  // ========== Access to raw histogram ==========

  public Histogram getHistogram() {
    return histogram;
  }
}

