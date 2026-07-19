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
package io.btrace.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.metrics.stats.StatsMetric;
import io.btrace.metrics.stats.StatsMetricImpl;
import io.btrace.metrics.stats.StatsSnapshot;
import org.junit.jupiter.api.Test;

class StatsMetricTest {
  @Test
  void accumulatesAndSnapshots() {
    StatsMetric m = new StatsMetricImpl("s");
    m.record(1);
    m.record(3);
    StatsSnapshot snap = m.snapshot();
    assertEquals("s", snap.getName());
    assertEquals(2, snap.count());
    assertEquals(4, snap.sum());
    assertEquals(1, snap.min());
    assertEquals(3, snap.max());
  }

  @Test
  void computesStandardDeviationWithoutSquareOverflow() {
    StatsMetric metric = new StatsMetricImpl("overflow");
    metric.record(0);
    metric.record(4_000_000_000L);

    StatsSnapshot snapshot = metric.snapshot();

    assertTrue(Double.isFinite(snapshot.stddev()));
    assertEquals(2_000_000_000D, snapshot.stddev(), 0.001D);
  }
}
