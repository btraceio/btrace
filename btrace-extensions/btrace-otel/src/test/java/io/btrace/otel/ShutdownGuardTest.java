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
package io.btrace.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ShutdownGuardTest {
  private static long in(long millis) {
    return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
  }

  @Test
  void rejectsBeforeOpenAndAfterClose() {
    ShutdownGuard guard = new ShutdownGuard();
    assertEquals(-1, guard.enter());
    guard.open();
    int ticket = guard.enter();
    assertTrue(ticket >= 0);
    guard.exit(ticket);
    assertEquals(0, guard.close(in(1000)));
    assertEquals(-1, guard.enter());
    // Rejected entries leave nothing in flight
    assertEquals(0, guard.close(in(0)));
  }

  @Test
  void closeWaitsForRecordingThatSawTheGuardOpen() throws Exception {
    ShutdownGuard guard = new ShutdownGuard();
    guard.open();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean recorded = new AtomicBoolean();
    Thread recorder =
        new Thread(
            () -> {
              int ticket = guard.enter();
              entered.countDown();
              try {
                // Simulates the recording thread being descheduled mid-recording
                release.await();
                recorded.set(true);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                guard.exit(ticket);
              }
            });
    recorder.start();
    assertTrue(entered.await(5, TimeUnit.SECONDS));

    AtomicLong pending = new AtomicLong(-1);
    AtomicBoolean recordedWhenClosed = new AtomicBoolean();
    Thread closer =
        new Thread(
            () -> {
              pending.set(guard.close(in(10_000)));
              recordedWhenClosed.set(recorded.get());
            });
    closer.start();
    closer.join(300);
    assertTrue(closer.isAlive(), "close() must wait for the recording in progress");
    assertEquals(-1, guard.enter(), "new recordings are rejected while close() waits");

    release.countDown();
    closer.join(5000);
    assertFalse(closer.isAlive());
    assertEquals(0, pending.get());
    assertTrue(recordedWhenClosed.get(), "close() returned before the recording finished");
    recorder.join();
  }

  @Test
  void closeReportsRecordingsStillInProgressAtDeadline() throws Exception {
    ShutdownGuard guard = new ShutdownGuard();
    guard.open();
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    Thread[] recorders = new Thread[2];
    for (int i = 0; i < recorders.length; i++) {
      recorders[i] =
          new Thread(
              () -> {
                int ticket = guard.enter();
                entered.countDown();
                try {
                  release.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  guard.exit(ticket);
                }
              });
      recorders[i].start();
    }
    assertTrue(entered.await(5, TimeUnit.SECONDS));

    long t0 = System.nanoTime();
    assertEquals(2, guard.close(in(100)));
    assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(2));

    release.countDown();
    for (Thread r : recorders) {
      r.join();
    }
  }
}
