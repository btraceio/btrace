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
package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * Wire protocol implementation using Java Object Serialization (V1 protocol).
 *
 * <p>This is the original BTrace wire protocol that uses ObjectInputStream/ObjectOutputStream for
 * command serialization. It provides broad compatibility but has performance limitations compared
 * to the binary protocol (V2).
 *
 * <p>Characteristics:
 *
 * <ul>
 *   <li>Uses standard Java serialization
 *   <li>Compatible with all BTrace versions
 *   <li>No magic byte prefix
 *   <li>Larger payload sizes
 *   <li>Slower serialization/deserialization
 *   <li>Synchronized write operations
 * </ul>
 *
 * <p>This implementation wraps the legacy {@link WireIO} class to provide the {@link WireProtocol}
 * interface.
 */
public class JavaSerializationProtocol implements WireProtocol {

  private final ObjectInputStream ois;
  private final ObjectOutputStream oos;
  private volatile boolean closed = false;

  /**
   * Creates a new JavaSerializationProtocol instance.
   *
   * @param inputStream the input stream to read commands from
   * @param outputStream the output stream to write commands to
   * @throws IOException if stream initialization fails
   */
  public JavaSerializationProtocol(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    // ObjectOutputStream must be created BEFORE ObjectInputStream
    // to avoid deadlock with stream headers. If ObjectInputStream creation fails,
    // ensure the already-created ObjectOutputStream is closed to avoid resource leak.
    ObjectOutputStream tempOos = null;
    try {
      tempOos = new ObjectOutputStream(outputStream);
      tempOos.flush(); // Write stream header immediately
      this.oos = tempOos;
      this.ois = new ObjectInputStream(inputStream);
    } catch (IOException e) {
      if (tempOos != null) {
        try {
          tempOos.close();
        } catch (IOException closeEx) {
          e.addSuppressed(closeEx);
        }
      }
      throw e;
    }
  }

  /**
   * Creates a JavaSerializationProtocol with existing ObjectStreams.
   *
   * <p>This constructor is provided for backward compatibility with code that already has
   * ObjectInputStream/ObjectOutputStream instances.
   *
   * @param ois the ObjectInputStream to read from
   * @param oos the ObjectOutputStream to write to
   */
  public JavaSerializationProtocol(ObjectInputStream ois, ObjectOutputStream oos) {
    this.ois = ois;
    this.oos = oos;
  }

  @Override
  public Command read() throws IOException, ClassNotFoundException {
    if (closed) {
      throw new IOException("Protocol is closed");
    }
    return WireIO.read(ois);
  }

  @Override
  public void write(Command command) throws IOException {
    if (closed) {
      throw new IOException("Protocol is closed");
    }
    oos.reset();
    WireIO.write(oos, command);
  }

  @Override
  public void flush() throws IOException {
    if (!closed) {
      oos.flush();
    }
  }

  @Override
  public ProtocolVersion getVersion() {
    return ProtocolVersion.V1;
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;

    IOException exception = null;

    // Close output stream first to flush any pending data
    try {
      oos.close();
    } catch (IOException e) {
      exception = e;
    }

    // Then close input stream
    try {
      ois.close();
    } catch (IOException e) {
      if (exception == null) {
        exception = e;
      } else {
        exception.addSuppressed(e);
      }
    }

    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Returns the underlying ObjectInputStream.
   *
   * <p>This method is provided for backward compatibility with code that needs direct access to the
   * ObjectInputStream.
   *
   * @return the ObjectInputStream
   */
  public ObjectInputStream getObjectInputStream() {
    return ois;
  }

  /**
   * Returns the underlying ObjectOutputStream.
   *
   * <p>This method is provided for backward compatibility with code that needs direct access to the
   * ObjectOutputStream.
   *
   * @return the ObjectOutputStream
   */
  public ObjectOutputStream getObjectOutputStream() {
    return oos;
  }

  /**
   * Resets the ObjectOutputStream state.
   *
   * <p>This method is useful for preventing memory leaks in long-running connections by clearing
   * the stream's object cache. It should be called periodically between writes.
   *
   * @throws IOException if an I/O error occurs
   */
  public void reset() throws IOException {
    if (!closed) {
      oos.reset();
    }
  }
}
