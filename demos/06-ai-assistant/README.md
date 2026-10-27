# Demo 06 - Let an AI assistant debug your JVM

![Demo 06: an assistant diagnoses the checkout service through the BTrace MCP server](../../docs/tutorials/images/06-ai-assistant.gif)

*An unedited, real Claude Code session, rendered from its transcript ([session.jsonl](session.jsonl)); the MP4 is next to the GIF.*

**Story.** A checkout service is running. Customers say checkout is sometimes slow and some orders
never complete, and nothing shows up in the logs. The service cannot be restarted. Instead of typing
a BTrace command, you describe the symptom to an AI coding assistant (Claude Code, Codex or Pi) with
the `btrace-observability` plugin installed. It reads the service source, finds the JVM, deploys a
narrow probe through the BTrace MCP server, reads what the probe printed, and removes the probe.

The cause sits partly in a library the assistant cannot read. The service calls a vendor SDK that is
shipped as a binary only, so the source explains what `checkout` does but not what happens inside
`PaymentClient` or `FraudScreen`. To see that, the assistant has to probe the live JVM.

| File | Role |
|---|---|
| `checkout-service/CheckoutServiceApp.java` | the target: four worker threads call `CheckoutService.checkout`, which screens the order for fraud (a `RiskServiceException` is swallowed and the order is dropped, left for a nightly batch), reserves inventory, then authorizes the payment |
| `checkout-service/lib/acme-sdk.jar` | a stand-in vendor SDK, binary only on purpose: `PaymentClient` and `FraudScreen` |
| `vendor/src` | the SDK sources, kept for maintainers; rebuild the JAR with `vendor/build-sdk.sh` (needs `JAVA_HOME`) |
| `run.sh` | starts the checkout service and leaves it running for a live session |
| `PROMPTS.md` | the rehearsed prompts, what a good answer looks like, and the "Do not record if" rules |
| `record-session.sh` | records a real headless session and renders it |
| `session.jsonl` | the raw stream-json transcript of the committed recording |

Inside the SDK, `PaymentClient.authorize` needs a gateway session. Every 12 calls the private
`refreshSession` takes 300-450 ms while holding the client's lock, so concurrent checkouts wait too.
`FraudScreen.screen` sends orders of 500.00 or more through a private `deepScore` that throws
`RiskServiceException` about half the time, so about 20% of all orders are dropped.

## Run it live

```sh
export JAVA_HOME=/path/to/jdk-11-or-newer
./run.sh
```

The service needs JDK 11 or newer (it is launched from source, as
`java -cp lib/acme-sdk.jar CheckoutServiceApp.java` in `checkout-service/`). Open your AI host in
`demos/06-ai-assistant/checkout-service` with the plugin installed, then follow
[PROMPTS.md](PROMPTS.md). Stop the service with the `kill` command `run.sh` prints.

## Record a session

```sh
AGENT_PLUGINS_DIR=~/src/agent-plugins ./record-session.sh
```

Requirements:

- the `claude` CLI, logged in (`claude auth login`)
- JBang
- a checkout of [btraceio/agent-plugins](https://github.com/btraceio/agent-plugins)
  (`AGENT_PLUGINS_DIR`, default `../../../agent-plugins` relative to this directory)
- `JAVA_HOME` and `BTRACE_HOME` set as for the other demos
- Python 3 with Pillow (`PYTHON` picks the interpreter) and ffmpeg for the MP4

The model is `DEMO_MODEL` (default `sonnet`). The script copies only the service source and the SDK
JAR into a scratch project directory and runs a headless session (`claude -p`) there with only the
`btrace-observability` plugin, `--strict-mcp-config` with the BTrace MCP server as the only MCP
server, and only `Read`, `Grep`, `Glob` and the BTrace MCP tools allowed. It sends two prompts and keeps
the raw transcript in the demo's log directory. The copy saved as `session.jsonl` goes through
`demos/lib/redact-transcript.py` first: home and temp paths become `~` and `/tmp`, and JVM listings
keep only the processes the session probed or mentioned. The script then renders
`docs/tutorials/images/06-ai-assistant.gif` and `.mp4` with `demos/lib/render-chat.py`. To
re-render without re-recording:

```sh
python3 ../lib/render-chat.py session.jsonl ../../docs/tutorials/images/06-ai-assistant \
  "claude  —  checkout service on call"
```

The renderer shows only what is in the transcript. It shortens long tool results, folds repeated
empty reads, and summarises JVMs on the machine that the session never probed or mentioned.

## What the committed take shows

The assistant (claude-sonnet-5-5) lists the JVMs, reads `CheckoutServiceApp.java`, and deploys one
script probe with `deploy_script` that times `FraudScreen.screen` (return and error) and
`PaymentClient.authorize`, with an `@OnTimer(5000)` report. It reads the report once with
`read_probe_output` (`wait_seconds` 12), stops the probe with `exit_probe`, and checks `list_probes`.

Its findings: `screen` threw `RiskServiceException` on 22 of 104 calls (about 21%) and `checkout`
swallows it, so those orders never complete and nothing is logged; `authorize` averaged about
129 ms with a worst case of 449 ms over 76 calls, while fraud screening took about 15 ms (max 41).
For the second prompt it confirms that no probe is running and explains that `list_jvms` still shows
"BTrace: attached" because the agent stays loaded in the JVM until it restarts.

## Check a take before publishing

The assistant is non-deterministic. Read the transcript against the "Do not record if" rules in
[PROMPTS.md](PROMPTS.md) (scope, leftover probes, printed data, answers given without reading probe
output) and re-record if any is broken.

## Harness cases

`btrace-qe` replays the scenario with three cases in `btrace-qe/cases/demos/06-checkout-*.yaml`:

- `com.acme.payments.PaymentClient::* @return if duration>200ms { print method, duration }`, which
  names `refreshSession`, a private method of a library with no source
- `CheckoutService::checkout @return if duration>200ms { print method, duration }`
- `com.acme.risk.FraudScreen::screen @error { print method, stack }`

The plugin lives in the [btraceio/agent-plugins](https://github.com/btraceio/agent-plugins)
repository. The matching tutorial is [tutorial 05](../../docs/tutorials/05-mcp-server.md).
