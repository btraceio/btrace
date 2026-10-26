# BTrace 3.0.0 — announcement (canonical copy)

Status: draft, 2026-10-26. Source of truth for the channel variants in this directory. Every claim
traces to `.github/release-notes/v3.0.0.md` or a page under `docs/`.

---

**BTrace 3.0.0: trace a live JVM in one line, let your coding agent do it for you, and attach to
production without fear.**

BTrace, the dynamic tracing tool for the Java platform, ships its first major release since 2.2.6.
Three things change the day-to-day for anyone who already traces JVMs:

- **No file, no compile.** `btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <pid>`
  attaches to a running JVM and answers the question on the spot. When a oneliner is not enough,
  the new flat DSL drops the `BTraceUtils.` prefixes and the import block, so a full script is
  about half the size it was in 2.x.
- **Your coding agent can run it.** The `btrace-observability` plugin for Claude Code, Codex and
  Pi takes an incident question such as "why is `POST /orders` slow in this pod", writes a narrow
  probe, attaches, reads the output and retracts the probe when the observation window ends. It
  ships from the separate [btraceio/agent-plugins](https://github.com/btraceio/agent-plugins)
  repository. Three bundled extensions cover AI workloads from the inside: LLM calls (tokens,
  latency, cost, budgets), RAG retrieval quality, and on-device inference through ONNX, DJL and
  TensorFlow Java.
- **Production-safe attach.** A JVM started with `-javaagent:btrace.jar=port=0` exposes a
  loopback, token-protected endpoint; `btrace doctor <pid>` reports readiness before anything is
  loaded; the client finds the token through the Attach API with no flags; and when the client
  exits, the probe's `invokedynamic` call sites become no-ops, so a retracted probe leaves no
  lingering overhead.

Under the hood: one `btrace.jar` replaces the three-jar layout, the project moves to the
`io.btrace` namespace under the Apache License 2.0, a binary wire protocol (V2) is the default with
automatic V1 fallback, and a second instrumentation backend built on the JDK ClassFile API keeps
class files newer than the ASM ceiling traceable. Extensions replace the old `libs=` profiles and
the `Aggregations` API, with permissions declared by the extension and granted on the agent side.

Upgrading: compiled probes from 2.x need no change. Source scripts need a one-line import fix,
which `scripts/migrate-btrace-script.sh` applies. Full details in the
[migration guide](https://github.com/btraceio/btrace/blob/v3.0.0/docs/Migration-2.x-to-3.0.md).

Install:

```sh
sdk install btrace
jbang io.btrace:btrace:3.0.0 <pid> ...
docker pull ghcr.io/btraceio/btrace:3.0.0
```

Release notes and downloads: https://github.com/btraceio/btrace/releases/tag/v3.0.0

---

**Boilerplate.** BTrace is a safe, dynamic tracing tool for the Java platform. It instruments a
running JVM with user-written probes and has been open source since 2008.

**Numbers worth quoting (verified):** first major release since November 2024; runs on Java 8
through 27; Java < 17 targets deprecated, removed in 4.0.
