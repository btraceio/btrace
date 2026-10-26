/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import io.btrace.core.annotations.*;
import io.btrace.metrics.MetricsService;
import io.btrace.metrics.histogram.HistogramMetric;
import io.btrace.metrics.histogram.HistogramSnapshot;

/**
 * Tutorial script for docs/tutorials/04-extensions-and-permissions.md.
 *
 * <p>Injects the bundled btrace-metrics extension and tracks latency percentiles for {@code
 * OrderService.chargeCard} and {@code OrderService.processOrder} in the demo app (../demo/DemoApp.java).
 *
 * <p>The {@code MetricsService} field is declared {@code optional = true} so that when the
 * extension's implementation is blocked by permission policy, BTrace hands back a throwing stub
 * (THROW mode, the default) instead of failing to link the script at all. That makes the failure
 * visible and catchable rather than fatal — see the permission-denial step in the tutorial.
 */
@BTrace
public class LatencyHistogram {
  @Injected(optional = true)
  private static MetricsService metrics;

  private static HistogramMetric chargeCardLatency;
  private static HistogramMetric processOrderLatency;

  @OnMethod(clazz = "OrderService", method = "chargeCard", location = @Location(Kind.RETURN))
  public static void onChargeCardReturn(@Duration long durationNanos) {
    if (chargeCardLatency == null) {
      chargeCardLatency = metrics.histogramMillis("chargeCard.latency");
    }
    chargeCardLatency.record(durationNanos / 1_000_000);
  }

  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
  public static void onProcessOrderReturn(@Duration long durationNanos) {
    if (processOrderLatency == null) {
      processOrderLatency = metrics.histogramMillis("processOrder.latency");
    }
    processOrderLatency.record(durationNanos / 1_000_000);
  }

  @OnTimer(5000)
  public static void report() {
    println("=== Latency Report ===");
    if (chargeCardLatency != null) {
      HistogramSnapshot h = chargeCardLatency.snapshot();
      println(
          "chargeCard    p50=" + h.p50() + "ms  p95=" + h.p95() + "ms  p99=" + h.p99()
              + "ms  (n=" + h.count() + ")");
    }
    if (processOrderLatency != null) {
      HistogramSnapshot h = processOrderLatency.snapshot();
      println(
          "processOrder  p50=" + h.p50() + "ms  p95=" + h.p95() + "ms  p99=" + h.p99()
              + "ms  (n=" + h.count() + ")");
    }
    println("=======================");
  }
}
