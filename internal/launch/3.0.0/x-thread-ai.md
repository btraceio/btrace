# X — AI thread (coding agents and AI workloads)

Status: draft, 2026-10-26. Six posts, each under 280 characters. Post 1 carries the coding-agent
recording from demo 06; post 4 carries `docs/tutorials/images/04-llm-trace.gif`.

---

1/
The thing that kept BTrace niche: at 2 a.m. nobody remembers the probe syntax.

BTrace 3.0.0: install the btrace-observability plugin in Claude Code, Codex or Pi and ask
"why is POST /orders slow in this pod?"

2/
The agent picks a narrow scope, writes the probe, attaches to the live JVM, reads the output, and
retracts the probe when the window ends.

Method names, durations, exception types by default. Never request bodies. Never trusted mode.

3/
It is skills first, MCP second: one plugin, eleven skills (latency, failures, runtime access over
SSH, Docker and Kubernetes, data safety, cleanup), with a local stdio MCP server underneath.

https://github.com/btraceio/agent-plugins

4/
3.0 also looks inside AI workloads with no SDK in the app:

btrace-llm-trace: tokens, latency, cost per model, latency budgets
btrace-rag-quality: retrieval vs generation time, empty hits, top scores
btrace-gpu-bridge: ONNX, DJL, TensorFlow Java inference timing

5/
All three are plain BTrace probes against the client library your app already calls. Attach,
read the summary, detach. Nothing left behind.

Tutorial: https://github.com/btraceio/btrace/blob/v3.0.0/docs/tutorials/07-llm-observability.md

6/
Release notes: https://github.com/btraceio/btrace/releases/tag/v3.0.0

Previous thread covered what changes for 2.x users: oneliners, the flat DSL, and attaching to
production without fear.
