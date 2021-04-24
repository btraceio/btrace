package org.openjdk.btrace.extensions.hdrhistogram;

import org.HdrHistogram.DoubleRecorder;
import org.HdrHistogram.SingleWriterDoubleRecorder;
import org.HdrHistogram.SingleWriterRecorder;
import org.HdrHistogram.packedarray.PackedArrayRecorder;
import org.HdrHistogram.packedarray.PackedArraySingleWriterRecorder;

/** Centralized factory class for various histogram recorder typesa */
public final class Recorders {
  /**
   * Construct an auto-resizing {@link DoubleRecorder} using a precision stated as a number of
   * significant decimal digits.
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static DoubleRecorder newDoubleRecorder(int numberOfSignificantValueDigits) {
    return new DoubleRecorder(numberOfSignificantValueDigits);
  }

  /**
   * Construct an auto-resizing {@link DoubleRecorder} using a precision stated as a number of
   * significant decimal digits.
   *
   * <p>Depending on the valuer of the <b><code>packed</code></b> parameter {@link DoubleRecorder}
   * can be configuired to track value counts in a packed internal representation optimized for
   * typical histogram recoded values are sparse in the value range and tend to be incremented in
   * small unit counts. This packed representation tends to require significantly smaller amounts of
   * stoarge when compared to unpacked representations, but can incur additional recording cost due
   * to resizing and repacking operations that may occur as previously unrecorded values are
   * encountered.
   *
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   * @param packed Specifies whether the recorder will uses a packed internal representation or not.
   */
  public static DoubleRecorder newDoubleRecorder(
      int numberOfSignificantValueDigits, boolean packed) {
    return new DoubleRecorder(numberOfSignificantValueDigits, packed);
  }

  /**
   * Construct a {@link DoubleRecorder} dynamic range of values to cover and a number of significant
   * decimal digits.
   *
   * @param highestToLowestValueRatio specifies the dynamic range to use (as a ratio)
   * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of
   *     significant decimal digits to which the histogram will maintain value resolution and
   *     separation. Must be a non-negative integer between 0 and 5.
   */
  public static DoubleRecorder newDoubleRecorder(
      long highestToLowestValueRatio, int numberOfSignificantValueDigits) {
    return new DoubleRecorder(highestToLowestValueRatio, numberOfSignificantValueDigits);
  }

  /**
   * Construct a {@link PackedArrayRecorder} with a given (virtual) array length.
   *
   * @param virtualLength The (virtual) array length
   */
  public static PackedArrayRecorder newPackedArrayRecorder(final int virtualLength) {
    return new PackedArrayRecorder(virtualLength);
  }

  /**
   * Construct a {@link PackedArrayRecorder} with a given (virtual) array length, starting with a
   * given initial physical backing store length
   *
   * @param virtualLength The (virtual) array length
   * @param initialPhysicalLength The initial physical backing store length
   */
  public static PackedArrayRecorder newPackedArrayRecorder(
      final int virtualLength, final int initialPhysicalLength) {
    return new PackedArrayRecorder(virtualLength, initialPhysicalLength);
  }

  /**
   * Construct a {@link PackedArraySingleWriterRecorder} with a given (virtual) array length.
   *
   * @param virtualLength The (virtual) array length
   */
  public static PackedArraySingleWriterRecorder newPackedArraySingleWriterRecorder(
      final int virtualLength) {
    return new PackedArraySingleWriterRecorder(virtualLength);
  }

  /**
   * Construct a {@link PackedArraySingleWriterRecorder} with a given (virtual) array length,
   * starting with a given initial physical backing store length
   *
   * @param virtualLength The (virtual) array length
   * @param initialPhysicalLength The initial physical backing store length
   */
  public static PackedArraySingleWriterRecorder newPackedArraySingleWriterRecorder(
      final int virtualLength, final int initialPhysicalLength) {
    return new PackedArraySingleWriterRecorder(virtualLength, initialPhysicalLength);
  }

  /**
   * Construct a {@link SingleWriterRecorder} with a given (virtual) array length.
   *
   * @param virtualLength The (virtual) array length
   */
  public static SingleWriterRecorder newSingleWriterRecorder(final int virtualLength) {
    return new SingleWriterRecorder(virtualLength);
  }

  /**
   * Construct a {@link SingleWriterRecorder} with a given (virtual) array length, starting with a
   * given initial physical backing store length
   *
   * @param virtualLength The (virtual) array length
   * @param initialPhysicalLength The initial physical backing store length
   */
  public static SingleWriterRecorder newSingleWriterRecorder(
      final int virtualLength, final int initialPhysicalLength) {
    return new SingleWriterRecorder(virtualLength, initialPhysicalLength);
  }

  /**
   * Construct a {@link SingleWriterDoubleRecorder} with a given (virtual) array length.
   *
   * @param virtualLength The (virtual) array length
   */
  public static SingleWriterDoubleRecorder newSingleWriterDoubleRecorder(final int virtualLength) {
    return new SingleWriterDoubleRecorder(virtualLength);
  }

  /**
   * Construct a {@link SingleWriterDoubleRecorder} with a given (virtual) array length, starting
   * with a given initial physical backing store length
   *
   * @param virtualLength The (virtual) array length
   * @param initialPhysicalLength The initial physical backing store length
   */
  public static SingleWriterDoubleRecorder newSingleWriterDoubleRecorder(
      final int virtualLength, final int initialPhysicalLength) {
    return new SingleWriterDoubleRecorder(virtualLength, initialPhysicalLength);
  }
}
