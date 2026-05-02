package org.openjdk.btrace.metrics.stats;

import org.jetbrains.annotations.Nullable;

public interface StatsSnapshot {
  @Nullable String getName();
  long count();
  long sum();
  long min();
  long max();
  double mean();
  double stddev();
}

