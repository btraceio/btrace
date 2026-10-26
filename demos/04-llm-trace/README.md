# Demo 04 - See what your LLM calls cost, live

An AI ticket summarizer calls a chat completion API for every ticket.
About 1 call in 12 "runs away": 14x the output tokens and seconds of latency.
The app itself only prints `answered N tickets`, so nobody sees the token bill or the slow calls.
One probe attached to the running JVM adds token totals, estimated cost and a 500 ms latency budget.
The runaway calls show up as budget violations; no code change, no restart.

## Run it

`./run.sh` plays the whole story (add `--keep` to leave the app running). By hand, in two terminals:

```sh
# Terminal 1: the app, with the two extensions allowed for this JVM only
java -Dbtrace.permissions=permissions.properties LlmTicketApp.java

# Terminal 2: look at the extension, then attach the probe (PID from `jps`)
btracex inspect btrace-llm-trace
btrace <PID> LlmTraceProbe.java
```

## You should see

```
$ btracex inspect btrace-llm-trace
Extension: btrace-llm-trace
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [THREADS]
Services : io.btrace.llm.LlmTraceService

$ btrace <PID> LlmTraceProbe.java
=== LLM + Contracts Dashboard ===
=== LLM Trace Summary ===
Model: gpt-4o-mini (openai)
  Calls: 20
  Tokens: 2571 in / 5013 out (avg 128/250)
  Latency: avg 464ms, min 181ms, max 3635ms
  Est. cost: $0.0034
--- Totals ---
  Calls: 20
  Tokens: 2571 in / 5013 out
  Est. total cost: $0.0034
=== Contract Summary ===
Contract: chat/latency
  Checks: 20 | VIOLATIONS: 1 (5%)
  Latency: avg 464ms, min 181ms, max 3635ms
  Last: Latency 3635ms exceeded budget 500ms
--- Totals ---
  Checks: 20
  Violations: 1
==================================
```

A new dashboard appears every 5 seconds; numbers vary run to run. Violations are
the runaway calls: they drag `max` latency into seconds and inflate the output-token and cost lines.

Full walkthrough: [Tutorial 7 - LLM observability](../../docs/tutorials/07-llm-observability.md).
