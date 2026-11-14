/*
 * Copyright (c) 2008, 2015, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Type-safe encoding for command payloads.
 *
 * <p>Provides explicit serialization for command parameters using type discriminators instead of
 * Java object serialization. This prevents unsafe deserialization while preserving type information
 * for supported value types. Centralizes helpers previously duplicated across {@link
 * SetSettingsCommand}, {@link NumberDataCommand}, {@link GridDataCommand}.
 */
public final class TypedEncoding {

  // Generic value tags (also used for SetSettingsCommand)
  public static final byte TAG_NULL = 0;
  public static final byte TAG_STRING = 1;
  public static final byte TAG_BOOLEAN = 2;
  public static final byte TAG_INT = 3;
  public static final byte TAG_LONG = 4;
  public static final byte TAG_DOUBLE = 5;
  public static final byte TAG_FLOAT = 6;
  public static final byte TAG_SHORT = 7;
  public static final byte TAG_BYTE = 8;
  public static final byte TAG_BIGINT = 9;
  public static final byte TAG_BIGDEC = 10;

  // Number-specific tags (subset, kept for readability - aliases to generic tags)
  public static final byte NUM_NULL = TAG_NULL;
  public static final byte NUM_INT = TAG_INT;
  public static final byte NUM_LONG = TAG_LONG;
  public static final byte NUM_DOUBLE = TAG_DOUBLE;
  public static final byte NUM_FLOAT = TAG_FLOAT;
  public static final byte NUM_SHORT = TAG_SHORT;
  public static final byte NUM_BYTE = TAG_BYTE;
  public static final byte NUM_BIGINT = TAG_BIGINT;
  public static final byte NUM_BIGDEC = TAG_BIGDEC;

  // Grid cell tags
  public static final byte CELL_NULL = 0;
  public static final byte CELL_STRING = 1;
  public static final byte CELL_INT = 2;
  public static final byte CELL_LONG = 3;
  public static final byte CELL_DOUBLE = 4;
  public static final byte CELL_FLOAT = 5;
  public static final byte CELL_BOOLEAN = 6;
  public static final byte CELL_HISTOGRAM = 7;

  // Limits (defense-in-depth)
  public static final int MAX_PARAMS = 1000;
  public static final int MAX_KEY_LEN = 4096;
  public static final int MAX_MAP_SIZE = 10000;
  public static final int MAX_ROWS = 10000;
  public static final int MAX_CELLS = 1000;
  public static final int MAX_CODE_BYTES = 20 * 1024 * 1024;
  public static final int MAX_ARGS = 1000;

  private TypedEncoding() {}

  public static void writeValue(ObjectOutput out, Object v) throws IOException {
    if (v == null) {
      out.writeByte(TAG_NULL);
    } else if (v instanceof String) {
      out.writeByte(TAG_STRING);
      out.writeUTF((String) v);
    } else if (v instanceof Boolean) {
      out.writeByte(TAG_BOOLEAN);
      out.writeBoolean((Boolean) v);
    } else if (v instanceof Integer) {
      out.writeByte(TAG_INT);
      out.writeInt((Integer) v);
    } else if (v instanceof Long) {
      out.writeByte(TAG_LONG);
      out.writeLong((Long) v);
    } else if (v instanceof Double) {
      out.writeByte(TAG_DOUBLE);
      out.writeDouble((Double) v);
    } else if (v instanceof Float) {
      out.writeByte(TAG_FLOAT);
      out.writeFloat((Float) v);
    } else if (v instanceof Short) {
      out.writeByte(TAG_SHORT);
      out.writeShort((Short) v);
    } else if (v instanceof Byte) {
      out.writeByte(TAG_BYTE);
      out.writeByte((Byte) v);
    } else if (v instanceof BigInteger) {
      out.writeByte(TAG_BIGINT);
      out.writeUTF(v.toString());
    } else if (v instanceof BigDecimal) {
      out.writeByte(TAG_BIGDEC);
      out.writeUTF(v.toString());
    } else if (v instanceof AtomicInteger) {
      out.writeByte(TAG_INT);
      out.writeInt(((AtomicInteger) v).get());
    } else if (v instanceof AtomicLong) {
      out.writeByte(TAG_LONG);
      out.writeLong(((AtomicLong) v).get());
    } else if (v instanceof Number) {
      out.writeByte(TAG_STRING);
      out.writeUTF(v.toString());
    } else {
      throw new IOException(
          "Invalid settings value type: expected String/Number/Boolean, got "
              + v.getClass().getName());
    }
  }

