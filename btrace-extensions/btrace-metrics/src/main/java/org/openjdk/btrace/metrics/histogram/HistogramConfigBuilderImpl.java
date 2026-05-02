package org.openjdk.btrace.metrics.histogram;

public final class HistogramConfigBuilderImpl implements HistogramConfigBuilder {
  private long lowest = 1L;
  private long highest = 3_600_000_000_000L; // 1 hour in nanos by default
  private int digits = 3;

  @Override
  public HistogramConfigBuilder lowestDiscernibleValue(long value) {
    this.lowest = value;
    return this;
  }

  @Override
  public HistogramConfigBuilder highestTrackableValue(long value) {
    this.highest = value;
    return this;
  }

  @Override
  public HistogramConfigBuilder significantDigits(int digits) {
    this.digits = digits;
    return this;
  }

  @Override
  public HistogramConfig build() {
    return new HistogramConfigImpl(lowest, highest, digits);
  }
}
