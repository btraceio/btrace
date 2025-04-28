package btrace;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnTimer;

@BTrace
public class HdrHistogramExtendedTest {
    private static BTraceUtils.HdrHistogram.HdrHistogramHandle histogram = BTraceUtils.HdrHistogram.create("test");

    @OnMethod(clazz = "resources.Main", method = "main")
    public static void onMain() {
        if (histogram != null) {
            BTraceUtils.println("Custom histogram created");
        }
    }

    @OnMethod(clazz = "resources.Main", method = "work")
    public static void onWork() {
        if (histogram != null) {
            long startTime = BTraceUtils.timeNanos();
            BTraceUtils.HdrHistogram.recordValue(histogram, startTime);
            BTraceUtils.println("Value recorded");
        }
    }

    @OnTimer(1000)
    public static void printStats() {
        if (histogram != null) {
            // Print basic statistics
            BTraceUtils.println("Min value: " + BTraceUtils.HdrHistogram.getMinValue(histogram));
            BTraceUtils.println("Max value: " + BTraceUtils.HdrHistogram.getMaxValue(histogram));
            BTraceUtils.println("Mean value: " + BTraceUtils.HdrHistogram.getMean(histogram));
            BTraceUtils.println("StdDeviation: " + BTraceUtils.HdrHistogram.getStdDeviation(histogram));
            BTraceUtils.println("Total count: " + BTraceUtils.HdrHistogram.getTotalCount(histogram));

            // Print percentiles
            BTraceUtils.println("50th percentile: " + BTraceUtils.HdrHistogram.getValueAtPercentile(histogram, 50.0));
            BTraceUtils.println("90th percentile: " + BTraceUtils.HdrHistogram.getValueAtPercentile(histogram, 90.0));
            BTraceUtils.println("99th percentile: " + BTraceUtils.HdrHistogram.getValueAtPercentile(histogram, 99.0));

            // Print memory footprint
            BTraceUtils.println("Estimated footprint: " + BTraceUtils.HdrHistogram.getEstimatedFootprintInBytes(histogram));

            // Reset histogram
            BTraceUtils.HdrHistogram.reset(histogram);
            BTraceUtils.println("Histogram reset");

            // Destroy histogram
            BTraceUtils.HdrHistogram.destroy(histogram);
            BTraceUtils.println("Histogram destroyed");
        }
    }
} 