# Let an AI Assistant Debug Your JVM in 10 Minutes

The BTrace MCP server is now part of the [BTrace Agent Plugins](https://github.com/btraceio/agent-plugins) marketplace. Install the `btrace-observability` plugin in Claude Code, Codex, or Pi; it bundles the `btrace` stdio MCP server and launches it with JBang.

The server downloads the single masked `io.btrace:btrace` artifact on first use. Use JDK 11 or newer and run the AI client on the same host as the target JVM (or first enter the target environment through SSH, Docker, or Kubernetes).

After installation, ask the assistant to list local JVMs, deploy a focused probe, inspect its output, and remove it at the end of the observation window. For the full installation instructions and the available tools, see the [BTrace Observability plugin README](https://github.com/btraceio/agent-plugins/tree/main/plugins/btrace-observability). For BTrace attach failures, see the [Troubleshooting guide](../Troubleshooting.md#jvm-attachment-issues).
