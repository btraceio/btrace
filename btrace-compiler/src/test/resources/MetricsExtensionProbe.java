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


package resources;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.metrics.MetricsService;
import org.openjdk.btrace.metrics.histogram.HistogramConfig;
import org.openjdk.btrace.metrics.histogram.HistogramMetric;
import org.openjdk.btrace.metrics.histogram.HistogramSnapshot;
import org.openjdk.btrace.metrics.stats.StatsMetric;
import org.openjdk.btrace.metrics.stats.StatsSnapshot;

import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * Test probe that uses the metrics extension to verify that:
 * 1. @Injected service fields are recognized
 * 2. Methods on service return types (HistogramMetric, StatsMetric) are allowed
 * 3. Methods on snapshot objects (HistogramSnapshot, StatsSnapshot) are allowed
 */
@BTrace
public class MetricsExtensionProbe {

    @Injected
    private static MetricsService metrics;

    private static HistogramMetric histogram;
    private static StatsMetric stats;

    @OnMethod(clazz = "java.lang.String", method = "length", location = @Location(Kind.RETURN))
    public static void onStringLength(@Duration long durationNanos) {
        // Initialize metrics on first call
        if (histogram == null) {
            histogram = metrics.histogramMicros("string.length");
            stats = metrics.stats("string.length.stats");
        }

        // Record duration - these calls should be allowed by the verifier
        long durationMicros = durationNanos / 1000;
        histogram.record(durationMicros);
        stats.record(durationMicros);

        // Get snapshots - these calls should be allowed
        HistogramSnapshot h = histogram.snapshot();
        StatsSnapshot s = stats.snapshot();

        // Call methods on snapshots - these should also be allowed
        println("Count: " + s.count());
        println("Mean: " + s.mean());
        println("P99: " + h.p99());
    }
}
