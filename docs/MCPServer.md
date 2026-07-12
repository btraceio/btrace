# BTrace MCP Server

The BTrace MCP server is distributed with the [BTrace Agent Plugins](https://github.com/btraceio/agent-plugins) marketplace, rather than with the BTrace source tree or binary distribution.

Install the `btrace-observability` plugin in your AI host. The plugin registers a local stdio server that JBang runs from its bundled sources. On first use, JBang downloads the single masked `io.btrace:btrace` JAR; no legacy `btrace-client.jar`, `btrace-agent.jar`, or `btrace-boot.jar` layout is required.

The server requires JBang and JDK 11 or newer, and must run on the host that can attach to the target JVM. It exposes tools for listing JVMs, deploying probes, sending events, and removing probes. See the [plugin README](https://github.com/btraceio/agent-plugins/tree/main/plugins/btrace-observability) for installation and host-specific configuration.

For attach prerequisites and diagnostics, see [Troubleshooting](Troubleshooting.md#jvm-attachment-issues).
