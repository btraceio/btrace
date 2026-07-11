# Let an AI Assistant Debug Your JVM in 10 Minutes

Skip the oneliners entirely — describe the symptom in plain English and let an AI coding
assistant attach BTrace, deploy probes, and read back the results for you. This tutorial wires
the BTrace MCP server into Claude Code and walks through a real diagnostic conversation.

**Persona:** anyone who already lives inside an AI coding assistant. **Time:** ~10 minutes
(the conversation itself takes seconds).

## What you'll need

- JDK 11 or newer on your PATH (for both the MCP server and the demo app)
- A checkout of the BTrace source tree — the MCP server isn't part of the binary distribution,
  so you build it yourself (Step 1)
- [Claude Code](https://docs.claude.com/claude-code) installed and logged in — other MCP hosts
  (Cursor, VS Code, Claude Desktop, ...) work too; see [MCPServer.md](../MCPServer.md) for their
  config formats

## Step 1 — Build the MCP server

The MCP server is a thin jar: it declares `Main-Class: io.btrace.mcp.BTraceMcpServer`, but it
depends on the other BTrace modules (`btrace-core`, `btrace-client`, `btrace-compiler`,
`btrace-boot`) at runtime instead of bundling them. Build it from the root of your checkout:

```sh
./gradlew :btrace-mcp-server:build
```

**You should see** a successful build ending in something like:

```
BUILD SUCCESSFUL in 14s
```

This produces `btrace-mcp-server/build/libs/btrace-mcp-server-<version>.jar`. Because the jar
isn't self-contained, gather it together with `btrace-core`, `btrace-client`, `btrace-compiler`,
`btrace-boot`, and their dependency jars into one directory — this tutorial calls that directory
`/path/to/btrace-mcp-libs`, the same placeholder used in [MCPServer.md](../MCPServer.md).

> **What just happened?** You now have a directory that, when put on a classpath with
> `java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer`, starts a process that
> speaks MCP (JSON-RPC 2.0) over stdin/stdout. That's the entire "protocol" — no listening socket,
> no network exposure. Your AI client launches this process itself and manages its lifecycle; you
> never run it by hand in normal use.

## Step 2 — Start the patient

Same demo app as the rest of the series ([demo/DemoApp.java](demo/DemoApp.java)): a small
order-processing service with a hidden latency bug. Run it in a terminal:

```sh
java DemoApp.java
```

**You should see**:

```
[demo] order service running - stop with Ctrl+C
[demo] processed 61 orders, 7 failed
```

Leave it running. This is the JVM your AI assistant will investigate — you won't touch it again
by hand.

## Step 3 — Give your AI assistant a way in

Claude Code reads project-scope MCP configuration from a `.mcp.json` file at the repository root,
so everyone working in the repo shares the same setup. Create it:

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  }
}
```

Replace `/path/to/btrace-mcp-libs` with the directory from Step 1. (If you'd rather register the
server for yourself only, not the whole project: `claude mcp add --scope user btrace -- java -cp
"/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer`.)

## Step 4 — Confirm the assistant can see BTrace

```sh
claude mcp list
```

