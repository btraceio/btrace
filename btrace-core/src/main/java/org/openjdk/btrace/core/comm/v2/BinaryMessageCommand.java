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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Binary implementation of the MessageCommand with compression support. This command is used to
 * send messages from the BTrace agent to the client.
 */
public class BinaryMessageCommand extends BinaryCommand {
  // Compression threshold in bytes
  private static final int COMPRESSION_THRESHOLD = 1024;

  // Message content
  private String message;
  private boolean urgent;
  private long timestamp;

  static {
    // Register this command type
    BinaryCommand.registerCommand(MESSAGE, BinaryMessageCommand::new);
  }

  public BinaryMessageCommand(long timestamp, String message, boolean urgent) {
    super(MESSAGE, urgent);
    this.message = message;
    this.urgent = urgent;
    this.timestamp = timestamp;
  }

  public BinaryMessageCommand(String message, boolean urgent) {
    this(System.currentTimeMillis(), message, urgent);
  }

  public BinaryMessageCommand(String message) {
    this(System.currentTimeMillis(), message, false);
  }

  public BinaryMessageCommand() {
    this(null);
  }

  @Override
  protected void write(OutputStream out) throws IOException {
    // Write timestamp
    BinaryProtocol.writeLong(out, timestamp);

    // Write urgent flag
    BinaryProtocol.writeBoolean(out, urgent);

    // Convert message to bytes
    byte[] messageBytes = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];

    // Determine if compression should be used
    boolean compress = messageBytes.length > COMPRESSION_THRESHOLD;
    BinaryProtocol.writeBoolean(out, compress);

    if (compress) {
      // Compress the message
      ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
      DeflaterOutputStream deflaterStream =
          new DeflaterOutputStream(compressedBytes, new Deflater(Deflater.BEST_SPEED));

      deflaterStream.write(messageBytes);
      deflaterStream.finish();
      deflaterStream.close();

      // Write the compressed bytes
      byte[] compressedData = compressedBytes.toByteArray();
      BinaryProtocol.writeInt(out, messageBytes.length); // Original size
      BinaryProtocol.writeInt(out, compressedData.length); // Compressed size
      out.write(compressedData);
    } else {
      // Write uncompressed message
      BinaryProtocol.writeInt(out, messageBytes.length);
      if (messageBytes.length > 0) {
        out.write(messageBytes);
      }
    }
  }

  @Override
  protected void read(InputStream in) throws IOException {
    // Read timestamp
    timestamp = BinaryProtocol.readLong(in);

    // Read urgent flag
    urgent = BinaryProtocol.readBoolean(in);
    if (urgent) {
      setUrgent();
    }

    // Check if the message is compressed
    boolean compressed = BinaryProtocol.readBoolean(in);

    if (compressed) {
      // Read the original and compressed sizes
      int originalSize = BinaryProtocol.readInt(in);
      int compressedSize = BinaryProtocol.readInt(in);

      // Read and decompress the message
      byte[] compressedData = new byte[compressedSize];
      BinaryProtocol.readFully(in, compressedData);

      // Create an inflater to decompress
      InflaterInputStream inflaterStream =
          new InflaterInputStream(new ByteArrayInputStream(compressedData), new Inflater());

      // Read the decompressed data
      byte[] decompressedData = new byte[originalSize];
      BinaryProtocol.readFully(inflaterStream, decompressedData);

      // Convert to string
      message = new String(decompressedData, StandardCharsets.UTF_8);
    } else {
      // Read uncompressed message
      int length = BinaryProtocol.readInt(in);
      if (length > 0) {
        byte[] messageBytes = new byte[length];
        BinaryProtocol.readFully(in, messageBytes);
        message = new String(messageBytes, StandardCharsets.UTF_8);
      } else {
        message = "";
      }
    }
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
