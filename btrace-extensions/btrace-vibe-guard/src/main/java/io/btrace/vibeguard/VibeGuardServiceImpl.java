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
package io.btrace.vibeguard;

import io.btrace.core.extensions.Extension;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe behavioral contract enforcement with lock-free statistics. */
public final class VibeGuardServiceImpl extends Extension implements VibeGuardService {

  private final Map<String, ContractStats> contracts = new ConcurrentHashMap<>();
  private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
  private final Map<String, CodePathStats> aiPaths = new ConcurrentHashMap<>();
  private final Map<String, CodePathStats> humanPaths = new ConcurrentHashMap<>();

  // ==================== Contract checks ====================

  @Override
  public void checkLatency(String contract, long durationNanos, long budgetNanos) {
    ContractStats stats = getOrCreate(contract);
    stats.checks.incrementAndGet();
    if (durationNanos > budgetNanos) {
      stats.violations.incrementAndGet();
      stats.lastViolationMessage =
          "Latency "
              + (durationNanos / 1_000_000)
              + "ms exceeded budget "
              + (budgetNanos / 1_000_000)
              + "ms";
    }
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void checkCallRate(String contract, int maxPerSecond) {
    ContractStats stats = getOrCreate(contract);
    stats.checks.incrementAndGet();

    RateWindow window = rateWindows.computeIfAbsent(contract, k -> new RateWindow());
    long now = System.nanoTime();
    long count = window.recordAndCount(now);
    if (count > maxPerSecond) {
      stats.violations.incrementAndGet();
      stats.lastViolationMessage = "Rate " + count + "/sec exceeded limit " + maxPerSecond + "/sec";
    }
  }

  @Override
  public void assertCondition(String contract, boolean condition, String message) {
    ContractStats stats = getOrCreate(contract);
    stats.checks.incrementAndGet();
    if (!condition) {
      stats.violations.incrementAndGet();
      stats.lastViolationMessage = message;
    }
  }

  @Override
  public void checkRange(String contract, long value, long min, long max) {
    ContractStats stats = getOrCreate(contract);
    stats.checks.incrementAndGet();
    if (value < min || value > max) {
      stats.violations.incrementAndGet();
      stats.lastViolationMessage = "Value " + value + " outside range [" + min + ", " + max + "]";
    }
  }

  @Override
  public void checkNotNull(String contract, Object value) {
    ContractStats stats = getOrCreate(contract);
    stats.checks.incrementAndGet();
    if (value == null) {
      stats.violations.incrementAndGet();
      stats.lastViolationMessage = "Unexpected null return";
    }
  }

  // ==================== Tracking ====================

  @Override
  public void trackAiCodePath(String contract, long durationNanos) {
    CodePathStats s = aiPaths.computeIfAbsent(contract, k -> new CodePathStats());
    s.calls.incrementAndGet();
    s.totalDurationNanos.addAndGet(durationNanos);
  }

  @Override
  public void trackHumanCodePath(String contract, long durationNanos) {
    CodePathStats s = humanPaths.computeIfAbsent(contract, k -> new CodePathStats());
    s.calls.incrementAndGet();
    s.totalDurationNanos.addAndGet(durationNanos);
  }

  // ==================== Reporting ====================

  @Override
  public String getSummary() {
    if (contracts.isEmpty() && aiPaths.isEmpty()) {
      return "No contracts checked.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== Vibe Guard Summary ===\n\n");

    long totalChecks = 0;
    long totalViolations = 0;

    for (Map.Entry<String, ContractStats> entry : contracts.entrySet()) {
      String name = entry.getKey();
      ContractStats s = entry.getValue();
      long checks = s.checks.get();
      long violations = s.violations.get();
      totalChecks += checks;
      totalViolations += violations;

      sb.append("Contract: ").append(name).append("\n");
      sb.append("  Checks: ").append(checks);
      if (violations > 0) {
        sb.append(" | VIOLATIONS: ").append(violations);
        sb.append(" (").append(violations * 100 / checks).append("%)");
      } else {
        sb.append(" | OK");
      }
      sb.append("\n");

      // Latency stats if tracked
      long dur = s.totalDurationNanos.get();
      if (dur > 0 && checks > 0) {
        long avgMs = (dur / checks) / 1_000_000;
        long minMs =
            s.minDurationNanos.get() == Long.MAX_VALUE ? 0 : s.minDurationNanos.get() / 1_000_000;
        long maxMs = s.maxDurationNanos.get() / 1_000_000;
        sb.append("  Latency: avg ").append(avgMs).append("ms");
        sb.append(", min ").append(minMs).append("ms");
        sb.append(", max ").append(maxMs).append("ms\n");
      }

      // Last violation
      if (violations > 0 && s.lastViolationMessage != null) {
        sb.append("  Last: ").append(s.lastViolationMessage).append("\n");
      }
      sb.append("\n");
    }

    // AI vs Human comparison
    if (!aiPaths.isEmpty() || !humanPaths.isEmpty()) {
      sb.append(
          humanPaths.isEmpty() ? "--- AI Code Paths ---\n" : "--- AI vs Human Code Paths ---\n");
      // Collect all contract names from both
      ConcurrentHashMap<String, Boolean> allNames = new ConcurrentHashMap<>();
      for (String k : aiPaths.keySet()) allNames.put(k, Boolean.TRUE);
      for (String k : humanPaths.keySet()) allNames.put(k, Boolean.TRUE);

      for (String name : allNames.keySet()) {
        CodePathStats ai = aiPaths.get(name);
        CodePathStats human = humanPaths.get(name);

        sb.append("  ").append(name).append(": ");
        if (ai != null) {
          long aiCalls = ai.calls.get();
          long aiAvgMs = aiCalls > 0 ? (ai.totalDurationNanos.get() / aiCalls) / 1_000_000 : 0;
          sb.append("AI ").append(aiCalls).append(" calls avg ").append(aiAvgMs).append("ms");
        }
        if (ai != null && human != null) sb.append(" | ");
        if (human != null) {
          long hCalls = human.calls.get();
          long hAvgMs = hCalls > 0 ? (human.totalDurationNanos.get() / hCalls) / 1_000_000 : 0;
          sb.append("Human ").append(hCalls).append(" calls avg ").append(hAvgMs).append("ms");
        }

        // Performance comparison
        if (ai != null && human != null) {
          long aiCalls = ai.calls.get();
          long hCalls = human.calls.get();
          if (aiCalls > 0 && hCalls > 0) {
            long aiAvg = ai.totalDurationNanos.get() / aiCalls;
            long hAvg = human.totalDurationNanos.get() / hCalls;
            if (hAvg > 0) {
              long pctDiff = ((aiAvg - hAvg) * 100) / hAvg;
              if (pctDiff > 0) {
                sb.append(" [AI ").append(pctDiff).append("% slower]");
              } else if (pctDiff < 0) {
                sb.append(" [AI ").append(-pctDiff).append("% faster]");
              }
            }
          }
        }
        sb.append("\n");
      }
      sb.append("\n");
    }

    sb.append("--- Totals ---\n");
    sb.append("  Checks: ").append(totalChecks).append("\n");
    sb.append("  Violations: ").append(totalViolations);
    if (totalViolations == 0) {
      sb.append(" (all contracts satisfied)");
    }
    sb.append("\n");

    return sb.toString();
  }

  @Override
  public long getTotalViolations() {
    long total = 0;
    for (ContractStats s : contracts.values()) {
      total += s.violations.get();
    }
    return total;
  }

  @Override
  public long getViolations(String contract) {
    ContractStats s = contracts.get(contract);
    return s != null ? s.violations.get() : 0;
  }

  @Override
  public long getTotalChecks() {
    long total = 0;
    for (ContractStats s : contracts.values()) {
      total += s.checks.get();
    }
    return total;
  }

  @Override
  public boolean hasViolations() {
    for (ContractStats s : contracts.values()) {
      if (s.violations.get() > 0) return true;
    }
    return false;
  }

  @Override
  public void reset() {
    contracts.clear();
    rateWindows.clear();
    aiPaths.clear();
    humanPaths.clear();
  }

  @Override
  public void close() {
    String summary = getSummary();
    if (!"No contracts checked.".equals(summary)) {
      getContext().send(summary);
    }
  }

  // ==================== Internals ====================

  private ContractStats getOrCreate(String contract) {
    return contracts.computeIfAbsent(contract, k -> new ContractStats());
  }

  private static void updateMinMax(AtomicLong min, AtomicLong max, long value) {
    long cur;
    do {
      cur = min.get();
      if (value >= cur) break;
    } while (!min.compareAndSet(cur, value));
    do {
      cur = max.get();
      if (value <= cur) break;
    } while (!max.compareAndSet(cur, value));
  }

  static final class ContractStats {
    final AtomicLong checks = new AtomicLong();
    final AtomicLong violations = new AtomicLong();
    final AtomicLong totalDurationNanos = new AtomicLong();
    final AtomicLong minDurationNanos = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxDurationNanos = new AtomicLong(0);
    volatile String lastViolationMessage;
  }

  static final class CodePathStats {
    final AtomicLong calls = new AtomicLong();
    final AtomicLong totalDurationNanos = new AtomicLong();
  }

  /**
   * Simple sliding-window rate counter. Tracks calls in the last second using a circular buffer of
   * 10 x 100ms buckets. Lock-free via CAS.
   */
  static final class RateWindow {
    private static final int BUCKETS = 10;
    private static final long BUCKET_NS = 100_000_000L; // 100ms
    private final AtomicLong[] counts = new AtomicLong[BUCKETS];
    private final AtomicLong[] timestamps = new AtomicLong[BUCKETS];

    RateWindow() {
      for (int i = 0; i < BUCKETS; i++) {
        counts[i] = new AtomicLong();
        timestamps[i] = new AtomicLong();
      }
    }

    long recordAndCount(long nowNanos) {
      int bucket = (int) ((nowNanos / BUCKET_NS) % BUCKETS);
      long bucketTime = (nowNanos / BUCKET_NS) * BUCKET_NS;

      // Reset bucket if stale
      if (timestamps[bucket].get() != bucketTime) {
        timestamps[bucket].set(bucketTime);
        counts[bucket].set(0);
      }
      counts[bucket].incrementAndGet();

      // Sum all non-stale buckets
      long total = 0;
      long windowStart = nowNanos - (BUCKETS * BUCKET_NS);
      for (int i = 0; i < BUCKETS; i++) {
        if (timestamps[i].get() > windowStart) {
          total += counts[i].get();
        }
      }
      return total;
    }
  }
}
