# Observability for LLM apps with zero code changes

*Token counts, cost, latency, and budget violations — on one dashboard — without touching the app making the calls.*

> Draft B6 · target: BTrace 3.0.0 + 4 weeks · grounded in `docs/tutorials/07-llm-observability.md`

Every team shipping an LLM-powered feature eventually asks the same three questions: how much is this costing us, how slow is it actually, and how often does it blow past whatever budget we quietly assumed it would respect. The usual answer is a tracing SDK, a log shipper, and a service to send it all to. BTrace's answer is different: attach to the JVM that's already making the calls, and read the numbers straight out of it — no SDK, no log shipper, no external service, and critically, no code changes to the app itself.

## A demo app that thinks it's fine

The tutorial this post is based on uses a small "AI ticket summarizer" — `LlmDemoApp.java` — that calls a mocked, OpenAI-client-shaped `OpenAiClient.chat(String)` for every support ticket. No real network calls, no dependencies, and one deliberate defect baked in: roughly one call in twelve "runs away," with the mocked model generating well past its normal reply length. That defect doesn't announce itself in the app's own logs — it just quietly shows up later as a token spike, a cost spike, and a blown latency budget, which is exactly the point. This is the ordinary shape of an LLM incident: nothing crashes, nothing logs an error, and the first sign of trouble is a bill or a complaint.

## What the two extensions actually need

Before granting anything, `btracex inspect` answers "what does this actually require" without touching a running JVM at all:

```
$ btracex inspect btrace-llm-trace
Extension: btrace-llm-trace
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [THREADS]
Services : io.btrace.llm.LlmTraceService

$ btracex inspect btrace-contracts
Extension: btrace-contracts
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [THREADS]
Services : io.btrace.contracts.ContractService
```

Neither extension declares a permission in its own source — no `@ExtensionDescriptor(permissions = ...)` anywhere in either module. `THREADS` shows up anyway because BTrace's build-time permission scanner statically inspects each extension's compiled implementation for JDK APIs that imply a permission, and both `LlmTraceServiceImpl` and `ContractServiceImpl` lean on `ConcurrentHashMap` and `AtomicLong` for lock-free bookkeeping. Same privileged tier as `btrace-metrics`, for an entirely unrelated reason. A one-line policy covers both:

```sh
mkdir -p ~/.btrace
cat > ~/.btrace/permissions.properties <<'EOF'
allowExtensions=btrace-llm-trace,btrace-contracts
EOF
```

## Injecting two services into one probe

The script that ties it together injects both at once — `LlmTraceService` to record tokens, latency, and cost per call, `ContractService` to enforce a hard latency budget on that same call:

```java
@Injected private static LlmTraceService llm;
@Injected private static ContractService contracts;

@OnMethod(clazz = "OpenAiClient", method = "chat", location = @Location(Kind.RETURN))
public static void onChatReturn(@Return Object response, @Duration long durationNanos) {
  Object usage = get(field("ChatCompletion", "usage"), response);
  int promptTokens = 0;
  int completionTokens = 0;
  if (usage != null) {
    promptTokens = getInt(field("Usage", "promptTokens"), usage);
    completionTokens = getInt(field("Usage", "completionTokens"), usage);
  }

  llm.call("gpt-4o-mini")
      .provider("openai")
      .inputTokens(promptTokens)
      .outputTokens(completionTokens)
      .duration(durationNanos)
      .record();

  contracts.checkLatency("chat/latency", durationNanos, 500_000_000L);
}
```

That `field`/`get`/`getInt` trio is doing something worth understanding rather than just copying: BTrace's verifier only lets a script call methods directly on itself, on `BTraceUtils`/`BTrace`, on an injected `Extension` subtype, or on a type living in an injected service's own package. The demo's `ChatCompletion` and `Usage` classes match none of those, so their fields get read reflectively instead of through a getter you'd normally reach for. It's a small extra step in exchange for a real guarantee: the probe can't call arbitrary code on arbitrary objects in the target app, even ones shaped exactly like a real OpenAI SDK response.

## One dashboard, two independent answers

Deploy the script, wait for the first ten-second tick, and both extensions report on the exact same 40 calls without knowing the other exists:

```
=== LLM + Contracts Dashboard ===
=== LLM Trace Summary ===

Model: gpt-4o-mini (openai)
  Calls: 40
  Tokens: 4352 in / 10716 out (avg 108/267)
  Latency: avg 528ms, min 151ms, max 3801ms
  Est. cost: $0.0071

--- Totals ---
  Calls: 40
  Tokens: 4352 in / 10716 out
  Est. total cost: $0.0071

=== Contract Summary ===

Contract: chat/latency
  Checks: 40 | VIOLATIONS: 3 (7%)
  Latency: avg 528ms, min 151ms, max 3801ms
  Last: Latency 3336ms exceeded budget 500ms

--- Totals ---
  Checks: 40
  Violations: 3

==================================
```

`llm.getSummary()` is the per-model rollup — token counts, latency spread, and a cost estimate from a built-in pricing table (an unrecognized model name would simply omit the `Est. cost` line rather than guess). `contracts.getSummary()` is the per-contract rollup — how many of those same 40 calls blew the 500ms budget, and the message from the most recent violation. Neither service is aware the other is running. The correlation — that the ~8% of calls flagged as `VIOLATIONS` are the same calls dragging `max` up into the seconds and inflating token counts and cost past what 40 well-behaved replies would cost — is something you read off the dashboard yourself. That's the actual value of injecting both from one script: a hard SLA number and the token/cost story behind it, on one screen, from an app that was never modified to produce either.

## Two more extensions in the same family

`btrace-llm-trace` and `btrace-contracts` aren't the only AI/ML extensions shipping in this release. Two more follow the exact same `@Injected` and permission pattern without a dedicated hands-on lab yet: `btrace-rag-quality`, which exposes vector-store query latency, result counts, and similarity scores through `RagQualityService`; and `btrace-gpu-bridge`, which tracks ONNX Runtime, DJL, and TensorFlow Java inference timing and GPU memory through `GpuBridgeService`. If the LLM dashboard above is the shape of thing you want for a retrieval pipeline or a GPU-backed inference service, that shape already exists — it's just waiting on its own tutorial.

---

- Full hands-on walkthrough: [docs/tutorials/07-llm-observability.md](../../docs/tutorials/07-llm-observability.md)
- New to BTrace? Start here: [../GettingStarted.md](../GettingStarted.md)
- Questions, ideas, war stories: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
