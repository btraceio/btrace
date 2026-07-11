# BTrace MCP Server

The BTrace MCP Server exposes BTrace's instrumentation capabilities through the [Model Context Protocol](https://modelcontextprotocol.io/) (MCP). This lets LLM clients — Claude Desktop, Claude Code, Cursor, and any other MCP-compatible host — instrument and diagnose running JVMs through a conversation, without leaving the AI interface.

## How it works

The MCP server runs as a subprocess launched by the MCP host. It speaks JSON-RPC 2.0 over stdio: the host writes requests to the process's stdin, and reads JSON responses from stdout. Log output goes to stderr and is never mixed into the protocol stream.

When you ask the AI to "show me which methods in my service are taking more than 100 ms", it calls the appropriate BTrace MCP tools, attaches to the target JVM, deploys a probe, reads the output, and surfaces the results — all within the conversation.

## Prerequisites

- JDK 11 or higher to run the MCP server
- The target JVM must be attachable (see [Troubleshooting](#troubleshooting))
- The BTrace MCP server jar plus its runtime dependencies (see [Getting the server](#getting-the-server))
- On JDK 8 targets: `tools.jar` from the JDK must be accessible at runtime (see [tools.jar note](#toolsjar-not-found))

## Getting the server

The MCP server is built from the BTrace sources:

```bash
./gradlew :btrace-mcp-server:build
```

This produces `btrace-mcp-server/build/libs/btrace-mcp-server-<version>.jar`. The jar declares `Main-Class: io.btrace.mcp.BTraceMcpServer`, but it is **not** self-contained — it needs the other BTrace module jars (btrace-core, btrace-client, btrace-compiler, btrace-boot and their dependencies) on the classpath, and it is not included in the binary BTrace distribution.

Collect the server jar together with its runtime dependencies into a single directory (referred to as `/path/to/btrace-mcp-libs` below) and launch the server with:

```bash
java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer
```

All the host configuration examples below use this same command line.

## Quick Setup

### Claude Desktop

Add the following to your Claude Desktop configuration file.

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`  
**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  }
}
```

Replace `/path/to/btrace-mcp-libs` with the actual path to the directory holding the server jar and its dependencies. After saving the file, restart Claude Desktop. You should see a tools indicator in the chat input area when BTrace tools are active.

### Claude Code

Run the following command in your terminal to register the server at user scope:

```bash
claude mcp add --scope user btrace -- java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer
```

To verify the server registered correctly:

```bash
claude mcp list
```

You can also add the server project-locally so that everyone working on the repository shares the same configuration:

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  }
}
```

Save this to `.mcp.json` at the root of the repository — project-scope MCP configuration for Claude Code lives in `.mcp.json`, not in `.claude/settings.json`.

### Cursor

Cursor reads MCP server definitions from a `mcp.json` file. Use the project-local file to share the config with the team, or the user-level file to apply it globally.

**Project-local** (committed to the repo): `.cursor/mcp.json`  
**User-level** (applies to all projects): `~/.cursor/mcp.json`

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  }
}
```

After saving, open the Cursor command palette (`Cmd+Shift+P` / `Ctrl+Shift+P`) and run **MCP: Reload Servers**, or restart Cursor. BTrace tools appear in the agent panel once the server is active.

### OpenAI Codex CLI

The Codex CLI (`@openai/codex`) reads MCP server configuration from `~/.codex/config.toml` using TOML syntax:

```toml
[mcp_servers.btrace]
command = "java"
args   = ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
```

The key under `[mcp_servers]` becomes the server name visible in the session. Restart any active Codex session after editing the file for the change to take effect.

If you prefer to keep the configuration per-project, create a `.codex/config.toml` at the repository root with the same content. Project-local configuration takes precedence over the user-level file.

### VS Code (GitHub Copilot)

VS Code exposes MCP server support through GitHub Copilot Chat's agent mode. The config file lives at `.vscode/mcp.json` within the workspace. Note that VS Code uses a slightly different schema than Claude Desktop — it requires an explicit `"type"` field:

```json
{
  "servers": {
    "btrace": {
      "type": "stdio",
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  }
}
```

Commit `.vscode/mcp.json` to share the setup with the rest of the team. After adding or editing the file, VS Code prompts you to reload the MCP configuration; accept the prompt or run **Developer: Reload Window**. The BTrace tools become available in Copilot Chat when you switch to agent mode (`@workspace` → agent).

To add the server globally (not tied to a workspace), open VS Code settings (`settings.json`) and add:

```json
"github.copilot.chat.mcp.servers": {
  "btrace": {
    "type": "stdio",
    "command": "java",
    "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
  }
}
```

### Windsurf (Codeium)

Windsurf stores MCP configuration in `~/.codeium/windsurf/mcp_config.json`. Create the file if it does not exist:

```json
{
  "mcpServers": {
    "btrace": {
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  }
}
```

Restart Windsurf after saving. The BTrace tools appear in Cascade (Windsurf's agentic panel) once the server connects.

### Continue.dev

Continue reads MCP servers from its configuration file. For the YAML format (Continue 0.9+), add a block under the `mcpServers` key in `~/.continue/config.yaml`:

```yaml
mcpServers:
  - name: btrace
    command: java
    args:
      - -cp
      - /path/to/btrace-mcp-libs/*
      - io.btrace.mcp.BTraceMcpServer
```

For the legacy JSON format (`~/.continue/config.json`):

```json
{
  "mcpServers": [
    {
      "name": "btrace",
      "command": "java",
      "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
    }
  ]
}
```

Reload the Continue extension after editing (`Continue: Reload` from the command palette). BTrace tools will be available in the Continue chat panel.

### Zed

Zed exposes MCP tools through its context server interface. Add the server to `~/.config/zed/settings.json` under `context_servers`:

```json
{
  "context_servers": {
    "btrace": {
      "command": {
        "path": "java",
        "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"]
      }
    }
  }
}
```

Restart Zed after saving, or use the command palette to reload context servers. BTrace tools are accessible in the Zed AI panel once the server is running.

### Other MCP-compatible hosts

Any host that implements the MCP specification will work. The canonical configuration pattern is:

```json
{
  "command": "java",
  "args": ["-cp", "/path/to/btrace-mcp-libs/*", "io.btrace.mcp.BTraceMcpServer"],
  "transport": "stdio"
}
```

The field names (`command`, `args`, `name`) and configuration file location vary by host. Consult your host's MCP documentation for the exact format. The BTrace MCP server itself has no host-specific requirements — it reads from stdin and writes to stdout using plain JSON-RPC 2.0.

## Available Tools Reference

These are the tools the MCP server exposes. Your AI client can call any of them.

| Tool | Description | Required Parameters | Optional Parameters |
|------|-------------|---------------------|---------------------|
| `list_jvms` | Lists all attachable JVMs on the local machine | — | — |
| `deploy_oneliner` | Deploys a DTrace-style oneliner probe | `pid`, `oneliner` | `port` |
| `deploy_script` | Deploys a full BTrace Java source script | `pid`, `script` | `args`, `port` |
| `list_probes` | Lists probes detached from and available to reconnect to on a JVM | `pid` | `port` |
| `send_event` | Fires an event to trigger `@OnEvent` handlers | `pid` | `event_name`, `port` |
| `detach_probe` | Disconnects the client; probe keeps running | `pid` | `port` |
| `exit_probe` | Stops and removes the probe, de-instruments the JVM | `pid` | `port` |

**Parameter notes:**

- `pid` — integer process ID of the target JVM
- `oneliner` — a BTrace oneliner string, e.g. `"com.example.Service::process @return { print method, duration }"`; see the [Oneliner Guide](OnelinerGuide.md) for syntax
- `script` — full BTrace Java source code as a string, including the `@BTrace` annotation
- `args` — string array of arguments passed to the script's `@BTrace(unsafe=...)` or as command-line args
- `port` — BTrace agent listener port; defaults to 2020

## Available Prompts Reference

The MCP server also exposes prompt templates. These generate step-by-step diagnostic instructions that the AI follows, guiding it through a structured investigation.

| Prompt | Description | Required Arguments | Optional Arguments |
|--------|-------------|--------------------|--------------------|
| `diagnose_slow_endpoint` | Diagnose a slow HTTP endpoint; identifies where time is spent | `endpoint_class`, `endpoint_method` | `pid` |
| `find_exception_source` | Find where a specific exception originates, with stack traces | `exception_class` | `pid` |
| `profile_method` | Profile a method's latency distribution using counters | `class_name`, `method_name` | `pid` |

## Example Session

The following illustrates what a conversation looks like when using the BTrace MCP server. The AI calls tools automatically; you only type the natural-language requests.

---

**You:** My OrderService.processOrder method seems slow. Can you find out what's happening?

**AI:** Let me start by finding the running JVMs on this machine.

*[calls `list_jvms`]*

```
Discovered Java VMs:

  PID: 18340  |  Main Class: com.example.order.OrderApplication  |  BTrace: not attached
  PID: 21904  |  Main Class: org.gradle.launcher.GradleWrapperMain  |  BTrace: not attached
```

I can see your order service at PID 18340. I'll deploy a oneliner to measure the duration of `processOrder`.

*[calls `deploy_oneliner` with pid=18340, oneliner="com.example.order.OrderService::processOrder @return { print method, duration }"]*

```
Probe deployed successfully to PID 18340.
Oneliner: com.example.order.OrderService::processOrder @return { print method, duration }
Port: 2020

Initial output:
processOrder
execution time: 342 ms
processOrder
execution time: 289 ms
processOrder
execution time: 1204 ms
processOrder
execution time: 301 ms

Use send_event, detach_probe, or exit_probe to interact with the running probe.
```

The method is occasionally taking over a second. Let me drill into all methods inside `OrderService` to find the bottleneck. Since this server tracks one active session per PID, I'll deploy the wider probe straight to the same PID — that replaces my handle to the first one, so I don't need it anymore.

*[calls `deploy_oneliner` with pid=18340, oneliner="com.example.order.OrderService::/.*/ @return if duration>50ms { print method, duration }"]*

```
Probe deployed successfully to PID 18340.
Oneliner: com.example.order.OrderService::/.*/ @return if duration>50ms { print method, duration }
Port: 2020

Initial output:
fetchInventory
execution time: 1150 ms
validatePayment
execution time: 12 ms

Use send_event, detach_probe, or exit_probe to interact with the running probe.
```

`fetchInventory` is the culprit — it accounts for almost all of the slow calls. That looks like a database or external service call worth investigating. I'll remove the probe now.

*[calls `exit_probe` with pid=18340]*

```
Probe stopped and removed from PID 18340.
```

---

The AI handled every tool call. You described the goal; BTrace provided the data.

## Typical Workflow

For most diagnostic tasks, the sequence is:

1. **`list_jvms`** — identify which process you want to inspect and get its PID.
2. **`deploy_oneliner`** — deploy a quick measurement for the method or class you suspect.
3. Read the output that streams back through the conversation.
4. **`deploy_script`** — if you need more detail, deploy a full BTrace script with state, aggregation, or `@OnEvent` triggers.
5. **`send_event`** (optional) — trigger `@OnEvent` handlers in your script to dump summaries or flush aggregated state.
6. **`list_probes`** (optional) — check which detached probes are still out there and reconnectable via the plain CLI.
7. **`exit_probe`** — remove the probe and restore the JVM to its original state.

Use `detach_probe` instead of `exit_probe` if you want the probe to keep collecting data after the client disconnects. Note this server has no `reconnect` tool: once detached, `list_probes` can confirm the probe is still running, but nothing in this tool set can send it an event or exit it afterward — reconnecting takes the plain `btrace -r` CLI flag, outside the assistant's reach. Prefer `exit_probe` for anything you want the assistant itself to clean up later.

## Writing Scripts for the MCP Server

Scripts passed to `deploy_script` are standard BTrace Java source. The `@BTrace` annotation and imports are required.

### Minimal example

```java
import io.btrace.core.annotations.*;
import static io.btrace.core.BTraceUtils.*;

@BTrace
public class MethodTimer {
    @OnMethod(
        clazz = "com.example.Service",
        method = "process",
        location = @Location(Kind.RETURN)
    )
    public static void onReturn(@Duration long duration) {
        println("process: " + (duration / 1_000_000) + "ms");
    }
}
```

### Script with event-triggered summary

```java
import io.btrace.core.annotations.*;
import static io.btrace.core.BTraceUtils.*;

@BTrace
public class CallCounter {
    private static long calls;
    private static long errors;

    @OnMethod(clazz = "com.example.Service", method = "process")
    public static void onEntry() {
        calls++;
    }

    @OnMethod(
        clazz = "com.example.Service",
        method = "process",
        location = @Location(Kind.ERROR)
    )
    public static void onError() {
        errors++;
    }

    @OnEvent
    public static void printSummary() {
        println("calls=" + calls + " errors=" + errors);
    }
}
```

After deploying this script, call `send_event` to trigger `printSummary` at any time.

See the [BTrace Tutorial](BTraceTutorial.md) and [Quick Reference](QuickReference.md) for the full annotation set and built-in functions.

## Security Considerations

**Local attachment only.** The BTrace MCP server uses the JVM Attach API (`VirtualMachine.list()` and `VirtualMachine.attach()`), which only works for JVMs running on the same machine under the same OS user. It cannot attach to remote JVMs or JVMs owned by a different user. There is no network exposure: the MCP transport is stdio, so there is no listening socket to exploit.

**BTrace safety model applies.** All scripts deployed through the MCP server go through BTrace's standard compile-time verifier, which rejects:

- Loops and recursion
- Object allocation with `new`
- `throw` statements
- Field assignments

These restrictions prevent BTrace scripts from destabilizing the target JVM. A probe that violates these rules will fail to compile and will not be deployed.

**No persistent access.** `exit_probe` removes all instrumentation and returns the JVM to its original state. The agent is unloaded when all probes are removed.

**JDK 21+ dynamic agent loading warning.** From JDK 21 onward (JEP 451), dynamically attaching an agent prints a warning to the target JVM's stderr. The probe still works. To suppress the warning, start the target JVM with:

```
-XX:+EnableDynamicAgentLoading
```

## Troubleshooting

### tools.jar not found

**Symptom:** The server starts but `list_jvms` returns an empty list or fails with a class-not-found error related to `com.sun.tools.attach`.

**Cause:** The JVM running the MCP server cannot find the Attach API classes. On JDK 8 these live in `tools.jar`; from JDK 9 onward the Attach API is in the `jdk.attach` module and is always available.

**Solution:** Make sure you are running the MCP server with a full JDK (not a JRE), JDK 11 or higher:

```bash
java -version   # should report 11 or above, from a JDK distribution
java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer
```

If you must use JDK 8, add `tools.jar` to the classpath explicitly:

```bash
java -cp "$JAVA_HOME/lib/tools.jar:/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer
```

### Port already in use

**Symptom:** `deploy_oneliner` or `deploy_script` fails with a connection error mentioning port 2020.

**Cause:** Another BTrace client or agent is already listening or connected on the default port.

**Solution:** Pass a different `port` value to the deploy tools, and make sure the BTrace agent on the target JVM is started with a matching port. For example, if the agent was loaded with `-javaagent:btrace.jar=port=2021`, use `port=2021` in every MCP tool call for that JVM.

### JVM not attachable

**Symptom:** `list_jvms` shows the process but `deploy_oneliner` fails with an attach exception.

**Common causes and fixes:**

| Cause | Fix |
|-------|-----|
| User mismatch: MCP server runs as a different OS user than the target JVM | Start the MCP server (and therefore the MCP host) as the same user as the target JVM |
| Target JVM started with `-XX:+DisableAttachMechanism` | Remove that flag and restart the application |
| JDK 21+: dynamic agent loading disabled | Add `-XX:+EnableDynamicAgentLoading` to the target JVM's startup flags |
| Target is a JRE, not a JDK | Run the target application with a full JDK |

See [JVM Attachment Issues](Troubleshooting.md#jvm-attachment-issues) in the main Troubleshooting Guide for a more complete list.

### MCP server not appearing in the client

**Symptom:** After adding the server configuration, tools do not appear in the AI client.

**Steps:**

1. Verify the classpath directory is correct and contains the server jar and its dependencies.
2. Test the server manually: `java -cp "/path/to/btrace-mcp-libs/*" io.btrace.mcp.BTraceMcpServer` should start without error and wait on stdin.
3. Check that you are using JDK 11+.
4. Restart the MCP host (Claude Desktop, Cursor, etc.) after changing the config file.
5. Check the MCP host's logs for subprocess errors (Claude Desktop logs to `~/Library/Logs/Claude/` on macOS).

### Probe output not visible in the conversation

**Symptom:** The probe deploys successfully but no output appears.

**Cause:** The target method may not be called during the observation window, or the class/method pattern does not match.

**Steps:**

1. Confirm the `deploy_oneliner`/`deploy_script` response itself said "deployed successfully" rather than timing out or erroring — that's the only success signal for a probe that's still connected (`list_probes` won't show it; it only lists probes you've since detached from).
2. Trigger the target code path in the application (send a request, run the workflow, etc.).
3. Verify the class and method names are correct — BTrace matching is case-sensitive and requires fully qualified class names.
4. Narrow the pattern: if you used a wildcard, try an exact class name first.
5. Check whether the class is loaded by a non-system classloader; see [Class/Method Not Found](Troubleshooting.md#classmethod-not-found) in the Troubleshooting Guide.

### Script verification failure

**Symptom:** `deploy_script` returns an error mentioning "unsafe" or "verification failed".

**Cause:** The script violates one of BTrace's safety restrictions (loop, allocation, throw, field write).

**Fix:** Review the script against the [BTrace Script Restrictions](GettingStarted.md#btrace-script-restrictions) and remove the offending construct. For cases where safety restrictions are intentionally relaxed, pass `unsafe=true` in the `@BTrace` annotation and include `"unsafe"` in the `args` array:

```java
@BTrace(unsafe = true)
public class UnsafeScript { ... }
```

Note that unsafe scripts require the BTrace agent to be started in unsafe mode as well.

## Related Documentation

- [Getting Started Guide](GettingStarted.md) — installation, first script, deployment modes
- [Oneliner Guide](OnelinerGuide.md) — oneliner syntax reference for `deploy_oneliner`
- [Quick Reference](QuickReference.md) — all annotations, locations, and built-in functions
- [BTrace Tutorial](BTraceTutorial.md) — comprehensive guide to writing BTrace scripts
- [Troubleshooting Guide](Troubleshooting.md) — common errors and their solutions
