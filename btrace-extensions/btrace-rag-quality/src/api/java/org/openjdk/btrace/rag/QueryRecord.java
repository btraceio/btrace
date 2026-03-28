package org.openjdk.btrace.rag;

/**
 * Fluent builder for recording a vector DB query with detailed metrics.
 *
 * <p>Obtain via {@link RagQualityService#query(String)}. All setters are optional.
 * Call {@link #record()} to commit the metrics.
 *
 * <p><strong>Allocation-free:</strong> Instances are pooled per-thread internally.
 * The returned reference must not be stored or shared across threads.
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
 */
public interface QueryRecord {

  /** Number of results/chunks returned by the query. */
  QueryRecord resultCount(int count);

  /** Highest similarity score in the result set (0.0 to 1.0). */
  QueryRecord topScore(float score);

  /** Lowest similarity score in the result set (0.0 to 1.0). */
  QueryRecord lowScore(float score);

  /** Embedding dimension used for the query vector. */
  QueryRecord embeddingDimension(int dimension);

  /** Total tokens across all returned chunks. */
  QueryRecord totalChunkTokens(int tokens);

  /** Query duration in nanoseconds. */
  QueryRecord duration(long nanos);

  /** Commits this query record to the service. */
  void record();
}
