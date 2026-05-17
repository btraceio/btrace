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
package io.btrace.contracts;

import io.btrace.core.extensions.ServiceDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * BTrace extension for enforcing runtime behavioral contracts.
 *
 * <p>Validates that methods respect declared invariants at runtime — latency budgets, call-rate
 * limits, null-safety, and value ranges — without modifying the target code. Violations are counted
 * and reported; the target application is never interrupted.
 *
 * <p>Optionally tracks call count and average latency per user-supplied tag, so you can compare any
 * two code paths (e.g. cached vs uncached, v1 vs v2, impl A vs impl B) side by side.
 *
 * <p>Usage in a BTrace script:
 *
 * <pre>
 * &#64;Injected ContractService contracts;
 *
 * &#64;OnMethod(clazz = "com.app.Service", method = "query",
 *           location = &#64;Location(Kind.RETURN))
 * void onReturn(&#64;Duration long dur) {
 *     contracts.checkLatency("Service.query", dur, 200_000_000L); // 200ms budget
 * }
 * </pre>
 */
@ServiceDescriptor
public interface ContractService {

  // ==================== Contract checks ====================

  /**
   * Checks that a method's latency does not exceed the budget. Records a violation if {@code
   * durationNanos > budgetNanos}.
   */
  void checkLatency(String contract, long durationNanos, long budgetNanos);

  /**
   * Checks that the call rate does not exceed the limit per second. Uses a sliding window to detect
   * bursts.
   */
  void checkCallRate(String contract, int maxPerSecond);

  /** Asserts a boolean condition. Records a violation if false. */
  void assertCondition(String contract, boolean condition, String message);

  /** Checks that a numeric value is within bounds (inclusive). Records a violation if not. */
  void checkRange(String contract, long value, long min, long max);

  /** Checks that a return value is not null. Records a violation if null. */
  void checkNotNull(String contract, Object value);

  // ==================== Tracking ====================

  /**
   * Records a call against the given tag for the contract. Call count and cumulative duration are
   * accumulated per tag; {@link #getSummary()} renders all tags side by side. The tag is
   * user-defined — common examples: {@code "cached"}/{@code "uncached"}, {@code "v1"}/{@code "v2"},
   * {@code "fast-path"}/{@code "slow-path"}.
   */
  void trackCodePath(String contract, long durationNanos, String tag);

  // ==================== Reporting ====================

  /** Returns a formatted summary of all contract checks, violations, and tracked paths. */
  @Nullable String getSummary();

  /** Returns total number of contract violations across all contracts. */
  long getTotalViolations();

  /** Returns number of violations for a specific contract. */
  long getViolations(String contract);

  /** Returns total number of contract checks performed. */
  long getTotalChecks();

  /** Returns true if any contract has been violated. */
  boolean hasViolations();

  /** Resets all metrics and violation history. */
  void reset();
}
