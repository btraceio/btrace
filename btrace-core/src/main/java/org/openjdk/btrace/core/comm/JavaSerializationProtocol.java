/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
    // to avoid deadlock with stream headers
    this.oos = new ObjectOutputStream(outputStream);
    this.oos.flush(); // Write stream header immediately
    this.ois = new ObjectInputStream(inputStream);
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
