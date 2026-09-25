# We Gave Claude a JVM Debugger

### BTrace's MCP server turns "some orders are slow" into a diagnosis, with no oneliner typed by a human

There's a particular kind of relief that comes from not having to remember tool syntax at 2am.
BTrace 3.0 comes with an MCP server — shipped through the `btrace-observability` agent plugin
rather than in the BTrace jar — that lets an AI coding assistant (Claude Code, Codex, Pi, or any
other MCP host) attach to a running JVM, deploy a probe, read back the numbers, and tell you what's wrong,
using plain English on your side of the conversation. You describe the symptom. It runs the
oneliners.

The premise is simple: the BTrace MCP server is a small local process that speaks MCP over
stdio. No listening socket, no network exposure — your AI client launches it, manages its
lifecycle, and calls its tools on your behalf. It no longer lives in the BTrace source tree or the
binary distribution: it's part of the [BTrace Agent Plugins](https://github.com/btraceio/agent-plugins)
marketplace, as the `btrace-observability` plugin. Install that plugin in your AI host and it
registers the stdio server, which JBang launches from the plugin's bundled sources — on first use
JBang downloads the single masked `io.btrace:btrace` jar, so there's nothing to build and no
legacy `btrace-client.jar`/`btrace-agent.jar` layout to assemble. You need JBang and JDK 11 or
newer, and the assistant has to run on a host that can attach to the target JVM. From then on
you're describing symptoms instead of typing `btrace <PID> Script.java`. The
[plugin README](https://github.com/btraceio/agent-plugins/tree/main/plugins/btrace-observability)
has the host-specific installation steps and the current tool list.

## The transcript

<!-- VERIFY against btraceio/agent-plugins: the tool names (list_jvms, deploy_oneliner, detach_probe, list_probes, send_event, exit_probe), the tool output text, and the "one active session per PID" remark below were recorded against the MCP server that used to live in this repo (before ae2918d5 / #864 moved it out). Re-record or confirm against the current plugins/btrace-observability server before publishing. -->

Here's what that actually looks like in practice, against the same order-processing demo app
that's been the constant test subject of this whole tutorial series — a service with `chargeCard`
occasionally hitting a slow downstream dependency.

**You:** Some orders are coming out slow. Can you find out why?

**AI:** Let me see what's running on this machine.

*[calls `list_jvms`]*

```
Discovered Java VMs:

  PID: 54021  |  Main Class: DemoApp  |  BTrace: not attached
  PID: 54198  |  Main Class: org.gradle.launcher.GradleWrapperMain  |  BTrace: not attached
```

Found it — PID 54021. I'll time `processOrder` first to see the shape of the problem.

*[calls `deploy_oneliner` with `pid: "54021"`, `oneliner: "OrderService::processOrder @return { print method, time }"`]*

```
Probe deployed successfully to PID 54021.
Oneliner: OrderService::processOrder @return { print method, time }
Port: 2020

Initial output:
processOrder
execution time: 64 ms
processOrder
execution time: 71 ms
processOrder
execution time: 318 ms

Use send_event, detach_probe, or exit_probe to interact with the running probe.
```

Most calls finish under 100 ms, but some spike past 300 ms. That confirms there's a real spike,
but not its cause. I want to attach a wider probe to the same PID next, and this server tracks one
active session per PID — so let me detach from this one first.

*[calls `detach_probe` with `pid: "54021"`]*

```
Detached from probe on PID 54021. Probe continues running.
```

Let me just confirm that's the only thing left running before I add a new probe.

*[calls `list_probes` with `pid: "54021"`]*

```
Active probes on PID 54021:
1: 3f1c2a9e-8b7d-4e21-9c4a-6f5d0a1b2c3d [BTraceOneliner_1752219841233]
```

Good, that's the one I just detached from. Now let me widen the probe to every method in
`OrderService` and keep only the slow calls, so I can see what's inside `processOrder` when it's
slow.

*[calls `deploy_oneliner` with `pid: "54021"`, `oneliner: "OrderService::* @return if duration>200ms { print method, time }"`]*

```
Probe deployed successfully to PID 54021.
Oneliner: OrderService::* @return if duration>200ms { print method, time }
Port: 2020

Initial output:
chargeCard
execution time: 337 ms
processOrder
execution time: 351 ms

Use send_event, detach_probe, or exit_probe to interact with the running probe.
```

`chargeCard` and `processOrder` show up together every time — `chargeCard` is what's dragging
`processOrder` down.

**AI:** Found it: `chargeCard` is the bottleneck. It's fast almost all the time, but roughly one
call in ten takes 250–400 ms — consistent with a slow downstream dependency, like a payment
provider having occasional latency spikes — and every one of those slow calls shows up as a slow
`processOrder` for the customer. I've left a filtered probe attached to PID 54021 so you can keep
watching `chargeCard` in real time; just ask me to remove it when you're done.

Nothing above is scripted output massaged for a demo. It's five real tool calls — list the JVMs,
deploy a oneliner twice, detach, list the probes — chained by the model, each one a plain MCP
`tools/call` request. The assistant decides *which* probe answers the question; BTrace's
oneliner compiler, verifier, and attach machinery do the rest, the same pipeline that's been
driving this tutorial series from the command line since lesson one. There's no BTrace-specific
magic baked into the model's side of this — just an assistant that knows how to read a tool
description and reach for the right one next.

<!-- VERIFY against btraceio/agent-plugins: the prompt template names profile_method / diagnose_slow_endpoint / find_exception_source came from the in-repo server; confirm the plugin still ships them (and under these names) or drop this paragraph. -->
If you'd rather skip the improvisation, the server we recorded this against also shipped ready-made
prompt templates (at the time: `profile_method`, `diagnose_slow_endpoint`, and
`find_exception_source`) that turn a question like "can you profile `OrderService.chargeCard`?"
into a structured, step-by-step brief for the assistant to execute, escalating from a quick oneliner
to a full histogram script only if needed. Same tools underneath, just sequenced by a template
instead of invented turn by turn — check the plugin README for what the current release provides.

## The one thing to check before you rely on this

<!-- VERIFY against btraceio/agent-plugins: whether the current server exposes a reconnect tool, whether list_probes shows only detached probes, and whether one session per PID is still enforced. The paragraph below is written to hold either way; tighten it once confirmed. -->
Here's the part that's easy to skim past and shouldn't be: "detach" and "exit" are not the same
thing, and the difference matters more when an assistant is driving. In the transcript above, the
assistant detached from the first probe before deploying the second one. Detaching doesn't kill
anything — the probe keeps running and collecting data on the target JVM, exactly as it does when
you pick `6. detach client` from the CLI's `Ctrl+C` menu. On the CLI you'd get back to it with
`btrace -r <probe id> <PID>`. When we recorded this session, the MCP server had no equivalent
reconnect tool: it could list the detached probe, but not send it an event or shut it down. Whether
the current plugin closes that gap is something to confirm in its README before you lean on it.

Practically, that means you should treat a detach as potentially one-way for anything you want the
assistant itself to clean up later. If you want the AI to be able to tear a probe down when you're
done — rather than leaving it running until someone with shell access notices — ask it to *exit*
the probe rather than detach from it, and treat "detach" as a decision you're making on the AI's
behalf, not a pause button. It's a small asymmetry, but it's exactly the kind of thing you want to
know on day one rather than discover mid-incident when a probe you thought you could resume turns
out to be reachable only by a human at a terminal.

None of this makes the tool less useful — the transcript above found a real bottleneck in five tool
calls and zero typed oneliners. It just means the honest shape of the tool includes an edge worth
knowing about before an AI assistant is the only thing standing between you and a probe you can't
get back.

---

- Hands-on tutorial: [docs/tutorials/05-mcp-server.md](../../docs/tutorials/05-mcp-server.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
<!-- TODO: replace with the per-post Discussions thread before publishing -->
- Questions, war stories, or "it did something weirder than this" reports: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
