/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BinaryWireProtocolConcurrencyTest {

  /**
   * A write stuck on one connection, such as a peer that stopped reading, must not hold up writes
   * on any other connection.
   */
  @Test
  void writeBlockedOnOneConnectionDoesNotBlockAnother() throws Exception {
    CountDownLatch writeEntered = new CountDownLatch(1);
    CountDownLatch releaseWrite = new CountDownLatch(1);
    OutputStream stuck =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            writeEntered.countDown();
            try {
              releaseWrite.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IOException(e);
            }
          }
        };
    WireProtocol stuckProtocol =
        new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), stuck);
    ByteArrayOutputStream healthyOut = new ByteArrayOutputStream();
    WireProtocol healthyProtocol =
        new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), healthyOut);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      executor.submit(
          () -> {
            stuckProtocol.write(new MessageCommand("stuck"));
            return null;
          });
      assertTrue(writeEntered.await(5, TimeUnit.SECONDS), "the stuck write never started");

      Future<?> healthyWrite =
          executor.submit(
              () -> {
                healthyProtocol.write(new MessageCommand("healthy"));
                healthyProtocol.flush();
                return null;
              });
      healthyWrite.get(5, TimeUnit.SECONDS);

      Command written =
          new BinaryWireProtocol(
                  new ByteArrayInputStream(healthyOut.toByteArray()), new ByteArrayOutputStream())
              .read();
      assertEquals("healthy", ((MessageCommand) written).getMessage());
    } finally {
      releaseWrite.countDown();
      executor.shutdownNow();
    }
  }

  /** Frames written by several threads to one connection must reach the reader intact. */
  @Test
  void concurrentWritersOnOneConnectionProduceWholeFrames() throws Exception {
    int threads = 4;
    int perThread = 500;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    WireProtocol protocol = new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), out);

    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int t = 0; t < threads; t++) {
        int thread = t;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < perThread; i++) {
                    protocol.write(new MessageCommand("t" + thread + "-m" + i));
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
    protocol.flush();

    WireProtocol reader =
        new BinaryWireProtocol(
            new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream());
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < threads * perThread; i++) {
      seen.add(((MessageCommand) reader.read()).getMessage());
    }
    assertEquals(threads * perThread, seen.size());
  }
}
