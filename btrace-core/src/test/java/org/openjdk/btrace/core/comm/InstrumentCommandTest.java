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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;

/**
 * Tests for InstrumentCommand defensive copying to prevent external modification of bytecode and
 * arguments.
 */
class InstrumentCommandTest {

  @Test
  void testConstructorWithArgsMapMakesDefensiveCopy() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    ArgsMap args = new ArgsMap();
    args.put("key1", "value1");

    InstrumentCommand cmd = new InstrumentCommand(bytecode, args);

    // Modify the original bytecode array
    bytecode[0] = (byte) 0xFF;

    // Modify the original ArgsMap
    args.put("key2", "value2");

    // Verify command's internal state was not affected
    byte[] retrievedCode = cmd.getCode();
    assertEquals(0x01, retrievedCode[0], "Bytecode should not be affected by external modification");

    ArgsMap retrievedArgs = cmd.getArguments();
    assertNull(retrievedArgs.get("key2"), "Args should not be affected by external modification");
    assertEquals("value1", retrievedArgs.get("key1"));
  }

  @Test
  void testConstructorWithStringArrayArgs() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    String[] args = {"key1=value1", "key2=value2"};

    InstrumentCommand cmd = new InstrumentCommand(bytecode, args);

    // Modify original bytecode
    bytecode[0] = (byte) 0xFF;

    // Verify bytecode is protected
    assertEquals(0x01, cmd.getCode()[0]);

    // Verify args were parsed correctly
    assertEquals("value1", cmd.getArguments().get("key1"));
    assertEquals("value2", cmd.getArguments().get("key2"));
  }

  @Test
  void testConstructorWithMapArgs() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    Map<String, String> argsMap = new HashMap<>();
    argsMap.put("key1", "value1");

    InstrumentCommand cmd = new InstrumentCommand(bytecode, argsMap);

    // Modify original bytecode and map
    bytecode[0] = (byte) 0xFF;
    argsMap.put("key2", "value2");

    // Verify command is protected
    assertEquals(0x01, cmd.getCode()[0]);
    assertNull(cmd.getArguments().get("key2"));
  }

  @Test
  void testGetCodeReturnsDefensiveCopy() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    InstrumentCommand cmd = new InstrumentCommand(bytecode, new ArgsMap());

    byte[] retrieved1 = cmd.getCode();
    byte[] retrieved2 = cmd.getCode();

    // Modify the returned array
    retrieved1[0] = (byte) 0xFF;

    // Verify original is unchanged
    assertEquals(0x01, cmd.getCode()[0], "getCode() should return a defensive copy");

    // Verify each call returns a new copy
    assertNotSame(retrieved1, retrieved2, "getCode() should return new copy each time");
    assertEquals(0x01, retrieved2[0], "Second call should return unmodified bytecode");
  }

  @Test
  void testNullBytecode() {
    InstrumentCommand cmd = new InstrumentCommand(null, new ArgsMap());

    assertNull(cmd.getCode(), "Null bytecode should remain null");
  }

  @Test
  void testNullArgs() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    InstrumentCommand cmd = new InstrumentCommand(bytecode, (ArgsMap) null);

    assertNull(cmd.getArguments(), "Null args should remain null");
  }

  @Test
  void testEmptyBytecode() {
    byte[] bytecode = {};
    InstrumentCommand cmd = new InstrumentCommand(bytecode, new ArgsMap());

    assertNotNull(cmd.getCode());
    assertEquals(0, cmd.getCode().length);
  }

  @Test
  void testEmptyArgs() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    ArgsMap args = new ArgsMap();
    InstrumentCommand cmd = new InstrumentCommand(bytecode, args);

    assertNotNull(cmd.getArguments());
    assertEquals(0, cmd.getArguments().size());
  }

  @Test
  void testLargeBytecodeArray() {
    byte[] bytecode = new byte[1024 * 1024]; // 1MB
    for (int i = 0; i < bytecode.length; i++) {
      bytecode[i] = (byte) (i % 256);
    }

    InstrumentCommand cmd = new InstrumentCommand(bytecode, new ArgsMap());

    // Modify original
    bytecode[1000] = (byte) 0xFF;

    // Verify copy is independent
    assertNotEquals((byte) 0xFF, cmd.getCode()[1000]);
  }

  @Test
  void testMultipleArguments() {
    byte[] bytecode = {0x01, 0x02, 0x03};
    ArgsMap args = new ArgsMap();
    args.put("arg1", "value1");
    args.put("arg2", "value2");
    args.put("arg3", "value3");

    InstrumentCommand cmd = new InstrumentCommand(bytecode, args);

    // Modify original
    args.put("arg4", "value4");
    args.put("arg1", "modified");

    // Verify command's args are unchanged
    ArgsMap cmdArgs = cmd.getArguments();
    assertEquals(3, cmdArgs.size());
    assertEquals("value1", cmdArgs.get("arg1"));
    assertEquals("value2", cmdArgs.get("arg2"));
    assertEquals("value3", cmdArgs.get("arg3"));
    assertNull(cmdArgs.get("arg4"));
  }
}

