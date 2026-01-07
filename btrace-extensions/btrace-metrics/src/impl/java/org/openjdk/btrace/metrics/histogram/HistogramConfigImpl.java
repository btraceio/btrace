package org.openjdk.btrace.metrics.histogram;

public final class HistogramConfigImpl implements HistogramConfig {
  private final long lowestDiscernibleValue;
  private final long highestTrackableValue;
  private final int numberOfSignificantValueDigits;

  public HistogramConfigImpl(long lowest, long highest, int digits) {
    this.lowestDiscernibleValue = lowest;
    this.highestTrackableValue = highest;
    this.numberOfSignificantValueDigits = digits;
  }

  @Override
  public long getLowestDiscernibleValue() {
    return lowestDiscernibleValue;
  }

  @Override
  public long getHighestTrackableValue() {
    return highestTrackableValue;
  }

  @Override
  public int getNumberOfSignificantValueDigits() {
    return numberOfSignificantValueDigits;
  }
}
