# Watch Your LLM App Think

Put a hard latency budget on an LLM call and watch token counts, cost, and budget violations line
up on one combined dashboard — no code changes to the app making the calls, and no SDK, log
shipper, or external service in between.

**Persona:** anyone instrumenting an AI-powered feature who wants token/latency/cost visibility
without adding a tracing SDK. **Time:** ~10 minutes.

## What you'll need

- JDK 11 or newer on your PATH (the demo uses single-file source launch)
- BTrace 3.0 installed, including the bundled `btrace-llm-trace` and `btrace-contracts`
  extensions — `bin/btrace` and `bin/btracex` on your PATH
  ([installation options](../GettingStarted.md#installation))
- Two terminal windows

## Step 1 — Start the patient

This tutorial uses its own demo app instead of the shared `DemoApp.java` from the rest of the
series, because it needs something LLM-shaped to instrument. [demo/LlmDemoApp.java](demo/LlmDemoApp.java)
is a tiny "AI ticket summarizer" that calls a mocked OpenAI-client-shaped `OpenAiClient.chat(String)`
for every ticket — no real network calls, no dependencies. One deliberate defect is hidden inside:
roughly 1 call in 12 (~8%) "runs away" — the mocked model keeps generating well past its normal
reply length, which shows up later as both a token/cost spike and a blown latency budget.

Run it in terminal 1:

```sh
java LlmDemoApp.java
```

**You should see** (numbers will vary):

```
[demo] llm ticket summarizer running - stop with Ctrl+C
[demo] answered 5 tickets
[demo] answered 10 tickets
```

## Step 2 — Find its PID

In terminal 2:

```sh
jps
```

**You should see** a line like `23456 LlmDemoApp`. That's the `<PID>` for every command below.

## Step 3 — See what the two extensions actually ask for

Before granting anything, check what `btrace-llm-trace` and `btrace-contracts` require. `btracex
inspect` reads extension manifests directly — no running JVM needed:

```sh
btracex inspect btrace-llm-trace
```

**You should see:**

```
Extension: btrace-llm-trace
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [THREADS]
Services : io.btrace.llm.LlmTraceService
```

```sh
btracex inspect btrace-contracts
```

**You should see:**

```
Extension: btrace-contracts
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [THREADS]
Services : io.btrace.contracts.ContractService
```

> **What just happened?** Neither extension declares a permission in source — there's no
> `package-info.java` with `@ExtensionDescriptor(permissions = ...)` in either module, unlike
> `btrace-metrics` (see [Tutorial 4](04-extensions-and-permissions.md)). `THREADS` shows up anyway
> because BTrace's Gradle extension plugin computes an extension's permissions by statically
> scanning its compiled implementation for JDK APIs that imply one (`PermissionScanner`, part of
> the `io.btrace.extension` plugin, `scanPermissions = true` by default) — and both
> `LlmTraceServiceImpl` and `ContractServiceImpl` lean entirely on `ConcurrentHashMap` and
> `AtomicLong` for lock-free per-model/per-contract bookkeeping. The scanner tags *any* call into
> `java.util.concurrent.*` as `THREADS`, whether or not the extension actually starts a thread —
> so both extensions land in the same privileged tier as `btrace-metrics`, for a different reason
> than `btrace-metrics` earns it.

## Step 4 — Grant both extensions their permission

```sh
mkdir -p ~/.btrace
cat > ~/.btrace/permissions.properties <<'EOF'
allowExtensions=btrace-llm-trace,btrace-contracts
EOF
```

`allowExtensions` takes a comma-separated list of extension ids, so one line covers both. Since
you haven't attached BTrace to this `LlmDemoApp` process yet, the very next attach (Step 5) will be
the *first* one — exactly when this policy file gets read. (See [Tutorial 4](04-extensions-and-permissions.md)
for what it looks like when you *skip* this step, and the full `btracex policy` reference.)

## Step 5 — Deploy the combined script

[demo/LlmObservability.java](demo/LlmObservability.java) injects both services at once —
`LlmTraceService` records tokens, latency, and cost per call; `ContractService` enforces a 500ms
latency budget on that same call:

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

> **What just happened?** `field`/`get`/`getInt` walk the mocked response the same way
> [Lesson 12](../BTraceTutorial.md) pulls `TokenUsage` out of a real LangChain4j response — but
> those three are `io.btrace.core.BTraceUtils` methods, not methods on the flat-DSL
> `io.btrace.BTrace` facade, so this script statically imports `io.btrace.core.BTraceUtils.*`
> instead of `io.btrace.BTrace.*`. That's not a style choice: BTrace's verifier only lets a script
> call methods directly on itself, on `BTraceUtils`/`BTrace`, on an injected `Extension` subtype, or
> on a type that lives in an injected service's own package. The demo's `ChatCompletion` and
> `Usage` classes match none of those, so their fields have to be read reflectively rather than
> through a getter — the same restriction is why Lesson 12's own example reaches for `field`/`get`
> instead of calling `response.getUsage()` directly.

Deploy it:

```sh
btrace <PID> LlmObservability.java
```

## Step 6 — Read the dashboard

**You should see**, every 10 seconds (numbers will vary):

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

> **What just happened?** Both extensions saw the exact same 40 calls and durations, but they
> answer different questions about them. `llm.getSummary()` is `LlmTraceServiceImpl`'s per-model
> rollup: token counts, latency min/mean/max, and a cost estimate from a built-in pricing table
> (`gpt-4o-mini` is priced in it; an unrecognized model name would simply omit the `Est. cost`
> line). `contracts.getSummary()` is `ContractServiceImpl`'s per-contract rollup: how many of those
> 40 calls blew the 500ms budget from Step 5, and the message from the most recent violation.
> Neither extension knows the other exists — the correlation is something *you* read off the
> dashboard: the ~8% of calls flagged as `VIOLATIONS` are the same calls dragging `max` up into
> seconds and inflating `Tokens ... out` / `Est. cost` past what 40 normal replies would cost.
> That's the point of injecting both from one script: a hard SLA number, and the token/cost story
> behind it, on one screen.

## Step 7 — Clean up

`Ctrl+C` the `btrace` client to detach (probes disable immediately, same as every other tutorial in
this series), then `Ctrl+C` `LlmDemoApp` in terminal 1. If you want to leave your BTrace
installation in its original state:

```sh
rm ~/.btrace/permissions.properties
```

## Troubleshooting

- **`btracex inspect btrace-llm-trace` (or `btrace-contracts`) prints an error or nothing** — it
  scans `$BTRACE_HOME/extensions/` and `~/.btrace/extensions/` (plus `$BTRACE_EXT_PATH` if set).
  Confirm `BTRACE_HOME` is set and both extensions are exploded under it — see
  [Tutorial 4](04-extensions-and-permissions.md)'s Troubleshooting section for the same check
  against `btrace-metrics`.
- **The dashboard never appears** — confirm `~/.btrace/permissions.properties` existed *before*
  your first `btrace <PID> ...` attach against this `LlmDemoApp` process. The policy loads once per
  JVM lifetime; if you attached once already (with an earlier, unrelated script) before writing the
  policy file, start a fresh `LlmDemoApp` and get a new PID.
- **No `VIOLATIONS` line ever shows up** — the runaway defect fires on roughly 1 call in 12 with a
  single worker thread, so a short window can miss it. Let the demo run for another `@OnTimer` tick
  (10 seconds) or two.

## Go deeper

- The concepts behind both services, plus a full LangChain4j-instrumented example for
  `btrace-llm-trace`: [BTrace Tutorial, Lesson 12 — AI/LLM Application Observability](../BTraceTutorial.md)
- `ContractService`'s full API (call-rate limits, range checks, null checks, and tagged code-path
  comparison — this tutorial only used `checkLatency`):
  [BTrace Tutorial, Lesson 11 — Runtime Contracts](../BTraceTutorial.md)
- Two more optional AI/ML extensions ship in this repo and follow the exact same `@Injected` +
  permission pattern: `btrace-rag-quality` (`RagQualityService` — vector-store query latency,
  result counts, similarity scores) and `btrace-gpu-bridge` (`GpuBridgeService` — ONNX
  Runtime/DJL/TensorFlow Java inference timing and GPU memory tracking). Both are covered
  conceptually in Lesson 12; neither has a dedicated hands-on lab yet.
- Full permission model, `btracex`, and what a *denied* privileged extension looks like end to end:
  [Tutorial 4 — Extensions and Permissions Without Tears](04-extensions-and-permissions.md)
- Everything else you can hook: [Quick Reference](../QuickReference.md)