**You should see** `btrace` listed with a connected status (the exact formatting depends on your
Claude Code version — what matters is that it doesn't show an error or "failed to connect"). If
you started Claude Code before creating `.mcp.json`, restart it first.

## Step 5 — Ask, don't type

Open a Claude Code session in the repo and describe the symptom instead of a command. What
follows is a realistic transcript — the exact millisecond values are illustrative (this is the
same demo app from [Tutorial 1](01-first-trace-in-2-minutes.md), where `chargeCard` hits a
simulated slow payment provider on roughly 10% of calls, adding 250–400 ms), but the sequence of
tool calls is exactly how the server's seven tools are meant to be chained.

---

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

---

> **What just happened?** The assistant chained five of the MCP server's seven tool calls —
> `list_jvms`, `deploy_oneliner` (twice), `detach_probe`, `list_probes` — using exactly the
> parameters those tools declare (`pid` and `oneliner` as strings, `port` defaulting to `2020`).
> Nothing here is BTrace-specific magic on the model's part: each call is a plain MCP
> `tools/call` JSON-RPC request: the assistant decides *which* probe answers the question, and
> BTrace's oneliner compiler, verifier, and attach machinery do the rest — the same pipeline
> [Tutorial 1](01-first-trace-in-2-minutes.md) drove from the command line.
>
> One thing worth knowing about `list_probes`: it doesn't list every probe currently streaming
> output to a client — it lists probes a client has *detached* from and could reconnect to (the
> same set the plain CLI's `-r` flag targets), identified by a UUID and the generated class name,
> not by the oneliner text. A probe that's still connected never shows up there, because you're
> already receiving its output directly — that's why the wildcard probe deployed a moment later
> doesn't appear in any `list_probes` call for the rest of this tutorial. Also worth knowing: this
> server doesn't currently expose a `reconnect` tool, so once the assistant detaches from a probe,
> `list_probes` can confirm it's still out there, but nothing in this toolset can `send_event` or
> `exit_probe` it afterward — reconnecting takes the plain `btrace -r` CLI, outside the assistant's
> reach. Treat `detach_probe` as one-way for anything you want the assistant itself to clean up
> later; prefer `exit_probe` for that.

## Step 6 — Skip the improvisation: built-in diagnostic prompts

The transcript above is the assistant improvising with `deploy_oneliner` and `list_probes`. For
common investigations, the server also ships ready-made prompt templates that generate a
structured plan instead. `profile_method` fits "why are some orders slow" well:

**You:** Can you profile `OrderService.chargeCard`?

The `profile_method` prompt (arguments `class_name: "OrderService"`, `method_name: "chargeCard"`)
returns a step-by-step brief for the assistant to follow — starting with the same kind of
oneliner you saw above, then escalating to a full script if needed:

```
# Profiling Method: OrderService::chargeCard

## Step 1: Find the target JVM
Use the `list_jvms` tool to find the target JVM PID.

## Step 2: Quick latency check with oneliner
Start with a simple oneliner to see individual call durations:
OrderService::chargeCard @return { print method, duration }

## Step 3: Detailed profiling with histogram
For a latency distribution, deploy this BTrace script:
[a full script with a call counter, a running total, and an @OnEvent summary handler]

## Step 4: Get summary
Use `send_event` to trigger the @OnEvent handler and get a summary.

## Step 5: Clean up
Use `exit_probe` to remove the instrumentation when done.
```

The assistant then executes that plan with `deploy_script`, `send_event`, and `exit_probe` —
the same tools, just sequenced by a template instead of improvised turn by turn. The other two
prompts, `diagnose_slow_endpoint` and `find_exception_source`, generate equivalent briefs for
"which layer of this request is slow" and "where does this exception get thrown," respectively.

## Step 7 — Clean up

The filtered probe from Step 5 is still attached and collecting data — it was never detached, so
`exit_probe` can reach it directly. When you're actually done, just ask:

**You:** Remove the BTrace probe from PID 54021.

*[calls `exit_probe` with `pid: "54021"`]*

```
Probe stopped and removed from PID 54021.
```

`exit_probe` fully de-instruments the JVM — unlike `detach_probe`, there's nothing left to
reconnect to afterward. Stop the demo app itself with `Ctrl+C` in its terminal.

## Troubleshooting

- **Tools don't appear in the chat / "failed to connect"** — verify the classpath directory in
  `.mcp.json` is correct and contains the server jar plus its dependencies; test it stand-alone
  with `java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer` (it should start and
  wait silently on stdin, with no stack trace on stderr). Restart Claude Code after editing
  `.mcp.json`.
- **`list_jvms` comes back empty, or a `com.sun.tools.attach` error** — you're likely running the
  server on a JRE instead of a full JDK, or on JDK 8 without `tools.jar` on the classpath. Use
  JDK 11+, or add `$JAVA_HOME/lib/tools.jar` to the classpath for JDK 8. See
  [MCPServer.md](../MCPServer.md#toolsjar-not-found).
- **`deploy_oneliner` fails with an attach exception** — the MCP server and the target JVM
  (DemoApp) must run as the same OS user. On JDK 21+, the target JVM needs
  `-XX:+EnableDynamicAgentLoading` for dynamic agent attachment to succeed.
- **A tool call mentions port 2020** — that's the BTrace agent's default listener port. If it's
  already in use by another probe or client, pass a different `port` argument to the deploy tools.

## Go deeper

- Every tool's parameters, every prompt's arguments, security model, and per-host config
  (Cursor, VS Code, Windsurf, Continue.dev, Zed, ...): [MCP Server Guide](../MCPServer.md)
- The conceptual walkthrough of MCP inside the full tutorial series: [BTrace Tutorial, Lesson 13](../BTraceTutorial.md)
