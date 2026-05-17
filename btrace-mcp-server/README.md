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

## Configuration

### Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": [
        "-cp", "/path/to/btrace/lib/*",
        "io.btrace.mcp.BTraceMcpServer"
      ]
    }
  }
}
```

### Claude Code

Add to your project's `.mcp.json`:

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": [
        "-cp", "/path/to/btrace/lib/*",
        "io.btrace.mcp.BTraceMcpServer"
      ]
    }
  }
}
```

Or configure globally in `~/.claude/settings.json`:

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": [
        "-cp", "/path/to/btrace/lib/*",
        "io.btrace.mcp.BTraceMcpServer"
      ]
    }
  }
}
```

### Using the BTrace distribution

If you have BTrace installed (e.g. via SDKMAN), you can reference the distribution directly:

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": [
        "-cp", "$BTRACE_HOME/lib/*:$BTRACE_HOME/build/btrace-mcp-server.jar",
        "io.btrace.mcp.BTraceMcpServer"
      ]
    }
  }
}
```

## Usage Examples

Once configured, you can ask your LLM client:

- "List all running Java processes"
- "Attach to PID 12345 and trace all methods in com.example.UserService that take longer than 100ms"
- "Find where NullPointerExceptions are being thrown in PID 12345"
- "Profile the latency of com.example.OrderService::processOrder"

## Protocol

The server uses MCP over stdio (stdin/stdout JSON-RPC). All logging goes to stderr.
