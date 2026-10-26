/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import io.btrace.core.annotations.*;
import io.btrace.gpu.GpuBridgeService;
import io.btrace.rag.RagQualityService;
import static io.btrace.core.BTraceUtils.*;

/**
 * Demo 05 probe: feeds the mocked vector store and ONNX session in RagSearchApp into two bundled
 * extensions, btrace-rag-quality (retrieval quality) and btrace-gpu-bridge (inference timing).
 * The result classes are not extension types, so their fields are read reflectively.
 */
@BTrace
public class AiWorkloadProbe {

  @Injected private static RagQualityService rag;
  @Injected private static GpuBridgeService gpu;

  @OnMethod(clazz = "VectorStore", method = "search", location = @Location(Kind.RETURN))
  public static void onSearch(@Return Object result, @Duration long durationNanos) {
    if (result == null) {
      return;
    }
    int count = getInt(field("SearchResult", "count"), result);
    float topScore = getFloat(field("SearchResult", "topScore"), result);
    int tokens = getInt(field("SearchResult", "totalTokens"), result);

    // An empty result (count == 0) is counted as an empty retrieval by the record itself.
    rag.query("vectorstore")
        .resultCount(count)
        .topScore(topScore)
        .totalChunkTokens(tokens)
        .duration(durationNanos)
        .record();
  }

  @OnMethod(clazz = "OnnxSession", method = "run", location = @Location(Kind.RETURN))
  public static void onEmbed(@Duration long durationNanos) {
    gpu.inference("onnx", "embed-small").duration(durationNanos).record();
  }

  @OnTimer(5000)
  public static void report() {
    println("=== RAG + Inference Dashboard ===");
    println(rag.getSummary());
    println(gpu.getSummary());
    println(strcat("empty retrievals: ", str(rag.getTotalEmptyRetrievals())));
    println("=================================");
  }
}
