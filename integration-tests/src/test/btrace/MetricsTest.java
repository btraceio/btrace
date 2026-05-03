/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
