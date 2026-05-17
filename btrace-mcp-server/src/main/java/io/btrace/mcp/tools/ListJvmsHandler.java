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

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles the list_jvms MCP tool - lists all attachable Java VMs. */
public final class ListJvmsHandler {
  private static final Logger log = LoggerFactory.getLogger(ListJvmsHandler.class);

  private ListJvmsHandler() {}

  /** Returns tool schema for MCP tools/list. */
  public static Map<String, Object> schema() {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", "list_jvms");
    tool.put(
        "description",
        "List all attachable Java Virtual Machines on this host. "
            + "Returns PID, main class, and whether BTrace is already attached (+/-). "
            + "Use this to find the PID of the JVM you want to instrument.");
    // No input parameters needed
    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put("type", "object");
    inputSchema.put("properties", new LinkedHashMap<>());
    tool.put("inputSchema", inputSchema);
    return tool;
  }

  /** Executes the list_jvms tool. */
  public static Map<String, Object> execute(
      @SuppressWarnings("unused") Map<String, Object> arguments) {
    try {
      Collection<Map<String, Object>> vms = listVms();
      StringBuilder sb = new StringBuilder();
      if (vms.isEmpty()) {
        sb.append("No Java VMs found.");
      } else {
        sb.append("Discovered Java VMs:\n\n");
        for (Map<String, Object> vm : vms) {
          sb.append(
              String.format(
                  "  PID: %s  |  Main Class: %s  |  BTrace: %s\n",
                  vm.get("pid"), vm.get("mainClass"), vm.get("btraceAttached")));
        }
      }
      return toolResult(sb.toString(), false);
    } catch (Exception e) {
      log.error("Failed to list JVMs", e);
      return toolResult("Error listing JVMs: " + e.getMessage(), true);
    }
  }

  private static Collection<Map<String, Object>> listVms() {
    List<Map<String, Object>> result = new ArrayList<>();
    try {
      for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
        String pid = vmd.id();
        int numericPid;
        try {
          numericPid = Integer.parseInt(pid);
        } catch (NumberFormatException e) {
          log.debug("Skipping JVM with non-numeric PID: {}", pid);
          continue;
        }
        String mainClass = vmd.displayName();
        if (mainClass == null || mainClass.isBlank()) mainClass = "<unknown>";
        Map<String, Object> vmInfo = new LinkedHashMap<>();
        vmInfo.put("pid", pid);
        vmInfo.put("mainClass", mainClass);
        vmInfo.put("btraceAttached", hasBTraceServer(numericPid) ? "attached" : "not attached");
        result.add(vmInfo);
      }
    } catch (Exception e) {
      log.warn("Error listing VMs", e);
    }
    return result;
  }

  private static boolean hasBTraceServer(int pid) {
    com.sun.tools.attach.VirtualMachine vm = null;
    try {
      vm = com.sun.tools.attach.VirtualMachine.attach(String.valueOf(pid));
      return vm.getSystemProperties().containsKey("btrace.port");
    } catch (Throwable ignored) {
      return false;
    } finally {
      if (vm != null) {
        try {
          vm.detach();
        } catch (Exception ignored) {
        }
      }
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
