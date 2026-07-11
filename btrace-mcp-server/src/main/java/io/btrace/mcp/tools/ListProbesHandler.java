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
package io.btrace.mcp.tools;

import io.btrace.client.Client;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.PrintableCommand;
import io.btrace.mcp.ClientManager;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles the list_probes MCP tool - lists active probes on a JVM. */
public final class ListProbesHandler {
  private static final Logger log = LoggerFactory.getLogger(ListProbesHandler.class);
  private static final int DEFAULT_PORT = 2020;
  private static final int TIMEOUT_SECONDS = 10;

  private ListProbesHandler() {}

  /** Returns tool schema for MCP tools/list. */
  public static Map<String, Object> schema() {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", "list_probes");
    tool.put(
        "description",
        "List BTrace probes on a running JVM that have been detached from and are available to "
            + "reconnect to (via the plain CLI's -r flag). Probes still connected to their "
            + "original client are not listed here, since their output is already streaming to "
            + "that client.");

    Map<String, Object> properties = new LinkedHashMap<>();

    Map<String, Object> pidProp = new LinkedHashMap<>();
    pidProp.put("type", "string");
    pidProp.put("description", "PID of the target JVM");
    properties.put("pid", pidProp);

    Map<String, Object> portProp = new LinkedHashMap<>();
    portProp.put("type", "integer");
    portProp.put("description", "BTrace agent port (default: 2020)");
    properties.put("port", portProp);

    List<String> required = new ArrayList<>();
    required.add("pid");

    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put("type", "object");
    inputSchema.put("properties", properties);
    inputSchema.put("required", required);
    tool.put("inputSchema", inputSchema);
    return tool;
  }

  /** Executes the list_probes tool. */
  public static Map<String, Object> execute(Map<String, Object> arguments) {
    String pid = getStringArg(arguments, "pid");
    int port = getIntArg(arguments, "port", DEFAULT_PORT);

    if (pid == null || pid.isEmpty()) {
      return toolResult("Error: 'pid' parameter is required", true);
    }

    try {
      Client client = ClientManager.getClient(port);
      client.attach(pid, null, ".");

      StringBuilder output = new StringBuilder();
      CountDownLatch latch = new CountDownLatch(1);

      client.connectAndListProbes(
          "localhost",
          cmd -> {
            if (cmd instanceof PrintableCommand) {
              StringWriter sw = new StringWriter();
              ((PrintableCommand) cmd).print(new java.io.PrintWriter(sw));
              output.append(sw.toString());
            }
            if (cmd.getType() == Command.LIST_PROBES) {
              latch.countDown();
            }
          });

      boolean done = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!done) {
        return toolResult("Timed out waiting for probe list from PID " + pid, true);
      }

      String resultText = "Active probes on PID " + pid + ":\n" + output.toString();
      return toolResult(resultText, false);
    } catch (Exception e) {
      log.error("Failed to list probes", e);
      return toolResult("Error listing probes on PID " + pid + ": " + e.getMessage(), true);
    }
  }

  private static String getStringArg(Map<String, Object> args, String key) {
    Object val = args == null ? null : args.get(key);
    return val == null ? null : val.toString();
  }

  private static int getIntArg(Map<String, Object> args, String key, int defaultVal) {
    Object val = args == null ? null : args.get(key);
    if (val == null) {
      return defaultVal;
    }
    if (val instanceof Number) {
      return ((Number) val).intValue();
    }
    try {
      return Integer.parseInt(val.toString());
    } catch (NumberFormatException e) {
      return defaultVal;
    }
  }

  private static Map<String, Object> toolResult(String text, boolean isError) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("type", "text");
    content.put("text", text);
    List<Object> contentList = new ArrayList<>();
    contentList.add(content);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", contentList);
    result.put("isError", isError);
    return result;
  }
}
