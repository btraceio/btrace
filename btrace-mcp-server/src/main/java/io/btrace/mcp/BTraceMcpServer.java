/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.mcp;

import io.btrace.mcp.prompts.DiagnosticPrompts;
import io.btrace.mcp.tools.DeployOnelinerHandler;
import io.btrace.mcp.tools.DeployScriptHandler;
import io.btrace.mcp.tools.DetachProbeHandler;
import io.btrace.mcp.tools.ExitProbeHandler;
import io.btrace.mcp.tools.ListJvmsHandler;
import io.btrace.mcp.tools.ListProbesHandler;
import io.btrace.mcp.tools.SendEventHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BTrace MCP (Model Context Protocol) server. Exposes BTrace operations as MCP tools over stdio
 * JSON-RPC transport, allowing LLM clients to instrument and diagnose running JVMs.
 */
public final class BTraceMcpServer {
  private static final Logger log = LoggerFactory.getLogger(BTraceMcpServer.class);
  private static final String SERVER_NAME = "btrace-mcp-server";
  private static final String SERVER_VERSION = "0.1.0";
  private static final String PROTOCOL_VERSION = "2024-11-05";

  private final McpProtocol protocol;

  BTraceMcpServer(McpProtocol protocol) {
    this.protocol = protocol;
  }

  public static void main(String[] args) {
    // Redirect System.out logging to stderr so stdout stays clean for MCP JSON-RPC
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");

    log.info("Starting {}", SERVER_NAME);
    McpProtocol proto = new McpProtocol(System.in, System.out);
    BTraceMcpServer server = new BTraceMcpServer(proto);
    try {
      server.run();
    } catch (IOException e) {
      log.error("Server error", e);
      System.exit(1);
    }
  }

  @SuppressWarnings("unchecked")
  void run() throws IOException {
    while (true) {
      Map<String, Object> message;
      try {
        message = protocol.readMessage();
      } catch (IllegalArgumentException parseError) {
        // Malformed JSON on one line must not kill the server. Reply with a JSON-RPC parse error
        // (id unknown for unparseable input, so null per spec) and keep serving.
        log.warn("Failed to parse JSON-RPC message: {}", parseError.getMessage());
        protocol.sendError(null, -32700, "Parse error: " + parseError.getMessage());
        continue;
      }
      if (message == null) {
        log.info("EOF on stdin, shutting down");
        break;
      }

      String method = (String) message.get("method");
      Object id = message.get("id");
      Map<String, Object> params =
          message.containsKey("params") ? (Map<String, Object>) message.get("params") : null;

      if (method == null) {
        // Response or notification without method — ignore
        continue;
      }

      try {
        switch (method) {
          case "initialize":
            handleInitialize(id);
            break;
          case "notifications/initialized":
            // Client acknowledgement — no response needed
            break;
          case "tools/list":
            handleToolsList(id);
            break;
          case "tools/call":
            handleToolsCall(id, params);
            break;
          case "prompts/list":
            handlePromptsList(id);
            break;
          case "prompts/get":
            handlePromptsGet(id, params);
            break;
          default:
            protocol.sendError(id, -32601, "Method not found: " + method);
        }
      } catch (Exception e) {
        log.error("Error handling method: {}", method, e);
        protocol.sendError(id, -32603, "Internal error: " + e.getMessage());
      }
    }
  }

  private void handleInitialize(Object id) throws IOException {
    Map<String, Object> serverInfo = new LinkedHashMap<>();
    serverInfo.put("name", SERVER_NAME);
    serverInfo.put("version", SERVER_VERSION);

    Map<String, Object> toolsCap = new LinkedHashMap<>();
    Map<String, Object> promptsCap = new LinkedHashMap<>();
    Map<String, Object> capabilities = new LinkedHashMap<>();
    capabilities.put("tools", toolsCap);
    capabilities.put("prompts", promptsCap);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("protocolVersion", PROTOCOL_VERSION);
    result.put("capabilities", capabilities);
    result.put("serverInfo", serverInfo);

    protocol.sendResult(id, result);
  }

  private void handleToolsList(Object id) throws IOException {
    List<Object> tools = new ArrayList<>();
    tools.add(ListJvmsHandler.schema());
    tools.add(DeployOnelinerHandler.schema());
    tools.add(DeployScriptHandler.schema());
    tools.add(ListProbesHandler.schema());
    tools.add(SendEventHandler.schema());
    tools.add(DetachProbeHandler.schema());
    tools.add(ExitProbeHandler.schema());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tools", tools);
    protocol.sendResult(id, result);
  }

  @SuppressWarnings("unchecked")
  private void handleToolsCall(Object id, Map<String, Object> params) throws IOException {
    if (params == null) {
      protocol.sendError(id, -32602, "Missing params");
      return;
    }
    String toolName = (String) params.get("name");
    Map<String, Object> arguments =
        params.containsKey("arguments")
            ? (Map<String, Object>) params.get("arguments")
            : new LinkedHashMap<>();

    Map<String, Object> result;
    switch (toolName) {
      case "list_jvms":
        result = ListJvmsHandler.execute(arguments);
        break;
      case "deploy_oneliner":
        result = DeployOnelinerHandler.execute(arguments);
        break;
      case "deploy_script":
        result = DeployScriptHandler.execute(arguments);
        break;
      case "list_probes":
        result = ListProbesHandler.execute(arguments);
        break;
      case "send_event":
        result = SendEventHandler.execute(arguments);
        break;
      case "detach_probe":
        result = DetachProbeHandler.execute(arguments);
        break;
      case "exit_probe":
        result = ExitProbeHandler.execute(arguments);
        break;
      default:
        protocol.sendError(id, -32602, "Unknown tool: " + toolName);
        return;
    }
    protocol.sendResult(id, result);
  }

  private void handlePromptsList(Object id) throws IOException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("prompts", DiagnosticPrompts.listPrompts());
    protocol.sendResult(id, result);
  }

  @SuppressWarnings("unchecked")
  private void handlePromptsGet(Object id, Map<String, Object> params) throws IOException {
    if (params == null) {
      protocol.sendError(id, -32602, "Missing params");
      return;
    }
    String name = (String) params.get("name");
    Map<String, Object> promptArgs =
        params.containsKey("arguments")
            ? (Map<String, Object>) params.get("arguments")
            : new LinkedHashMap<>();

    Map<String, Object> result = DiagnosticPrompts.getPrompt(name, promptArgs);
    if (result == null) {
      protocol.sendError(id, -32602, "Unknown prompt: " + name);
      return;
    }
    protocol.sendResult(id, result);
  }
}
