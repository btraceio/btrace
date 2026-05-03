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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.junit.jupiter.api.Test;

/** Tests for WireProtocol interface and implementations. */
public class WireProtocolTest {

  @Test
  public void testJavaSerializationProtocolBasic() throws Exception {
    // Use piped streams for proper ObjectStream initialization
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    // Create protocols
    WireProtocol writer = WireProtocol.create(ProtocolVersion.V1, pis, pos);

    // Write command in separate thread to avoid deadlock
    Thread writerThread =
        new Thread(
            () -> {
              try {
                Command cmd = new MessageCommand("test message");
                writer.write(cmd);
                writer.flush();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    writerThread.start();

    // Read command
    Command readCmd = writer.read();

    assertEquals(Command.MESSAGE, readCmd.getType());
    assertTrue(readCmd instanceof MessageCommand);
    assertEquals("test message", ((MessageCommand) readCmd).getMessage());
    assertEquals(ProtocolVersion.V1, writer.getVersion());

    writerThread.join();
  }

  @Test
  public void testBinaryWireProtocolBasic() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Write with binary protocol
    WireProtocol writer = new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), baos);
    Command cmd = new MessageCommand("test message");
    writer.write(cmd);
    writer.flush();

    // Read with binary protocol
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    WireProtocol reader = new BinaryWireProtocol(bais, new ByteArrayOutputStream());
    Command readCmd = reader.read();

    assertEquals(Command.MESSAGE, readCmd.getType());
    assertTrue(readCmd instanceof MessageCommand);
    assertEquals("test message", ((MessageCommand) readCmd).getMessage());
    assertEquals(ProtocolVersion.V2, reader.getVersion());
  }

  @Test
  public void testWireProtocolCreateV1() throws Exception {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    WireProtocol protocol = WireProtocol.create(ProtocolVersion.V1, pis, pos);

    assertTrue(protocol instanceof JavaSerializationProtocol);
    assertEquals(ProtocolVersion.V1, protocol.getVersion());
    protocol.close();
  }

  @Test
  public void testWireProtocolCreateV2() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    WireProtocol protocol =
        WireProtocol.create(ProtocolVersion.V2, new ByteArrayInputStream(new byte[0]), baos);

