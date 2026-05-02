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
