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
package io.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wire I/O implementation for the binary protocol. This replaces the original WireIO class that
 * relied on Java serialization.
 */
public class BinaryWireIO {
  public static final int VERSION = 2;

  // Singleton lock for stream synchronization
  private static final ReentrantLock writeLock = new ReentrantLock();

  private BinaryWireIO() {}

  /** Read a command from the input stream */
  public static BinaryCommand read(InputStream in) throws IOException {
    // Read the protocol version
    byte protocolVersion = BinaryProtocol.readByte(in);
    if (protocolVersion != BinaryProtocol.VERSION) {
      throw new ProtocolVersionMismatchException(BinaryProtocol.VERSION, protocolVersion);
    }

    // Read the command type
    byte type = BinaryProtocol.readByte(in);

    // Create and read the command
    BinaryCommand cmd;
    try {
      cmd = BinaryCommand.createCommand(type);
    } catch (IllegalArgumentException e) {
      throw new MalformedCommandException(type, "Unknown command type", e);
    }

    try {
      cmd.read(in);
    } catch (IOException e) {
      throw new CommandDeserializationException(type, e);
    }

    return cmd;
  }

  /** Write a command to the output stream */
  public static void write(OutputStream out, BinaryCommand cmd) throws IOException {
    writeLock.lock();
    try {
      // Write protocol version
      BinaryProtocol.writeByte(out, BinaryProtocol.VERSION);

      // Write command type
      BinaryProtocol.writeByte(out, cmd.getType());

      // Write command data
      cmd.write(out);

      // Flush if urgent
      if (cmd.isUrgent()) {
        out.flush();
      }
    } finally {
      writeLock.unlock();
    }
  }
}
