package resources;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.metrics.MetricsService;
import io.btrace.metrics.histogram.HistogramConfig;
import io.btrace.metrics.histogram.HistogramMetric;
import io.btrace.metrics.histogram.HistogramSnapshot;
import io.btrace.metrics.stats.StatsMetric;
import io.btrace.metrics.stats.StatsSnapshot;

import static io.btrace.core.BTraceUtils.println;

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
