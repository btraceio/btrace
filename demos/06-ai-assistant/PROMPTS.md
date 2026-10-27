# Rehearsed script: an AI assistant debugs the checkout service

A recorded session with Claude Code. The same prompts work in Codex or Pi once the plugin is
installed there. The assistant's behavior is non-deterministic: wording, probe shape and the number
of tool calls differ from run to run, so rehearse the whole session before recording and re-take
it if it breaks one of the "Do not record if" rules below.

The scenario: checkout is sometimes slow and a lot of orders end up in manual review, with no fraud
spike and nothing in the error logs. The service source is available to the assistant and is not
wrong; the vendor SDK it calls (`PaymentClient`, `FraudScreen`) is a binary only, so both causes
have to be found by probing the running JVM.

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

To record a session unattended instead, use `record-session.sh`; it sends the prompt below, word for word.

## The prompt

> Customers report that checkout is sometimes slow, and a lot of orders are ending up in manual
> review, yet there is no fraud spike and nothing in the error logs. The checkout service is
> running on this machine and we cannot restart it. Its source is in this directory; payments and
> fraud screening come from a vendor SDK we only have as lib/acme-sdk.jar. Find the root cause of
> both problems, down to what happens inside the SDK, and back every claim with numbers from the
> running service. Keep probes narrow, and leave nothing attached when you are done.

Good answer:

- It reads `CheckoutServiceApp.java` and sees that a `HOLD` verdict from `fraud.screen` sends the
  order to manual review, and that the service code itself has no fault.
- It calls `list_jvms` and picks the checkout service.
- It probes narrowly, first the SDK entry points (`FraudScreen.screen`, `PaymentClient.authorize`),
  then into `com.acme` internals (a short entry trace, or a duration filter such as
  `if duration>200ms`) to find the private methods. Probing `*::*` is not needed.
- It finds that `refreshSession` takes 300-450 ms about once a second and holds up all four workers
  at once, and that `deepScore` throws `RiskServiceException`, which `screen` turns into `HOLD`.
- It backs each claim with numbers from probe output read with `read_probe_output` (optionally with
  `wait_seconds`): outlier durations, how many throws matched how many holds, and that throws are as
  fast as successful calls.
- It says which parts are inferred rather than measured (for example the shared lock, when it saw
  only the pattern of the stalls).
- It removes each probe with `exit_probe` before deploying the next, and ends with `list_probes`
  showing nothing attached.

Watch for: `detach_probe` instead of `exit_probe`; a leftover probe; a conclusion that is not backed
by numbers from the probe; probing `*::*`; a cause stated as fact that no probe showed.

If you ask a follow-up such as "Double-check that no BTrace probe is still running", a good answer
calls `list_probes` for the PID (it also reports probes that were detached and are still running)
and states plainly that nothing is running. `list_jvms` may still show `BTrace: attached`; that
means the agent is still loaded in the JVM until it restarts, not that a probe is running.

## Do not record if

- The assistant asks you to run BTrace in trusted or unsafe mode by hand (the MCP tools have no
  such switch, and the verifier is supposed to stay on).
- It widens the scope to `*::*`, a whole package, or the JVM when a class or method was enough.
- It leaves a probe attached at the end of the session, or detaches where it should exit.
- It deploys a new probe without removing the previous one.
- It prints argument values, request bodies, or other data you would not want on screen.
- It prints order ids or amounts.
- It guesses without reading probe output.
- It claims a cause it did not observe without saying it is inferred.
- It never calls the BTrace tools and answers from general knowledge.

## After the session

Stop the service: `kill <PID>`. The log is under `${TMPDIR:-/tmp}/btrace-demo-06-ai-assistant/app.log`.
