package org.openjdk.btrace.metrics.histogram;

/** Builder for {@link HistogramConfig}. Obtain from the injected service. */
public interface HistogramConfigBuilder {
  HistogramConfigBuilder lowestDiscernibleValue(long value);
  HistogramConfigBuilder highestTrackableValue(long value);
  HistogramConfigBuilder significantDigits(int digits);
  HistogramConfig build();
}

