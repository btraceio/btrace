package org.openjdk.btrace.metrics.stats;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of statistics.
 */
public final class StatsSnapshotImpl implements StatsSnapshot {

  private final String name;
  private final long count;
  private final long sum;
  private final long min;
  private final long max;
  private final double mean;
  private final double stddev;

  public StatsSnapshotImpl(
      String name, long count, long sum, long min, long max, double mean, double stddev) {
    this.name = name;
    this.count = count;
    this.sum = sum;
    this.min = min;
    this.max = max;
    this.mean = mean;
    this.stddev = stddev;
  }

  @NotNull
  public String getName() {
    return name;
  }

  public long count() {
    return count;
  }

  public long sum() {
    return sum;
  }

  public long min() {
    return min;
  }

  public long max() {
    return max;
  }

  public double mean() {
    return mean;
  }

  public double stddev() {
    return stddev;
  }
}