  public static Object readValue(ObjectInput in) throws IOException {
    byte tag = in.readByte();
    switch (tag) {
      case TAG_NULL:
        return null;
      case TAG_STRING:
        return in.readUTF();
      case TAG_BOOLEAN:
        return in.readBoolean();
      case TAG_INT:
        return in.readInt();
      case TAG_LONG:
        return in.readLong();
      case TAG_DOUBLE:
        return in.readDouble();
      case TAG_FLOAT:
        return in.readFloat();
      case TAG_SHORT:
        return in.readShort();
      case TAG_BYTE:
        return in.readByte();
      case TAG_BIGINT:
        return new BigInteger(in.readUTF());
      case TAG_BIGDEC:
        return new BigDecimal(in.readUTF());
      default:
        throw new IOException("Invalid settings value type: unknown tag " + tag);
    }
  }

  public static void writeNumber(ObjectOutput out, Number n) throws IOException {
    if (n == null) {
      out.writeByte(NUM_NULL);
    } else if (n instanceof Integer) {
      out.writeByte(NUM_INT);
      out.writeInt(n.intValue());
    } else if (n instanceof Long) {
      out.writeByte(NUM_LONG);
      out.writeLong(n.longValue());
    } else if (n instanceof Double) {
      out.writeByte(NUM_DOUBLE);
      out.writeDouble(n.doubleValue());
    } else if (n instanceof Float) {
      out.writeByte(NUM_FLOAT);
      out.writeFloat(n.floatValue());
    } else if (n instanceof Short) {
      out.writeByte(NUM_SHORT);
      out.writeShort(n.shortValue());
    } else if (n instanceof Byte) {
      out.writeByte(NUM_BYTE);
      out.writeByte(n.byteValue());
    } else if (n instanceof BigInteger) {
      out.writeByte(NUM_BIGINT);
      out.writeUTF(n.toString());
    } else if (n instanceof BigDecimal) {
      out.writeByte(NUM_BIGDEC);
      out.writeUTF(n.toString());
    } else {
      out.writeByte(NUM_DOUBLE);
      out.writeDouble(n.doubleValue());
    }
  }

  public static Number readNumber(ObjectInput in) throws IOException {
    byte tag = in.readByte();
    switch (tag) {
      case NUM_NULL:
        return null;
      case NUM_INT:
        return in.readInt();
      case NUM_LONG:
        return in.readLong();
      case NUM_DOUBLE:
        return in.readDouble();
      case NUM_FLOAT:
        return in.readFloat();
      case NUM_SHORT:
        return in.readShort();
      case NUM_BYTE:
        return in.readByte();
      case NUM_BIGINT:
        return new BigInteger(in.readUTF());
      case NUM_BIGDEC:
        return new BigDecimal(in.readUTF());
      default:
        throw new IOException("Invalid data type: expected Number, got tag " + tag);
    }
  }

  public static void writeCell(ObjectOutput out, Object cell) throws IOException {
    if (cell == null) {
      out.writeByte(CELL_NULL);
    } else if (cell instanceof String) {
      out.writeByte(CELL_STRING);
      out.writeUTF((String) cell);
    } else if (cell instanceof Integer) {
      out.writeByte(CELL_INT);
      out.writeInt((Integer) cell);
    } else if (cell instanceof Long) {
      out.writeByte(CELL_LONG);
      out.writeLong((Long) cell);
    } else if (cell instanceof Double) {
      out.writeByte(CELL_DOUBLE);
      out.writeDouble((Double) cell);
    } else if (cell instanceof Float) {
      out.writeByte(CELL_FLOAT);
      out.writeFloat((Float) cell);
    } else if (cell instanceof Boolean) {
      out.writeByte(CELL_BOOLEAN);
      out.writeBoolean((Boolean) cell);
    } else if (cell instanceof Number) {
      out.writeByte(CELL_DOUBLE);
      out.writeDouble(((Number) cell).doubleValue());
    } else {
      // Fallback: render via toString (covers HistogramData and other types) - never use
      // writeObject
      out.writeByte(CELL_STRING);
      out.writeUTF(cell.toString());
    }
  }

  public static Object readCell(ObjectInput in) throws IOException {
    byte tag = in.readByte();
    switch (tag) {
      case CELL_NULL:
        return null;
      case CELL_STRING:
        return in.readUTF();
      case CELL_INT:
        return in.readInt();
      case CELL_LONG:
        return in.readLong();
      case CELL_DOUBLE:
        return in.readDouble();
      case CELL_FLOAT:
        return in.readFloat();
      case CELL_BOOLEAN:
        return in.readBoolean();
      case CELL_HISTOGRAM:
        return in.readUTF();
      default:
        throw new IOException("Invalid data type: unknown grid cell tag " + tag);
    }
  }
}
