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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Edge case tests for the binary protocol. Tests null values, empty collections, large payloads,
 * boundary conditions, and malformed data.
 */
public class BinaryProtocolEdgeCasesTest {

  // ===== NULL AND EMPTY VALUE TESTS =====

  @Test
  public void testNullString() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryProtocol.writeString(baos, null);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    String result = BinaryProtocol.readString(bais);
    assertNull(result);
  }

  @Test
  public void testEmptyString() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryProtocol.writeString(baos, "");

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    String result = BinaryProtocol.readString(bais);
    assertNotNull(result);
    assertEquals("", result);
  }

  @Test
  public void testNullByteArray() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryProtocol.writeByteArray(baos, null);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    byte[] result = BinaryProtocol.readByteArray(bais);
    assertNull(result);
  }

  @Test
  public void testEmptyByteArray() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryProtocol.writeByteArray(baos, new byte[0]);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    byte[] result = BinaryProtocol.readByteArray(bais);
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  @Test
  public void testMessageCommandNullMessage() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    BinaryMessageCommand original = new BinaryMessageCommand(null, false);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    // Empty message should be preserved as empty string (not null)
    assertEquals("", msgCommand.getMessage());
  }

  @Test
  public void testErrorCommandNullMessage() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    BinaryErrorCommand original = new BinaryErrorCommand(404, null);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryErrorCommand);
    BinaryErrorCommand errorCommand = (BinaryErrorCommand) readCommand;
    assertNull(errorCommand.getMessage());
  }

  @Test
  public void testEventCommandNullEvent() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    BinaryEventCommand original = new BinaryEventCommand(null);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryEventCommand);
    BinaryEventCommand eventCommand = (BinaryEventCommand) readCommand;
    assertNull(eventCommand.getEvent());
  }

  @Test
  public void testStringMapWithNullValues() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    Map<String, String> data = new HashMap<>();
    data.put("key1", "value1");
    data.put("key2", null);
    data.put("key3", "");

    BinaryStringMapDataCommand original = new BinaryStringMapDataCommand("TestMap", data);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryStringMapDataCommand);
    BinaryStringMapDataCommand mapCommand = (BinaryStringMapDataCommand) readCommand;
    Map<String, String> readData = mapCommand.getData();
    assertEquals("value1", readData.get("key1"));
    assertNull(readData.get("key2"));
    assertEquals("", readData.get("key3"));
  }

  @Test
  public void testEmptyNumberMap() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    Map<String, Number> data = new HashMap<>();
    BinaryNumberMapDataCommand original = new BinaryNumberMapDataCommand("EmptyMap", data);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryNumberMapDataCommand);
    BinaryNumberMapDataCommand mapCommand = (BinaryNumberMapDataCommand) readCommand;
    assertTrue(mapCommand.getData().isEmpty());
  }

  // ===== BOUNDARY CONDITION TESTS =====

  @Test
  public void testVeryLargeMessage() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Create a 10MB message
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10 * 1024 * 1024; i++) {
      sb.append('A');
    }
    String largeMessage = sb.toString();

    BinaryMessageCommand original = new BinaryMessageCommand(largeMessage, false);
    BinaryWireIO.write(baos, original);

    // Verify it was written
    assertTrue(baos.size() > 0);

    // Verify it can be read back
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    assertEquals(largeMessage.length(), msgCommand.getMessage().length());
    assertEquals(largeMessage, msgCommand.getMessage());
  }

  @Test
  public void testLargeBytecodeArray() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Create a 1MB bytecode array
    byte[] largeCode = new byte[1024 * 1024];
    for (int i = 0; i < largeCode.length; i++) {
      largeCode[i] = (byte) (i % 256);
    }

    Map<String, String> args = new HashMap<>();
    args.put("test", "value");

    BinaryInstrumentCommand original = new BinaryInstrumentCommand(largeCode, args);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryInstrumentCommand);
    BinaryInstrumentCommand instrCommand = (BinaryInstrumentCommand) readCommand;
    assertArrayEquals(largeCode, instrCommand.getCode());
  }

  @Test
  public void testMapWith1000Entries() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    Map<String, Number> data = new HashMap<>();
    for (int i = 0; i < 1000; i++) {
      data.put("key" + i, i);
    }

    BinaryNumberMapDataCommand original = new BinaryNumberMapDataCommand("LargeMap", data);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryNumberMapDataCommand);
    BinaryNumberMapDataCommand mapCommand = (BinaryNumberMapDataCommand) readCommand;
    assertEquals(1000, mapCommand.getData().size());
    for (int i = 0; i < 1000; i++) {
      assertEquals(i, mapCommand.getData().get("key" + i).intValue());
    }
  }

  @Test
  public void testGridDataWithManyRows() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    List<String> columnNames = new ArrayList<>();
    columnNames.add("Col1");
    columnNames.add("Col2");

    List<Object[]> data = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      data.add(new Object[] {"Row" + i, i});
    }

    BinaryGridDataCommand original = new BinaryGridDataCommand("LargeGrid", columnNames, data);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryGridDataCommand);
    BinaryGridDataCommand gridCommand = (BinaryGridDataCommand) readCommand;
    assertEquals(500, gridCommand.getData().size());
  }

  // ===== UNICODE AND SPECIAL CHARACTER TESTS =====

  @Test
  public void testUnicodeStrings() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Test various Unicode characters
    String unicode = "Hello 世界 🌍 مرحبا Привет";
    BinaryMessageCommand original = new BinaryMessageCommand(unicode, false);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    assertEquals(unicode, msgCommand.getMessage());
  }

  @Test
  public void testEmojiInMessage() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    String emoji = "🚀 BTrace launched! 🎉🎊🎈";
    BinaryMessageCommand original = new BinaryMessageCommand(emoji, false);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    assertEquals(emoji, msgCommand.getMessage());
  }

  @Test
  public void testControlCharactersInString() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    String controlChars = "Line1\nLine2\tTabbed\rCarriageReturn\0Null";
    BinaryProtocol.writeString(baos, controlChars);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    String result = BinaryProtocol.readString(bais);
    assertEquals(controlChars, result);
  }

  // ===== MALFORMED DATA TESTS =====

  @Test
  public void testInvalidProtocolVersion() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Write invalid protocol version
    BinaryProtocol.writeByte(baos, (byte) 99); // Invalid version
    BinaryProtocol.writeByte(baos, BinaryCommand.MESSAGE); // Valid command type

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

    // Should throw IOException due to version mismatch
    assertThrows(
        IOException.class,
        () -> {
          BinaryWireIO.read(bais);
        });
  }

  @Test
  public void testInvalidCommandType() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Write valid protocol version but invalid command type
    BinaryProtocol.writeByte(baos, BinaryProtocol.VERSION);
    BinaryProtocol.writeByte(baos, (byte) 99); // Invalid command type

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

    // Should throw MalformedCommandException due to unknown command type
    assertThrows(
        MalformedCommandException.class,
        () -> {
          BinaryWireIO.read(bais);
        });
  }

  @Test
  public void testTruncatedStream() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Write only protocol version and command type, but not the data
    BinaryProtocol.writeByte(baos, BinaryProtocol.VERSION);
    BinaryProtocol.writeByte(baos, BinaryCommand.MESSAGE);
    // Missing message data...

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

    // Should throw IOException due to unexpected end of stream
    assertThrows(
        IOException.class,
        () -> {
          BinaryWireIO.read(bais);
        });
  }

  // ===== COMPRESSION EDGE CASES =====

  @Test
  public void testCompressionThreshold() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Create message exactly at threshold (1024 bytes)
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1024; i++) {
      sb.append('X');
    }
    String thresholdMessage = sb.toString();

    BinaryMessageCommand original = new BinaryMessageCommand(thresholdMessage, false);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    assertEquals(thresholdMessage, msgCommand.getMessage());
  }

  @Test
  public void testCompressionJustAboveThreshold() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Create message just above threshold (1025 bytes)
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1025; i++) {
      sb.append('Y');
    }
    String aboveThresholdMessage = sb.toString();

    BinaryMessageCommand original = new BinaryMessageCommand(aboveThresholdMessage, false);
    BinaryWireIO.write(baos, original);

    // Message should be compressed, verify it's smaller than original
    int wireSize = baos.size();
    // Wire size should be less than original due to compression
    // (accounting for protocol overhead)
    assertTrue(wireSize < aboveThresholdMessage.length());

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    assertEquals(aboveThresholdMessage, msgCommand.getMessage());
  }

  @Test
  public void testHighlyCompressibleMessage() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Create highly compressible message (many repeated characters)
    String compressible = "AAAAAAAAAA".repeat(1000); // 10,000 'A's

    BinaryMessageCommand original = new BinaryMessageCommand(compressible, false);
    BinaryWireIO.write(baos, original);

    // Compressed size should be much smaller than original
    int wireSize = baos.size();
    assertTrue(wireSize < compressible.length() / 5); // Should compress to less than 20%

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinaryMessageCommand);
    BinaryMessageCommand msgCommand = (BinaryMessageCommand) readCommand;
    assertEquals(compressible, msgCommand.getMessage());
  }

  // ===== NUMERIC BOUNDARY TESTS =====

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE})
  public void testIntegerBoundaries(int value) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryProtocol.writeInt(baos, value);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    int result = BinaryProtocol.readInt(bais);
    assertEquals(value, result);
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE})
  public void testLongBoundaries(long value) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryProtocol.writeLong(baos, value);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    long result = BinaryProtocol.readLong(bais);
    assertEquals(value, result);
  }

  @Test
  public void testSpecialFloatValues() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Test special float values
    BinaryProtocol.writeFloat(baos, Float.NaN);
    BinaryProtocol.writeFloat(baos, Float.POSITIVE_INFINITY);
    BinaryProtocol.writeFloat(baos, Float.NEGATIVE_INFINITY);
    BinaryProtocol.writeFloat(baos, Float.MIN_VALUE);
    BinaryProtocol.writeFloat(baos, Float.MAX_VALUE);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    assertTrue(Float.isNaN(BinaryProtocol.readFloat(bais)));
    assertEquals(Float.POSITIVE_INFINITY, BinaryProtocol.readFloat(bais));
    assertEquals(Float.NEGATIVE_INFINITY, BinaryProtocol.readFloat(bais));
    assertEquals(Float.MIN_VALUE, BinaryProtocol.readFloat(bais));
    assertEquals(Float.MAX_VALUE, BinaryProtocol.readFloat(bais));
  }

  @Test
  public void testSpecialDoubleValues() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // Test special double values
    BinaryProtocol.writeDouble(baos, Double.NaN);
    BinaryProtocol.writeDouble(baos, Double.POSITIVE_INFINITY);
    BinaryProtocol.writeDouble(baos, Double.NEGATIVE_INFINITY);
    BinaryProtocol.writeDouble(baos, Double.MIN_VALUE);
    BinaryProtocol.writeDouble(baos, Double.MAX_VALUE);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    assertTrue(Double.isNaN(BinaryProtocol.readDouble(bais)));
    assertEquals(Double.POSITIVE_INFINITY, BinaryProtocol.readDouble(bais));
    assertEquals(Double.NEGATIVE_INFINITY, BinaryProtocol.readDouble(bais));
    assertEquals(Double.MIN_VALUE, BinaryProtocol.readDouble(bais));
    assertEquals(Double.MAX_VALUE, BinaryProtocol.readDouble(bais));
  }

  // ===== SETTINGS COMMAND EDGE CASES =====

  @Test
  public void testSetSettingsCommandWithMixedTypes() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    Map<String, Object> settings = new HashMap<>();
    settings.put("stringValue", "test");
    settings.put("intValue", 42);
    settings.put("longValue", 9876543210L);
    settings.put("boolValue", true);
    settings.put("floatValue", 3.14f);
    settings.put("doubleValue", 2.71828);
    settings.put("nullValue", null);

    BinarySetSettingsCommand original = new BinarySetSettingsCommand(settings);
    BinaryWireIO.write(baos, original);

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    BinaryCommand readCommand = BinaryWireIO.read(bais);

    assertTrue(readCommand instanceof BinarySetSettingsCommand);
    BinarySetSettingsCommand settingsCommand = (BinarySetSettingsCommand) readCommand;
    Map<String, Object> readSettings = settingsCommand.getParams();

    assertEquals("test", readSettings.get("stringValue"));
    assertEquals(42, readSettings.get("intValue"));
    assertEquals(9876543210L, readSettings.get("longValue"));
    assertEquals(true, readSettings.get("boolValue"));
    assertEquals(3.14f, ((Number) readSettings.get("floatValue")).floatValue(), 0.001);
    assertEquals(2.71828, ((Number) readSettings.get("doubleValue")).doubleValue(), 0.00001);
    assertNull(readSettings.get("nullValue"));
  }
}
