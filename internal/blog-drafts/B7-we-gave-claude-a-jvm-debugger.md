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
marketplace, as the `btrace-observability` plugin. Install that plugin in your AI host and you get a suite of skills (endpoint diagnostics, latency
analysis, failure analysis, probe lifecycle, data safety and more) that decide how to investigate;
underneath them it registers the stdio server, named `btrace`, which JBang launches from the plugin's bundled sources — on first use
JBang downloads the single masked `io.btrace:btrace` jar, so there's nothing to build and no
legacy `btrace-client.jar`/`btrace-agent.jar` layout to assemble. You need JBang and JDK 11 or
newer, and the assistant has to run on a host that can attach to the target JVM. From then on
you're describing symptoms instead of typing `btrace <PID> Script.java`. The
[plugin README](https://github.com/btraceio/agent-plugins/tree/main/plugins/btrace-observability)
has the host-specific installation steps and the list of skills it bundles.

## The transcript


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

The skills come first, and they are what make this more than a tool list. The plugin's skills tell
the assistant how to scope a probe (an exact method before a package, a package before a wildcard),
what not to print, and how long to leave the probe attached, and they point it at the server's
tools when it needs to act. The `btrace` server also ships three ready-made prompt templates,
`diagnose_slow_endpoint`, `find_exception_source` and `profile_method`, that turn a question like
"can you profile `OrderService.chargeCard`?" into a structured brief for the assistant to execute.
Same tools underneath, just sequenced by a template instead of invented turn by turn.

## The one thing to check before you rely on this

Here's the part that's easy to skim past and shouldn't be: "detach" and "exit" are not the same
thing, and the difference matters more when an assistant is driving. In the transcript above, the
assistant detached from the first probe before deploying the second one, because the server tracks
one active session per PID and port. Detaching doesn't kill anything: the probe keeps running and
collecting data on the target JVM, exactly as it does when you pick `6. detach client` from the
CLI's `Ctrl+C` menu. On the CLI you'd get back to it with `btrace -r <probe id> <PID>`. The server
has no resume tool. It can list detached probes (`list_probes` shows only those, not the ones still
attached in the current session) and it can stop one (`stop_detached_probe`), but it cannot hand
you back a live session or send a detached probe an event. The plugin's own guidance is to prefer
`exit_probe`, to call `list_probes` before deploying so probes left by an earlier session are found
instead of stacked on, and to stop someone else's listed probe only after you confirm it is no
longer needed.

Practically, that means you should treat a detach as a step that takes the probe out of the
assistant's hands for anything but stopping it. If you want the AI to be able to tear a probe down when you're
done — rather than leaving it running until someone remembers it — ask it to *exit*
the probe rather than detach from it, and treat "detach" as a decision you're making on the AI's
behalf, not a pause button. It's a small asymmetry, but it's exactly the kind of thing you want to
know on day one rather than discover mid-incident when a probe you thought you could resume turns
out to be stoppable but no longer steerable.

None of this makes the tool less useful — the transcript above found a real bottleneck in five tool
calls and zero typed oneliners. It just means the honest shape of the tool includes an edge worth
knowing about before an AI assistant is the only thing standing between you and a probe you can't
steer any more.

---

- Hands-on tutorial: [docs/tutorials/05-mcp-server.md](../../docs/tutorials/05-mcp-server.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
<!-- TODO: replace with the per-post Discussions thread before publishing -->
- Questions, war stories, or "it did something weirder than this" reports: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
