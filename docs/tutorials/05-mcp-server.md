# Let an AI Assistant Debug Your JVM

Describe a symptom to an AI coding assistant and let it attach BTrace to a live JVM, find the slow
method and the swallowed exception, and remove its probe afterwards. You will install the
`btrace-observability` plugin, ask four questions in plain English, and learn how to read what the
assistant did so you can trust (or veto) it.

![Demo 06: an assistant diagnoses the checkout service through the BTrace MCP server](images/06-ai-assistant.gif)

*An unedited, real session against [demo 06](../../demos/06-ai-assistant/README.md), rendered from its transcript; the MP4 is next to the GIF.*

**Persona:** anyone who would rather describe a problem than type a probe. **Time:** ~10 minutes.

## What you'll need

- JDK 11 or newer, and [JBang](https://www.jbang.dev/) on your PATH (the plugin's MCP server is launched with JBang and downloads the single `io.btrace:btrace` artifact on first use)
- [Claude Code](https://claude.com/claude-code). Codex and Pi are supported by the same plugin; see the note in step 1
- The BTrace repository checked out, for the demo app `demos/01-oneliner/OrderServiceApp.java`
- The AI host running on the same machine as the target JVM (the server uses the local Attach API; across SSH, Docker or Kubernetes you first have to run the host inside that environment)

## Step 1 — Install the plugin

The plugin lives in the [btraceio/agent-plugins](https://github.com/btraceio/agent-plugins)
marketplace, not in the BTrace distribution. In Claude Code:

```text
/plugin marketplace add btraceio/agent-plugins
/plugin install btrace-observability@btraceio-agent-plugins
```

**You should see** the plugin listed under `/plugin`, and a `btrace` server under `/mcp`. The plugin
bundles a suite of skills (endpoint diagnostics, latency analysis, failure analysis, probe
lifecycle, data safety and more) and the stdio MCP server named `btrace`.

> Codex and Pi are covered by the same repository: Pi installs with
> `pi install git:github.com/btraceio/agent-plugins`; Codex adds a cloned checkout as a marketplace
> (`codex plugin marketplace add /path/to/agent-plugins`) and then installs `btrace-observability`.
> The [agent-plugins README](https://github.com/btraceio/agent-plugins#install) is authoritative.

## Step 2 — Start the patient

From the BTrace repository root:

```sh
java demos/01-oneliner/OrderServiceApp.java
```

Demo 06 (`demos/06-ai-assistant/run.sh`) is a separate, harder scenario: a checkout service whose cause sits partly in a binary-only SDK. It has its own prompts in [PROMPTS.md](../../demos/06-ai-assistant/PROMPTS.md); the walkthrough below uses the order service.

**You should see** (numbers will vary):

```
[demo] order service running - stop with Ctrl+C
[demo] processed 64 orders, 8 failed
```

Roughly one `chargeCard` call in ten is slow, and `validateOrder` throws for about 8% of orders
while the app swallows the exception. Find the PID with `jps`; you will give it to the assistant.

## Step 3 — Ask the four questions

Open your AI host in any directory and ask these, one at a time. For the same kind of script, with
notes on what a good answer looks like, for the harder checkout-service scenario, see
[demos/06-ai-assistant/PROMPTS.md](../../demos/06-ai-assistant/PROMPTS.md).

1. `What Java processes are running on this machine?`
2. `The order service with PID <PID> processes orders and some are slow. Find which method in OrderService is slow and by how much. Keep the probe narrow and remove it when you are done.`
3. `Some orders fail and the app swallows the exception. Show me where it is thrown.`
4. `Confirm no BTrace probe is still attached.`

**You should see**, in outline (the exact wording, number of tool calls and probe text depend on the
assistant and differ every run):

- Prompt 1: a call to `list_jvms` and a list of JVMs, with `OrderServiceApp` marked as having no
  BTrace attached.
- Prompt 2: one or more `deploy_oneliner` calls, then a `read_probe_output` call to collect what the
  probe printed, then a conclusion that `chargeCard` takes roughly
  250-400 ms on about one call in ten and drags `processOrder` with it, then a probe removal.
- Prompt 3: a probe on the error path of `validateOrder` and the call stack leading to it.
- Prompt 4: a `list_probes` check and a plain statement that nothing is left.

## Step 4 — Read what the assistant did

Do not stop at the answer; read the tool calls. This is where you decide whether you would let it
loose on something that matters.

- **The probe.** For prompt 2 expect a oneliner shaped like
  `OrderService::* @return if duration>200ms { print method, time }`; for prompt 3 something like
  `OrderService::validateOrder @error { print method, stack }`. They are the same probes you wrote
  by hand in [tutorial 01](01-first-trace-in-2-minutes.md).
- **The scope.** It should be `OrderService` or one of its methods, not `*::*`. The plugin's
  guidance is an exact method for production, a bounded class or package for discovery, and a broad
  match only as an explicit, time-bounded escalation.
- **The data.** Method names, durations and stacks are fine; argument values, request bodies and
  credentials are not.
- **The verifier.** BTrace's verifier stays on by default. If the assistant asks for trusted or
  unsafe mode, say no.
- **The output.** The assistant gets probe results from `read_probe_output`, which returns what an
  active probe printed since the last read. With `wait_seconds` (0-30) it returns as soon as a report
  arrives. The server keeps up to 64 KB of unread output per probe and says how many of the oldest
  lines it dropped beyond that. Output is kept only for probes deployed in the current session. If the
  answer cites numbers, they should come from there.
- **The cleanup.** A probe should be ended with `exit_probe`, which stops and removes it and returns
  any output not yet read.
  `detach_probe` leaves the probe running on the JVM; it can then only be found with `list_probes`
  and stopped with `stop_detached_probe`, and only after you confirm it is no longer needed.

## Step 5 — Clean up

Make sure no probe is left (prompt 4 does exactly this), then stop the demo app with `Ctrl+C` in its
terminal, or `kill <PID>`. To remove the plugin, use `/plugin` in Claude Code.

## Troubleshooting

- **The assistant answers from general knowledge and never calls a BTrace tool** — the `btrace` MCP
  server is not connected. Check `/mcp`, that `jbang` is on the PATH of the host, and that JDK 11+
  is available. The first start downloads the BTrace artifact, so it can be slow.
- **`Can not attach to PID ...`** — run the AI host as the same OS user as the target JVM and on the
  same machine; see [Troubleshooting: attachment issues](../Troubleshooting.md#jvm-attachment-issues).
- **A different probe each time** — expected. The assistant chooses; compare it against the shapes
  in step 4 rather than against fixed output.
- **A probe is still running after a session** — ask the assistant to `list_probes` for the PID and
  stop it, or attach with `btrace -r <probe id> <PID>` and exit.
- Anything plugin-specific: check the plugin README.

## Go deeper

- The plugin and its skills: [btrace-observability README](https://github.com/btraceio/agent-plugins/tree/main/plugins/btrace-observability)
- The MCP server and how it relates to BTrace: [MCP Server](../MCPServer.md)
- The harder scenario, with a recorded session and a "do not record if" list: [demo 06](../../demos/06-ai-assistant/README.md) and its [PROMPTS.md](../../demos/06-ai-assistant/PROMPTS.md)
- Doing the same by hand: [Your First Trace in 2 Minutes](01-first-trace-in-2-minutes.md)
