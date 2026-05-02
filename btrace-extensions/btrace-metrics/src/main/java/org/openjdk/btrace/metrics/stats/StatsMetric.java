package org.openjdk.btrace.metrics.stats;

import org.jetbrains.annotations.Nullable;

public interface StatsMetric {
  void record(long value);
  @Nullable StatsSnapshot snapshot();
  void reset();
  @Nullable String getName();
}

