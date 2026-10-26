# BTrace Hands-On Tutorials

Scenario-driven, copy-paste-friendly walkthroughs. Each tutorial makes a time-boxed promise,
uses the same zero-dependency [demo app](demo/DemoApp.java) (`java DemoApp.java`, JDK 11+), and
shows the expected output after every command, so you always know you're on track.

These tutorials are hands-on labs; the concept-oriented companion is the
[BTrace Tutorial](../BTraceTutorial.md), and the reference lives in the
[Quick Reference](../QuickReference.md).

## Available tutorials

| # | Tutorial | You will | Time |
|---|---|---|---|
| 01 | [Your First Trace in 2 Minutes](01-first-trace-in-2-minutes.md) | find a latency bug and a swallowed exception in a live JVM with oneliners | ~5 min |
| 02 | [From Oneliner to Script: The Flat DSL](02-oneliner-to-script.md) | turn a oneliner into a version-controllable script and add `@TLS` state | ~10 min |
| 03 | [Upgrade from 2.x in 10 Minutes](03-upgrade-from-2x.md) | migrate scripts to the new package, and see why compiled probes need no changes at all | ~10 min |
| 04 | [Extensions and Permissions Without Tears](04-extensions-and-permissions.md) | grant, deny, and inspect extension permissions against a live JVM | ~10 min |
| 05 | [Let an AI Assistant Debug Your JVM](05-mcp-server.md) | install the `btrace-observability` plugin from the external [agent-plugins](https://github.com/btraceio/agent-plugins) marketplace and have an AI assistant find the slow method and the swallowed exception in a live JVM, then check that it cleaned up | ~10 min |
| 06 | [Write Your Own Extension in 30 Minutes](06-write-your-own-extension.md) | scaffold, build, install, and grant permissions for your own `io.btrace.extension` Gradle-plugin extension | ~25 min |
| 07 | [Watch Your LLM App Think](07-llm-observability.md) | put a latency budget on an LLM call and read token/latency/cost and budget violations off one combined dashboard | ~10 min |
| 08 | [One JAR to Rule Them All: Building a BTrace Fat Agent](08-fat-agent.md) | package BTrace plus a real extension into a single `-javaagent` JAR with the supported Gradle fat-agent plugin | ~15 min |
| 09 | [BTrace in Kubernetes, the Sidecar Way](09-kubernetes-sidecar.md) | package BTrace into three container variants and attach across a container boundary, as a sidecar or a baked-in `-javaagent` | ~15 min |
| 10 | [What Changed for 2.x Users, in 15 Minutes](10-whats-new-for-2x-users.md) | attach with a oneliner, run a 2.2.6-compiled probe untouched, migrate a 2.x source with one command, and swap `Aggregations` for `btrace-metrics` on one live JVM | ~15 min |
| 11 | [Watch Your RAG Pipeline and Your Models](11-ai-workload-observability.md) | see empty retrievals, similarity scores and embedding-model latency from a live RAG answerer, using two bundled extensions in one probe | ~10 min |
| 12 | [Attach to Production Without Fear](12-production-safe-attach.md) | start a JVM in prepared mode, check it with `btrace doctor`, attach with no flags, and watch the app's own throughput prove nothing is left behind after you exit | ~10 min |

## Demos

Each tutorial's commands are also packaged as a self-contained, replayable scenario under
[`demos/`](../../demos/README.md): the target app, the probe, a `run.sh` that plays the whole story,
and a README with the expected output. The `btrace-qe` harness replays those scenarios on every push,
so what a tutorial promises is what the build checks.

## Conventions

- **"You should see"** blocks show realistic output; exact numbers will differ.
- Commands are complete except for `<PID>` (get it with `jps`).
- Every tutorial ends with a cleanup step and "Go deeper" links.
- All commands are verified against the BTrace version noted in the tutorial before publishing.
