/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Small helper to unify encoding of numeric values (null, int, long, float, double) across binary
 * commands that share the same type code mapping.
 */
final class NumberEncoding {
  private final byte nullCode;
  private final byte intCode;
  private final byte longCode;
  private final byte floatCode;
  private final byte doubleCode;

  NumberEncoding(byte nullCode, byte intCode, byte longCode, byte floatCode, byte doubleCode) {
    this.nullCode = nullCode;
    this.intCode = intCode;
    this.longCode = longCode;
    this.floatCode = floatCode;
    this.doubleCode = doubleCode;
  }

  void writeNumber(OutputStream out, Number value) throws IOException {
    if (value == null) {
      BinaryProtocol.writeByte(out, nullCode);
      return;
    }
    if (value instanceof Integer) {
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
    } else {
      // Default to long for other number implementations
      BinaryProtocol.writeByte(out, longCode);
      BinaryProtocol.writeLong(out, value.longValue());
    }
  }

  Number readNumber(InputStream in) throws IOException {
    byte type = BinaryProtocol.readByte(in);
    if (type == nullCode) {
      return null;
    } else if (type == intCode) {
      return BinaryProtocol.readInt(in);
    } else if (type == longCode) {
      return BinaryProtocol.readLong(in);
    } else if (type == floatCode) {
      return BinaryProtocol.readFloat(in);
    } else if (type == doubleCode) {
      return BinaryProtocol.readDouble(in);
    } else {
      throw new IOException("Unsupported number type: " + type);
    }
  }
}
