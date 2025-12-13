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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract wire protocol for BTrace command communication.
 *
 * <p>This interface provides a pluggable abstraction for different wire protocol implementations,
 * allowing BTrace to support multiple serialization formats while maintaining a consistent API.
 *
 * <p>Implementations:
 *
 * <ul>
 *   <li>{@link JavaSerializationProtocol} - V1 protocol using ObjectInputStream/ObjectOutputStream
 *   <li>{@link BinaryWireProtocol} - V2 protocol using custom binary format
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>
 * // Create protocol based on negotiation
 * InputStream is = socket.getInputStream();
 * OutputStream os = socket.getOutputStream();
 *
 * ProtocolNegotiator negotiator = new ProtocolNegotiator();
 * ProtocolVersion version = negotiator.negotiate(createNegotiationStream(is));
 *
 * WireProtocol protocol = WireProtocol.create(version, is, os);
 *
 * // Read commands
 * Command cmd = protocol.read();
 *
 * // Write commands
 * protocol.write(new MessageCommand("hello"));
 *
 * // Close when done
 * protocol.close();
 * </pre>
 */
public interface WireProtocol extends Closeable {

  /**
   * Reads a command from the input stream.
   *
   * <p>This method blocks until a complete command is available or an error occurs.
   *
   * @return the command read from the stream
   * @throws IOException if an I/O error occurs or the stream is closed
   * @throws ClassNotFoundException if the command type cannot be deserialized (V1 only)
   */
  Command read() throws IOException, ClassNotFoundException;

  /**
   * Writes a command to the output stream.
   *
   * <p>If the command is marked as urgent, the stream will be flushed immediately. Otherwise,
   * buffering behavior depends on the underlying protocol implementation.
   *
   * @param command the command to write
   * @throws IOException if an I/O error occurs or the stream is closed
   */
  void write(Command command) throws IOException;

  /**
   * Flushes any buffered data to the output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  void flush() throws IOException;

  /**
   * Returns the protocol version in use.
   *
   * @return the ProtocolVersion
   */
  ProtocolVersion getVersion();

  /**
   * Closes the protocol and releases any associated resources.
   *
   * <p>After calling this method, no further read or write operations should be performed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Override
  void close() throws IOException;

  /**
   * Creates a WireProtocol instance for the specified version.
   *
   * <p>The streams should already be positioned correctly after protocol negotiation.
   *
   * @param version the protocol version to use
   * @param inputStream the input stream to read commands from
   * @param outputStream the output stream to write commands to
   * @return a WireProtocol instance for the specified version
   * @throws IOException if protocol initialization fails
   */
  static WireProtocol create(
      ProtocolVersion version, InputStream inputStream, OutputStream outputStream)
      throws IOException {
    switch (version) {
      case V1:
        return new JavaSerializationProtocol(inputStream, outputStream);
      case V2:
        return new BinaryWireProtocol(inputStream, outputStream);
      default:
        throw new IllegalArgumentException("Unsupported protocol version: " + version);
    }
  }

  /**
   * Creates a WireProtocol instance with automatic protocol negotiation.
   *
   * <p>This method performs protocol negotiation on the input stream and creates the appropriate
   * WireProtocol implementation. The inputStream must support either PushbackInputStream or
   * mark/reset.
   *
   * @param inputStream the input stream (will be wrapped in PushbackInputStream if needed)
   * @param outputStream the output stream
   * @return a WireProtocol instance for the negotiated version
   * @throws IOException if negotiation or protocol initialization fails
   */
  static WireProtocol createWithNegotiation(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    InputStream negotiationStream = ProtocolNegotiator.createNegotiationStream(inputStream);
    ProtocolVersion version = negotiator.negotiate(negotiationStream);
    return create(version, negotiationStream, outputStream);
  }

  /**
   * Creates a WireProtocol instance with configuration-based setup.
   *
   * <p>If auto-negotiation is enabled in the config, performs negotiation. If forceVersion is set,
   * uses the configured version without negotiation.
   *
   * @param config the protocol configuration
   * @param inputStream the input stream
   * @param outputStream the output stream
   * @return a WireProtocol instance based on configuration
   * @throws IOException if protocol setup fails
   */
  static WireProtocol createWithConfig(
      ProtocolConfig config, InputStream inputStream, OutputStream outputStream)
      throws IOException {
    if (config.isForceVersion()) {
      // Force specific version without negotiation
      return create(config.getVersion(), inputStream, outputStream);
    } else if (config.isAutoNegotiate()) {
      // Perform negotiation
      return createWithNegotiation(inputStream, outputStream);
    } else {
      // Use configured version without negotiation
      return create(config.getVersion(), inputStream, outputStream);
    }
  }
}
