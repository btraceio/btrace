package org.openjdk.btrace.llm;

import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/**
 * LLM inference observability service for BTrace scripts.
 *
 * <p>Records LLM API call metrics — token counts, latencies, costs — and provides
 * aggregated statistics. Scripts use {@code @OnMethod} to intercept LLM SDK calls
 * and feed data into this service.
 *
 * <p>Thread-safe. All methods can be called concurrently from instrumented threads.
 *
 * <h3>Quick start — latency only (works with any SDK)</h3>
 * <pre>
 * &#64;Injected private static LlmTraceService llm;
 *
 * &#64;OnMethod(clazz = "+dev.langchain4j.model.chat.ChatLanguageModel",
 *           method = "generate", location = &#64;Location(Kind.RETURN))
 * public static void onChat(&#64;ProbeClassName String cls, &#64;Duration long dur) {
 *     llm.recordCall(cls, dur);
 * }
 * </pre>
 *
 * <h3>Full metrics with fluent builder</h3>
 * <pre>
 * llm.call("claude-sonnet-4-20250514")
 *     .provider("anthropic")
 *     .inputTokens(1500)
 *     .outputTokens(300)
 *     .cacheReadTokens(800)
 *     .duration(durationNanos)
 *     .record();
 * </pre>
 */
@ServiceDescriptor
public interface LlmTraceService {

  // ==================== Simple recording ====================

  /**
   * Records an LLM call with only latency (no token info).
   * Use this when token counts aren't easily extractable.
   *
   * @param model model identifier or class name
   * @param durationNanos call duration in nanoseconds
   */
  void recordCall(String model, long durationNanos);

  /**
   * Records an LLM call with token counts.
   *
   * @param model model identifier (e.g. "gpt-4o", "claude-sonnet-4-20250514")
   * @param inputTokens number of input/prompt tokens
   * @param outputTokens number of output/completion tokens
   * @param durationNanos call duration in nanoseconds
   */
  void recordCall(String model, int inputTokens, int outputTokens, long durationNanos);

  // ==================== Fluent builder ====================

  /**
   * Starts a fluent call record for the given model.
   *
   * <p>The returned builder is allocation-free (ThreadLocal-pooled). It is safe
   * to use on hot paths. The builder must be used inline on the calling thread
   * and {@link CallRecord#record()} must be called before the next {@code call()}.
   *
   * <pre>
   * llm.call("claude-sonnet-4-20250514")
   *     .provider("anthropic")
   *     .inputTokens(1500)
   *     .outputTokens(300)
   *     .cacheReadTokens(800)
   *     .streaming()
   *     .timeToFirstToken(200_000_000L)
   *     .duration(durationNanos)
   *     .record();
   * </pre>
   *
   * @param model model identifier
   * @return a call record builder (thread-local, do not store)
   */
  CallRecord call(String model);

  // ==================== Specialized recording ====================

  /**
   * Records an embedding API call.
   *
   * @param model embedding model identifier (e.g. "text-embedding-3-small")
   * @param tokenCount number of tokens embedded
   * @param durationNanos call duration in nanoseconds
   */
  void recordEmbedding(String model, int tokenCount, long durationNanos);

  /**
   * Records a tool/function call invocation by the LLM.
   *
   * @param model model that made the tool call
   * @param toolName name of the tool/function called
   */
  void recordToolUse(String model, String toolName);

  /**
   * Records a failed LLM API call.
   *
   * @param model model identifier
   * @param errorType error class name or HTTP status code
   * @param durationNanos call duration before failure
   */
  void recordError(String model, String errorType, long durationNanos);

  // ==================== Reporting ====================

  /**
   * Returns a formatted summary of all recorded metrics.
   * Includes per-model token counts, latency stats, cost estimates,
   * cache hit rates, tool use, and error breakdown.
   *
   * @return multi-line summary string
   */
  String getSummary();

  /**
   * Returns a one-line summary for a specific model.
   *
   * @param model model identifier
   * @return summary string, or "No data" if none recorded
   */
  String getModelSummary(String model);

  /**
   * Returns total estimated cost across all models in USD.
   * Uses built-in pricing table. Returns -1 if all models are unknown.
   *
   * @return estimated cost in USD, or -1 if pricing unknown
   */
  double getEstimatedCostUsd();

  /** Returns total number of chat completion calls recorded. */
  long getTotalCalls();

  /** Returns total input tokens across all calls. */
  long getTotalInputTokens();

  /** Returns total output tokens across all calls. */
  long getTotalOutputTokens();

  /** Returns total number of embedding calls recorded. */
  long getTotalEmbeddingCalls();

  /** Resets all collected metrics. */
  void reset();
}
