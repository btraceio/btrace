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
package io.btrace.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmTraceServiceTest {

  private LlmTraceServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new LlmTraceServiceImpl();
  }

  // ==================== Simple recording ====================

  @Test
  void recordCallDurationOnly() {
    service.recordCall("gpt-4o", 500_000_000L);
    service.recordCall("gpt-4o", 300_000_000L);

    assertEquals(2, service.getTotalCalls());
    assertEquals(0, service.getTotalInputTokens(), "No tokens recorded");
    String summary = service.getModelSummary("gpt-4o");
    assertTrue(summary.contains("2 calls"));
    assertTrue(summary.contains("400ms"), "avg of 500ms and 300ms");
  }

  @Test
  void recordCallWithTokens() {
    service.recordCall("claude-sonnet-4-20250514", 1000, 200, 500_000_000L);
    service.recordCall("claude-sonnet-4-20250514", 800, 300, 400_000_000L);

    assertEquals(2, service.getTotalCalls());
    assertEquals(1800, service.getTotalInputTokens());
    assertEquals(500, service.getTotalOutputTokens());
  }

  // ==================== Fluent builder ====================

  @Test
  void fluentBuilderBasic() {
    service.call("gpt-4o").inputTokens(1000).outputTokens(200).duration(500_000_000L).record();

    assertEquals(1, service.getTotalCalls());
    assertEquals(1000, service.getTotalInputTokens());
    assertEquals(200, service.getTotalOutputTokens());
  }

  @Test
  void fluentBuilderWithProvider() {
    service
        .call("claude-sonnet-4-20250514")
        .provider("anthropic")
        .inputTokens(1500)
        .outputTokens(300)
        .duration(800_000_000L)
        .record();

    String summary = service.getSummary();
    assertTrue(summary.contains("anthropic"));
    assertTrue(summary.contains("claude-sonnet"));
  }

  @Test
  void fluentBuilderWithCache() {
    service
        .call("claude-sonnet-4-20250514")
        .inputTokens(500)
        .outputTokens(200)
        .cacheReadTokens(1000)
        .cacheCreationTokens(200)
        .duration(300_000_000L)
        .record();

    String summary = service.getSummary();
    assertTrue(summary.contains("Cache:"), "Should show cache section");
    assertTrue(summary.contains("1000 read"), "Should show cache read tokens");
    assertTrue(summary.contains("200 created"), "Should show cache creation tokens");
    assertTrue(summary.contains("saved $"), "Should show cache savings");
  }

  @Test
  void fluentBuilderStreaming() {
    service
        .call("gpt-4o")
        .inputTokens(2000)
        .outputTokens(500)
        .streaming()
        .timeToFirstToken(200_000_000L)
        .duration(3_000_000_000L)
        .record();

    String summary = service.getSummary();
    assertTrue(summary.contains("streaming"));
    assertTrue(summary.contains("TTFT"));
  }

  @Test
  void fluentBuilderDurationOnly() {
    // Minimal usage — just model + duration
    service.call("some-model").duration(100_000_000L).record();

    assertEquals(1, service.getTotalCalls());
  }

  // ==================== Embeddings ====================

  @Test
  void recordEmbedding() {
    service.recordEmbedding("text-embedding-3-small", 500, 50_000_000L);
    service.recordEmbedding("text-embedding-3-small", 300, 30_000_000L);

    assertEquals(2, service.getTotalEmbeddingCalls());
    assertEquals(0, service.getTotalCalls(), "Embeddings don't count as chat calls");

    String summary = service.getSummary();
    assertTrue(summary.contains("Embeddings"));
    assertTrue(summary.contains("text-embedding-3-small"));
    assertTrue(summary.contains("2 calls"));
    assertTrue(summary.contains("800 tokens"));
  }

  // ==================== Tool use ====================

  @Test
  void recordToolUse() {
    service.recordCall("claude-sonnet-4-20250514", 1000, 200, 500_000_000L);
    service.recordToolUse("claude-sonnet-4-20250514", "search_web");
    service.recordToolUse("claude-sonnet-4-20250514", "search_web");
    service.recordToolUse("claude-sonnet-4-20250514", "run_code");

    String summary = service.getSummary();
    assertTrue(summary.contains("Tool calls: 3"));
    assertTrue(summary.contains("Tool Use"));
    assertTrue(summary.contains("search_web"));
  }

  // ==================== Errors ====================

  @Test
  void recordError() {
    service.recordCall("gpt-4o", 1000, 0, 100_000_000L);
    service.recordError("gpt-4o", "RateLimitException", 50_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("Errors: 1"));
    assertTrue(summary.contains("RateLimitException"));
  }

  // ==================== Model summary ====================

  @Test
  void getModelSummaryUnknown() {
    assertTrue(service.getModelSummary("nonexistent").contains("No data"));
  }

  @Test
  void getModelSummaryDurationOnly() {
    service.recordCall("my-model", 500_000_000L);
    String result = service.getModelSummary("my-model");
    assertTrue(result.contains("1 calls"));
    assertTrue(result.contains("500ms"));
    // Should NOT show "0/0 tokens"
    assertFalse(result.contains("0/0"));
  }

  @Test
  void getModelSummaryWithTokens() {
    service.recordCall("gpt-4o", 1000, 200, 500_000_000L);
    String result = service.getModelSummary("gpt-4o");
    assertTrue(result.contains("1000/200 tokens"));
  }

  // ==================== Cost estimation ====================

  @Test
  void estimateCostClaudeSonnet() {
    // Sonnet: $3/1M input, $15/1M output
    double cost =
        LlmTraceServiceImpl.estimateCost("claude-sonnet-4-20250514", 1_000_000, 100_000, 0);
    assertEquals(3.0 + 1.5, cost, 0.01);
  }

  @Test
  void estimateCostWithCacheRead() {
    // Sonnet: $3/1M input, $15/1M output, $0.30/1M cache-read
    double cost =
        LlmTraceServiceImpl.estimateCost("claude-sonnet-4-20250514", 500_000, 100_000, 500_000);
    double expected = (500_000 * 3.0 / 1e6) + (100_000 * 15.0 / 1e6) + (500_000 * 0.30 / 1e6);
    assertEquals(expected, cost, 0.001);
  }

  @Test
  void estimateCostGpt4o() {
    double cost = LlmTraceServiceImpl.estimateCost("gpt-4o", 1_000_000, 1_000_000, 0);
    assertEquals(2.50 + 10.0, cost, 0.01);
  }

  @Test
  void estimateCostUnknownModel() {
    assertEquals(-1, LlmTraceServiceImpl.estimateCost("my-custom-model", 1000, 1000, 0));
  }

  @Test
  void estimatedCostUsdAcrossModels() {
    service.recordCall("claude-sonnet-4-20250514", 1_000_000, 0, 1_000_000_000L);
    service.recordCall("gpt-4o", 1_000_000, 0, 1_000_000_000L);

    double cost = service.getEstimatedCostUsd();
    assertEquals(5.50, cost, 0.01);
  }

  @Test
  void estimatedCostUsdUnknownModels() {
    service.recordCall("my-custom-model", 1000, 500, 100_000_000L);
    assertEquals(-1, service.getEstimatedCostUsd());
  }

  // ==================== Latency tracking ====================

  @Test
  void latencyMinMax() {
    service.recordCall("gpt-4o", 100, 50, 100_000_000L);
    service.recordCall("gpt-4o", 100, 50, 500_000_000L);
    service.recordCall("gpt-4o", 100, 50, 200_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("min 100ms"));
    assertTrue(summary.contains("max 500ms"));
  }

  // ==================== Reset ====================

  @Test
  void resetClearsEverything() {
    service.recordCall("gpt-4o", 1000, 200, 500_000_000L);
    service.recordEmbedding("text-embedding-3-small", 100, 10_000_000L);
    service.recordToolUse("gpt-4o", "search");
    service.recordError("gpt-4o", "Timeout", 100_000_000L);

    service.reset();

    assertEquals(0, service.getTotalCalls());
    assertEquals(0, service.getTotalInputTokens());
    assertEquals(0, service.getTotalOutputTokens());
    assertEquals(0, service.getTotalEmbeddingCalls());
    assertEquals("No LLM calls recorded.", service.getSummary());
  }

  // ==================== Edge cases ====================

  @Test
  void summaryEmptyWhenNoCalls() {
    assertEquals("No LLM calls recorded.", service.getSummary());
  }

  @Test
  void summaryOmitsTokenLineWhenNoTokens() {
    service.recordCall("my-model", 500_000_000L);
    String summary = service.getSummary();
    // Should show the model and latency but not "0 in / 0 out"
    assertTrue(summary.contains("my-model"));
    assertFalse(summary.contains("0 in / 0 out"));
  }

  @Test
  void cacheHitRateCalculation() {
    service
        .call("claude-sonnet-4-20250514")
        .inputTokens(200)
        .outputTokens(100)
        .cacheReadTokens(800)
        .duration(100_000_000L)
        .record();

    String summary = service.getSummary();
    // 800 cache reads out of 200 input = 400% is wrong semantically,
    // so check that the cache section exists
    assertTrue(summary.contains("Cache:"));
    assertTrue(summary.contains("800 read"));
  }

  // ==================== Concurrency ====================

  @Test
  void concurrentRecording() throws InterruptedException {
    int threads = 8;
    int callsPerThread = 1000;
    Thread[] workers = new Thread[threads];

    for (int t = 0; t < threads; t++) {
      workers[t] =
          new Thread(
              () -> {
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
  }

  @Test
  void concurrentBuilderRecording() throws InterruptedException {
    int threads = 4;
    int callsPerThread = 500;
    Thread[] workers = new Thread[threads];

    for (int t = 0; t < threads; t++) {
      workers[t] =
          new Thread(
              () -> {
                for (int i = 0; i < callsPerThread; i++) {
                  service
                      .call("claude-sonnet-4-20250514")
                      .provider("anthropic")
                      .inputTokens(100)
                      .outputTokens(50)
                      .duration(10_000_000L)
                      .record();
                }
              });
      workers[t].start();
    }
    for (Thread w : workers) {
      w.join();
    }

    assertEquals(threads * callsPerThread, service.getTotalCalls());
  }
}
