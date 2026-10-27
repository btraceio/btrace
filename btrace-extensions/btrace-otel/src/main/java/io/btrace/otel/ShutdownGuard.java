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

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Lets recording threads run without locks while guaranteeing that {@link #close(long)} returns
 * only after every recording that saw the guard open has finished.
 *
 * <p>{@link #enter()} increments a per-thread stripe and then reads {@link #open}; {@link
 * #close(long)} clears {@code open} and then waits for every stripe to reach zero. All of these are
 * volatile accesses and so totally ordered (JLS 17.4.4). If a recording read {@code open == true},
 * its increment precedes the clearing write and therefore close's read of that stripe; close cannot
 * observe zero until the matching {@link #exit(int)}, which happens-after the recording's work. A
 * recording that read {@code false} backs out and does nothing.
 */
final class ShutdownGuard {
  // A stripe is fixed per thread so a thread's increment and decrement hit the same counter
  private static final int STRIPES = 64;
  // 16 longs = 128 bytes between stripes to keep them on separate cache lines
  private static final int PAD = 16;

  private final AtomicLongArray inFlight = new AtomicLongArray(STRIPES * PAD);
  private volatile boolean open;

  void open() {
    open = true;
  }

  /**
   * Enters the guard.
   *
   * @return a ticket to pass to {@link #exit(int)}, or -1 when the guard is not open, in which case
   *     the caller must not record and must not call {@link #exit(int)}
   */
  int enter() {
    int stripe = ((int) Thread.currentThread().getId() & (STRIPES - 1)) * PAD;
    inFlight.incrementAndGet(stripe);
    if (!open) {
      inFlight.decrementAndGet(stripe);
      return -1;
    }
    return stripe;
  }

  void exit(int ticket) {
    inFlight.decrementAndGet(ticket);
  }

  /**
   * Closes the guard and waits for recordings in progress to finish.
   *
   * @param deadline {@link System#nanoTime()} after which to stop waiting
   * @return the number of recordings still in progress when the deadline passed, 0 otherwise
   */
  long close(long deadline) {
    open = false;
    for (int stripe = 0; stripe < STRIPES * PAD; stripe += PAD) {
      while (inFlight.get(stripe) != 0) {
        if (System.nanoTime() - deadline >= 0) {
          long pending = 0;
          for (int s = stripe; s < STRIPES * PAD; s += PAD) {
            pending += inFlight.get(s);
          }
          return pending;
        }
        Thread.yield();
      }
    }
    return 0;
  }
}
