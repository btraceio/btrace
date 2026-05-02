package org.openjdk.btrace.metrics.histogram;

import org.jetbrains.annotations.Nullable;

public interface HistogramMetric {
  void record(long value);
  void recordValueWithCount(long value, long count);
  @Nullable HistogramSnapshot snapshot();
  void reset();
  @Nullable String getName();
}

