/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.mcp.tools;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.openjdk.btrace.client.Client;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.PrintableCommand;
import org.openjdk.btrace.mcp.ClientManager;
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
        "List active BTrace probes on a running JVM. "
            + "Shows which probes are currently deployed and their IDs.");

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
