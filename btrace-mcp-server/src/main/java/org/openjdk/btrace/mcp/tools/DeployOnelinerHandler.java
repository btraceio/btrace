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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.btrace.client.Client;
import org.openjdk.btrace.compiler.oneliner.OnelinerAST.OnelinerNode;
import org.openjdk.btrace.compiler.oneliner.OnelinerCodeGenerator;
import org.openjdk.btrace.compiler.oneliner.OnelinerParser;
import org.openjdk.btrace.compiler.oneliner.OnelinerValidator;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.PrintableCommand;
import org.openjdk.btrace.mcp.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles the deploy_oneliner MCP tool - deploys a BTrace oneliner probe. */
public final class DeployOnelinerHandler {
  private static final Logger log = LoggerFactory.getLogger(DeployOnelinerHandler.class);
  private static final int DEFAULT_PORT = 2020;
  private static final int PROBE_TIMEOUT_SECONDS = 30;

  private DeployOnelinerHandler() {}

  /** Returns tool schema for MCP tools/list. */
  public static Map<String, Object> schema() {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", "deploy_oneliner");
    tool.put(
        "description",
        "Deploy a BTrace oneliner probe to a running JVM. "
            + "Oneliners are concise probe expressions like: "
            + "\"com.example.Service::method @return { print duration }\" "
            + "or \"com.example.Dao::query @return if duration>100ms { print method, duration }\". "
            + "The probe attaches to the target JVM and captures output for the specified duration.");

    Map<String, Object> properties = new LinkedHashMap<>();

    Map<String, Object> pidProp = new LinkedHashMap<>();
    pidProp.put("type", "string");
    pidProp.put("description", "PID of the target JVM (use list_jvms to find it)");
    properties.put("pid", pidProp);

    Map<String, Object> onelinerProp = new LinkedHashMap<>();
    onelinerProp.put("type", "string");
    onelinerProp.put(
        "description",
        "BTrace oneliner expression, e.g. "
            + "\"com.example.Service::method @return { print method, duration }\"");
    properties.put("oneliner", onelinerProp);

    Map<String, Object> portProp = new LinkedHashMap<>();
    portProp.put("type", "integer");
    portProp.put("description", "BTrace agent port (default: 2020)");
    properties.put("port", portProp);

    List<String> required = new ArrayList<>();
    required.add("pid");
    required.add("oneliner");

    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put("type", "object");
    inputSchema.put("properties", properties);
    inputSchema.put("required", required);
    tool.put("inputSchema", inputSchema);
    return tool;
  }

  /** Executes the deploy_oneliner tool. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> execute(Map<String, Object> arguments) {
    String pid = getStringArg(arguments, "pid");
    String oneliner = getStringArg(arguments, "oneliner");
    int port = getIntArg(arguments, "port", DEFAULT_PORT);

    if (pid == null || pid.isEmpty()) {
      return toolResult("Error: 'pid' parameter is required", true);
    }
    if (oneliner == null || oneliner.isEmpty()) {
      return toolResult("Error: 'oneliner' parameter is required", true);
    }

    try {
      // Parse and validate the oneliner
      OnelinerNode ast = OnelinerParser.parse(oneliner);
      OnelinerValidator.validate(ast, oneliner);
      String className = "BTraceOneliner_" + System.currentTimeMillis();
      String javaSource = OnelinerCodeGenerator.generate(ast, className);
      String fileName = className + ".java";

      log.info("Generated oneliner source for {}: {}", oneliner, javaSource);

      // Compile the oneliner
      Client client = ClientManager.getClient(port);
      StringWriter errorWriter = new StringWriter();
      PrintWriter errPw = new PrintWriter(errorWriter);
      byte[] code = client.compileSource(fileName, javaSource, ".", errPw, null);

      if (code == null) {
        String errors = errorWriter.toString();
        return toolResult(
            "Oneliner compilation failed:\n" + errors + "\nGenerated source:\n" + javaSource, true);
      }

      // Attach and submit
      client.attach(pid, null, ".");

      StringBuilder output = new StringBuilder();
      CountDownLatch statusLatch = new CountDownLatch(1);
      AtomicBoolean success = new AtomicBoolean(false);
      AtomicBoolean exited = new AtomicBoolean(false);

      client.submit(
          "localhost",
          fileName,
          code,
          new String[0],
          cmd -> {
            int type = cmd.getType();
            if (cmd instanceof PrintableCommand) {
              StringWriter sw = new StringWriter();
              ((PrintableCommand) cmd).print(new java.io.PrintWriter(sw));
              output.append(sw.toString());
            }
            if (type == Command.STATUS) {
              success.set(true);
              statusLatch.countDown();
            }
            if (type == Command.EXIT) {
              exited.set(true);
              statusLatch.countDown();
            }
          });

      // Wait for probe to start (or fail)
      boolean started = statusLatch.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      if (!started) {
        return toolResult("Probe deployment timed out after " + PROBE_TIMEOUT_SECONDS + "s", true);
      }

      if (exited.get() && !success.get()) {
        return toolResult("Probe exited with error:\n" + output.toString(), true);
      }

      String resultText =
          "Probe deployed successfully to PID "
              + pid
              + ".\n"
              + "Oneliner: "
              + oneliner
              + "\n"
              + "Port: "
              + port
              + "\n";
      if (output.length() > 0) {
        resultText += "\nInitial output:\n" + output.toString();
      }
      resultText +=
          "\nUse send_event, detach_probe, or exit_probe to interact with the running probe.";
      return toolResult(resultText, false);
    } catch (Exception e) {
      log.error("Failed to deploy oneliner", e);
      return toolResult("Error deploying oneliner: " + e.getMessage(), true);
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
