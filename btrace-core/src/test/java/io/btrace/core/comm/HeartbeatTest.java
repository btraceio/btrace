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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HeartbeatTest {

  @Test
  void heartbeatRoundTripsOnV2() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    WireProtocol writer = new BinaryWireProtocol(new ByteArrayInputStream(new byte[0]), out);
    writer.write(new HeartbeatCommand(42L));

    Command read =
        new BinaryWireProtocol(
                new ByteArrayInputStream(out.toByteArray()), new ByteArrayOutputStream())
            .read();

    assertEquals(Command.HEARTBEAT, read.getType());
    assertEquals(42L, ((HeartbeatCommand) read).getNonce());
  }

  @Test
  void v1RefusesHeartbeatWithoutTouchingTheStream() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream v1 = new ObjectOutputStream(out);
    v1.flush();
    int header = out.size();

    assertThrows(IOException.class, () -> WireIO.write(v1, new HeartbeatCommand(1L)));
    v1.flush();
    assertEquals(header, out.size(), "a refused heartbeat must not write anything");
  }

  @Test
  void silenceTriggersTimeoutOnce() throws Exception {
    AtomicInteger timeouts = new AtomicInteger();
    CountDownLatch fired = new CountDownLatch(1);
    HeartbeatMonitor monitor =
        HeartbeatMonitor.watch(
            "test",
            200,
            () -> {
              timeouts.incrementAndGet();
              fired.countDown();
            });

    assertTrue(fired.await(5, TimeUnit.SECONDS));
    assertTrue(monitor.isTimedOut());
    Thread.sleep(400);
    assertEquals(1, timeouts.get());
  }

  @Test
  void trafficKeepsTheConnectionAlive() throws Exception {
    AtomicInteger timeouts = new AtomicInteger();
    HeartbeatMonitor monitor = HeartbeatMonitor.watch("test", 300, timeouts::incrementAndGet);
    try {
      long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1200);
      while (System.nanoTime() < end) {
        monitor.received();
        Thread.sleep(50);
      }
      assertEquals(0, timeouts.get());
      assertFalse(monitor.isTimedOut());
    } finally {
      monitor.close();
    }
  }

  @Test
  void pingsEveryIntervalEvenWhileTrafficArrives() throws Exception {
    AtomicInteger pings = new AtomicInteger();
    HeartbeatMonitor monitor =
        HeartbeatMonitor.pingAndWatch(
            "test", 100, 5_000, nonce -> pings.incrementAndGet(), () -> {});
    try {
      long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000);
      while (System.nanoTime() < end) {
        monitor.received();
        Thread.sleep(20);
      }
      assertTrue(pings.get() >= 5, "only " + pings.get() + " pings");
    } finally {
      monitor.close();
    }
  }

  @Test
  void stuckPingDoesNotDelayTheTimeout() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch fired = new CountDownLatch(1);
    HeartbeatMonitor monitor =
        HeartbeatMonitor.pingAndWatch(
            "test",
            50,
            300,
            nonce -> {
              try {
                release.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            fired::countDown);
    try {
      assertTrue(fired.await(5, TimeUnit.SECONDS), "a blocked ping held up the timeout");
    } finally {
      release.countDown();
      monitor.close();
    }
  }

  @Test
  void zeroOrNegativeValuesDisableTheMonitor() {
    assertNull(HeartbeatMonitor.watch("test", 0, () -> {}));
    assertNull(HeartbeatMonitor.pingAndWatch("test", 0, 1000, n -> {}, () -> {}));
    assertNull(HeartbeatMonitor.pingAndWatch("test", 1000, -1, n -> {}, () -> {}));
  }

  @Test
  void propertiesFallBackToDefaults() {
    try {
      System.setProperty(HeartbeatMonitor.INTERVAL_PROPERTY, "not-a-number");
      assertEquals(HeartbeatMonitor.DEFAULT_INTERVAL_MILLIS, HeartbeatMonitor.intervalMillis());
      System.setProperty(HeartbeatMonitor.TIMEOUT_PROPERTY, "1234");
      assertEquals(1234L, HeartbeatMonitor.timeoutMillis());
    } finally {
      System.clearProperty(HeartbeatMonitor.INTERVAL_PROPERTY);
      System.clearProperty(HeartbeatMonitor.TIMEOUT_PROPERTY);
    }
  }
}
