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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import org.junit.jupiter.api.Test;

/** Tests for ProtocolNegotiator and ProtocolVersion. */
public class ProtocolNegotiatorTest {

  @Test
  public void testProtocolVersionV1() {
    assertEquals(1, ProtocolVersion.V1.getVersion());
    assertFalse(ProtocolVersion.V1.hasMagicBytes());
    assertEquals(0, ProtocolVersion.V1.getMagicBytes().length);
  }

  @Test
  public void testProtocolVersionV2() {
    assertEquals(2, ProtocolVersion.V2.getVersion());
    assertTrue(ProtocolVersion.V2.hasMagicBytes());
    byte[] magicBytes = ProtocolVersion.V2.getMagicBytes();
    assertEquals(4, magicBytes.length);
    assertEquals(0x42, magicBytes[0]); // 'B'
    assertEquals(0x54, magicBytes[1]); // 'T'
    assertEquals(0x52, magicBytes[2]); // 'R'
    assertEquals(0x32, magicBytes[3]); // '2'
  }

  @Test
  public void testProtocolVersionFromVersion() {
    assertEquals(ProtocolVersion.V1, ProtocolVersion.fromVersion(1));
    assertEquals(ProtocolVersion.V2, ProtocolVersion.fromVersion(2));

    assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromVersion(0));
    assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromVersion(3));
    assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromVersion(-1));
  }

  @Test
  public void testProtocolVersionDetectFromPrefix() {
    // V2 magic bytes
    byte[] v2Prefix = {0x42, 0x54, 0x52, 0x32}; // "BTR2"
    assertEquals(ProtocolVersion.V2, ProtocolVersion.detectFromPrefix(v2Prefix));

    // V2 with extra bytes
    byte[] v2PrefixExtra = {0x42, 0x54, 0x52, 0x32, 0x01, 0x02};
    assertEquals(ProtocolVersion.V2, ProtocolVersion.detectFromPrefix(v2PrefixExtra));

    // V1 (no magic bytes)
    byte[] v1Prefix = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05}; // Java serialization header
    assertEquals(ProtocolVersion.V1, ProtocolVersion.detectFromPrefix(v1Prefix));

    // Empty
    assertEquals(ProtocolVersion.V1, ProtocolVersion.detectFromPrefix(new byte[0]));
    assertEquals(ProtocolVersion.V1, ProtocolVersion.detectFromPrefix(null));

    // Incomplete V2 magic
    byte[] incomplete = {0x42, 0x54, 0x52}; // Only "BTR"
    assertEquals(ProtocolVersion.V1, ProtocolVersion.detectFromPrefix(incomplete));

    // Wrong magic bytes
    byte[] wrong = {0x42, 0x54, 0x52, 0x31}; // "BTR1"
    assertEquals(ProtocolVersion.V1, ProtocolVersion.detectFromPrefix(wrong));
  }

  @Test
  public void testProtocolVersionGetDefault() {
    assertEquals(ProtocolVersion.V2, ProtocolVersion.getDefault());
  }

  @Test
  public void testNegotiatorDefaultConstructor() {
    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    assertEquals(ProtocolVersion.V2, negotiator.getPreferredVersion());
  }

  @Test
  public void testNegotiatorCustomVersion() {
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V1);
    assertEquals(ProtocolVersion.V1, negotiator.getPreferredVersion());
  }

  @Test
  public void testNegotiateV2WithPushback() throws IOException {
    byte[] v2Data = {0x42, 0x54, 0x52, 0x32, 0x01, 0x02, 0x03}; // "BTR2" + data
    PushbackInputStream pis = new PushbackInputStream(new ByteArrayInputStream(v2Data), 4);

    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    ProtocolVersion detected = negotiator.negotiate(pis);

    assertEquals(ProtocolVersion.V2, detected);

    // After negotiation, stream should be positioned after magic bytes
    assertEquals(0x01, pis.read());
    assertEquals(0x02, pis.read());
    assertEquals(0x03, pis.read());
  }

  @Test
  public void testNegotiateV1WithPushback() throws IOException {
    byte[] v1Data = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05}; // Java serialization header
    PushbackInputStream pis = new PushbackInputStream(new ByteArrayInputStream(v1Data), 4);

    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    ProtocolVersion detected = negotiator.negotiate(pis);

    assertEquals(ProtocolVersion.V1, detected);

    // After negotiation, stream should be reset to beginning (bytes pushed back)
    assertEquals(0xAC, pis.read() & 0xFF);
    assertEquals(0xED, pis.read() & 0xFF);
    assertEquals(0x00, pis.read());
    assertEquals(0x05, pis.read());
  }

  @Test
  public void testNegotiateV2WithMark() throws IOException {
    byte[] v2Data = {0x42, 0x54, 0x52, 0x32, 0x01, 0x02, 0x03}; // "BTR2" + data
    ByteArrayInputStream bis = new ByteArrayInputStream(v2Data);

    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    ProtocolVersion detected = negotiator.negotiate(bis);

    assertEquals(ProtocolVersion.V2, detected);

    // After negotiation, stream should be positioned after magic bytes
    assertEquals(0x01, bis.read());
    assertEquals(0x02, bis.read());
    assertEquals(0x03, bis.read());
  }

  @Test
  public void testNegotiateV1WithMark() throws IOException {
    byte[] v1Data = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05}; // Java serialization header
    ByteArrayInputStream bis = new ByteArrayInputStream(v1Data);

    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    ProtocolVersion detected = negotiator.negotiate(bis);

    assertEquals(ProtocolVersion.V1, detected);

    // After negotiation, stream should be reset to beginning
    assertEquals(0xAC, bis.read() & 0xFF);
    assertEquals(0xED, bis.read() & 0xFF);
    assertEquals(0x00, bis.read());
    assertEquals(0x05, bis.read());
  }

  @Test
  public void testNegotiateEmptyStream() {
    byte[] emptyData = {};
    PushbackInputStream pis = new PushbackInputStream(new ByteArrayInputStream(emptyData), 4);

    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    assertThrows(IOException.class, () -> negotiator.negotiate(pis));
  }

  @Test
  public void testNegotiateUnsupportedStream() {
    // Create a stream that doesn't support mark/reset or pushback
    InputStream unsupported =
        new InputStream() {
          @Override
          public int read() throws IOException {
            return 0;
          }

          @Override
          public boolean markSupported() {
            return false;
          }
        };

    ProtocolNegotiator negotiator = new ProtocolNegotiator();
    assertThrows(IllegalArgumentException.class, () -> negotiator.negotiate(unsupported));
  }

  @Test
  public void testCreateNegotiationStream() {
    ByteArrayInputStream bis = new ByteArrayInputStream(new byte[10]);
    PushbackInputStream pis = ProtocolNegotiator.createNegotiationStream(bis);

    assertNotNull(pis);
    assertTrue(pis instanceof PushbackInputStream);
  }

  @Test
  public void testCreateNegotiationStreamAlreadyPushback() {
    PushbackInputStream original = new PushbackInputStream(new ByteArrayInputStream(new byte[10]));
    PushbackInputStream result = ProtocolNegotiator.createNegotiationStream(original);

    assertSame(original, result);
  }

  @Test
  public void testNegotiateMultipleV2Connections() throws IOException {
    // Simulate multiple connections to ensure negotiation works repeatedly
    for (int i = 0; i < 5; i++) {
      byte[] v2Data = {0x42, 0x54, 0x52, 0x32, (byte) i};
      PushbackInputStream pis = new PushbackInputStream(new ByteArrayInputStream(v2Data), 4);

      ProtocolNegotiator negotiator = new ProtocolNegotiator();
      ProtocolVersion detected = negotiator.negotiate(pis);

      assertEquals(ProtocolVersion.V2, detected);
      assertEquals(i, pis.read()); // Verify data after magic bytes
    }
  }

  @Test
  public void testNegotiateMultipleV1Connections() throws IOException {
    // Simulate multiple connections to ensure negotiation works repeatedly
    for (int i = 0; i < 5; i++) {
      byte[] v1Data = {(byte) 0xAC, (byte) 0xED, 0x00, (byte) i};
      PushbackInputStream pis = new PushbackInputStream(new ByteArrayInputStream(v1Data), 4);

      ProtocolNegotiator negotiator = new ProtocolNegotiator();
      ProtocolVersion detected = negotiator.negotiate(pis);

      assertEquals(ProtocolVersion.V1, detected);
      assertEquals(0xAC, pis.read() & 0xFF); // Verify pushed back
    }
  }

  @Test
  public void testProtocolVersionToString() {
    assertTrue(ProtocolVersion.V1.toString().contains("version=1"));
    assertTrue(ProtocolVersion.V1.toString().contains("hasMagicBytes=false"));

    assertTrue(ProtocolVersion.V2.toString().contains("version=2"));
    assertTrue(ProtocolVersion.V2.toString().contains("hasMagicBytes=true"));
  }

  @Test
  public void testMagicBytesImmutable() {
    // Ensure that modifying returned magic bytes doesn't affect the enum
    byte[] magicBytes = ProtocolVersion.V2.getMagicBytes();
    byte original = magicBytes[0];
    magicBytes[0] = 0x00; // Try to modify

    byte[] magicBytes2 = ProtocolVersion.V2.getMagicBytes();
    assertEquals(original, magicBytes2[0]); // Should still be original value
  }
}
