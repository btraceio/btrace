package org.openjdk.btrace.llm;

/**
 * Fluent builder for recording an LLM API call.
 *
 * <p>Obtain via {@link LlmTraceService#call(String)}. All setters are optional except
 * {@link #duration(long)} — if omitted, duration defaults to 0.
 *
 * <p>Call {@link #record()} to commit the metrics.
 *
 * <p><strong>Allocation-free:</strong> Instances are pooled per-thread internally,
 * so calling {@code call()} does not allocate on the heap. Safe for hot-path
 * instrumentation. However, the returned reference must not be stored or shared
 * across threads — use it inline and call {@link #record()} immediately.
 *
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
public interface CallRecord {

  /** Sets the provider name (e.g. "openai", "anthropic", "google"). */
  CallRecord provider(String provider);

  /** Sets the number of input/prompt tokens. */
  CallRecord inputTokens(int tokens);

  /** Sets the number of output/completion tokens. */
  CallRecord outputTokens(int tokens);

  /**
   * Sets the number of cache-read input tokens (prompt caching).
   * Anthropic: {@code usage.cache_read_input_tokens}.
   * OpenAI: {@code usage.prompt_tokens_details.cached_tokens}.
   */
  CallRecord cacheReadTokens(int tokens);

  /**
   * Sets the number of cache-creation input tokens.
   * Anthropic: {@code usage.cache_creation_input_tokens}.
   */
  CallRecord cacheCreationTokens(int tokens);

  /** Marks this call as a streaming response. */
  CallRecord streaming();

  /**
   * Sets the time-to-first-token for streaming calls.
   * Only meaningful when {@link #streaming()} is set.
   *
   * @param nanos time from request start to first token, in nanoseconds
   */
  CallRecord timeToFirstToken(long nanos);

  /**
   * Sets the total call duration.
   *
   * @param nanos duration in nanoseconds (typically from {@code @Duration})
   */
  CallRecord duration(long nanos);

  /**
   * Commits this call record to the trace service.
   * Must be called exactly once to record the metrics.
   */
  void record();
}
