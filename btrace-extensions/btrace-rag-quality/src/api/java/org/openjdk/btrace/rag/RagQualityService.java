package org.openjdk.btrace.rag;

import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/**
 * BTrace extension service for RAG (Retrieval-Augmented Generation) pipeline observability.
 *
 * <p>Tracks vector database query performance, retrieval quality metrics,
 * and end-to-end RAG pipeline latency. Works with any vector DB client
 * (Pinecone, Milvus, Weaviate, Chroma, pgvector, Qdrant).
 *
 * <p>Usage in a BTrace script:
 * <pre>
 * &#64;Injected RagQualityService rag;
 *
 * &#64;OnMethod(clazz = "io.pinecone.PineconeClient", method = "query")
 * void onQuery(&#64;Duration long dur) {
 *     rag.recordQuery("pinecone", 10, dur);
 * }
 * </pre>
 */
@ServiceDescriptor
public interface RagQualityService {

  // ==================== Query recording ====================

  /**
   * Records a vector DB query with duration only.
   *
   * @param source vector DB or index name (e.g. "pinecone", "milvus-products")
   * @param durationNanos query duration in nanoseconds
   */
  void recordQuery(String source, long durationNanos);

  /**
   * Records a vector DB query with result count and duration.
   *
   * @param source vector DB or index name
   * @param resultCount number of results/chunks returned (top-K)
   * @param durationNanos query duration in nanoseconds
   */
  void recordQuery(String source, int resultCount, long durationNanos);

  /**
   * Starts a detailed query record builder. Allocation-free (ThreadLocal-pooled).
   *
   * <p>Use inline on the calling thread and call {@link QueryRecord#record()}
   * before the next {@code query()} call. Do not store the returned reference.
   *
   * <pre>
   * rag.query("pinecone")
   *     .resultCount(5)
   *     .topScore(0.92f)
   *     .lowScore(0.71f)
   *     .embeddingDimension(1536)
   *     .duration(durationNanos)
   *     .record();
   * </pre>
   *
   * @param source vector DB or index name
   * @return a query record builder (thread-local, do not store)
   */
  QueryRecord query(String source);

  // ==================== Pipeline recording ====================

  /**
   * Records an end-to-end RAG pipeline invocation (retrieve + generate).
   *
   * @param pipelineName pipeline identifier
   * @param retrievalNanos time spent in retrieval phase
   * @param generationNanos time spent in generation phase
   */
  void recordPipeline(String pipelineName, long retrievalNanos, long generationNanos);

  /**
   * Records a chunk that was retrieved and used in context.
   * Useful for tracking context window utilization.
   *
   * @param source vector DB or index name
   * @param chunkTokens approximate token count of the chunk
   */
  void recordChunk(String source, int chunkTokens);

  /**
   * Records a retrieval that returned no results (empty context).
   *
   * @param source vector DB or index name
   */
  void recordEmptyRetrieval(String source);

  // ==================== Reporting ====================

  /** Returns a formatted summary of all RAG quality metrics. */
  String getSummary();

  /** Returns summary for a specific source/index. */
  String getSourceSummary(String source);

  /** Returns total number of queries recorded across all sources. */
  long getTotalQueries();

  /** Returns total number of empty retrievals. */
  long getTotalEmptyRetrievals();

  /** Returns average similarity score across all queries that reported scores. */
  float getAverageTopScore();

  /** Resets all collected metrics. */
  void reset();
}
