# We Gave Claude a JVM Debugger

### BTrace 3.0's MCP server turns "some orders are slow" into a diagnosis, with no oneliner typed by a human

There's a particular kind of relief that comes from not having to remember tool syntax at 2am.
BTrace 3.0 ships an MCP server that lets an AI coding assistant — Claude Code, or any other MCP
host — attach to a running JVM, deploy a probe, read back the numbers, and tell you what's wrong,
using plain English on your side of the conversation. You describe the symptom. It runs the
oneliners.

The premise is simple: the BTrace MCP server is a small process that speaks MCP (JSON-RPC 2.0)
over stdin/stdout. No listening socket, no network exposure — your AI client launches it, manages
its lifecycle, and calls its seven tools on your behalf. You build it once from the BTrace source
tree (`./gradlew :btrace-mcp-server:build`), point Claude Code at the resulting jar via a
`.mcp.json` file at your repo root, and from then on you're describing symptoms instead of typing
`btrace <PID> Script.java`.

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

Nothing above is scripted output massaged for a demo. It's five real tool calls — `list_jvms`,
`deploy_oneliner` twice, `detach_probe`, `list_probes` — chained by the model, each one a plain MCP
`tools/call` JSON-RPC request. The assistant decides *which* probe answers the question; BTrace's
oneliner compiler, verifier, and attach machinery do the rest, the same pipeline that's been
driving this tutorial series from the command line since lesson one. There's no BTrace-specific
magic baked into the model's side of this — just an assistant that knows how to read a tool
description and reach for the right one next.

If you'd rather skip the improvisation, the server also ships three ready-made prompt templates —
`profile_method`, `diagnose_slow_endpoint`, and `find_exception_source` — that turn a question like
"can you profile `OrderService.chargeCard`?" into a structured, step-by-step brief for the assistant
to execute, escalating from a quick oneliner to a full histogram script only if needed. Same tools
underneath, just sequenced by a template instead of invented turn by turn.

## The one thing to know before you rely on this

Here's the part that's easy to skim past and shouldn't be: this MCP server does not currently
expose a `reconnect` tool. In the transcript above, notice the assistant detached from the first
probe before deploying the second one — that's not incidental color, it's a real constraint the
server's design imposes, because it tracks one active session per PID. `detach_probe` doesn't kill
anything; the probe keeps running and collecting data on the target JVM. But once the assistant
walks away from it, nothing in this toolset can bring it back. `list_probes` can confirm a detached
probe is still out there, identified by a UUID and its generated class name — but it can't
`send_event` or `exit_probe` a probe you've detached from. Reconnecting to it takes the plain
`btrace -r` CLI flag, which lives entirely outside the assistant's reach.

Practically, that means `detach_probe` is one-way for anything you want the assistant itself to
clean up later. If you want the AI to be able to tear a probe down when you're done — rather than
leaving it running until someone with shell access notices — reach for `exit_probe` instead of
`detach_probe` whenever you can, and treat "detach" as a decision you're making on the AI's behalf,
not a pause button. It's a small asymmetry in a seven-tool surface, but it's exactly the kind of
thing you want to know on day one rather than discover mid-incident when a probe you thought you
could resume turns out to be permanently out of reach for anything but a human at a terminal.

None of this makes the tool less useful — the transcript above found a real bottleneck in five tool
calls and zero typed oneliners. It just means the honest shape of the tool includes a rough edge,
and it's worth knowing where it is before an AI assistant is the only thing standing between you
and a probe you can't get back.

---

- Hands-on tutorial: [docs/tutorials/05-mcp-server.md](../../docs/tutorials/05-mcp-server.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
- Questions, war stories, or "it did something weirder than this" reports: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
