# Watch Your RAG Pipeline and Your Models

See empty retrievals, similarity scores, and embedding-model latency from a live RAG answerer —
no SDK in the app, no code change, no restart — using two bundled extensions, `btrace-rag-quality`
and `btrace-gpu-bridge`, in one probe.

![Demo 05: RAG retrieval quality and embedding-model latency](images/05-rag-and-inference.gif)

*Recorded from [`demos/05-rag-and-inference/run.sh`](../../demos/05-rag-and-inference/README.md); the MP4 is next to the GIF.*

**Persona:** someone running an AI feature on the JVM who needs to know whether retrieval and
on-device inference are healthy. **Time:** ~10 minutes.

## What you'll need

- JDK 11 or newer with `JAVA_HOME` set (the demo uses single-file source launch; `bin/btrace` refuses to start without `JAVA_HOME`)
- BTrace 3.0 installed, including the bundled `btrace-rag-quality` and `btrace-gpu-bridge`
  extensions — `bin/btrace` on your PATH and `BTRACE_HOME` set
  ([installation options](../GettingStarted.md#installation))
- Two terminal windows, both in a checkout of this repository
- Optional: [Tutorial 7](07-llm-observability.md) covers the LLM-call side (tokens, cost, latency
  budgets). This tutorial adds the two other halves of an AI request path: retrieval and inference.

## Step 1 — Start the patient

[RagSearchApp.java](../../demos/05-rag-and-inference/RagSearchApp.java) is a tiny RAG question
answerer with no dependencies and no network. For each question it embeds the text with a mocked
local ONNX session (`OnnxSession.run`), searches a mocked vector store (`VectorStore.search`), then
"generates" an answer. One deliberate defect is hidden inside: roughly 1 retrieval in 10 comes back
empty, and the app carries on and answers from no context.

The two extensions are privileged, so the target JVM needs a permission policy. The demo ships one
([permissions.properties](../../demos/05-rag-and-inference/permissions.properties)) containing a
single line, `allowExtensions=btrace-rag-quality,btrace-gpu-bridge`, and passes it on the app's own
command line, so nothing global changes on your machine. In terminal 1:

```sh
cd demos/05-rag-and-inference
java -Dbtrace.permissions=permissions.properties RagSearchApp.java
```

**You should see** (the count keeps growing):

```
[demo] rag answerer running - stop with Ctrl+C
[demo] answered 16 questions
```

> **What just happened?** The app reports that it is answering questions, and it is — it just does
> not tell you that about one in ten of them was answered from no context at all. Nothing logs the
> empty retrieval and no metric shows it. That is the gap the next steps close.
> `-Dbtrace.permissions=` is the first source in the policy lookup order (then
> `~/.btrace/permissions.properties`, then a classpath resource); see the
> [Permission Policy](../PermissionPolicy.md) reference and
> [Tutorial 4](04-extensions-and-permissions.md) for the full model.

## Step 2 — Deploy the probe

In terminal 2, attach
[AiWorkloadProbe.java](../../demos/05-rag-and-inference/AiWorkloadProbe.java) to the JVM whose command line matches `RagSearchApp`:

```sh
btrace '~RagSearchApp' AiWorkloadProbe.java
```

(Run it from `demos/05-rag-and-inference` so the file name resolves, or pass its path.) The probe
injects both services and hooks two methods:

```java
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
```

> **What just happened?** `rag.query("vectorstore")` opens a record for one retrieval against a
> named source; `resultCount` is how many chunks came back, `topScore` the highest similarity score
> in the result set, `totalChunkTokens` the size of the context handed to the model, and `duration`
> the query latency. `record()` commits it. A record with `resultCount(0)` is counted as an empty
> retrieval by `record()` itself — the probe does not (and must not) call `recordEmptyRetrieval`
> as well, or every miss would be counted twice. `gpu.inference("onnx", "embed-small")` does the
> same for the embedding model: the first argument is the runtime, the second the model name, and
> the summary shows them as `onnx/embed-small`. The demo's `SearchResult` class is not an extension
> type, so its fields are read reflectively with `field`/`getInt`/`getFloat` from
> `io.btrace.core.BTraceUtils`, the same technique as in [Tutorial 7](07-llm-observability.md).

## Step 3 — Read the first dashboard

The probe prints a dashboard every 5 seconds from its `@OnTimer(5000)` method.

**You should see** (numbers will vary; the exact first-dashboard figures depend on how many
questions were answered before you attached):

```
=== RAG + Inference Dashboard ===

=== RAG Quality Summary ===

Source: vectorstore
  Queries: 17
  Results: 85 total (avg 5/query)
  Similarity (top): avg 0.874, min 0.850, max 0.899
  Latency: avg 57ms, min 24ms, max 86ms
  Context: 15700 tokens

--- Totals ---
  Queries: 17


=== GPU Bridge Summary ===

Model: onnx/embed-small
  Inferences: 17
  Latency: avg 11ms, min 7ms, max 14ms
  Batch size: avg 1
  Throughput: 87.5 items/sec

--- Totals ---
  Inferences: 17


empty retrievals: 0

=================================
```

> **What just happened?** Both extensions saw the same 17 questions through two different hooks. The
> first dashboard shows no empties yet — a small sample can simply miss a 1-in-10 defect — and
> no `Empty retrievals` line at all, because the summary only prints it once one has been recorded.

## Step 4 — Read a later dashboard

Wait a few more ticks. **You should see** the counters climb and the empty-retrieval lines appear:

```
=== RAG Quality Summary ===

Source: vectorstore
  Queries: 35 (3 empty, 8%)
  Results: 160 total (avg 4/query)
  Similarity (top): avg 0.800, min 0.000, max 0.899
  Latency: avg 58ms, min 24ms, max 86ms
  Context: 29195 tokens

--- Totals ---
  Queries: 35
  Empty retrievals: 3 (8%)


=== GPU Bridge Summary ===

Model: onnx/embed-small
  Inferences: 35
  Latency: avg 12ms, min 6ms, max 18ms
  Batch size: avg 1
  Throughput: 81.6 items/sec

--- Totals ---
  Inferences: 35


empty retrievals: 3
```

> **What just happened?** Reading it line by line:
>
> - `Queries: 35 (3 empty, 8%)` — 35 retrievals, 3 of which returned zero chunks. This is the
>   hollow-answer rate the app never reported; it converges on the ~10% the demo injects.
> - `Results: 160 total (avg 4/query)` — chunks returned across all queries. The average drops when
>   empty queries are mixed in.
> - `Similarity (top): ... min 0.000` — the average, minimum, and maximum of each query's top score.
>   The `min 0.000` is the empty retrievals: in the first dashboard the minimum was 0.850, so the
>   score floor collapsing to zero is the same signal as the empty counter, seen from the score side.
>   (On a real store, watch the average sliding down instead; that is retrieval quality degrading
>   without going empty.)
> - `Latency` — vector store query time; in the demo it is 20-80 ms by construction.
> - `Context: 29195 tokens` — total chunk tokens handed to the generator across all queries.
> - `Inferences: 35` and `Latency: avg 12ms` — one embedding call per question, with its own
>   timing. `Batch size: avg 1` reflects that the probe records one item per call and does not set
>   `batchSize`; `Throughput` is reported in items per second.
> - `empty retrievals: 3` — the final line is the probe's own `println` of
>   `rag.getTotalEmptyRetrievals()`, a plain number you can grep for or alert on.
>
> Retrieval problems and model problems separate cleanly here: an embedding latency spike lands in
> the GPU Bridge block, an empty or low-score retrieval in the RAG block, and the same questions
> feed both.

## Step 5 — Point the same probe at a real stack

The probe shape does not change; only the matchers do. Change `clazz`/`method` on `onSearch` to
your vector store client and read its result with the same `get(field(...))` helpers, and change
`onEmbed` to your inference runtime. Matchers from the bundled samples
([GpuBridge.java](../../btrace-dist/src/main/resources/samples/GpuBridge.java) and
[RagQuality.java](../../btrace-dist/src/main/resources/samples/RagQuality.java)):

| Runtime / store | `clazz` | `method` |
|---|---|---|
| ONNX Runtime | `ai.onnxruntime.OrtSession` | `run` |
| DJL | `/ai\\.djl\\.inference\\.Predictor/` | `predict` |
| TensorFlow Java | `/org\\.tensorflow\\.Session/` | `run` |
| Pinecone | `/io\\.pinecone\\..*/` | `query` |
| Milvus | `/io\\.milvus\\.client\\..*/` | `search` |
| Weaviate | `/io\\.weaviate\\.client\\..*/` | `/get|search/` |
| Chroma | `/tech\\.amikos\\.chromadb\\..*/` | `query` |

Keep `Kind.RETURN` and `@Duration` as in the demo. The samples use the simpler
`recordQuery(source, dur)` / `recordInference(runtime, model, dur)` calls; to get result counts
and scores, as the demo does, read them off `@Return` the way `onSearch` does.

## Step 6 — Clean up

`Ctrl+C` the `btrace` client to detach (probes disable immediately), then `Ctrl+C` `RagSearchApp`
in terminal 1. The policy was passed on the app's command line, so there is nothing to undo.

## Troubleshooting

- **The probe fails to deploy, or prints `! ERROR ... optional service unavailable`** — the
  permission policy was not loaded by the target JVM. Restart `RagSearchApp` with
  `-Dbtrace.permissions=permissions.properties` (the path is resolved from the app's working
  directory) and attach to the new PID. The policy loads once per JVM lifetime, so adding it later
  to a running app does not help. The `! ERROR` block with `BTrace optional service unavailable:
  <service>` is what an *optional* injection looks like when blocked, shown in
  [Tutorial 4](04-extensions-and-permissions.md); this demo's fields are plain `@Injected`, which
  [never fall back](../PermissionPolicy.md), so expect the deploy itself to be refused.
- **The first dashboard prints `No RAG queries recorded.`** — it ran before the first retrieval
  was recorded. Wait for the next 5-second tick.
- **`empty retrievals: 0` for a while** — the defect hits about 1 query in 10 and the app answers
  one question every few hundred milliseconds, so a short window can miss it. Give it a few more
  ticks.

## Go deeper

- Run the whole scenario unattended: [demos/05-rag-and-inference](../../demos/05-rag-and-inference/README.md)
  (`run.sh`)
- Concepts and a full LangChain4j-style RAG example, plus the GPU bridge in depth:
  [BTrace Tutorial, Lesson 12 — AI/LLM Application Observability](../BTraceTutorial.md)
- The LLM side of the same request path (tokens, cost, latency budgets):
  [Tutorial 7 — Watch Your LLM App Think](07-llm-observability.md)
- Permission policy sources and options: [Permission Policy](../PermissionPolicy.md) and
  [Tutorial 4](04-extensions-and-permissions.md)
