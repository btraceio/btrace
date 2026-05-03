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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A utility class for binary serialization and deserialization. This replaces the default Java
 * serialization with a more efficient binary protocol.
 */
public class BinaryProtocol {
  public static final byte VERSION = 1;

  // Maximum size for strings and byte arrays to prevent OOM attacks (100MB)
  private static final int MAX_ALLOCATION_SIZE = 100 * 1024 * 1024;

  // Basic read functions

  public static byte readByte(InputStream is) throws IOException {
    int value = is.read();
    if (value == -1) {
      throw new IOException("End of stream reached");
    }
    return (byte) value;
  }

  public static int readInt(InputStream is) throws IOException {
    byte[] buffer = new byte[4];
    readFully(is, buffer);
    return ByteBuffer.wrap(buffer).getInt();
  }

  public static long readLong(InputStream is) throws IOException {
    byte[] buffer = new byte[8];
    readFully(is, buffer);
    return ByteBuffer.wrap(buffer).getLong();
  }

  public static float readFloat(InputStream is) throws IOException {
    byte[] buffer = new byte[4];
    readFully(is, buffer);
    return ByteBuffer.wrap(buffer).getFloat();
  }

  public static double readDouble(InputStream is) throws IOException {
    byte[] buffer = new byte[8];
    readFully(is, buffer);
    return ByteBuffer.wrap(buffer).getDouble();
  }

  public static boolean readBoolean(InputStream is) throws IOException {
    return readByte(is) != 0;
  }

  public static String readString(InputStream is) throws IOException {
    int length = readInt(is);
    if (length == -1) {
      return null;
    }
    if (length == 0) {
      return "";
    }
    if (length < 0 || length > MAX_ALLOCATION_SIZE) {
      throw new IOException(
          "Invalid string length: "
              + length
              + " (must be between 0 and "
              + MAX_ALLOCATION_SIZE
              + ")");
    }
    byte[] buffer = new byte[length];
    readFully(is, buffer);
    return new String(buffer, StandardCharsets.UTF_8);
  }

  public static byte[] readByteArray(InputStream is) throws IOException {
    int length = readInt(is);
    if (length == -1) {
      return null;
    }
    if (length == 0) {
      return new byte[0];
    }
    if (length < 0 || length > MAX_ALLOCATION_SIZE) {
      throw new IOException(
          "Invalid byte array length: "
              + length
              + " (must be between 0 and "
              + MAX_ALLOCATION_SIZE
              + ")");
    }
    byte[] buffer = new byte[length];
    readFully(is, buffer);
    return buffer;
  }

  // Basic write functions

  public static void writeByte(OutputStream os, byte value) throws IOException {
    os.write(value);
  }

  public static void writeInt(OutputStream os, int value) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(4);
    buffer.putInt(value);
    os.write(buffer.array());
  }

  public static void writeLong(OutputStream os, long value) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.putLong(value);
    os.write(buffer.array());
  }

  public static void writeFloat(OutputStream os, float value) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(4);
    buffer.putFloat(value);
    os.write(buffer.array());
  }

  public static void writeDouble(OutputStream os, double value) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.putDouble(value);
    os.write(buffer.array());
  }

  public static void writeBoolean(OutputStream os, boolean value) throws IOException {
    writeByte(os, value ? (byte) 1 : (byte) 0);
  }

  public static void writeString(OutputStream os, String value) throws IOException {
    if (value == null) {
      writeInt(os, -1);
      return;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    writeInt(os, bytes.length);
    if (bytes.length > 0) {
      os.write(bytes);
    }
  }

  public static void writeByteArray(OutputStream os, byte[] value) throws IOException {
    if (value == null) {
      writeInt(os, -1);
      return;
    }
    writeInt(os, value.length);
    if (value.length > 0) {
      os.write(value);
    }
  }

  // Utility functions

  public static void readFully(InputStream is, byte[] buffer) throws IOException {
    readFully(is, buffer, 0, buffer.length);
  }

  public static void readFully(InputStream is, byte[] buffer, int offset, int length)
      throws IOException {
    int totalBytesRead = 0;
    while (totalBytesRead < length) {
      int bytesRead = is.read(buffer, offset + totalBytesRead, length - totalBytesRead);
      if (bytesRead == -1) {
        throw new IOException("End of stream reached");
      }
      totalBytesRead += bytesRead;
    }
  }
}
