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
import io.btrace.compiler.oneliner.OnelinerAST.OnelinerNode;
import io.btrace.compiler.oneliner.OnelinerCodeGenerator;
import io.btrace.compiler.oneliner.OnelinerParser;
import io.btrace.compiler.oneliner.OnelinerValidator;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.PrintableCommand;
import io.btrace.mcp.ClientManager;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

      ClientManager.registerClient(pid, port, client);

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
