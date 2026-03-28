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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openjdk.btrace.client.Client;
import org.openjdk.btrace.mcp.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles the send_event MCP tool - sends an event to a running probe. */
public final class SendEventHandler {
  private static final Logger log = LoggerFactory.getLogger(SendEventHandler.class);
  private static final int DEFAULT_PORT = 2020;

  private SendEventHandler() {}

  /** Returns tool schema for MCP tools/list. */
  public static Map<String, Object> schema() {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", "send_event");
    tool.put(
        "description",
        "Send an event to a running BTrace probe. "
            + "Events can trigger @OnEvent handlers in the probe script. "
            + "If no event_name is specified, an unnamed event is sent.");

    Map<String, Object> properties = new LinkedHashMap<>();

    Map<String, Object> pidProp = new LinkedHashMap<>();
    pidProp.put("type", "string");
    pidProp.put("description", "PID of the target JVM");
    properties.put("pid", pidProp);

    Map<String, Object> eventNameProp = new LinkedHashMap<>();
    eventNameProp.put("type", "string");
    eventNameProp.put("description", "Name of the event to send (optional)");
    properties.put("event_name", eventNameProp);

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

  /** Executes the send_event tool. */
  public static Map<String, Object> execute(Map<String, Object> arguments) {
    String pid = getStringArg(arguments, "pid");
    String eventName = getStringArg(arguments, "event_name");
    int port = getIntArg(arguments, "port", DEFAULT_PORT);

    if (pid == null || pid.isEmpty()) {
      return toolResult("Error: 'pid' parameter is required", true);
    }

    try {
      Client client = ClientManager.getExistingClient(pid, port);
      if (client == null) {
        return toolResult(
            "No active BTrace session for PID "
                + pid
                + ". Deploy a probe first using deploy_oneliner or deploy_script.",
            true);
      }

      if (eventName != null && !eventName.isEmpty()) {
        client.sendEvent(eventName);
        return toolResult("Event '" + eventName + "' sent to PID " + pid, false);
      } else {
        client.sendEvent();
        return toolResult("Unnamed event sent to PID " + pid, false);
      }
    } catch (Exception e) {
      log.error("Failed to send event", e);
      return toolResult("Error sending event to PID " + pid + ": " + e.getMessage(), true);
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
