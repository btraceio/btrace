package org.openjdk.btrace.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.metrics.stats.StatsMetric;
import org.openjdk.btrace.metrics.stats.StatsSnapshot;

class StatsMetricTest {
  @Test
  void accumulatesAndSnapshots() {
    StatsMetric m = new org.openjdk.btrace.metrics.stats.StatsMetricImpl("s");
    m.record(1);
    m.record(3);
    StatsSnapshot snap = m.snapshot();
    assertEquals("s", snap.getName());
    assertEquals(2, snap.count());
    assertEquals(4, snap.sum());
    assertEquals(1, snap.min());
    assertEquals(3, snap.max());
  }
}
