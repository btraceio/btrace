package org.openjdk.btrace.rag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.btrace.core.extensions.Extension;

/**
 * Thread-safe RAG quality tracking with lock-free per-source statistics.
 */
public final class RagQualityServiceImpl extends Extension implements RagQualityService {

  private final Map<String, SourceStats> sourceStats = new ConcurrentHashMap<>();
  private final Map<String, PipelineStats> pipelineStats = new ConcurrentHashMap<>();

  private final ThreadLocal<QueryRecordImpl> queryRecordPool =
      ThreadLocal.withInitial(QueryRecordImpl::new);

  // ==================== Simple recording ====================

  @Override
  public void recordQuery(String source, long durationNanos) {
    SourceStats stats = getOrCreate(source);
    stats.queries.incrementAndGet();
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void recordQuery(String source, int resultCount, long durationNanos) {
    SourceStats stats = getOrCreate(source);
    stats.queries.incrementAndGet();
    stats.totalResults.addAndGet(resultCount);
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
    if (resultCount == 0) {
      stats.emptyRetrievals.incrementAndGet();
    }
  }

  // ==================== Fluent builder ====================

  @Override
  public QueryRecord query(String source) {
    return queryRecordPool.get().reset(this, source);
  }

  void commitQueryRecord(QueryRecordImpl rec) {
    SourceStats stats = getOrCreate(rec.source);
    stats.queries.incrementAndGet();
    stats.totalResults.addAndGet(rec.resultCountVal);
    stats.totalDurationNanos.addAndGet(rec.durationVal);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, rec.durationVal);

    if (rec.resultCountVal == 0) {
      stats.emptyRetrievals.incrementAndGet();
    }

    if (rec.topScoreVal >= 0) {
      stats.scoredQueries.incrementAndGet();
      // Accumulate scores as fixed-point (multiply by 10000) to avoid floating point atomics
      stats.totalTopScore.addAndGet((long) (rec.topScoreVal * 10000));
      updateMin(stats.minTopScore, (long) (rec.topScoreVal * 10000));
      updateMax(stats.maxTopScore, (long) (rec.topScoreVal * 10000));
    }
    if (rec.lowScoreVal >= 0) {
      stats.totalLowScore.addAndGet((long) (rec.lowScoreVal * 10000));
    }
    if (rec.chunkTokensVal > 0) {
      stats.totalChunkTokens.addAndGet(rec.chunkTokensVal);
    }
    if (rec.embDimension > 0) {
      stats.lastEmbeddingDimension = rec.embDimension;
    }
  }

  // ==================== Pipeline recording ====================

  @Override
  public void recordPipeline(String pipelineName, long retrievalNanos, long generationNanos) {
    PipelineStats ps = pipelineStats.computeIfAbsent(pipelineName, k -> new PipelineStats());
    ps.invocations.incrementAndGet();
    ps.totalRetrievalNanos.addAndGet(retrievalNanos);
    ps.totalGenerationNanos.addAndGet(generationNanos);
  }

  @Override
  public void recordChunk(String source, int chunkTokens) {
    SourceStats stats = getOrCreate(source);
    stats.totalChunkTokens.addAndGet(chunkTokens);
    stats.chunkCount.incrementAndGet();
  }

  @Override
  public void recordEmptyRetrieval(String source) {
    getOrCreate(source).emptyRetrievals.incrementAndGet();
  }

  // ==================== Reporting ====================

