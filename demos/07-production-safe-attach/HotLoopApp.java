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

/**
 * Target application for demos/07-production-safe-attach: one thread calls a cheap hot method
 * ({@code Work.step}) in a tight loop and reports its own throughput every 2 seconds, so the cost
 * of a probe on that method shows up as a lower number. Run with {@code java HotLoopApp.java}
 * (JDK 11+, no dependencies).
 */
public class HotLoopApp {
  private static volatile long sink;

  public static void main(String[] args) throws Exception {
    System.out.println("[demo] hot loop running - stop with Ctrl+C");
    long calls = 0;
    long acc = 0;
    long windowStart = System.nanoTime();
    long i = 0;
    while (true) {
      acc += Work.step(i++);
      calls++;
      if ((calls & 0xFFFF) == 0) {
        sink = acc; // keep the computation observable
        long now = System.nanoTime();
        if (now - windowStart >= 2_000_000_000L) {
          double perSecond = calls * 1e9 / (now - windowStart);
          System.out.printf("[demo] %.1f M calls/s%n", perSecond / 1e6);
          calls = 0;
          windowStart = now;
        }
      }
    }
  }
}

class Work {
  static long step(long i) {
    long x = i * 0x9E3779B97F4A7C15L;
    x ^= (x >>> 29);
    x *= 0xBF58476D1CE4E5B9L;
    return x ^ (x >>> 32);
  }
}
