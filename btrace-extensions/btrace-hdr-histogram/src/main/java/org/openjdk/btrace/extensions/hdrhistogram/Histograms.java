package org.openjdk.btrace.extensions.hdrhistogram;

import org.HdrHistogram.*;

/** Centralized factory class for various histogram types */
public final class Histograms {
  /**
   * Construct a AtomicHistogram given the Highest value to be tracked and a number of significant
   * decimal digits. The histogram will be constructed to implicitly track (distinguish from 0)
   * values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static AtomicHistogram newAtomicHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a AtomicHistogram given the Lowest and Highest values to be tracked and a number of
   * significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where
   * the units used for the histogram's values are much smaller that the minimal accuracy required.
   * E.g. when tracking time values stated in nanosecond units, where the minimal accuracy required
   * is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static AtomicHistogram newAtomicHistogram(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new AtomicHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a histogram with the same range settings as a given source histogram, duplicating the
   * source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static AtomicHistogram newAtomicHistogram(AbstractHistogram source) {
    return new AtomicHistogram(source);
  }

  /**
   * Construct an auto-resizing ConcurrentDoubleHistogram with a lowest discernible value of 1 and
   * an auto-adjusting highestTrackableValue. Can auto-resize up to track values up to
   * (Long.MAX_VALUE / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ConcurrentDoubleHistogram newConcurrentDoubleHistogram(
      int numberOfSignificantValueDigits) {
    return new ConcurrentDoubleHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a ConcurrentDoubleHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ConcurrentDoubleHistogram newConcurrentDoubleHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ConcurrentDoubleHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct an auto-resizing ConcurrentHistogram with a lowest discernible value of 1 and an
   * auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE
   * / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ConcurrentHistogram newConcurrentHistogram(int numberOfSignificantValueDigits) {
    return new ConcurrentHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a ConcurrentHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ConcurrentHistogram newConcurrentHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a ConcurrentHistogram given the Lowest and Highest values to be tracked and a number
   * of significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where
   * the units used for the histogram's values are much smaller that the minimal accuracy required.
   * E.g. when tracking time values stated in nanosecond units, where the minimal accuracy required
   * is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ConcurrentHistogram newConcurrentHistogram(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ConcurrentHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a histogram with the same range settings as a given source histogram, duplicating the
   * source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static ConcurrentHistogram newConcurrentHistogram(AbstractHistogram source) {
    return new ConcurrentHistogram(source);
  }

  /**
   * Construct an auto-resizing IntCountsHistogram with a lowest discernible value of 1 and an
   * auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE
   * / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static IntCountsHistogram newIntCountsHistogram(int numberOfSignificantValueDigits) {
    return new IntCountsHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a IntCountsHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static IntCountsHistogram newIntCountsHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new IntCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a IntCountsHistogram given the Lowest and Highest values to be tracked and a number
   * of significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where
   * the units used for the histogram's values are much smaller that the minimal accuracy required.
   * E.g. when tracking time values stated in nanosecond units, where the minimal accuracy required
   * is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static IntCountsHistogram newIntCountsHistogram(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new IntCountsHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a histogram with the same range settings as a given source histogram, duplicating the
   * source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static IntCountsHistogram newIntCountsHistogram(AbstractHistogram source) {
    return new IntCountsHistogram(source);
  }

  /**
   * Construct an auto-resizing PackedHistogram with a lowest discernible value of 1 and an
   * auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE
   * / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedHistogram newPackedHistogram(int numberOfSignificantValueDigits) {
    return new PackedHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedHistogram given the Highest value to be tracked and a number of significant
   * decimal digits. The histogram will be constructed to implicitly track (distinguish from 0)
   * values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedHistogram newPackedHistogram(
      final long highestTrackableValue, final int numberOfSignificantValueDigits) {
    return new PackedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedHistogram given the Lowest and Highest values to be tracked and a number of
   * significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where
   * the units used for the histogram's values are much smaller that the minimal accuracy required.
   * E.g. when tracking time values stated in nanosecond units, where the minimal accuracy required
   * is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public PackedHistogram newPackedHistogram(
      final long lowestDiscernibleValue,
      final long highestTrackableValue,
      final int numberOfSignificantValueDigits) {
    return new PackedHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedHistogram with the same range settings as a given source histogram,
   * duplicating the source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public PackedHistogram newPackedHistogram(final AbstractHistogram source) {
    return new PackedHistogram(source);
  }

  /**
   * Construct an auto-resizing PackedDoubleHistogram with a lowest discernible value of 1 and an
   * auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE
   * / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedDoubleHistogram newPackedDoubleHistogram(int numberOfSignificantValueDigits) {
    return new PackedDoubleHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedDoubleHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedDoubleHistogram newPackedDoubleHistogram(
      final long highestTrackableValue, final int numberOfSignificantValueDigits) {
    return new PackedDoubleHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct an auto-resizing PackedConcurrentHistogram with a lowest discernible value of 1 and
   * an auto-adjusting highestTrackableValue. Can auto-resize up to track values up to
   * (Long.MAX_VALUE / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedConcurrentHistogram newPackedConcurrentHistogram(
      int numberOfSignificantValueDigits) {
    return new PackedConcurrentHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedConcurrentHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedConcurrentHistogram newPackedConcurrentHistogram(
      final long highestTrackableValue, final int numberOfSignificantValueDigits) {
    return new PackedConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedConcurrentHistogram given the Lowest and Highest values to be tracked and a
   * number of significant decimal digits. Providing a lowestDiscernibleValue is useful is
   * situations where the units used for the histogram's values are much smaller that the minimal
   * accuracy required. E.g. when tracking time values stated in nanosecond units, where the minimal
   * accuracy required is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedConcurrentHistogram newPackedConcurrentHistogram(
      final long lowestDiscernibleValue,
      final long highestTrackableValue,
      final int numberOfSignificantValueDigits) {
    return new PackedConcurrentHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedConcurrentHistogram with the same range settings as a given source histogram,
   * duplicating the source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static PackedConcurrentHistogram newPackedConcurrentHistogram(
      final AbstractHistogram source) {
    return new PackedConcurrentHistogram(source);
  }

  /**
   * Construct an auto-resizing PackedConcurrentDoubleHistogram with a lowest discernible value of 1
   * and an auto-adjusting highestTrackableValue. Can auto-resize up to track values up to
   * (Long.MAX_VALUE / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedConcurrentDoubleHistogram newPackedConcurrentDoubleHistogram(
      int numberOfSignificantValueDigits) {
    return new PackedConcurrentDoubleHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a PackedConcurrentDoubleHistogram given the Highest value to be tracked and a number
   * of significant decimal digits. The histogram will be constructed to implicitly track
   * (distinguish from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static PackedConcurrentDoubleHistogram newPackedConcurrentDoubleHistogram(
      final long highestTrackableValue, final int numberOfSignificantValueDigits) {
    return new PackedConcurrentDoubleHistogram(
        highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a {@link PackedConcurrentDoubleHistogram} with the same range settings as a given
   * source, duplicating the source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static PackedConcurrentDoubleHistogram newPackedConcurrentDoubleHistogram(
      final DoubleHistogram source) {
    return new PackedConcurrentDoubleHistogram(source);
  }

  /**
   * Construct an auto-resizing ShortCountsHistogram with a lowest discernible value of 1 and an
   * auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE
   * / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ShortCountsHistogram newShortCountsHistogram(int numberOfSignificantValueDigits) {
    return new ShortCountsHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a ShortCountsHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ShortCountsHistogram newShortCountsHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ShortCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a ShortCountsHistogram given the Lowest and Highest values to be tracked and a number
   * of significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where
   * the units used for the histogram's values are much smaller that the minimal accuracy required.
   * E.g. when tracking time values stated in nanosecond units, where the minimal accuracy required
   * is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static ShortCountsHistogram newShortCountsHistogram(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new ShortCountsHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a histogram with the same range settings as a given source histogram, duplicating the
   * source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static ShortCountsHistogram newShortCountsHistogram(AbstractHistogram source) {
    return new ShortCountsHistogram(source);
  }

  /**
   * Construct an auto-resizing SynchronizedHistogram with a lowest discernible value of 1 and an
   * auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE
   * / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static SynchronizedHistogram newSynchronizedHistogram(int numberOfSignificantValueDigits) {
    return new SynchronizedHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a SynchronizedHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static SynchronizedHistogram newSynchronizedHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a SynchronizedHistogram given the Lowest and Highest values to be tracked and a
   * number of significant decimal digits. Providing a lowestDiscernibleValue is useful is
   * situations where the units used for the histogram's values are much smaller that the minimal
   * accuracy required. E.g. when tracking time values stated in nanosecond units, where the minimal
   * accuracy required is a microsecond, the proper value for lowestDiscernibleValue would be 1000.
   *
   * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by
   *     the histogram. Must be a positive integer that is {@literal >=} 1. May be internally
   *     rounded down to nearest power of 2.
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} (2 * lowestDiscernibleValue).
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static SynchronizedHistogram newSynchronizedHistogram(
      long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new SynchronizedHistogram(
        lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
  }

  /**
   * Construct a histogram with the same range settings as a given source histogram, duplicating the
   * source's start/end timestamps (but NOT it's contents)
   *
   * @param source The source histogram to duplicate
   */
  public static SynchronizedHistogram newSynchronizedHistogram(AbstractHistogram source) {
    return new SynchronizedHistogram(source);
  }

  /**
   * Construct an auto-resizing SynchronizedDoubleHistogram with a lowest discernible value of 1 and
   * an auto-adjusting highestTrackableValue. Can auto-resize up to track values up to
   * (Long.MAX_VALUE / 2).
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static SynchronizedDoubleHistogram newSynchronizedDoubleHistogram(
      int numberOfSignificantValueDigits) {
    return new SynchronizedDoubleHistogram(numberOfSignificantValueDigits);
  }

  /**
   * Construct a SynchronizedDoubleHistogram given the Highest value to be tracked and a number of
   * significant decimal digits. The histogram will be constructed to implicitly track (distinguish
   * from 0) values as low as 1.
   *
   * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a
   *     positive integer that is {@literal >=} 2.
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static SynchronizedDoubleHistogram newSynchronizedDoubleHistogram(
      long highestTrackableValue, int numberOfSignificantValueDigits) {
    return new SynchronizedDoubleHistogram(highestTrackableValue, numberOfSignificantValueDigits);
  }
}
