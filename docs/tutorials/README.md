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
| 05 | [Let an AI Assistant Debug Your JVM in 10 Minutes](05-mcp-server.md) | wire the BTrace MCP server into an AI coding assistant and run a real diagnostic conversation | ~10 min |
| 06 | [Write Your Own Extension in 30 Minutes](06-write-your-own-extension.md) | scaffold, build, install, and grant permissions for your own `io.btrace.extension` Gradle-plugin extension | ~25 min |
| 07 | [Watch Your LLM App Think](07-llm-observability.md) | put a latency budget on an LLM call and read token/latency/cost and budget violations off one combined dashboard | ~10 min |
| 08 | [One JAR to Rule Them All: Building a BTrace Fat Agent](08-fat-agent.md) | package BTrace plus a real extension into a single `-javaagent` JAR with the Gradle and Maven fat-agent plugins | ~15 min |
| 09 | [BTrace in Kubernetes, the Sidecar Way](09-kubernetes-sidecar.md) | package BTrace into three container variants and attach across a container boundary, as a sidecar or a baked-in `-javaagent` | ~15 min |

## Coming next

This wave-2 batch closes out the planned hands-on series (tracked in
`internal/plans/2026-07-11-v3.0.0-tutorials-plan.md`). A contingent T10 (BTrace Console TUI) exists
only if that exploratory interface is greenlit for a future release.

## Conventions

- **"You should see"** blocks show realistic output; exact numbers will differ.
- Commands are complete except for `<PID>` (get it with `jps`).
- Every tutorial ends with a cleanup step and "Go deeper" links.
- All commands are verified against the BTrace version noted in the tutorial before publishing.
