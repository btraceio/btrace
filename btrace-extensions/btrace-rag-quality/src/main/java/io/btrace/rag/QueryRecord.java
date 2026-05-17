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
package io.btrace.rag;

/**
 * Fluent builder for recording a vector DB query with detailed metrics.
 *
 * <p>Obtain via {@link RagQualityService#query(String)}. All setters are optional. Call {@link
 * #record()} to commit the metrics.
 *
 * <p><strong>Allocation-free:</strong> Instances are pooled per-thread internally. The returned
 * reference must not be stored or shared across threads.
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
