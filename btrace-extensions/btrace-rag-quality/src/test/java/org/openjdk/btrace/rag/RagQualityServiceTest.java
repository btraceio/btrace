package org.openjdk.btrace.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RagQualityServiceTest {

  private RagQualityServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new RagQualityServiceImpl();
  }

  @Test
  void durationOnlyQuery() {
    service.recordQuery("pinecone", 5_000_000L);
    assertEquals(1, service.getTotalQueries());
    String summary = service.getSummary();
    assertTrue(summary.contains("pinecone"));
    assertTrue(summary.contains("1"));
  }

  @Test
  void queryWithResultCount() {
    service.recordQuery("milvus", 10, 8_000_000L);
    assertEquals(1, service.getTotalQueries());
    assertEquals(0, service.getTotalEmptyRetrievals());
    assertTrue(service.getSummary().contains("10 total"));
  }

  @Test
  void emptyRetrieval() {
    service.recordQuery("weaviate", 0, 2_000_000L);
    assertEquals(1, service.getTotalEmptyRetrievals());
    assertTrue(service.getSummary().contains("1 empty"));
  }

  @Test
  void explicitEmptyRetrieval() {
    service.recordEmptyRetrieval("chroma");
    assertEquals(1, service.getTotalEmptyRetrievals());
  }

  @Test
  void fluentBuilder() {
    service.query("pinecone")
        .resultCount(5)
        .topScore(0.92f)
        .lowScore(0.71f)
        .embeddingDimension(1536)
        .totalChunkTokens(3000)
        .duration(10_000_000L)
        .record();

    assertEquals(1, service.getTotalQueries());
    float avgScore = service.getAverageTopScore();
    assertTrue(avgScore > 0.91f && avgScore < 0.93f, "avgScore=" + avgScore);
    String summary = service.getSummary();
    assertTrue(summary.contains("0.920"));
    assertTrue(summary.contains("3000 tokens"));
  }

  @Test
  void fluentBuilderMinimal() {
    service.query("qdrant")
        .duration(1_000_000L)
        .record();

    assertEquals(1, service.getTotalQueries());
    assertEquals(-1f, service.getAverageTopScore());
  }

  @Test
  void fluentBuilderEmptyResult() {
    service.query("pgvector")
        .resultCount(0)
        .duration(500_000L)
        .record();

    assertEquals(1, service.getTotalEmptyRetrievals());
  }

  @Test
  void multipleSourcesTrackedSeparately() {
    service.recordQuery("pinecone", 5, 10_000_000L);
    service.recordQuery("milvus", 3, 8_000_000L);
    service.recordQuery("pinecone", 7, 12_000_000L);

    assertEquals(3, service.getTotalQueries());
    assertTrue(service.getSourceSummary("pinecone").contains("2 queries"));
    assertTrue(service.getSourceSummary("milvus").contains("1 queries"));
  }

  @Test
  void pipelineRecording() {
    service.recordPipeline("qa-bot", 5_000_000L, 50_000_000L);
    service.recordPipeline("qa-bot", 3_000_000L, 45_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("qa-bot"));
    assertTrue(summary.contains("2 invocations"));
  }

  @Test
  void chunkRecording() {
    service.recordChunk("pinecone", 500);
    service.recordChunk("pinecone", 750);

    String summary = service.getSourceSummary("pinecone");
    // Chunks don't count as queries
    assertTrue(summary.contains("0 queries"));
  }

  @Test
  void similarityScoreAggregation() {
    service.query("pinecone").topScore(0.90f).duration(1_000_000L).record();
    service.query("pinecone").topScore(0.80f).duration(1_000_000L).record();
    service.query("milvus").topScore(0.70f).duration(1_000_000L).record();

    float avg = service.getAverageTopScore();
    // (0.90 + 0.80 + 0.70) / 3 = 0.8
    assertTrue(avg > 0.79f && avg < 0.81f, "avg=" + avg);
  }

  @Test
  void latencyMinMax() {
    service.recordQuery("pinecone", 10_000_000L);
    service.recordQuery("pinecone", 2_000_000L);
    service.recordQuery("pinecone", 50_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("min 2ms"));
    assertTrue(summary.contains("max 50ms"));
  }

  @Test
  void noDataSummary() {
    assertEquals("No RAG queries recorded.", service.getSummary());
  }

  @Test
  void unknownSourceSummary() {
    assertEquals("No data for source: unknown", service.getSourceSummary("unknown"));
  }

  @Test
  void reset() {
    service.recordQuery("pinecone", 5_000_000L);
    service.recordEmptyRetrieval("milvus");
    service.recordPipeline("qa", 1L, 1L);
    service.reset();

    assertEquals(0, service.getTotalQueries());
    assertEquals(0, service.getTotalEmptyRetrievals());
    assertEquals("No RAG queries recorded.", service.getSummary());
  }

  @Test
  void concurrentRecording() throws Exception {
    int threads = 8;
    int queriesPerThread = 1000;
    CountDownLatch latch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      new Thread(() -> {
        try {
          for (int i = 0; i < queriesPerThread; i++) {
            service.recordQuery("pinecone", 5, 1_000_000L);
          }
        } finally {
          latch.countDown();
        }
      }).start();
    }
    latch.await();

    assertEquals(threads * queriesPerThread, service.getTotalQueries());
  }

  @Test
  void concurrentBuilderRecording() throws Exception {
    int threads = 8;
    int queriesPerThread = 500;
    CountDownLatch latch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      new Thread(() -> {
        try {
          for (int i = 0; i < queriesPerThread; i++) {
            service.query("milvus")
                .resultCount(3)
                .topScore(0.85f)
                .duration(2_000_000L)
                .record();
          }
        } finally {
          latch.countDown();
        }
      }).start();
    }
    latch.await();

    assertEquals(threads * queriesPerThread, service.getTotalQueries());
  }
}
