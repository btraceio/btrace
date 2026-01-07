package org.openjdk.btrace.metrics.histogram;

/**
 * Configuration for HdrHistogram.
 *
 * <p>Defines the range and precision of histogram measurements. Probes do not instantiate configs
 * directly; obtain a builder from the injected service and pass the built config handle back to the
 * service.
 */
public interface HistogramConfig {
  long getLowestDiscernibleValue();
  long getHighestTrackableValue();
  int getNumberOfSignificantValueDigits();
}
