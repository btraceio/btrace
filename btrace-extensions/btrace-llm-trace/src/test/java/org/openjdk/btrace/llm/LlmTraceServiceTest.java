package org.openjdk.btrace.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmTraceServiceTest {

  private LlmTraceServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new LlmTraceServiceImpl();
  }

  @Test
  void recordCallUpdatesTotals() {
    service.recordCall("claude-sonnet-4-20250514", 1000, 200, 500_000_000L);
    service.recordCall("claude-sonnet-4-20250514", 800, 300, 400_000_000L);

    assertEquals(2, service.getTotalCalls());
    assertEquals(1800, service.getTotalInputTokens());
    assertEquals(500, service.getTotalOutputTokens());
  }

  @Test
  void recordCallWithProvider() {
    service.recordCall("anthropic", "claude-haiku-4-5", 500, 100, 200_000_000L);

    assertEquals(1, service.getTotalCalls());
    String summary = service.getSummary();
    assertTrue(summary.contains("anthropic"), "Summary should contain provider");
    assertTrue(summary.contains("claude-haiku"), "Summary should contain model");
  }

  @Test
  void recordStreamingCallTracksTimeToFirstToken() {
    service.recordStreamingCall("gpt-4o", 2000, 500, 3_000_000_000L, 200_000_000L);

    assertEquals(1, service.getTotalCalls());
    String summary = service.getSummary();
    assertTrue(summary.contains("streaming"), "Summary should mention streaming");
    assertTrue(summary.contains("TTFT"), "Summary should show time-to-first-token");
  }

  @Test
  void recordToolUse() {
    service.recordCall("claude-sonnet-4-20250514", 1000, 200, 500_000_000L);
    service.recordToolUse("claude-sonnet-4-20250514", "search_web");
    service.recordToolUse("claude-sonnet-4-20250514", "search_web");
    service.recordToolUse("claude-sonnet-4-20250514", "run_code");

    String summary = service.getSummary();
    assertTrue(summary.contains("Tool calls: 3"), "Summary should show tool call count");
    assertTrue(summary.contains("Tool Use"), "Summary should have tool use section");
    assertTrue(summary.contains("search_web"), "Summary should list tool names");
  }

  @Test
  void recordError() {
    service.recordCall("gpt-4o", 1000, 0, 100_000_000L);
    service.recordError("gpt-4o", "RateLimitException", 50_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("Errors: 1"), "Summary should show error count");
    assertTrue(summary.contains("RateLimitException"), "Summary should show error type");
  }

  @Test
  void getModelSummaryForUnknownModel() {
    String result = service.getModelSummary("nonexistent");
    assertTrue(result.contains("No data"), "Should indicate no data");
  }

  @Test
  void getModelSummaryForKnownModel() {
    service.recordCall("gpt-4o", 1000, 200, 500_000_000L);
    String result = service.getModelSummary("gpt-4o");
    assertTrue(result.contains("1 calls"), "Should show call count");
    assertTrue(result.contains("1000/200"), "Should show token counts");
    assertTrue(result.contains("500ms"), "Should show avg latency");
  }

  @Test
  void estimateCostClaude() {
    // Claude Sonnet: $3/1M input, $15/1M output
    double cost = LlmTraceServiceImpl.estimateCost("claude-sonnet-4-20250514", 1_000_000, 100_000);
    assertEquals(3.0 + 1.5, cost, 0.01);
  }

  @Test
  void estimateCostGpt4o() {
    // GPT-4o: $2.50/1M input, $10/1M output
    double cost = LlmTraceServiceImpl.estimateCost("gpt-4o", 1_000_000, 1_000_000);
    assertEquals(2.50 + 10.0, cost, 0.01);
  }

  @Test
  void estimateCostUnknownModel() {
    double cost = LlmTraceServiceImpl.estimateCost("my-custom-model", 1000, 1000);
    assertEquals(-1, cost);
  }

  @Test
  void estimatedCostUsdAcrossModels() {
    service.recordCall("claude-sonnet-4-20250514", 1_000_000, 0, 1_000_000_000L);
    service.recordCall("gpt-4o", 1_000_000, 0, 1_000_000_000L);

    // Claude Sonnet input: $3.00, GPT-4o input: $2.50
    double cost = service.getEstimatedCostUsd();
    assertEquals(5.50, cost, 0.01);
  }

  @Test
  void estimatedCostUsdReturnsNegativeWhenAllUnknown() {
    service.recordCall("my-custom-model", 1000, 500, 100_000_000L);
    assertEquals(-1, service.getEstimatedCostUsd());
  }

  @Test
  void resetClearsEverything() {
    service.recordCall("gpt-4o", 1000, 200, 500_000_000L);
    service.recordToolUse("gpt-4o", "search");
    service.recordError("gpt-4o", "Timeout", 100_000_000L);

    service.reset();

    assertEquals(0, service.getTotalCalls());
    assertEquals(0, service.getTotalInputTokens());
    assertEquals(0, service.getTotalOutputTokens());
    assertEquals("No LLM calls recorded.", service.getSummary());
  }

  @Test
  void latencyMinMax() {
    service.recordCall("gpt-4o", 100, 50, 100_000_000L); // 100ms
    service.recordCall("gpt-4o", 100, 50, 500_000_000L); // 500ms
    service.recordCall("gpt-4o", 100, 50, 200_000_000L); // 200ms

    String summary = service.getSummary();
    assertTrue(summary.contains("min 100ms"), "Should track min latency");
    assertTrue(summary.contains("max 500ms"), "Should track max latency");
  }

  @Test
  void summaryIsEmptyWhenNoCalls() {
    assertEquals("No LLM calls recorded.", service.getSummary());
  }

  @Test
  void concurrentRecording() throws InterruptedException {
    int threads = 8;
    int callsPerThread = 1000;
    Thread[] workers = new Thread[threads];

    for (int t = 0; t < threads; t++) {
      workers[t] = new Thread(() -> {
        for (int i = 0; i < callsPerThread; i++) {
          service.recordCall("gpt-4o", 100, 50, 10_000_000L);
        }
      });
      workers[t].start();
    }

    for (Thread w : workers) {
      w.join();
    }

    assertEquals(threads * callsPerThread, service.getTotalCalls());
    assertEquals(threads * callsPerThread * 100L, service.getTotalInputTokens());
    assertEquals(threads * callsPerThread * 50L, service.getTotalOutputTokens());
  }
}
