package org.openjdk.btrace.metrics;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.metrics.histogram.HistogramConfigImpl;
import org.openjdk.btrace.metrics.histogram.HistogramMetric;
import org.openjdk.btrace.metrics.histogram.HistogramMetricImpl;
import org.openjdk.btrace.metrics.histogram.HistogramSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistogramMetricTest {
  @Test
  void recordsAndSnapshots() {
    HistogramMetric m = new HistogramMetricImpl("h", new HistogramConfigImpl(1L, 600_000L, 3));
    m.record(10);
    m.record(20);
    HistogramSnapshot snap = m.snapshot();
    assertEquals("h", snap.getName());
    assertTrue(snap.count() >= 2);
    assertTrue(snap.max() >= 20);
  }
}
