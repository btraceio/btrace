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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.openjdk.btrace.core.comm.WireIO;

/**
 * Performance tests comparing the binary protocol to Java serialization. This is not included in
 * the regular test suite to avoid slowing down normal builds.
 */
public class BinaryProtocolPerformanceTest {

  private static final int ITERATIONS = 10000;
  private static final byte[] SAMPLE_CODE = new byte[1024]; // 1KB of code
  private static final String LARGE_MESSAGE = generateLargeMessage(10 * 1024); // 10KB message

  static {
    // Initialize sample code with random data
    for (int i = 0; i < SAMPLE_CODE.length; i++) {
      SAMPLE_CODE[i] = (byte) (Math.random() * 256);
    }
  }

  private static String generateLargeMessage(int size) {
    StringBuilder sb = new StringBuilder(size);
    for (int i = 0; i < size; i++) {
      sb.append((char) ('a' + (i % 26)));
    }
    return sb.toString();
  }

  /**
   * This test compares the performance of binary protocol vs Java serialization for the
   * InstrumentCommand, which contains bytecode and arguments.
   */
  @Test
  public void testInstrumentCommandPerformance() throws IOException, ClassNotFoundException {
    // Create test data
    Map<String, String> args = new HashMap<>();
    args.put("className", "com.example.TestClass");
    args.put("methodName", "testMethod");
    args.put("probeId", "12345");

    // Binary protocol
    long binaryStartTime = System.nanoTime();
    long binaryTotalSize = 0;

    for (int i = 0; i < ITERATIONS; i++) {
      // Serialize
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      BinaryCommand cmd = new BinaryInstrumentCommand(SAMPLE_CODE, args);
      BinaryWireIO.write(baos, cmd);
      byte[] serialized = baos.toByteArray();
      binaryTotalSize += serialized.length;

      // Deserialize
      ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
      BinaryCommand deserialized = BinaryWireIO.read(bais);
    }

    long binaryEndTime = System.nanoTime();
    long binaryDuration = binaryEndTime - binaryStartTime;

    // Java serialization
    long javaStartTime = System.nanoTime();
    long javaTotalSize = 0;

    for (int i = 0; i < ITERATIONS; i++) {
      // Serialize
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      Command cmd = new InstrumentCommand(SAMPLE_CODE, args);
      WireIO.write(oos, cmd);
      oos.close();
      byte[] serialized = baos.toByteArray();
      javaTotalSize += serialized.length;

      // Deserialize
      ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
      ObjectInputStream ois = new ObjectInputStream(bais);
      Command deserialized = WireIO.read(ois);
      ois.close();
    }

    long javaEndTime = System.nanoTime();
    long javaDuration = javaEndTime - javaStartTime;

    // Print results
    System.out.println("InstrumentCommand Performance Test Results:");
    System.out.println("  Binary Protocol:");
    System.out.println("    Time: " + (binaryDuration / 1_000_000) + " ms");
    System.out.println("    Avg Size: " + (binaryTotalSize / ITERATIONS) + " bytes");
    System.out.println("  Java Serialization:");
    System.out.println("    Time: " + (javaDuration / 1_000_000) + " ms");
    System.out.println("    Avg Size: " + (javaTotalSize / ITERATIONS) + " bytes");
    System.out.println("  Improvement:");
    System.out.println("    Time: " + (javaDuration / (double) binaryDuration) + "x faster");
    System.out.println("    Size: " + (javaTotalSize / (double) binaryTotalSize) + "x smaller");
  }

  /**
   * This test compares the performance of binary protocol vs Java serialization for the
   * MessageCommand with compression.
   */
  @Test
  public void testMessageCommandPerformance() throws IOException, ClassNotFoundException {
    // Binary protocol
    long binaryStartTime = System.nanoTime();
    long binaryTotalSize = 0;

    for (int i = 0; i < ITERATIONS; i++) {
      // Serialize
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      BinaryCommand cmd = new BinaryMessageCommand(LARGE_MESSAGE, true);
      BinaryWireIO.write(baos, cmd);
      byte[] serialized = baos.toByteArray();
      binaryTotalSize += serialized.length;

      // Deserialize
      ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
      BinaryCommand deserialized = BinaryWireIO.read(bais);
    }

    long binaryEndTime = System.nanoTime();
    long binaryDuration = binaryEndTime - binaryStartTime;

    // Java serialization
    long javaStartTime = System.nanoTime();
    long javaTotalSize = 0;

    for (int i = 0; i < ITERATIONS; i++) {
      // Serialize
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      Command cmd = new MessageCommand(LARGE_MESSAGE);
      WireIO.write(oos, cmd);
      oos.close();
      byte[] serialized = baos.toByteArray();
      javaTotalSize += serialized.length;

      // Deserialize
      ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
      ObjectInputStream ois = new ObjectInputStream(bais);
      Command deserialized = WireIO.read(ois);
      ois.close();
    }

    long javaEndTime = System.nanoTime();
    long javaDuration = javaEndTime - javaStartTime;

    // Print results
    System.out.println("MessageCommand Performance Test Results:");
    System.out.println("  Binary Protocol (with compression):");
    System.out.println("    Time: " + (binaryDuration / 1_000_000) + " ms");
    System.out.println("    Avg Size: " + (binaryTotalSize / ITERATIONS) + " bytes");
    System.out.println("  Java Serialization:");
    System.out.println("    Time: " + (javaDuration / 1_000_000) + " ms");
    System.out.println("    Avg Size: " + (javaTotalSize / ITERATIONS) + " bytes");
    System.out.println("  Improvement:");
    System.out.println("    Time: " + (javaDuration / (double) binaryDuration) + "x faster");
    System.out.println("    Size: " + (javaTotalSize / (double) binaryTotalSize) + "x smaller");
  }
}
