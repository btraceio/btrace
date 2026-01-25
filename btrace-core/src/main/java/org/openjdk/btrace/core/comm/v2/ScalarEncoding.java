package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.openjdk.btrace.core.aggregation.HistogramData;

/**
 * Helper to encode/decode common scalar types with configurable type codes.
 * Supported: null, String, Integer, Long, Float, Double, Boolean.
 */
final class ScalarEncoding {
  private final byte nullCode;
  private final byte stringCode;
  private final byte intCode;
  private final byte longCode;
  private final byte floatCode;
  private final byte doubleCode;
  private final byte booleanCode;
  private final byte histogramCode;

  ScalarEncoding(
      byte nullCode,
      byte stringCode,
      byte intCode,
      byte longCode,
      byte floatCode,
      byte doubleCode,
      byte booleanCode,
      byte histogramCode) {
    this.nullCode = nullCode;
    this.stringCode = stringCode;
    this.intCode = intCode;
    this.longCode = longCode;
    this.floatCode = floatCode;
    this.doubleCode = doubleCode;
    this.booleanCode = booleanCode;
    this.histogramCode = histogramCode;
  }

  void writeValue(OutputStream out, Object value) throws IOException {
    if (value == null) {
      BinaryProtocol.writeByte(out, nullCode);
      return;
    }
    if (value instanceof String) {
      BinaryProtocol.writeByte(out, stringCode);
      BinaryProtocol.writeString(out, (String) value);
    } else if (value instanceof Integer) {
      BinaryProtocol.writeByte(out, intCode);
      BinaryProtocol.writeInt(out, (Integer) value);
    } else if (value instanceof Long) {
      BinaryProtocol.writeByte(out, longCode);
      BinaryProtocol.writeLong(out, (Long) value);
    } else if (value instanceof Float) {
      BinaryProtocol.writeByte(out, floatCode);
      BinaryProtocol.writeFloat(out, (Float) value);
    } else if (value instanceof Double) {
      BinaryProtocol.writeByte(out, doubleCode);
      BinaryProtocol.writeDouble(out, (Double) value);
    } else if (value instanceof Boolean) {
      BinaryProtocol.writeByte(out, booleanCode);
      BinaryProtocol.writeBoolean(out, (Boolean) value);
    } else if (value instanceof HistogramData) {
      BinaryProtocol.writeByte(out, histogramCode);
      writeLongArray(out, ((HistogramData) value).getValues());
      writeLongArray(out, ((HistogramData) value).getCounts());
    } else {
      // Fallback: write as string
      BinaryProtocol.writeByte(out, stringCode);
      BinaryProtocol.writeString(out, value.toString());
    }
  }

  Object readValue(InputStream in) throws IOException {
    byte type = BinaryProtocol.readByte(in);
    if (type == nullCode) {
      return null;
    } else if (type == stringCode) {
      return BinaryProtocol.readString(in);
    } else if (type == intCode) {
      return BinaryProtocol.readInt(in);
    } else if (type == longCode) {
      return BinaryProtocol.readLong(in);
    } else if (type == floatCode) {
      return BinaryProtocol.readFloat(in);
    } else if (type == doubleCode) {
      return BinaryProtocol.readDouble(in);
    } else if (type == booleanCode) {
      return BinaryProtocol.readBoolean(in);
    } else if (type == histogramCode) {
      long[] values = readLongArray(in);
      long[] counts = readLongArray(in);
      return new HistogramData(values, counts);
    } else {
      throw new IOException("Unsupported scalar type: " + type);
    }
  }

  private void writeLongArray(OutputStream out, long[] values) throws IOException {
    if (values == null) {
      BinaryProtocol.writeInt(out, -1);
      return;
    }
    BinaryProtocol.writeInt(out, values.length);
    for (long value : values) {
      BinaryProtocol.writeLong(out, value);
    }
  }

  private long[] readLongArray(InputStream in) throws IOException {
    int length = BinaryProtocol.readInt(in);
    if (length < 0) {
      return null;
    }
    long[] values = new long[length];
    for (int i = 0; i < length; i++) {
      values[i] = BinaryProtocol.readLong(in);
    }
    return values;
  }
}
