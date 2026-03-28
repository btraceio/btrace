package org.openjdk.btrace.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.btrace.core.extensions.Extension;

/**
 * Thread-safe implementation of LLM call tracing and aggregation.
 *
 * <p>Maintains per-model statistics using lock-free counters. No external dependencies —
 * all aggregation is done with atomics and simple math.
 */
public final class LlmTraceServiceImpl extends Extension implements LlmTraceService {

  private final Map<String, ModelStats> modelStats = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> toolUseCounts = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

  @Override
  public void recordCall(String model, int inputTokens, int outputTokens, long durationNanos) {
    recordCall("unknown", model, inputTokens, outputTokens, durationNanos);
  }

  @Override
  public void recordCall(String provider, String model, int inputTokens, int outputTokens,
      long durationNanos) {
    ModelStats stats = getOrCreateStats(model);
    stats.provider = provider;
    stats.calls.incrementAndGet();
    stats.inputTokens.addAndGet(inputTokens);
    stats.outputTokens.addAndGet(outputTokens);
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void recordStreamingCall(String model, int inputTokens, int outputTokens,
      long durationNanos, long timeToFirstTokenNanos) {
    ModelStats stats = getOrCreateStats(model);
    stats.calls.incrementAndGet();
    stats.streamingCalls.incrementAndGet();
    stats.inputTokens.addAndGet(inputTokens);
    stats.outputTokens.addAndGet(outputTokens);
    stats.totalDurationNanos.addAndGet(durationNanos);
    stats.totalTimeToFirstToken.addAndGet(timeToFirstTokenNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void recordToolUse(String model, String toolName) {
    String key = model + "::" + toolName;
    toolUseCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    getOrCreateStats(model).toolCalls.incrementAndGet();
  }

  @Override
  public void recordError(String model, String errorType, long durationNanos) {
    String key = model + "::" + errorType;
    errorCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    getOrCreateStats(model).errors.incrementAndGet();
  }

  @Override
  public String getSummary() {
    if (modelStats.isEmpty()) {
      return "No LLM calls recorded.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== LLM Trace Summary ===\n\n");

    long totalCalls = 0;
    long totalIn = 0;
    long totalOut = 0;
    double totalCost = 0;

    for (Map.Entry<String, ModelStats> entry : modelStats.entrySet()) {
      String model = entry.getKey();
      ModelStats s = entry.getValue();
      long calls = s.calls.get();
      long inTok = s.inputTokens.get();
      long outTok = s.outputTokens.get();

      totalCalls += calls;
      totalIn += inTok;
      totalOut += outTok;

      sb.append("Model: ").append(model);
      if (!"unknown".equals(s.provider)) {
        sb.append(" (").append(s.provider).append(")");
      }
      sb.append("\n");
      sb.append("  Calls: ").append(calls);
      long streaming = s.streamingCalls.get();
      if (streaming > 0) {
        sb.append(" (").append(streaming).append(" streaming)");
      }
      sb.append("\n");
      sb.append("  Tokens: ").append(inTok).append(" in / ").append(outTok).append(" out");
      if (calls > 0) {
        sb.append(" (avg ").append(inTok / calls).append("/").append(outTok / calls).append(")");
      }
      sb.append("\n");

      if (calls > 0) {
        long avgMs = (s.totalDurationNanos.get() / calls) / 1_000_000;
        long minMs = s.minDurationNanos.get() / 1_000_000;
        long maxMs = s.maxDurationNanos.get() / 1_000_000;
        sb.append("  Latency: avg ").append(avgMs).append("ms");
        sb.append(", min ").append(minMs).append("ms");
        sb.append(", max ").append(maxMs).append("ms\n");
      }

      if (streaming > 0) {
        long avgTtft = (s.totalTimeToFirstToken.get() / streaming) / 1_000_000;
        sb.append("  TTFT (avg): ").append(avgTtft).append("ms\n");
      }

      long toolCalls = s.toolCalls.get();
      if (toolCalls > 0) {
        sb.append("  Tool calls: ").append(toolCalls).append("\n");
      }

      long errors = s.errors.get();
      if (errors > 0) {
        sb.append("  Errors: ").append(errors).append("\n");
      }

      double cost = estimateCost(model, inTok, outTok);
      if (cost >= 0) {
        totalCost += cost;
        sb.append("  Est. cost: $").append(formatCost(cost)).append("\n");
      }
      sb.append("\n");
    }

    // Tool use breakdown
    if (!toolUseCounts.isEmpty()) {
      sb.append("--- Tool Use ---\n");
      for (Map.Entry<String, AtomicLong> entry : toolUseCounts.entrySet()) {
        sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get())
            .append("\n");
      }
      sb.append("\n");
    }

    // Error breakdown
    if (!errorCounts.isEmpty()) {
      sb.append("--- Errors ---\n");
      for (Map.Entry<String, AtomicLong> entry : errorCounts.entrySet()) {
        sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get())
            .append("\n");
      }
      sb.append("\n");
    }

    sb.append("--- Totals ---\n");
    sb.append("  Calls: ").append(totalCalls).append("\n");
    sb.append("  Tokens: ").append(totalIn).append(" in / ").append(totalOut).append(" out\n");
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

    return model + ": " + calls + " calls, "
        + inTok + "/" + outTok + " tokens (in/out), "
        + "avg " + avgMs + "ms";
  }

  @Override
  public double getEstimatedCostUsd() {
    double total = 0;
    boolean anyKnown = false;
    for (Map.Entry<String, ModelStats> entry : modelStats.entrySet()) {
      double cost = estimateCost(entry.getKey(),
          entry.getValue().inputTokens.get(),
          entry.getValue().outputTokens.get());
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
  public void reset() {
    modelStats.clear();
    toolUseCounts.clear();
    errorCounts.clear();
  }

  @Override
  public void close() {
    // Print final summary on detach
    String summary = getSummary();
    if (!"No LLM calls recorded.".equals(summary)) {
      getContext().send(summary);
    }
  }

  // --- Internals ---

  private ModelStats getOrCreateStats(String model) {
    return modelStats.computeIfAbsent(model, k -> new ModelStats());
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
   * Estimates cost in USD based on a built-in pricing table.
   * Prices are per 1M tokens. Returns -1 for unknown models.
   */
  static double estimateCost(String model, long inputTokens, long outputTokens) {
    double inputPer1M = -1;
    double outputPer1M = -1;

    // Normalize model name for matching
    String m = model.toLowerCase();

    // Anthropic Claude models
    if (m.contains("claude") && m.contains("opus")) {
      inputPer1M = 15.0;
      outputPer1M = 75.0;
    } else if (m.contains("claude") && m.contains("sonnet")) {
      inputPer1M = 3.0;
      outputPer1M = 15.0;
    } else if (m.contains("claude") && m.contains("haiku")) {
      inputPer1M = 0.80;
      outputPer1M = 4.0;
    }
    // OpenAI GPT models
    else if (m.contains("gpt-4o-mini")) {
      inputPer1M = 0.15;
      outputPer1M = 0.60;
    } else if (m.contains("gpt-4o")) {
      inputPer1M = 2.50;
      outputPer1M = 10.0;
    } else if (m.contains("gpt-4") && m.contains("turbo")) {
      inputPer1M = 10.0;
      outputPer1M = 30.0;
    } else if (m.contains("gpt-4")) {
      inputPer1M = 30.0;
      outputPer1M = 60.0;
    } else if (m.contains("gpt-3.5")) {
      inputPer1M = 0.50;
      outputPer1M = 1.50;
    } else if (m.contains("o1-mini")) {
      inputPer1M = 3.0;
      outputPer1M = 12.0;
    } else if (m.contains("o1")) {
      inputPer1M = 15.0;
      outputPer1M = 60.0;
    }
    // Google Gemini
    else if (m.contains("gemini") && m.contains("pro")) {
      inputPer1M = 1.25;
      outputPer1M = 5.0;
    } else if (m.contains("gemini") && m.contains("flash")) {
      inputPer1M = 0.075;
      outputPer1M = 0.30;
    }

    if (inputPer1M < 0) {
      return -1;
    }
    return (inputTokens * inputPer1M / 1_000_000.0) + (outputTokens * outputPer1M / 1_000_000.0);
  }

  private static String formatCost(double cost) {
    if (cost < 0.01) {
      return String.format("%.4f", cost);
    }
    return String.format("%.2f", cost);
  }

  /**
   * Lock-free per-model statistics.
   */
  static final class ModelStats {
    volatile String provider = "unknown";
    final AtomicLong calls = new AtomicLong();
    final AtomicLong streamingCalls = new AtomicLong();
    final AtomicLong inputTokens = new AtomicLong();
    final AtomicLong outputTokens = new AtomicLong();
    final AtomicLong totalDurationNanos = new AtomicLong();
    final AtomicLong minDurationNanos = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxDurationNanos = new AtomicLong(0);
    final AtomicLong totalTimeToFirstToken = new AtomicLong();
    final AtomicLong toolCalls = new AtomicLong();
    final AtomicLong errors = new AtomicLong();
  }
}
