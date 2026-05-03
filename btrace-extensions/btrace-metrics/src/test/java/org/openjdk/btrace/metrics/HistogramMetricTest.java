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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.metrics.histogram.HistogramConfigImpl;
import org.openjdk.btrace.metrics.histogram.HistogramMetric;
import org.openjdk.btrace.metrics.histogram.HistogramMetricImpl;
import org.openjdk.btrace.metrics.histogram.HistogramSnapshot;

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
