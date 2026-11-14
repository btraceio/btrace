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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.btrace.core.comm.v2.BinaryCommand;
import io.btrace.core.comm.v2.CommandAdapter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The v1 ({@link Command}) and v2 ({@link BinaryCommand}) command tables must describe the same set
 * of commands. A command added to one table and not the other breaks the wire protocol only at
 * runtime, against a peer on the other protocol version.
 */
class CommandTableParityTest {

  @Test
  void v1AndV2DeclareTheSameTypeConstants() throws Exception {
    assertEquals(typeConstants(Command.class), typeConstants(BinaryCommand.class));
  }

  @Test
  void rangeBoundsAgree() throws Exception {
    Map<String, Byte> constants = typeConstants(Command.class);
    byte min = Collections.min(constants.values());
    byte max = Collections.max(constants.values());

    assertEquals(Command.FIRST_COMMAND, BinaryCommand.FIRST_COMMAND);
    assertEquals(Command.LAST_COMMAND, BinaryCommand.LAST_COMMAND);
    assertEquals(min, Command.FIRST_COMMAND);
    assertEquals(max, Command.LAST_COMMAND);
    assertEquals(constants.size(), max - min + 1, "type constants must be contiguous");
  }

  @Test
  void everyTypeHasAV2Factory() {
    for (byte type = Command.FIRST_COMMAND; type <= Command.LAST_COMMAND; type++) {
      BinaryCommand command;
      try {
        command = BinaryCommand.createCommand(type);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("no v2 factory for " + nameOf(type) + " (" + type + ")", e);
      }
      assertNotNull(command, "no v2 factory for " + nameOf(type));
      assertEquals(type, command.getType(), "wrong v2 type for " + nameOf(type));
    }
  }

  @Test
  void everyTypeConvertsBothWays() {
    for (byte type = Command.FIRST_COMMAND; type <= Command.LAST_COMMAND; type++) {
      String name = nameOf(type);
      // a freshly created command is enough for the type mapping; field values are not compared
      Command v1 = CommandAdapter.toBtraceCommand(BinaryCommand.createCommand(type));
      assertEquals(type, v1.getType(), "v2 -> v1 changed the type of " + name);
      BinaryCommand v2 = CommandAdapter.toBinaryCommand(v1);
      assertEquals(type, v2.getType(), "v1 -> v2 changed the type of " + name);
    }
  }

  @Test
  void heartbeatIsTheOnlyTypeTheV1StreamRefuses() throws IOException {
    List<Byte> refused = new ArrayList<>();
    for (byte type = Command.FIRST_COMMAND; type <= Command.LAST_COMMAND; type++) {
      if (v1StreamRejectsType(type)) {
        refused.add(type);
      }
    }
    assertEquals(
        Collections.singletonList(Command.HEARTBEAT),
        refused,
        "the v1 stream may refuse HEARTBEAT and nothing else");

    // the write side refuses before writing the type byte, so the stream stays intact
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.flush();
      int before = bytes.size();
      assertThrows(IOException.class, () -> WireIO.write(out, new HeartbeatCommand(1L)));
      out.flush();
      assertEquals(before, bytes.size());
    }
  }

  /** True when WireIO.read does not know the type byte (as opposed to a truncated payload). */
  private static boolean v1StreamRejectsType(byte type) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeByte(type);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      WireIO.read(in);
      return false;
    } catch (EOFException e) {
      return false; // type accepted, payload missing
    } catch (IOException e) {
      return String.valueOf(e.getMessage()).startsWith("invalid command");
    }
  }

  /** Name to value of every {@code public static final byte} except the range bounds. */
  private static Map<String, Byte> typeConstants(Class<?> table) throws IllegalAccessException {
    Map<String, Byte> constants = new TreeMap<>();
    for (Field field : table.getDeclaredFields()) {
      int mod = field.getModifiers();
      if (field.getType() == byte.class
          && Modifier.isPublic(mod)
          && Modifier.isStatic(mod)
          && Modifier.isFinal(mod)
          && !field.getName().equals("FIRST_COMMAND")
          && !field.getName().equals("LAST_COMMAND")) {
        constants.put(field.getName(), field.getByte(null));
      }
    }
    return constants;
  }

  private static String nameOf(byte type) {
    try {
      for (Map.Entry<String, Byte> entry : typeConstants(Command.class).entrySet()) {
        if (entry.getValue() == type) {
          return entry.getKey();
        }
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    return "type " + type;
  }
}
