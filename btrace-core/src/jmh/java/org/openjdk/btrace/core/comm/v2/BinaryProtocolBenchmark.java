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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.openjdk.btrace.core.comm.EventCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.GridDataCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.openjdk.btrace.core.comm.NumberDataCommand;
import org.openjdk.btrace.core.comm.NumberMapDataCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.openjdk.btrace.core.comm.StringMapDataCommand;
import org.openjdk.btrace.core.comm.WireIO;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH benchmarks for the binary protocol vs Java serialization. Tests serialization and
 * deserialization performance across all command types.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class BinaryProtocolBenchmark {

  @Param({
    "InstrumentCommand",
    "MessageCommand",
    "ErrorCommand",
    "EventCommand",
    "StatusCommand",
    "NumberMapDataCommand",
    "StringMapDataCommand",
    "NumberDataCommand",
    "GridDataCommand",
    "ExitCommand"
  })
  private String commandType;

  @Param({"small", "medium", "large"})
  private String dataSize;

  private BinaryCommand binaryCommand;
  private Command javaCommand;

  @Setup
  public void setup() {
    switch (commandType) {
      case "InstrumentCommand":
        byte[] code = createByteArray(dataSize);
        String[] args = {"arg1", "arg2", "arg3"};
        binaryCommand = new BinaryInstrumentCommand(code, args);
        javaCommand = new InstrumentCommand(code, args);
        break;

      case "MessageCommand":
        String message = createString(dataSize);
        binaryCommand = new BinaryMessageCommand(message, false);
        javaCommand = new MessageCommand(message);
        break;

      case "ErrorCommand":
        Throwable cause = new RuntimeException(createString(dataSize));
        binaryCommand = new BinaryErrorCommand(1, cause.getMessage());
        javaCommand = new ErrorCommand(cause);
        break;

      case "EventCommand":
        String eventName = "test.event." + createString(dataSize);
        binaryCommand = new BinaryEventCommand(eventName);
        javaCommand = new EventCommand(eventName);
        break;

      case "StatusCommand":
        binaryCommand = new BinaryStatusCommand(1, true);
        javaCommand = new StatusCommand(1);
        break;

      case "NumberMapDataCommand":
        Map<String, Number> numberMap = createNumberMap(dataSize);
        binaryCommand = new BinaryNumberMapDataCommand("test", numberMap);
        javaCommand = new NumberMapDataCommand("test", numberMap);
        break;

      case "StringMapDataCommand":
        Map<String, String> stringMap = createStringMap(dataSize);
        binaryCommand = new BinaryStringMapDataCommand("test", stringMap);
        javaCommand = new StringMapDataCommand("test", stringMap);
        break;

      case "NumberDataCommand":
        binaryCommand = new BinaryNumberDataCommand("test", 42);
        javaCommand = new NumberDataCommand("test", 42);
        break;

      case "GridDataCommand":
        List<Object[]> gridData = createGridData(dataSize);
        List<String> columnNames = new ArrayList<>();
        columnNames.add("Name");
        columnNames.add("ID");
        columnNames.add("Value");
        binaryCommand = new BinaryGridDataCommand("test", columnNames, gridData);
        javaCommand = new GridDataCommand("test", gridData);
        break;

      case "ExitCommand":
        binaryCommand = new BinaryExitCommand(0);
        javaCommand = new ExitCommand(0);
        break;

      default:
        throw new IllegalArgumentException("Unknown command type: " + commandType);
    }
  }

  @Benchmark
  public byte[] binarySerialize() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryWireIO.write(baos, binaryCommand);
    return baos.toByteArray();
  }

  @Benchmark
  public BinaryCommand binaryDeserialize() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryWireIO.write(baos, binaryCommand);
    byte[] data = baos.toByteArray();

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    return BinaryWireIO.read(bais);
  }

  @Benchmark
  public byte[] javaSerialize() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    WireIO.write(oos, javaCommand);
    oos.close();
    return baos.toByteArray();
  }

  @Benchmark
  public Command javaDeserialize() throws IOException, ClassNotFoundException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    WireIO.write(oos, javaCommand);
    oos.close();
    byte[] data = baos.toByteArray();

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    ObjectInputStream ois = new ObjectInputStream(bais);
    Command cmd = WireIO.read(ois);
    ois.close();
    return cmd;
  }

  @Benchmark
  public byte[] binaryRoundTrip() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryWireIO.write(baos, binaryCommand);
    byte[] serialized = baos.toByteArray();

    ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
    BinaryCommand deserialized = BinaryWireIO.read(bais);

    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    BinaryWireIO.write(baos2, deserialized);
    return baos2.toByteArray();
  }

  @Benchmark
  public byte[] javaRoundTrip() throws IOException, ClassNotFoundException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    WireIO.write(oos, javaCommand);
    oos.close();
    byte[] serialized = baos.toByteArray();

    ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
    ObjectInputStream ois = new ObjectInputStream(bais);
    Command deserialized = WireIO.read(ois);
    ois.close();

    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    ObjectOutputStream oos2 = new ObjectOutputStream(baos2);
    WireIO.write(oos2, deserialized);
    oos2.close();
    return baos2.toByteArray();
  }

  private byte[] createByteArray(String size) {
    int length;
    switch (size) {
      case "small":
        length = 100;
        break;
      case "medium":
        length = 1000;
        break;
      case "large":
        length = 10000;
        break;
      default:
        length = 100;
    }
    byte[] data = new byte[length];
    for (int i = 0; i < length; i++) {
      data[i] = (byte) (i % 256);
    }
    return data;
  }

  private String createString(String size) {
    int length;
    switch (size) {
      case "small":
        length = 50;
        break;
      case "medium":
        length = 500;
        break;
      case "large":
        length = 5000;
        break;
      default:
        length = 50;
    }
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append((char) ('A' + (i % 26)));
    }
    return sb.toString();
  }

  private Map<String, Number> createNumberMap(String size) {
    int entries;
    switch (size) {
      case "small":
        entries = 5;
        break;
      case "medium":
        entries = 50;
        break;
      case "large":
        entries = 500;
        break;
      default:
        entries = 5;
    }
    Map<String, Number> map = new HashMap<>();
    for (int i = 0; i < entries; i++) {
      map.put("key" + i, i * 1.5);
    }
    return map;
  }

  private Map<String, String> createStringMap(String size) {
    int entries;
    switch (size) {
      case "small":
        entries = 5;
        break;
      case "medium":
        entries = 50;
        break;
      case "large":
        entries = 500;
        break;
      default:
        entries = 5;
    }
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < entries; i++) {
      map.put("key" + i, "value" + i);
    }
    return map;
  }

  private List<Object[]> createGridData(String size) {
    int rows;
    switch (size) {
      case "small":
        rows = 5;
        break;
      case "medium":
        rows = 50;
        break;
      case "large":
        rows = 500;
        break;
      default:
        rows = 5;
    }

    List<Object[]> data = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      Object[] row = new Object[3];
      row[0] = "row" + i;
      row[1] = i;
      row[2] = i * 1.5;
      data.add(row);
    }

    return data;
  }
}
