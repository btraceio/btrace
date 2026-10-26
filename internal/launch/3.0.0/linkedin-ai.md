# LinkedIn — AI post (coding agents and AI workloads)

Status: draft, 2026-10-26. About 200 words. Attach the coding-agent recording from demo 06
(rehearsed, hand-picked question) or, failing that, `docs/tutorials/images/04-llm-trace.gif`.

---

The one thing that kept BTrace niche for fifteen years: at 2 a.m. during an incident, nobody
remembers the probe syntax.

BTrace 3.0.0 removes that. Install the btrace-observability plugin in Claude Code, Codex or Pi and
ask in plain words:

"Why is POST /orders slow in this pod?"

The agent picks a narrow scope, writes the probe, attaches to the live JVM, reads the output, and
retracts the probe when the observation window ends. It defaults to method names, durations and
exception types, never request bodies or credentials, and it never assumes trusted mode. The
plugin is a set of skills with a local MCP server underneath, and it lives in the
btraceio/agent-plugins repository.

The release also looks inside AI workloads, with no SDK in the app and no log shipper:

- btrace-llm-trace: tokens, latency and cost per model, with latency budgets that count violations
- btrace-rag-quality: retrieval versus generation time, empty retrievals, top similarity scores
- btrace-gpu-bridge: inference and model-load timing for ONNX Runtime, DJL and TensorFlow Java

All three are plain BTrace probes against the client library your app already calls.

Plugin: https://github.com/btraceio/agent-plugins
Tutorial: https://github.com/btraceio/btrace/blob/v3.0.0/docs/tutorials/07-llm-observability.md
Release notes: https://github.com/btraceio/btrace/releases/tag/v3.0.0

#java #jvm #observability #llm #claudecode
