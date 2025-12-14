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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for type safety validation in Command deserialization.
 * These tests ensure that malformed or malicious data cannot cause
 * ClassCastException at runtime.
 */
class TypeSafetyTest {

  @Test
  void testNumberDataCommandValidNumber() throws Exception {
    NumberDataCommand cmd = new NumberDataCommand("test", 42);

    // Serialize and deserialize
    NumberDataCommand deserialized = roundTrip(cmd, NumberDataCommand.class);

    assertEquals("test", deserialized.name);
    assertEquals(42, deserialized.getValue());
  }

  @Test
  void testNumberDataCommandNullValue() throws Exception {
    NumberDataCommand cmd = new NumberDataCommand("test", null);

    NumberDataCommand deserialized = roundTrip(cmd, NumberDataCommand.class);

    assertEquals("test", deserialized.name);
    assertNull(deserialized.getValue());
  }

  @Test
  void testNumberDataCommandRejectsInvalidType() throws Exception {
    // Create a command and manually serialize with wrong type
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // Write command header
    oos.writeByte(Command.NUMBER);

    // Write name
    oos.writeUTF("test");

    // Write WRONG type (String instead of Number)
    oos.writeObject("not a number");
    oos.flush();

    // Try to deserialize - should fail during WireIO.read()
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);

    IOException thrown = assertThrows(IOException.class, () -> {
      WireIO.read(ois);
    });
    assertTrue(thrown.getMessage().contains("Invalid data type"));
  }

  @Test
  void testErrorCommandValidThrowable() throws Exception {
    Throwable cause = new RuntimeException("test error");
    ErrorCommand cmd = new ErrorCommand(cause);

    ErrorCommand deserialized = roundTrip(cmd, ErrorCommand.class);

    assertNotNull(deserialized.getCause());
    assertEquals("test error", deserialized.getCause().getMessage());
  }

  @Test
  void testErrorCommandNullCause() throws Exception {
    ErrorCommand cmd = new ErrorCommand(null);

    ErrorCommand deserialized = roundTrip(cmd, ErrorCommand.class);

    assertNull(deserialized.getCause());
  }

  @Test
  void testErrorCommandRejectsInvalidType() throws Exception {
    // Create a command and manually serialize with wrong type
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // Write command header
    oos.writeByte(Command.ERROR);

    // Write WRONG type (String instead of Throwable)
    oos.writeObject("not a throwable");
    oos.flush();

    // Try to deserialize
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);

    IOException thrown = assertThrows(IOException.class, () -> {
      WireIO.read(ois);
    });
    assertTrue(thrown.getMessage().contains("Invalid data type"));
  }

  @Test
  void testSetSettingsCommandValidTypes() throws Exception {
    Map<String, Object> params = new HashMap<>();
    params.put("string_param", "value");
    params.put("int_param", 42);
    params.put("bool_param", true);
    params.put("long_param", 123L);
    params.put("double_param", 3.14);

    SetSettingsCommand cmd = new SetSettingsCommand(params);
    SetSettingsCommand deserialized = roundTrip(cmd, SetSettingsCommand.class);

    assertEquals(5, deserialized.getParams().size());
    assertEquals("value", deserialized.getParams().get("string_param"));
    assertEquals(42, deserialized.getParams().get("int_param"));
    assertEquals(true, deserialized.getParams().get("bool_param"));
    assertEquals(123L, deserialized.getParams().get("long_param"));
    assertEquals(3.14, deserialized.getParams().get("double_param"));
  }

  @Test
  void testSetSettingsCommandRejectsInvalidType() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // Write command header
    oos.writeByte(Command.SET_PARAMS);

    // Write params with invalid type (use a serializable but invalid type)
    oos.writeInt(1); // size
    oos.writeUTF("bad_param");
    oos.writeObject(new java.util.ArrayList<>()); // Invalid type - ArrayList is serializable but not a valid settings type
    oos.flush();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);

    IOException thrown = assertThrows(IOException.class, () -> {
      WireIO.read(ois);
    });
    assertTrue(thrown.getMessage().contains("Invalid settings value type"));
  }

  @Test
  void testSetSettingsCommandRejectsNegativeSize() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    oos.writeByte(Command.SET_PARAMS);
    oos.writeInt(-1); // Negative size!
    oos.flush();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);

    IOException thrown = assertThrows(IOException.class, () -> {
      WireIO.read(ois);
    });
    assertTrue(thrown.getMessage().contains("Invalid params size"));
  }

  @Test
  void testSetSettingsCommandRejectsExcessiveSize() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    oos.writeByte(Command.SET_PARAMS);
    oos.writeInt(100000); // Excessive size!
    oos.flush();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);

    IOException thrown = assertThrows(IOException.class, () -> {
      WireIO.read(ois);
    });
    assertTrue(thrown.getMessage().contains("Invalid params size"));
  }

  // Helper method to serialize and deserialize a command
  private <T extends Command> T roundTrip(T cmd, Class<T> clazz) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    WireIO.write(oos, cmd);
    oos.flush();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);
    Command result = WireIO.read(ois);

    assertInstanceOf(clazz, result);
    return clazz.cast(result);
  }
}
