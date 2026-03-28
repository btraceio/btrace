package org.openjdk.btrace.vibeguard;

import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/**
 * BTrace extension for runtime behavioral contracts on AI-generated code.
 *
 * <p>Validates that methods respect declared invariants at runtime — useful for
 * "vibe coding" workflows where LLMs generate code that needs guardrails.
 * Tracks contract violations, latency budgets, call frequency limits, and
 * return value constraints without modifying the target code.
 *
 * <p>Contracts are defined in BTrace scripts and enforced at instrumentation
 * points. When a contract is violated, the service records it and optionally
 * triggers an alert via the configured handler.
 *
 * <p>Usage in a BTrace script:
 * <pre>
 * &#64;Injected VibeGuardService guard;
 *
 * &#64;OnMethod(clazz = "com.app.AiService", method = "generate")
 * void onEntry() {
 *     guard.checkCallRate("AiService.generate", 100); // max 100 calls/sec
 * }
 *
 * &#64;OnMethod(clazz = "com.app.AiService", method = "generate",
 *          location = &#64;Location(Kind.RETURN))
 * void onReturn(&#64;Duration long dur) {
 *     guard.checkLatency("AiService.generate", dur, 500_000_000L); // 500ms budget
 * }
 * </pre>
 */
@ServiceDescriptor
public interface VibeGuardService {

  // ==================== Contract checks ====================

  /**
   * Checks that a method's latency does not exceed the budget.
   * Records a violation if {@code durationNanos > budgetNanos}.
   *
   * @param contract contract/method name
   * @param durationNanos actual duration
   * @param budgetNanos maximum allowed duration
   */
  void checkLatency(String contract, long durationNanos, long budgetNanos);

  /**
   * Checks that call rate does not exceed the limit per second.
   * Uses a sliding window to detect bursts.
   *
   * @param contract contract/method name
   * @param maxPerSecond maximum allowed calls per second
   */
  void checkCallRate(String contract, int maxPerSecond);

  /**
   * Asserts a boolean condition. Records a violation if false.
   *
   * @param contract contract name
   * @param condition the condition to check
   * @param message violation message if condition is false
   */
  void assertCondition(String contract, boolean condition, String message);

  /**
   * Checks that a numeric return value is within bounds.
   *
   * @param contract contract name
   * @param value actual value
   * @param min minimum allowed (inclusive)
   * @param max maximum allowed (inclusive)
   */
  void checkRange(String contract, long value, long min, long max);

  /**
   * Checks that a return value is not null. Records a violation if null.
   *
   * @param contract contract name
   * @param value the value to check
   */
  void checkNotNull(String contract, Object value);

  // ==================== Tracking ====================

  /**
   * Tags a method invocation as AI-generated. Used to compare behavior
   * between AI and human code paths.
   *
   * @param contract contract/method name
   * @param durationNanos execution duration
   */
  void trackAiCodePath(String contract, long durationNanos);

  /**
   * Tags a method invocation as human-written (baseline).
   *
   * @param contract contract/method name
   * @param durationNanos execution duration
   */
  void trackHumanCodePath(String contract, long durationNanos);

  // ==================== Reporting ====================

  /** Returns a formatted summary of all contract violations and stats. */
  String getSummary();

  /** Returns total number of contract violations across all contracts. */
  long getTotalViolations();

  /** Returns number of violations for a specific contract. */
  long getViolations(String contract);

  /** Returns total number of contract checks performed. */
  long getTotalChecks();

  /**
   * Returns true if any contract has been violated.
   * Useful in {@code @OnEvent} handlers for alerting.
   */
  boolean hasViolations();

  /** Resets all metrics and violation history. */
  void reset();
}
