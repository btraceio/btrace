# Rehearsed script: an AI assistant debugs the order service

A recorded session with Claude Code. The same prompts work in Codex or Pi once the plugin is
installed there. The assistant's behavior is non-deterministic: wording, probe shape and the number
of tool calls differ from run to run, so rehearse the whole session before recording and re-take
it if it breaks one of the "Do not record if" rules below.

## Setup

1. Prerequisites for the plugin's MCP server: JBang and JDK 11 or newer. The AI host must run on
   the machine that runs the target JVM. The server downloads the `io.btrace:btrace` artifact on
   first use, so run one throwaway prompt before recording to warm it up.
2. Install the plugin in Claude Code, exactly as the plugin repository's README says:

   ```text
   /plugin marketplace add btraceio/agent-plugins
   /plugin install btrace-observability@btraceio-agent-plugins
   ```

   Codex and Pi: follow the Codex and Pi sections of the
   [agent-plugins README](https://github.com/btraceio/agent-plugins#install)
   (`pi install git:github.com/btraceio/agent-plugins` for Pi; for Codex, add the cloned checkout
   as a marketplace, then install `btrace-observability`). Restart the host if it asks you to.
3. Start the app from this directory and note the PID it prints:

   ```sh
   ./run.sh
   ```

4. Confirm the host sees the plugin and its `btrace` MCP server (in Claude Code: `/plugin` and
   `/mcp`). The first prompt below doubles as the check that it can see the JVM.

## Prompt 1 - discover

> What Java processes are running on this machine?

Good answer: calls `list_jvms` and lists the JVMs with their main classes and whether BTrace is
attached. `OrderServiceApp` is there, not attached; the assistant identifies its PID.
Watch for: it should not attach anything yet. If it does not call the tool and guesses with
`jps`, the MCP server is not connected; stop and fix the setup.

## Prompt 2 - slow method

> The order service with PID <PID> processes orders and some are slow. Find which method in
> OrderService is slow and by how much. Keep the probe narrow and remove it when you are done.

Good answer: the plugin's latency skill and oneliner triage apply. The probe shape is a oneliner
such as

```
OrderService::* @return if duration>200ms { print method, time }
```

or `OrderService::processOrder @return { print method, time }` first, then narrowing. Scope stays
inside `OrderService` (one class, method wildcard at most). The conclusion is that `chargeCard`
takes roughly 250-400 ms on about one call in ten, and `processOrder` is slow only because it
calls it. Cleanup: it calls `exit_probe` for the PID and says it did.
Watch for: `detach_probe` instead of `exit_probe`; a leftover probe; a stated observation window.

## Prompt 3 - swallowed exception

> Some orders fail and the app swallows the exception. Show me where it is thrown.

Good answer: an `@error` probe on the failing method, for example

```
OrderService::validateOrder @error { print method, stack }
```

(or a short script via `deploy_script` if it wants more detail). It reports `validateOrder` as
the origin, with the stack through `processOrder`, for roughly one order in twelve. It prints
method names and stacks only, not order contents. Cleanup: `exit_probe` again.
Watch for: probing `*::*` or all exceptions in the JVM; printing arguments.

## Prompt 4 - confirm cleanup

> Confirm no BTrace probe is still attached.

Good answer: calls `list_probes` for the PID (which reports probes that were detached and are
still running) and `list_jvms` (the `BTrace:` column), then states plainly that nothing is left.
If a detached probe is listed it stops it with `stop_detached_probe` only after asking you.
Watch for: "I believe it is gone" with no tool call.

## Do not record if

- The assistant uses trusted or unsafe mode (the verifier is supposed to stay on), or asks you to
  turn it on.
- It widens the scope to `*::*`, a whole package, or the JVM when a class or method was enough.
- It leaves a probe attached at the end of any prompt, or detaches where it should exit.
- It prints argument values, request bodies, or other data you would not want on screen.
- It never calls the BTrace tools and answers from general knowledge.

## After the session

Stop the app: `kill <PID>`. The log is under `${TMPDIR:-/tmp}/btrace-demo-06-ai-assistant/app.log`.