  @Override
  public String getSummary() {
    if (sourceStats.isEmpty() && pipelineStats.isEmpty()) {
      return "No RAG queries recorded.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== RAG Quality Summary ===\n\n");

    long totalQueries = 0;
    long totalEmpty = 0;

    for (Map.Entry<String, SourceStats> entry : sourceStats.entrySet()) {
      String source = entry.getKey();
      SourceStats s = entry.getValue();
      long queries = s.queries.get();
      totalQueries += queries;
      long empty = s.emptyRetrievals.get();
      totalEmpty += empty;

      sb.append("Source: ").append(source).append("\n");
      sb.append("  Queries: ").append(queries);
      if (empty > 0) {
        sb.append(" (").append(empty).append(" empty, ")
            .append(empty * 100 / queries).append("%)");
      }
      sb.append("\n");

      // Results
      long totalRes = s.totalResults.get();
      if (totalRes > 0 && queries > 0) {
        sb.append("  Results: ").append(totalRes)
            .append(" total (avg ").append(totalRes / queries).append("/query)\n");
      }

      // Similarity scores
      long scored = s.scoredQueries.get();
      if (scored > 0) {
        float avgTop = (s.totalTopScore.get() / (float) scored) / 10000f;
        float minTop = s.minTopScore.get() / 10000f;
        float maxTop = s.maxTopScore.get() / 10000f;
        sb.append("  Similarity (top): avg ").append(String.format("%.3f", avgTop));
        sb.append(", min ").append(String.format("%.3f", minTop));
        sb.append(", max ").append(String.format("%.3f", maxTop));
        sb.append("\n");

        long totalLow = s.totalLowScore.get();
        if (totalLow > 0) {
          float avgLow = (totalLow / (float) scored) / 10000f;
          float spread = avgTop - avgLow;
          sb.append("  Similarity (low): avg ").append(String.format("%.3f", avgLow));
          sb.append(" (spread ").append(String.format("%.3f", spread)).append(")\n");
        }
      }

      // Latency
      if (queries > 0) {
        long avgMs = (s.totalDurationNanos.get() / queries) / 1_000_000;
        long minMs = s.minDurationNanos.get() / 1_000_000;
        long maxMs = s.maxDurationNanos.get() / 1_000_000;
        sb.append("  Latency: avg ").append(avgMs).append("ms");
        sb.append(", min ").append(minMs).append("ms");
        sb.append(", max ").append(maxMs).append("ms\n");
      }

      // Chunks
      long chunks = s.chunkCount.get();
      long chunkTokens = s.totalChunkTokens.get();
      if (chunkTokens > 0) {
        sb.append("  Context: ").append(chunkTokens).append(" tokens");
        if (chunks > 0) {
          sb.append(" (").append(chunks).append(" chunks, avg ")
              .append(chunkTokens / chunks).append(" tok/chunk)");
        }
        sb.append("\n");
      }

      sb.append("\n");
    }

    // Pipelines
    if (!pipelineStats.isEmpty()) {
      sb.append("--- Pipelines ---\n");
      for (Map.Entry<String, PipelineStats> entry : pipelineStats.entrySet()) {
        PipelineStats ps = entry.getValue();
        long inv = ps.invocations.get();
        long avgRetMs = inv > 0 ? (ps.totalRetrievalNanos.get() / inv) / 1_000_000 : 0;
        long avgGenMs = inv > 0 ? (ps.totalGenerationNanos.get() / inv) / 1_000_000 : 0;
        sb.append("  ").append(entry.getKey()).append(": ")
            .append(inv).append(" invocations, avg retrieval ")
            .append(avgRetMs).append("ms, avg generation ")
            .append(avgGenMs).append("ms\n");
      }
      sb.append("\n");
    }

    sb.append("--- Totals ---\n");
    sb.append("  Queries: ").append(totalQueries).append("\n");
    if (totalEmpty > 0) {
      sb.append("  Empty retrievals: ").append(totalEmpty);
      if (totalQueries > 0) {
        sb.append(" (").append(totalEmpty * 100 / totalQueries).append("%)");
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  @Override
  public String getSourceSummary(String source) {
    SourceStats s = sourceStats.get(source);
    if (s == null) {
      return "No data for source: " + source;
    }
    long queries = s.queries.get();
    long avgMs = queries > 0 ? (s.totalDurationNanos.get() / queries) / 1_000_000 : 0;
    long empty = s.emptyRetrievals.get();
    StringBuilder sb = new StringBuilder();
    sb.append(source).append(": ").append(queries).append(" queries, avg ").append(avgMs).append("ms");
    if (empty > 0) {
      sb.append(", ").append(empty).append(" empty");
    }
    return sb.toString();
  }

  @Override
  public long getTotalQueries() {
    long total = 0;
    for (SourceStats s : sourceStats.values()) {
      total += s.queries.get();
    }
    return total;
  }

  @Override
  public long getTotalEmptyRetrievals() {
    long total = 0;
    for (SourceStats s : sourceStats.values()) {
      total += s.emptyRetrievals.get();
    }
    return total;
  }

  @Override
  public float getAverageTopScore() {
    long totalScored = 0;
    long totalScore = 0;
    for (SourceStats s : sourceStats.values()) {
      totalScored += s.scoredQueries.get();
      totalScore += s.totalTopScore.get();
    }
    if (totalScored == 0) return -1f;
    return (totalScore / (float) totalScored) / 10000f;
  }

  @Override
  public void reset() {
    sourceStats.clear();
    pipelineStats.clear();
  }

  @Override
  public void close() {
    String summary = getSummary();
    if (!"No RAG queries recorded.".equals(summary)) {
      getContext().send(summary);
    }
  }

  // ==================== Internals ====================

  private SourceStats getOrCreate(String source) {
    return sourceStats.computeIfAbsent(source, k -> new SourceStats());
  }

  private static void updateMinMax(AtomicLong min, AtomicLong max, long value) {
    updateMin(min, value);
    updateMax(max, value);
  }

  private static void updateMin(AtomicLong min, long value) {
    long cur;
    do {
      cur = min.get();
      if (value >= cur) break;
    } while (!min.compareAndSet(cur, value));
  }

  private static void updateMax(AtomicLong max, long value) {
    long cur;
    do {
      cur = max.get();
      if (value <= cur) break;
    } while (!max.compareAndSet(cur, value));
  }

  static final class SourceStats {
    final AtomicLong queries = new AtomicLong();
    final AtomicLong totalResults = new AtomicLong();
    final AtomicLong emptyRetrievals = new AtomicLong();
    final AtomicLong scoredQueries = new AtomicLong();
    final AtomicLong totalTopScore = new AtomicLong();      // fixed-point * 10000
    final AtomicLong minTopScore = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxTopScore = new AtomicLong(0);
    final AtomicLong totalLowScore = new AtomicLong();      // fixed-point * 10000
    final AtomicLong totalDurationNanos = new AtomicLong();
    final AtomicLong minDurationNanos = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxDurationNanos = new AtomicLong(0);
    final AtomicLong totalChunkTokens = new AtomicLong();
    final AtomicLong chunkCount = new AtomicLong();
    volatile int lastEmbeddingDimension;
  }

  static final class PipelineStats {
    final AtomicLong invocations = new AtomicLong();
    final AtomicLong totalRetrievalNanos = new AtomicLong();
    final AtomicLong totalGenerationNanos = new AtomicLong();
  }
}
