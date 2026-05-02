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
import java.io.PushbackInputStream;

/**
 * Handles protocol version negotiation for BTrace command communication.
 *
 * <p>Protocol negotiation happens ONCE PER CONNECTION by examining the first few bytes from the
 * stream. The negotiator detects which protocol version is being used based on magic byte prefixes:
 *
 * <ul>
 *   <li>V2: Starts with 0x42 0x54 0x52 0x32 ("BTR2")
 *   <li>V1: No magic bytes, uses Java serialization format
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>
 * InputStream is = socket.getInputStream();
 * ProtocolNegotiator negotiator = new ProtocolNegotiator();
 * ProtocolVersion version = negotiator.negotiate(is);
 *
 * if (version == ProtocolVersion.V2) {
 *   // Use binary protocol
 *   BinaryCommand cmd = BinaryWireIO.read(is);
 * } else {
 *   // Use Java serialization
 *   Command cmd = WireIO.read(new ObjectInputStream(is));
 * }
 * </pre>
 */
public class ProtocolNegotiator {

  /** Maximum size of magic byte prefix to detect */
  private static final int MAX_MAGIC_BYTES = 4; // Length of "BTR2"

  private final ProtocolVersion preferredVersion;

  /** Creates a negotiator with the default preferred version (V2). */
  public ProtocolNegotiator() {
    this(ProtocolVersion.getDefault());
  }

  /**
   * Creates a negotiator with a specific preferred version.
   *
   * @param preferredVersion the protocol version to prefer when initiating connections
   */
  public ProtocolNegotiator(ProtocolVersion preferredVersion) {
    this.preferredVersion = preferredVersion;
  }

  /**
   * Negotiates the protocol version by detecting magic bytes from the input stream.
   *
   * <p>This method reads up to {@link #MAX_MAGIC_BYTES} bytes from the stream to detect the
   * protocol version. The bytes are pushed back onto the stream (if using PushbackInputStream) or
   * the stream is marked and reset.
   *
   * <p><b>IMPORTANT:</b> This method must be called ONCE per connection, before any other read
   * operations. The stream must support either:
   *
   * <ul>
   *   <li>PushbackInputStream with at least {@link #MAX_MAGIC_BYTES} bytes pushback buffer
   *   <li>mark/reset with at least {@link #MAX_MAGIC_BYTES} read limit
   * </ul>
   *
   * @param is the input stream to negotiate protocol on
   * @return the detected ProtocolVersion (V2 if magic bytes match, V1 otherwise)
   * @throws IOException if an I/O error occurs
   * @throws IllegalArgumentException if the stream doesn't support pushback or mark/reset
   */
  public ProtocolVersion negotiate(InputStream is) throws IOException {
    if (is instanceof PushbackInputStream) {
      return negotiateWithPushback((PushbackInputStream) is);
    } else if (is.markSupported()) {
      return negotiateWithMark(is);
    } else {
      throw new IllegalArgumentException(
          "InputStream must be either PushbackInputStream or support mark/reset");
    }
  }

  /**
   * Negotiates protocol version using PushbackInputStream.
   *
   * @param pis the pushback input stream
   * @return the detected protocol version
   * @throws IOException if an I/O error occurs
   */
  private ProtocolVersion negotiateWithPushback(PushbackInputStream pis) throws IOException {
    byte[] prefix = new byte[MAX_MAGIC_BYTES];
    int bytesRead = pis.read(prefix);

    if (bytesRead <= 0) {
      throw new IOException("Connection closed during protocol negotiation");
    }

    // Detect protocol version
    ProtocolVersion detected = ProtocolVersion.detectFromPrefix(prefix);

    // Push back the bytes we read
    if (detected == ProtocolVersion.V2) {
      // V2 magic bytes are consumed by the protocol, don't push back
      // The V2 protocol expects to read these as part of its header
    } else {
      // V1 has no magic bytes, push everything back
      pis.unread(prefix, 0, bytesRead);
    }

    return detected;
  }

  /**
   * Negotiates protocol version using mark/reset.
   *
   * @param is the input stream supporting mark/reset
   * @return the detected protocol version
   * @throws IOException if an I/O error occurs
   */
  private ProtocolVersion negotiateWithMark(InputStream is) throws IOException {
    is.mark(MAX_MAGIC_BYTES);

    byte[] prefix = new byte[MAX_MAGIC_BYTES];
    int bytesRead = is.read(prefix);

    if (bytesRead <= 0) {
      throw new IOException("Connection closed during protocol negotiation");
    }

    // Detect protocol version
    ProtocolVersion detected = ProtocolVersion.detectFromPrefix(prefix);

    // Reset stream to beginning
    if (detected == ProtocolVersion.V2) {
      // V2 magic bytes are part of the protocol, don't reset
      // The stream is now positioned after the magic bytes
    } else {
      // V1 has no magic bytes, reset to beginning
      is.reset();
    }

    return detected;
  }

  /**
   * Returns the preferred protocol version for initiating connections.
   *
   * @return the preferred ProtocolVersion
   */
  public ProtocolVersion getPreferredVersion() {
    return preferredVersion;
  }

  /**
   * Creates a PushbackInputStream suitable for protocol negotiation.
   *
   * <p>The returned stream has a pushback buffer of {@link #MAX_MAGIC_BYTES} bytes, which is
   * sufficient for detecting all protocol versions.
   *
   * @param is the underlying input stream
   * @return a PushbackInputStream with appropriate buffer size
   */
  public static PushbackInputStream createNegotiationStream(InputStream is) {
    if (is instanceof PushbackInputStream) {
      return (PushbackInputStream) is;
    }
    return new PushbackInputStream(is, MAX_MAGIC_BYTES);
  }
}
