# Rehearsed script: an AI assistant debugs the checkout service

A recorded session with Claude Code. The same prompts work in Codex or Pi once the plugin is
installed there. The assistant's behavior is non-deterministic: wording, probe shape and the number
of tool calls differ from run to run, so rehearse the whole session before recording and re-take
it if it breaks one of the "Do not record if" rules below.

The scenario: checkout is sometimes slow and some orders never complete, with nothing in the logs.
The service source is available to the assistant, but the vendor SDK it calls (`PaymentClient`,
`FraudScreen`) is a binary only, so the cause has to be found by probing the running JVM.

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
3. Start the service from this directory:

   ```sh
   ./run.sh
   ```

4. Open the AI host in `checkout-service/` (the directory that holds `CheckoutServiceApp.java`),
   so the assistant can read the service source.
5. Confirm the host sees the plugin and its `btrace` MCP server (in Claude Code: `/plugin` and
   `/mcp`).

To record a session unattended instead, use `record-session.sh`; it sends the same two prompts.

## Prompt 1 - the incident

> Customers report that checkout is sometimes slow, and that some orders never complete, but
> nothing shows up in the logs. The checkout service is running on this machine and we cannot
> restart it; its source is in this directory. Find out what is going on. Keep any probe narrow,
> and leave nothing attached when you are done.

Good answer:

- It reads `CheckoutServiceApp.java` and sees that `checkout` calls `fraud.screen`, swallows
  `RiskServiceException`, and returns, then calls `inventory.reserve` and `payments.authorize`.
- It calls `list_jvms` and picks the checkout service.
- It probes `FraudScreen.screen` and `PaymentClient.authorize` (a oneliner or a short script through
  `deploy_script`). Probing `com.acme.payments.PaymentClient::*` is also fine and reaches the private
  `refreshSession`; a duration filter such as `if duration>200ms` keeps it narrow.
- It reads the output with `read_probe_output` (optionally with `wait_seconds`), not by guessing.
- It reports that `screen` throws `RiskServiceException` on roughly 20% of orders and `checkout`
  swallows it, so those orders are dropped and nothing is logged, and that payment authorization is
  the slow step (if it probes inside the client, `refreshSession` at 300-450 ms).
- It removes the probe with `exit_probe`, which also returns any output not yet read, and says so.

Watch for: `detach_probe` instead of `exit_probe`; a leftover probe; a conclusion that is not backed
by numbers from the probe; probing `*::*`.

## Prompt 2 - confirm cleanup

> Double-check that no BTrace probe is still running on the checkout service.

Good answer: calls `list_probes` for the PID (which also reports probes that were detached and are
still running) and `list_jvms`, then states plainly that no probe is running. `list_jvms` may still
show `BTrace: attached`; a good answer explains that this means the agent is still loaded in the
JVM until it restarts, not that a probe is running. If a detached probe is listed it stops it with
`stop_detached_probe` only after asking you.
Watch for: "I believe it is gone" with no tool call; reading "BTrace: attached" as a running probe.

## Do not record if

- The assistant asks you to run BTrace in trusted or unsafe mode by hand (the MCP tools have no
  such switch, and the verifier is supposed to stay on).
- It widens the scope to `*::*`, a whole package, or the JVM when a class or method was enough.
- It leaves a probe attached at the end of any prompt, or detaches where it should exit.
- It prints argument values, request bodies, or other data you would not want on screen.
- It prints order ids or amounts.
- It guesses without reading probe output.
- It never calls the BTrace tools and answers from general knowledge.

## After the session

Stop the service: `kill <PID>`. The log is under `${TMPDIR:-/tmp}/btrace-demo-06-ai-assistant/app.log`.
