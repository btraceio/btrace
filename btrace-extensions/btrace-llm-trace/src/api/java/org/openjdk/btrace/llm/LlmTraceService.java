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
 * <p><b>Usage in a BTrace script:</b>
 * <pre>
 * &#64;Injected
 * private static LlmTraceService llm;
 *
 * &#64;OnMethod(clazz = "com.anthropic.client.AnthropicClient",
 *           method = "createMessage",
 *           location = &#64;Location(Kind.RETURN))
 * public static void onMessage(&#64;Duration long duration,
 *                               &#64;Return Object response) {
 *     llm.recordCall("claude-sonnet-4-20250514", 1500, 300, duration);
 * }
 * </pre>
 */
@ServiceDescriptor
public interface LlmTraceService {

  /**
   * Records an LLM API call.
   *
   * @param model model identifier (e.g. "gpt-4", "claude-sonnet-4-20250514")
   * @param inputTokens number of input/prompt tokens
   * @param outputTokens number of output/completion tokens
   * @param durationNanos call duration in nanoseconds
   */
  void recordCall(String model, int inputTokens, int outputTokens, long durationNanos);

  /**
   * Records an LLM API call with provider info.
   *
   * @param provider provider name (e.g. "openai", "anthropic", "ollama")
   * @param model model identifier
   * @param inputTokens number of input/prompt tokens
   * @param outputTokens number of output/completion tokens
   * @param durationNanos call duration in nanoseconds
   */
  void recordCall(String provider, String model, int inputTokens, int outputTokens,
      long durationNanos);

  /**
   * Records a streaming LLM call completion.
   *
   * @param model model identifier
   * @param inputTokens input tokens
   * @param outputTokens output tokens
   * @param durationNanos total stream duration (first token to last)
   * @param timeToFirstTokenNanos time from request to first token
   */
  void recordStreamingCall(String model, int inputTokens, int outputTokens,
      long durationNanos, long timeToFirstTokenNanos);

  /**
   * Records a tool/function call made by the LLM.
   *
   * @param model model that made the tool call
   * @param toolName name of the tool/function called
   */
  void recordToolUse(String model, String toolName);

  /**
   * Records a failed LLM API call.
   *
   * @param model model identifier
   * @param errorType error class name or HTTP status
   * @param durationNanos call duration before failure
   */
  void recordError(String model, String errorType, long durationNanos);

  /**
   * Returns a formatted summary of all recorded metrics.
   * Includes per-model token counts, latency percentiles, estimated costs.
   *
   * @return multi-line summary string
   */
  String getSummary();

  /**
   * Returns a formatted summary for a specific model.
   *
   * @param model model identifier
   * @return summary string for that model, or "No data" if none recorded
   */
  String getModelSummary(String model);

  /**
   * Returns total estimated cost across all models in USD.
   * Uses built-in pricing table; returns -1 if model pricing is unknown.
   *
   * @return estimated cost in USD, or -1 if unknown
   */
  double getEstimatedCostUsd();

  /**
   * Returns total number of calls recorded.
   *
   * @return call count
   */
  long getTotalCalls();

  /**
   * Returns total input tokens across all calls.
   *
   * @return total input tokens
   */
  long getTotalInputTokens();

  /**
   * Returns total output tokens across all calls.
   *
   * @return total output tokens
   */
  long getTotalOutputTokens();

  /**
   * Resets all collected metrics.
   */
  void reset();
}
