package org.openjdk.btrace.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.btrace.core.extensions.Extension;

/**
 * Thread-safe LLM call tracing with lock-free per-model statistics.
 * Zero external dependencies.
 */
public final class LlmTraceServiceImpl extends Extension implements LlmTraceService {

  private final Map<String, ModelStats> modelStats = new ConcurrentHashMap<>();
  private final Map<String, ModelStats> embeddingStats = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> toolUseCounts = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

  // ==================== Simple recording ====================

  @Override
  public void recordCall(String model, long durationNanos) {
    ModelStats stats = getOrCreate(modelStats, model);
    stats.calls.incrementAndGet();
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void recordCall(String model, int inputTokens, int outputTokens, long durationNanos) {
    ModelStats stats = getOrCreate(modelStats, model);
    stats.calls.incrementAndGet();
    stats.inputTokens.addAndGet(inputTokens);
    stats.outputTokens.addAndGet(outputTokens);
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  // ==================== Fluent builder ====================

  /**
   * ThreadLocal-pooled builder — one CallRecordImpl per thread, reused across calls.
   * Eliminates per-call heap allocation, making the builder safe for hot paths.
   */
  private final ThreadLocal<CallRecordImpl> callRecordPool =
      ThreadLocal.withInitial(CallRecordImpl::new);

  @Override
  public CallRecord call(String model) {
    return callRecordPool.get().reset(this, model);
  }

  void commitCallRecord(CallRecordImpl rec) {
    ModelStats stats = getOrCreate(modelStats, rec.model);
    if (rec.providerVal != null) {
      stats.provider = rec.providerVal;
    }
    stats.calls.incrementAndGet();
    stats.inputTokens.addAndGet(rec.inputTok);
    stats.outputTokens.addAndGet(rec.outputTok);
    stats.cacheReadTokens.addAndGet(rec.cacheReadTok);
    stats.cacheCreationTokens.addAndGet(rec.cacheCreateTok);
    stats.totalDurationNanos.addAndGet(rec.durationVal);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, rec.durationVal);
    if (rec.isStreaming) {
      stats.streamingCalls.incrementAndGet();
      stats.totalTimeToFirstToken.addAndGet(rec.ttftVal);
    }
  }

  // ==================== Specialized recording ====================

  @Override
  public void recordEmbedding(String model, int tokenCount, long durationNanos) {
    ModelStats stats = getOrCreate(embeddingStats, model);
    stats.calls.incrementAndGet();
    stats.inputTokens.addAndGet(tokenCount);
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void recordToolUse(String model, String toolName) {
    String key = model + "::" + toolName;
    toolUseCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    getOrCreate(modelStats, model).toolCalls.incrementAndGet();
  }

  @Override
  public void recordError(String model, String errorType, long durationNanos) {
    String key = model + "::" + errorType;
    errorCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    getOrCreate(modelStats, model).errors.incrementAndGet();
  }

  // ==================== Reporting ====================

  @Override
  public String getSummary() {
    if (modelStats.isEmpty() && embeddingStats.isEmpty()) {
      return "No LLM calls recorded.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== LLM Trace Summary ===\n\n");

    long totalCalls = 0;
    long totalIn = 0;
    long totalOut = 0;
    double totalCost = 0;

    // Chat completions
    for (Map.Entry<String, ModelStats> entry : modelStats.entrySet()) {
      String model = entry.getKey();
      ModelStats s = entry.getValue();
      long calls = s.calls.get();
      long inTok = s.inputTokens.get();
      long outTok = s.outputTokens.get();
      long cacheRead = s.cacheReadTokens.get();
      long cacheCreate = s.cacheCreationTokens.get();

      totalCalls += calls;
      totalIn += inTok;
      totalOut += outTok;

      sb.append("Model: ").append(model);
      if (!"unknown".equals(s.provider)) {
        sb.append(" (").append(s.provider).append(")");
      }
      sb.append("\n");

      // Calls
      sb.append("  Calls: ").append(calls);
      long streaming = s.streamingCalls.get();
      if (streaming > 0) {
        sb.append(" (").append(streaming).append(" streaming)");
      }
      sb.append("\n");

      // Tokens
      if (inTok > 0 || outTok > 0) {
        sb.append("  Tokens: ").append(inTok).append(" in / ").append(outTok).append(" out");
        if (calls > 0) {
          sb.append(" (avg ").append(inTok / calls).append("/").append(outTok / calls).append(")");
        }
        sb.append("\n");
      }

      // Cache
      if (cacheRead > 0 || cacheCreate > 0) {
        sb.append("  Cache: ");
        if (cacheRead > 0) {
          sb.append(cacheRead).append(" read");
          // Show cache hit rate relative to total input
          if (inTok > 0) {
            long hitPct = (cacheRead * 100) / inTok;
            sb.append(" (").append(hitPct).append("% hit)");
          }
        }
        if (cacheCreate > 0) {
          if (cacheRead > 0) sb.append(", ");
          sb.append(cacheCreate).append(" created");
        }
        sb.append("\n");
      }

      // Latency
      if (calls > 0) {
        long avgMs = (s.totalDurationNanos.get() / calls) / 1_000_000;
        long minMs = s.minDurationNanos.get() / 1_000_000;
        long maxMs = s.maxDurationNanos.get() / 1_000_000;
        sb.append("  Latency: avg ").append(avgMs).append("ms");
        sb.append(", min ").append(minMs).append("ms");
        sb.append(", max ").append(maxMs).append("ms\n");
      }

      // TTFT
      if (streaming > 0) {
        long avgTtft = (s.totalTimeToFirstToken.get() / streaming) / 1_000_000;
        sb.append("  TTFT (avg): ").append(avgTtft).append("ms\n");
      }

      // Tool calls
      long tc = s.toolCalls.get();
      if (tc > 0) {
        sb.append("  Tool calls: ").append(tc).append("\n");
      }

      // Errors
      long errs = s.errors.get();
      if (errs > 0) {
        sb.append("  Errors: ").append(errs).append("\n");
      }

      // Cost
      double cost = estimateCost(model, inTok, outTok, cacheRead);
      if (cost >= 0) {
        totalCost += cost;
        sb.append("  Est. cost: $").append(formatCost(cost));
        if (cacheRead > 0) {
          double uncachedCost = estimateCost(model, inTok + cacheRead, outTok, 0);
          if (uncachedCost > cost) {
            sb.append(" (saved $").append(formatCost(uncachedCost - cost)).append(" via cache)");
          }
        }
        sb.append("\n");
      }
      sb.append("\n");
    }

    // Embeddings
    if (!embeddingStats.isEmpty()) {
      sb.append("--- Embeddings ---\n");
      for (Map.Entry<String, ModelStats> entry : embeddingStats.entrySet()) {
        ModelStats s = entry.getValue();
        long calls = s.calls.get();
        long tokens = s.inputTokens.get();
        long avgMs = calls > 0 ? (s.totalDurationNanos.get() / calls) / 1_000_000 : 0;
        sb.append("  ").append(entry.getKey()).append(": ")
            .append(calls).append(" calls, ")
            .append(tokens).append(" tokens, avg ")
            .append(avgMs).append("ms\n");
      }
      sb.append("\n");
    }

    // Tool use breakdown
    if (!toolUseCounts.isEmpty()) {
      sb.append("--- Tool Use ---\n");
      for (Map.Entry<String, AtomicLong> entry : toolUseCounts.entrySet()) {
        sb.append("  ").append(entry.getKey()).append(": ")
            .append(entry.getValue().get()).append("\n");
      }
      sb.append("\n");
    }

    // Error breakdown
    if (!errorCounts.isEmpty()) {
      sb.append("--- Errors ---\n");
      for (Map.Entry<String, AtomicLong> entry : errorCounts.entrySet()) {
        sb.append("  ").append(entry.getKey()).append(": ")
            .append(entry.getValue().get()).append("\n");
      }
      sb.append("\n");
    }

    sb.append("--- Totals ---\n");
    sb.append("  Calls: ").append(totalCalls).append("\n");
    if (totalIn > 0 || totalOut > 0) {
      sb.append("  Tokens: ").append(totalIn).append(" in / ").append(totalOut).append(" out\n");
    }
    if (totalCost > 0) {
      sb.append("  Est. total cost: $").append(formatCost(totalCost)).append("\n");
    }

    return sb.toString();
  }

  @Override
  public String getModelSummary(String model) {
    ModelStats s = modelStats.get(model);
    if (s == null) {
      return "No data for model: " + model;
    }
    long calls = s.calls.get();
    long inTok = s.inputTokens.get();
    long outTok = s.outputTokens.get();
    long avgMs = calls > 0 ? (s.totalDurationNanos.get() / calls) / 1_000_000 : 0;

    StringBuilder sb = new StringBuilder();
    sb.append(model).append(": ").append(calls).append(" calls");
    if (inTok > 0 || outTok > 0) {
      sb.append(", ").append(inTok).append("/").append(outTok).append(" tokens (in/out)");
    }
    sb.append(", avg ").append(avgMs).append("ms");
    return sb.toString();
  }

  @Override
  public double getEstimatedCostUsd() {
    double total = 0;
    boolean anyKnown = false;
    for (Map.Entry<String, ModelStats> entry : modelStats.entrySet()) {
      ModelStats s = entry.getValue();
      double cost = estimateCost(entry.getKey(),
          s.inputTokens.get(), s.outputTokens.get(), s.cacheReadTokens.get());
      if (cost >= 0) {
        total += cost;
        anyKnown = true;
      }
    }
    return anyKnown ? total : -1;
  }

  @Override
  public long getTotalCalls() {
    long total = 0;
    for (ModelStats s : modelStats.values()) {
      total += s.calls.get();
    }
    return total;
  }

  @Override
  public long getTotalInputTokens() {
    long total = 0;
    for (ModelStats s : modelStats.values()) {
      total += s.inputTokens.get();
    }
    return total;
  }

  @Override
  public long getTotalOutputTokens() {
    long total = 0;
    for (ModelStats s : modelStats.values()) {
      total += s.outputTokens.get();
    }
    return total;
  }

  @Override
  public long getTotalEmbeddingCalls() {
    long total = 0;
    for (ModelStats s : embeddingStats.values()) {
      total += s.calls.get();
    }
    return total;
  }

  @Override
  public void reset() {
    modelStats.clear();
    embeddingStats.clear();
    toolUseCounts.clear();
    errorCounts.clear();
  }

  @Override
  public void close() {
    String summary = getSummary();
    if (!"No LLM calls recorded.".equals(summary)) {
      getContext().send(summary);
    }
  }

  // ==================== Internals ====================

  private static ModelStats getOrCreate(Map<String, ModelStats> map, String key) {
    return map.computeIfAbsent(key, k -> new ModelStats());
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

  /**
   * Estimates cost in USD. Cache-read tokens are priced at ~10% of input rate
   * for models that support caching.
   */
  static double estimateCost(String model, long inputTokens, long outputTokens,
      long cacheReadTokens) {
    double inputPer1M = -1;
    double outputPer1M = -1;
    double cacheReadPer1M = -1;

    String m = model.toLowerCase();

    // Anthropic Claude
    if (m.contains("claude") && m.contains("opus")) {
      inputPer1M = 15.0;   outputPer1M = 75.0;  cacheReadPer1M = 1.50;
    } else if (m.contains("claude") && m.contains("sonnet")) {
      inputPer1M = 3.0;    outputPer1M = 15.0;   cacheReadPer1M = 0.30;
    } else if (m.contains("claude") && m.contains("haiku")) {
      inputPer1M = 0.80;   outputPer1M = 4.0;    cacheReadPer1M = 0.08;
    }
    // OpenAI GPT
    else if (m.contains("gpt-4o-mini")) {
      inputPer1M = 0.15;   outputPer1M = 0.60;   cacheReadPer1M = 0.075;
    } else if (m.contains("gpt-4o")) {
      inputPer1M = 2.50;   outputPer1M = 10.0;   cacheReadPer1M = 1.25;
    } else if (m.contains("gpt-4") && m.contains("turbo")) {
      inputPer1M = 10.0;   outputPer1M = 30.0;
    } else if (m.contains("gpt-4")) {
      inputPer1M = 30.0;   outputPer1M = 60.0;
    } else if (m.contains("gpt-3.5")) {
      inputPer1M = 0.50;   outputPer1M = 1.50;
    } else if (m.contains("o1-mini")) {
      inputPer1M = 3.0;    outputPer1M = 12.0;   cacheReadPer1M = 1.50;
    } else if (m.contains("o1")) {
      inputPer1M = 15.0;   outputPer1M = 60.0;   cacheReadPer1M = 7.50;
    }
    // Google Gemini
    else if (m.contains("gemini") && m.contains("pro")) {
      inputPer1M = 1.25;   outputPer1M = 5.0;
    } else if (m.contains("gemini") && m.contains("flash")) {
      inputPer1M = 0.075;  outputPer1M = 0.30;
    }

    if (inputPer1M < 0) {
      return -1;
    }

    double cost = (inputTokens * inputPer1M / 1_000_000.0)
        + (outputTokens * outputPer1M / 1_000_000.0);
    if (cacheReadTokens > 0 && cacheReadPer1M > 0) {
      cost += (cacheReadTokens * cacheReadPer1M / 1_000_000.0);
    }
    return cost;
  }

  static String formatCost(double cost) {
    if (cost < 0.01) {
      return String.format("%.4f", cost);
    }
    return String.format("%.2f", cost);
  }

  /** Lock-free per-model statistics. */
  static final class ModelStats {
    volatile String provider = "unknown";
    final AtomicLong calls = new AtomicLong();
    final AtomicLong streamingCalls = new AtomicLong();
    final AtomicLong inputTokens = new AtomicLong();
    final AtomicLong outputTokens = new AtomicLong();
    final AtomicLong cacheReadTokens = new AtomicLong();
    final AtomicLong cacheCreationTokens = new AtomicLong();
    final AtomicLong totalDurationNanos = new AtomicLong();
    final AtomicLong minDurationNanos = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxDurationNanos = new AtomicLong(0);
    final AtomicLong totalTimeToFirstToken = new AtomicLong();
    final AtomicLong toolCalls = new AtomicLong();
    final AtomicLong errors = new AtomicLong();
  }
}
