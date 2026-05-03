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

/**
 * Represents the wire protocol version for BTrace command communication.
 *
 * <p>BTrace supports multiple wire protocol versions to allow for performance improvements while
 * maintaining backward compatibility.
 */
public enum ProtocolVersion {
  /**
   * Version 1: Java ObjectInputStream/ObjectOutputStream serialization.
   *
   * <p>This is the original protocol using standard Java serialization. Compatible with all BTrace
   * versions.
   *
   * <p>Characteristics:
   *
   * <ul>
   *   <li>Uses ObjectOutputStream for serialization
   *   <li>No magic byte prefix
   *   <li>Larger payload sizes
   *   <li>Slower serialization/deserialization
   * </ul>
   */
  V1(1, new byte[] {}),

  /**
   * Version 2: Custom binary protocol with compression.
   *
   * <p>High-performance binary protocol with automatic compression for large payloads.
   *
   * <p>Characteristics:
   *
   * <ul>
   *   <li>Magic byte prefix: 0x42 0x54 0x52 0x32 ("BTR2")
   *   <li>3-6x faster serialization
   *   <li>2-5x smaller payloads (10-100x with compression)
   *   <li>Automatic deflate compression for payloads &gt; 1KB
   *   <li>Zero-copy optimizations where possible
   * </ul>
   */
  V2(2, new byte[] {0x42, 0x54, 0x52, 0x32}); // "BTR2"

  private final int version;
  private final byte[] magicBytes;

  ProtocolVersion(int version, byte[] magicBytes) {
    this.version = version;
    this.magicBytes = magicBytes;
  }

  /**
   * Returns the protocol version number.
   *
   * @return the version number (1 or 2)
   */
  public int getVersion() {
    return version;
  }

  /**
   * Returns the magic byte prefix for this protocol version.
   *
   * <p>V1 has no magic bytes (empty array). V2 uses "BTR2" (0x42 0x54 0x52 0x32).
   *
   * @return the magic byte prefix, or empty array if none
   */
  public byte[] getMagicBytes() {
    return magicBytes.clone();
  }

  /**
   * Returns whether this protocol version uses a magic byte prefix.
   *
   * @return true if this version has magic bytes, false otherwise
   */
  public boolean hasMagicBytes() {
    return magicBytes.length > 0;
  }

  /**
   * Looks up a protocol version by its version number.
   *
   * @param version the version number (1 or 2)
   * @return the corresponding ProtocolVersion
   * @throws IllegalArgumentException if the version number is not supported
   */
  public static ProtocolVersion fromVersion(int version) {
    for (ProtocolVersion pv : values()) {
      if (pv.version == version) {
        return pv;
      }
    }
    throw new IllegalArgumentException("Unsupported protocol version: " + version);
  }

  /**
   * Detects the protocol version from a magic byte prefix.
   *
   * <p>This method checks if the provided bytes match any known magic byte prefix. If no match is
   * found, it assumes V1 (which has no magic bytes).
   *
   * @param prefix the first few bytes from the stream
   * @return the detected ProtocolVersion (V2 if magic bytes match, V1 otherwise)
   */
  public static ProtocolVersion detectFromPrefix(byte[] prefix) {
    if (prefix == null || prefix.length == 0) {
      return V1;
    }

    // Check V2 magic bytes
    if (prefix.length >= V2.magicBytes.length) {
      boolean matches = true;
      for (int i = 0; i < V2.magicBytes.length; i++) {
        if (prefix[i] != V2.magicBytes[i]) {
          matches = false;
          break;
        }
      }
      if (matches) {
        return V2;
      }
    }

    // Default to V1 if no magic bytes match
    return V1;
  }

  /**
   * Returns the default protocol version.
   *
   * <p>Currently defaults to V2 for new connections, but can be overridden via configuration.
   *
   * @return the default ProtocolVersion (V2)
   */
  public static ProtocolVersion getDefault() {
    return V2;
  }

  @Override
  public String toString() {
    return "ProtocolVersion{version=" + version + ", hasMagicBytes=" + hasMagicBytes() + "}";
  }
}
