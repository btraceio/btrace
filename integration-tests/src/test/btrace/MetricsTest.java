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


package btrace;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnTimer;
import org.openjdk.btrace.metrics.MetricsService;
import org.openjdk.btrace.metrics.histogram.HistogramConfig;
import org.openjdk.btrace.metrics.histogram.HistogramMetric;
import org.openjdk.btrace.metrics.histogram.HistogramSnapshot;
import org.openjdk.btrace.metrics.stats.StatsMetric;
import org.openjdk.btrace.metrics.stats.StatsSnapshot;

import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * Test HDR histogram metrics integration.
 */
@BTrace
public class MetricsTest {

  @Injected
  private static MetricsService metrics;

  private static HistogramMetric histogram;
  private static StatsMetric stats;

  static {
    println("=== HDR Histogram Metrics Test ===");
  }

  @OnMethod(clazz = "resources.Main", method = "callB")
  public static void onEntry() {
    println("On call B entry");
    if (histogram == null) {
      histogram = metrics.histogramMicros("main.callB");
      stats = metrics.stats("main.callB.stats");
    }
  }

  @OnMethod(clazz = "resources.Main", method = "callB", location = @Location(Kind.RETURN))
  public static void onReturn(@Duration long durationNanos) {
    println("On return");
    if (histogram != null) {
      long durationMicros = durationNanos / 1000;
      histogram.record(durationMicros);
      stats.record(durationMicros);
    }
  }

  @OnTimer(100)
  public static void onTimer() {
    if (histogram != null) {
      HistogramSnapshot h = histogram.snapshot();
      StatsSnapshot s = stats.snapshot();

      println("=== Metrics Report ===");
      println("Count: " + s.count());
      println("Mean: " + s.mean() + " μs");
      println("Min: " + s.min() + " μs");
      println("Max: " + s.max() + " μs");
      println("P50: " + h.p50() + " μs");
      println("P95: " + h.p95() + " μs");
      println("P99: " + h.p99() + " μs");
      println("======================");
    }
  }
}
