# BTrace MCP Server

An MCP (Model Context Protocol) server that exposes BTrace operations as tools, allowing LLM clients (Claude Desktop, Claude Code, Cursor, etc.) to diagnose running JVMs.

## Tools

| Tool | Description |
|------|-------------|
| `list_jvms` | List all attachable Java VMs on this host |
| `deploy_oneliner` | Deploy a BTrace oneliner probe (e.g. `com.example.Service::method @return { print method, duration }`) |
| `deploy_script` | Deploy a full BTrace Java script |
| `list_probes` | List active probes on a JVM |
| `send_event` | Send an event to a running probe |
| `detach_probe` | Detach from a running probe (probe continues) |
| `exit_probe` | Stop and remove a probe |

## Prompts

| Prompt | Description |
|--------|-------------|
| `diagnose_slow_endpoint` | Step-by-step guide to diagnose a slow HTTP endpoint |
| `find_exception_source` | Guide to find where exceptions originate |
| `profile_method` | Guide to profile a method's latency |

## Building

```bash
./gradlew :btrace-mcp-server:build
```

This produces `btrace-mcp-server/build/libs/btrace-mcp-server-<version>.jar`. The jar declares `Main-Class: io.btrace.mcp.BTraceMcpServer`, but it is **not** self-contained — it needs the other BTrace module jars (btrace-core, btrace-client, btrace-compiler, btrace-boot and their dependencies) on the classpath, and it is not included in the binary BTrace distribution.

Collect the server jar together with its runtime dependencies into a single directory (referred to as `/path/to/btrace-mcp-libs` below) and launch the server with:

```bash
java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer
```

## Configuration

### Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": [
        "-cp", "/path/to/btrace-mcp-libs/*",
        "io.btrace.mcp.BTraceMcpServer"
      ]
    }
  }
}
```

### Claude Code

Add to your project's `.mcp.json` (project-scope MCP configuration):

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": [
        "-cp", "/path/to/btrace-mcp-libs/*",
        "io.btrace.mcp.BTraceMcpServer"
      ]
    }
  }
}
```

Or register the server at user scope with the CLI:

```bash
claude mcp add --scope user btrace -- java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer
```

See [docs/MCPServer.md](../docs/MCPServer.md) for configuration examples covering other MCP hosts (Cursor, VS Code, Windsurf, Continue, Zed, ...).

## Usage Examples

Once configured, you can ask your LLM client:

- "List all running Java processes"
- "Attach to PID 12345 and trace all methods in com.example.UserService that take longer than 100ms"
- "Find where NullPointerExceptions are being thrown in PID 12345"
- "Profile the latency of com.example.OrderService::processOrder"

## Protocol

The server uses MCP over stdio (stdin/stdout JSON-RPC). All logging goes to stderr.
