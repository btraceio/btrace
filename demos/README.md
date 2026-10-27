# BTrace 3.0 demo scenarios

Self-contained, end-to-end demos of the features that change a BTrace user's day. Each directory
holds the target application, the probe or oneliner, a `run.sh` that plays the whole scenario, and
a README with the commands to type by hand and the output to expect. The scenarios are replayed
by the `btrace-qe` harness (`./gradlew :btrace-qe:qe -Pqe.surface=demos`), so what the README
promises is what the harness checks.

| Dir | Story | Pairs with |
|---|---|---|
| [01-oneliner](01-oneliner/) | attach to a live JVM with one line, find a latency bug and a swallowed exception | [tutorial 01](../docs/tutorials/01-first-trace-in-2-minutes.md) |
| [02-upgrade-from-2x](02-upgrade-from-2x/) | a probe compiled with BTrace 2.2.6 deploys untouched; the migration script fixes a source file | [tutorial 03](../docs/tutorials/03-upgrade-from-2x.md) |
| [03-metrics-and-permissions](03-metrics-and-permissions/) | p50/p95/p99 from `btrace-metrics`; a blocked extension is explained by `-le`, then allowed by policy | [tutorial 04](../docs/tutorials/04-extensions-and-permissions.md) |
| [04-llm-trace](04-llm-trace/) | tokens, cost and a latency budget on a mocked chat-completion client | [tutorial 07](../docs/tutorials/07-llm-observability.md) |
| [05-rag-and-inference](05-rag-and-inference/) | empty retrievals and inference timing from `btrace-rag-quality` and `btrace-gpu-bridge` | [tutorial 11](../docs/tutorials/11-ai-workload-observability.md) |
| [06-ai-assistant](06-ai-assistant/) | a coding agent with the `btrace-observability` plugin diagnoses a checkout service that drops orders and stalls on a binary-only SDK | [tutorial 05](../docs/tutorials/05-mcp-server.md) |
| [07-production-safe-attach](07-production-safe-attach/) | prepared mode, `btrace doctor`, token auth with no flags, and no overhead once the client exits | [tutorial 12](../docs/tutorials/12-production-safe-attach.md) |

## Running a scenario

```sh
export JAVA_HOME=/path/to/jdk-11-or-newer
export BTRACE_HOME=/path/to/btrace-3.0.0      # or put bin/btrace on PATH
demos/01-oneliner/run.sh                       # plays the scenario and stops the app
demos/01-oneliner/run.sh --keep                # leaves the app running for a live demo
```

Each `run.sh` prints the commands it runs, prefixed with `$`, and exits non-zero if an expected
output line never appears. `DEMO_PORT=<port>` adds `-p <port>` to every `btrace` command so two
scenarios can run side by side (the default agent port is 2020). Logs land in
`$TMPDIR/btrace-demo-<name>/`.

## Recording

The GIF and MP4 under `docs/tutorials/images/` are paced replays of a real run's output, rendered
by `lib/render-replay.py` (Python 3 with Pillow; ffmpeg for the MP4). Run the scenario first,
then render:

```sh
cd demos/01-oneliner
./run.sh > run.out 2>&1
python3 ../lib/render-replay.py run.out ../../docs/tutorials/images/01-oneliner
```

The renderer drops only the JDK's own flag-deprecation warning lines and collapses exactly
repeated blocks (a probe that prints the same error on every call) into one line plus a count.
It replaced VHS, whose terminal stopped updating after a few seconds on the machine the
recordings were made on.

Shared helpers are in [lib/common.sh](lib/common.sh).
