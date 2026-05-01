package org.openjdk.btrace.metrics.histogram;

import org.jetbrains.annotations.Nullable;

public interface HistogramSnapshot {
  @Nullable String getName();
  long p50();
  long p75();
  long p90();
  long p95();
  long p99();
  long p999();
  long p9999();
  long percentile(double percentile);
  long count();
  long min();
  long max();
  double mean();
  double stddev();
}

