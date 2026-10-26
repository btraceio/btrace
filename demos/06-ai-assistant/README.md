# Demo 06 - Let an AI assistant debug your JVM

An order service is running and some orders are slow while others fail with the exception swallowed.
Instead of typing a BTrace command, you ask an AI coding assistant (Claude Code, Codex or Pi) with
the `btrace-observability` plugin installed. It finds the JVM, writes a narrow probe, reads the
numbers, and removes the probe when it is done.
`run.sh` starts the app and leaves it running; the session itself is scripted in [PROMPTS.md](PROMPTS.md).
The plugin lives in the [btraceio/agent-plugins](https://github.com/btraceio/agent-plugins) repository.
