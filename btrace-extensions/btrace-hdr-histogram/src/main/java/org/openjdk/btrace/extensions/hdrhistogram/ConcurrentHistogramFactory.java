package org.openjdk.btrace.extensions.hdrhistogram;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.ConcurrentHistogram;

public final class ConcurrentHistogramFactory {
  public static ConcurrentHistogram newInstance(int numberOfSignificantNumberDigits) {
    return new ConcurrentHistogram(numberOfSignificantNumberDigits);
  }

  public static ConcurrentHistogram newInstance(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  public static ConcurrentHistogram newInstance(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ConcurrentHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  public static ConcurrentHistogram newInstance(AbstractHistogram source) {
    return new ConcurrentHistogram(source);
  }
}
