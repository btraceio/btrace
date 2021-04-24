package org.openjdk.btrace.extensions.hdrhistogram;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.IntCountsHistogram;

public final class IntCountsHistogramFactory {
  public static IntCountsHistogram newInstance(int numberOfSignificantNumberDigits) {
    return new IntCountsHistogram(numberOfSignificantNumberDigits);
  }

  public static IntCountsHistogram newInstance(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new IntCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  public static IntCountsHistogram newInstance(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new IntCountsHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  public static IntCountsHistogram newInstance(AbstractHistogram source) {
    return new IntCountsHistogram(source);
  }
}