    assertTrue(protocol instanceof BinaryWireProtocol);
    assertEquals(ProtocolVersion.V2, protocol.getVersion());
  }

  @Test
  public void testWireProtocolWithNegotiationV2() throws Exception {
    // Create stream with V2 magic bytes
    ByteArrayOutputStream setupStream = new ByteArrayOutputStream();
    setupStream.write(new byte[] {0x42, 0x54, 0x52, 0x32}); // "BTR2"

    // Add some dummy data for the protocol to work with
    ByteArrayInputStream inputStream = new ByteArrayInputStream(setupStream.toByteArray());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    WireProtocol protocol = WireProtocol.createWithNegotiation(inputStream, outputStream);

    assertEquals(ProtocolVersion.V2, protocol.getVersion());
    assertTrue(protocol instanceof BinaryWireProtocol);
  }

  @Test
  public void testWireProtocolWithNegotiationV1() throws Exception {
    // Create stream with Java serialization header
    ByteArrayOutputStream setupStream = new ByteArrayOutputStream();
    setupStream.write(new byte[] {(byte) 0xAC, (byte) 0xED, 0x00, 0x05});

    ByteArrayInputStream inputStream = new ByteArrayInputStream(setupStream.toByteArray());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    WireProtocol protocol = WireProtocol.createWithNegotiation(inputStream, outputStream);

    assertEquals(ProtocolVersion.V1, protocol.getVersion());
    assertTrue(protocol instanceof JavaSerializationProtocol);
  }

  @Test
  public void testWireProtocolWithConfigForceV1() throws Exception {
    ProtocolConfig config =
        ProtocolConfig.builder()
            .version(ProtocolVersion.V1)
            .autoNegotiate(false)
            .forceVersion(true)
            .build();

    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    WireProtocol protocol = WireProtocol.createWithConfig(config, pis, pos);

    assertEquals(ProtocolVersion.V1, protocol.getVersion());
    assertTrue(protocol instanceof JavaSerializationProtocol);
    protocol.close();
  }

  @Test
  public void testWireProtocolWithConfigAutoNegotiate() throws Exception {
    ProtocolConfig config =
        ProtocolConfig.builder()
            .version(ProtocolVersion.V2) // Preferred version
            .autoNegotiate(true)
            .build();

    // Stream with V2 magic bytes
    ByteArrayOutputStream setupStream = new ByteArrayOutputStream();
    setupStream.write(new byte[] {0x42, 0x54, 0x52, 0x32});

    ByteArrayInputStream inputStream = new ByteArrayInputStream(setupStream.toByteArray());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    WireProtocol protocol = WireProtocol.createWithConfig(config, inputStream, outputStream);

    assertEquals(ProtocolVersion.V2, protocol.getVersion());
  }

  @Test
  public void testMultipleCommandsV1() throws Exception {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);

    // Single protocol for both read and write
    WireProtocol protocol = new JavaSerializationProtocol(pis, pos);

    // Write multiple commands in separate thread
    Thread writerThread =
        new Thread(
            () -> {
              try {
                protocol.write(new MessageCommand("msg1"));
                protocol.write(new MessageCommand("msg2"));
                protocol.write(new ExitCommand(0));
                protocol.flush();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    writerThread.start();

    // Read multiple commands
    Command cmd1 = protocol.read();
    assertEquals("msg1", ((MessageCommand) cmd1).getMessage());

    Command cmd2 = protocol.read();
    assertEquals("msg2", ((MessageCommand) cmd2).getMessage());

    Command cmd3 = protocol.read();
    assertEquals(Command.EXIT, cmd3.getType());

    writerThread.join();
    protocol.close();
  }

  @Test
  public void testMultipleCommandsV2() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    WireProtocol writer = new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), baos);

    // Write multiple commands
    writer.write(new MessageCommand("msg1"));
    writer.write(new MessageCommand("msg2"));
    writer.write(new ExitCommand(0));
    writer.flush();

    // Read multiple commands
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    WireProtocol reader = new BinaryWireProtocol(bais, new ByteArrayOutputStream());

    Command cmd1 = reader.read();
    assertEquals("msg1", ((MessageCommand) cmd1).getMessage());

    Command cmd2 = reader.read();
    assertEquals("msg2", ((MessageCommand) cmd2).getMessage());

    Command cmd3 = reader.read();
    assertEquals(Command.EXIT, cmd3.getType());
  }

  @Test
  public void testCloseV1() throws Exception {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);
    WireProtocol protocol = new JavaSerializationProtocol(pis, pos);

    protocol.close();

    assertThrows(IOException.class, () -> protocol.write(new MessageCommand("test")));
    assertThrows(IOException.class, () -> protocol.read());
  }

  @Test
  public void testCloseV2() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    WireProtocol protocol = new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), baos);

    protocol.close();

    assertThrows(IOException.class, () -> protocol.write(new MessageCommand("test")));
    assertThrows(IOException.class, () -> protocol.read());
  }

  @Test
  public void testJavaSerializationProtocolReset() throws Exception {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);
    JavaSerializationProtocol protocol = new JavaSerializationProtocol(pis, pos);

    // Reset should not throw
    protocol.reset();

    // Should still be able to write after reset (in separate thread to avoid deadlock)
    Thread writerThread =
        new Thread(
            () -> {
              try {
                protocol.write(new MessageCommand("test"));
                protocol.flush();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    writerThread.start();
    writerThread.join();

    protocol.close();
  }

  @Test
  public void testJavaSerializationProtocolAccessors() throws Exception {
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);
    JavaSerializationProtocol protocol = new JavaSerializationProtocol(pis, pos);

    assertNotNull(protocol.getObjectInputStream());
    assertNotNull(protocol.getObjectOutputStream());
    protocol.close();
  }

  @Test
  public void testBinaryWireProtocolAccessors() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    BinaryWireProtocol protocol = new BinaryWireProtocol(bais, baos);

    assertSame(bais, protocol.getInputStream());
    assertSame(baos, protocol.getOutputStream());
  }

  @Test
  public void testV1V2Interoperability() throws Exception {
    // Test V2 - simpler with byte arrays
    ByteArrayOutputStream v2Stream = new ByteArrayOutputStream();
    WireProtocol v2Writer = new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), v2Stream);
    v2Writer.write(new MessageCommand("v2 message"));
    v2Writer.flush();

    // Read with V2
    WireProtocol v2Reader =
        new BinaryWireProtocol(
            new ByteArrayInputStream(v2Stream.toByteArray()), new ByteArrayOutputStream());
    Command v2Cmd = v2Reader.read();
    assertEquals("v2 message", ((MessageCommand) v2Cmd).getMessage());
    v2Reader.close();

    // Test V1 with piped streams
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);
    WireProtocol v1Protocol = new JavaSerializationProtocol(pis, pos);

    // Write in separate thread
    Thread writerThread =
        new Thread(
            () -> {
              try {
                v1Protocol.write(new MessageCommand("v1 message"));
                v1Protocol.flush();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    writerThread.start();

    // Read
    Command v1Cmd = v1Protocol.read();
    assertEquals("v1 message", ((MessageCommand) v1Cmd).getMessage());

    writerThread.join();
    v1Protocol.close();
  }
}
