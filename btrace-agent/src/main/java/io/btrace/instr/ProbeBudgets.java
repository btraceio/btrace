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
package io.btrace.instr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-probe hit counters and "tripped" marks backing probe budgets. Handler handles built by {@link
 * HandlerRepositoryImpl} increment a probe's counter when a rate ceiling is configured; a tripped
 * probe resolves to a no-op handler.
 *
 * <p>All state is keyed by the probe's internal class name and is dropped when the probe is
 * unregistered, so a redeploy starts with a clean counter and no tripped mark.
 */
public final class ProbeBudgets {
  private static final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> tripped = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> exempt = new ConcurrentHashMap<>();

  private static volatile boolean rateLimited;

  private ProbeBudgets() {}

  /** Called once at agent start. {@code false} means no counting step is ever composed. */
  public static void setRateLimited(boolean on) {
    rateLimited = on;
  }

  public static boolean isRateLimited() {
    return rateLimited;
  }

  /** Counter for a probe, by internal class name; created on first use. */
  public static LongAdder counter(String probeInternalName) {
    // No lambda: this is reachable from premain, which must not touch invokedynamic.
    LongAdder existing = counters.get(probeInternalName);
    if (existing != null) {
      return existing;
    }
    LongAdder created = new LongAdder();
    LongAdder raced = counters.putIfAbsent(probeInternalName, created);
    return raced != null ? raced : created;
  }

  /** Current hit count of a probe, without creating a counter; 0 while it has none. */
  public static long hits(String probeInternalName) {
    LongAdder counter = counters.get(probeInternalName);
    return counter != null ? counter.sum() : 0;
  }

  public static void markTripped(String probeInternalName) {
    tripped.put(probeInternalName, Boolean.TRUE);
  }

  public static boolean isTripped(String probeInternalName) {
    return tripped.containsKey(probeInternalName);
  }

  /** Marks a probe the budget governor skips, so its handlers are not counted either. */
  public static void markExempt(String probeInternalName) {
    exempt.put(probeInternalName, Boolean.TRUE);
  }

  public static boolean isExempt(String probeInternalName) {
    return exempt.containsKey(probeInternalName);
  }

  /** Drops only the tripped mark. */
  public static void clearTripped(String probeInternalName) {
    tripped.remove(probeInternalName);
  }

  /** Drops the counter and tripped mark; called when the probe is unregistered. */
  public static void forget(String probeInternalName) {
    counters.remove(probeInternalName);
    tripped.remove(probeInternalName);
    exempt.remove(probeInternalName);
  }

  static void resetForTesting() {
    counters.clear();
    tripped.clear();
    exempt.clear();
    rateLimited = false;
  }
}
