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
 * ThreadLocal-pooled builder implementing the {@link QueryRecord} fluent API. One instance per
 * thread, reused across calls — zero heap allocation.
 */
final class QueryRecordImpl implements QueryRecord {

  String source;
  int resultCountVal;
  float topScoreVal;
  float lowScoreVal;
  int embDimension;
  int chunkTokensVal;
  long durationVal;

  private RagQualityServiceImpl service;

  QueryRecordImpl() {}

  QueryRecordImpl reset(RagQualityServiceImpl service, String source) {
    this.service = service;
    this.source = source;
    this.resultCountVal = 0;
    this.topScoreVal = -1f;
    this.lowScoreVal = -1f;
    this.embDimension = 0;
    this.chunkTokensVal = 0;
    this.durationVal = 0;
    return this;
  }

  @Override
  public QueryRecord resultCount(int count) {
    this.resultCountVal = count;
    return this;
  }

  @Override
  public QueryRecord topScore(float score) {
    this.topScoreVal = score;
    return this;
  }

  @Override
  public QueryRecord lowScore(float score) {
    this.lowScoreVal = score;
    return this;
  }

  @Override
  public QueryRecord embeddingDimension(int dimension) {
    this.embDimension = dimension;
    return this;
  }

  @Override
  public QueryRecord totalChunkTokens(int tokens) {
    this.chunkTokensVal = tokens;
    return this;
  }

  @Override
  public QueryRecord duration(long nanos) {
    this.durationVal = nanos;
    return this;
  }

  @Override
  public void record() {
    service.commitQueryRecord(this);
  }
}
