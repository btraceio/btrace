# Demo 05 - RAG and inference observability

1. A RAG question answerer embeds each question with a local ONNX model, searches a vector store, then generates an answer.
2. Roughly 1 retrieval in 10 comes back empty. The app carries on and answers from no context, so some answers are hollow.
3. Nothing logs it, and no metric shows it.
4. One probe, two bundled extensions: `btrace-rag-quality` (retrieval quality) and `btrace-gpu-bridge` (inference timing).
5. Attach to the running JVM, and the dashboard shows the empty-retrieval rate, top scores, chunk tokens and embedding latency. No restart, no code change.

## Run it

Needs a BTrace 3.0 install (`BTRACE_HOME`) and JDK 11+ (`JAVA_HOME`).

```bash
# terminal 1: the app (extensions must be allowed for the target JVM)
cd demos/05-rag-and-inference
java -Dbtrace.permissions=permissions.properties RagSearchApp.java

# terminal 2: attach the probe
btrace '~RagSearchApp' AiWorkloadProbe.java
```

Or run both unattended: `demos/05-rag-and-inference/run.sh`.

## You should see

```
[demo] rag answerer running - stop with Ctrl+C
[demo] answered 16 questions
...
=== RAG + Inference Dashboard ===
=== RAG Quality Summary ===
Source: vectorstore
  Queries: 18 (3 empty, 16%)
  Results: 75 total (avg 4/query)
  Similarity (top): avg 0.730, min 0.000, max 0.896
  Latency: avg 53ms, min 26ms, max 82ms
  Context: 14610 tokens
--- Totals ---
  Queries: 18
  Empty retrievals: 3 (16%)
=== GPU Bridge Summary ===
Model: onnx/embed-small
  Inferences: 17
  Latency: avg 13ms, min 5ms, max 23ms
  Batch size: avg 1
  Throughput: 74.6 items/sec
--- Totals ---
  Inferences: 17
empty retrievals: 3
=================================
```

(The first dashboard, printed before any traffic is recorded, reads `No RAG queries recorded.`; numbers vary per run.)

## Against a real stack

The probe shape does not change; only the matchers do. Point `clazz`/`method` at your vector store client (for example `io.pinecone.PineconeClient` / `query`) and read its result with the same `get(field(...))` helpers, or at `ai.onnxruntime.OrtSession` / `run` for ONNX Runtime. See `btrace-dist/src/main/resources/samples/GpuBridge.java` (ONNX Runtime, DJL, TensorFlow) and `RagQuality.java` for ready-made matchers.

