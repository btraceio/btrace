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
package io.btrace.mcp.prompts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Provides MCP prompt templates for common BTrace diagnostic scenarios. */
public final class DiagnosticPrompts {

  private DiagnosticPrompts() {}

  /** Returns all available prompt schemas for prompts/list. */
  public static List<Map<String, Object>> listPrompts() {
    List<Map<String, Object>> prompts = new ArrayList<>();
    prompts.add(diagnoseSlowEndpointSchema());
    prompts.add(findExceptionSourceSchema());
    prompts.add(profileMethodSchema());
    return prompts;
  }

  /** Returns a prompt by name, or null if not found. */
  public static Map<String, Object> getPrompt(String name, Map<String, Object> arguments) {
    switch (name) {
      case "diagnose_slow_endpoint":
        return diagnoseSlowEndpoint(arguments);
      case "find_exception_source":
        return findExceptionSource(arguments);
      case "profile_method":
        return profileMethod(arguments);
      default:
        return null;
    }
  }

  // --- diagnose_slow_endpoint ---

  private static Map<String, Object> diagnoseSlowEndpointSchema() {
    Map<String, Object> prompt = new LinkedHashMap<>();
    prompt.put("name", "diagnose_slow_endpoint");
    prompt.put(
        "description",
        "Step-by-step guide to diagnose a slow HTTP endpoint using BTrace. "
            + "Helps identify where time is spent in request processing.");

    List<Map<String, Object>> args = new ArrayList<>();
    args.add(
        promptArg("endpoint_class", "Fully qualified class name of the endpoint/controller", true));
    args.add(promptArg("endpoint_method", "Method name to diagnose", true));
    args.add(
        promptArg(
            "pid", "PID of the target JVM (optional, will use list_jvms if not provided)", false));
    prompt.put("arguments", args);
    return prompt;
  }

  private static Map<String, Object> diagnoseSlowEndpoint(Map<String, Object> arguments) {
    String endpointClass = getArg(arguments, "endpoint_class", "com.example.Controller");
    String endpointMethod = getArg(arguments, "endpoint_method", "handleRequest");
    String pid = getArg(arguments, "pid", null);

    StringBuilder text = new StringBuilder();
    text.append("# Diagnosing Slow Endpoint: ")
        .append(endpointClass)
        .append("::")
        .append(endpointMethod)
        .append("\n\n");

    text.append("## Step 1: Find the target JVM\n");
    if (pid != null) {
      text.append("Target PID: ").append(pid).append("\n");
    } else {
      text.append("Use the `list_jvms` tool to find the target JVM PID.\n");
    }
    text.append("\n");

    text.append("## Step 2: Measure endpoint latency\n");
    text.append("Deploy a oneliner to measure the method's execution time:\n");
    text.append("```\n");
    text.append(endpointClass)
        .append("::")
        .append(endpointMethod)
        .append(" @return { print method, duration }\n");
    text.append("```\n\n");

    text.append("## Step 3: Trace internal method calls\n");
    text.append(
        "If the endpoint is slow, trace the internal methods it calls to find the bottleneck:\n");
    text.append("```\n");
    text.append(endpointClass)
        .append("::/.*/")
        .append(" @return if duration>10ms { print method, duration }\n");
    text.append("```\n\n");

    text.append("## Step 4: Analyze results\n");
    text.append("Look for methods with unexpectedly high durations. Common causes include:\n");
    text.append("- Database queries taking too long\n");
    text.append("- External service calls with high latency\n");
    text.append("- Lock contention\n");
    text.append("- Excessive object allocation\n\n");

    text.append("## Step 5: Clean up\n");
    text.append("Use `exit_probe` to remove the instrumentation when done.\n");

    return promptResult(text.toString());
  }

  // --- find_exception_source ---

  private static Map<String, Object> findExceptionSourceSchema() {
    Map<String, Object> prompt = new LinkedHashMap<>();
    prompt.put("name", "find_exception_source");
    prompt.put(
        "description",
        "Guide to find where specific exceptions originate in a running JVM. "
            + "Uses BTrace to intercept exception constructors and capture stack traces.");

    List<Map<String, Object>> args = new ArrayList<>();
    args.add(
        promptArg("exception_class", "Exception class name (e.g. NullPointerException)", true));
    args.add(promptArg("pid", "PID of the target JVM (optional)", false));
    prompt.put("arguments", args);
    return prompt;
  }

  private static Map<String, Object> findExceptionSource(Map<String, Object> arguments) {
    String exceptionClass = getArg(arguments, "exception_class", "java.lang.NullPointerException");
    String pid = getArg(arguments, "pid", null);

    StringBuilder text = new StringBuilder();
    text.append("# Finding Exception Source: ").append(exceptionClass).append("\n\n");

    text.append("## Step 1: Find the target JVM\n");
    if (pid != null) {
      text.append("Target PID: ").append(pid).append("\n");
    } else {
      text.append("Use the `list_jvms` tool to find the target JVM PID.\n");
    }
    text.append("\n");

    text.append("## Step 2: Deploy an exception tracing script\n");
    text.append("Deploy this BTrace script to capture exception creation with stack traces:\n\n");
    text.append("```java\n");
    text.append("import io.btrace.core.annotations.*;\n");
    text.append("import static io.btrace.core.BTraceUtils.*;\n\n");
    text.append("@BTrace\n");
    text.append("public class ExceptionTracer {\n");
    text.append("    @OnMethod(\n");
    text.append("        clazz = \"").append(exceptionClass).append("\",\n");
    text.append("        method = \"<init>\"\n");
    text.append("    )\n");
    text.append("    public static void onException(@Self Throwable self) {\n");
    text.append("        println(\"--- Exception created: \" + Strings.str(self) + \" ---\");\n");
    text.append("        Threads.jstack();\n");
    text.append("        println(\"\");\n");
    text.append("    }\n");
    text.append("}\n");
    text.append("```\n\n");

    text.append("## Step 3: Analyze the stack traces\n");
    text.append(
        "Each time the exception is created, you will see the full stack trace "
            + "showing exactly which code path creates it.\n\n");

    text.append("## Step 4: Clean up\n");
    text.append("Use `exit_probe` to remove the instrumentation when done.\n");

    return promptResult(text.toString());
  }

  // --- profile_method ---

  private static Map<String, Object> profileMethodSchema() {
    Map<String, Object> prompt = new LinkedHashMap<>();
    prompt.put("name", "profile_method");
    prompt.put(
        "description",
        "Guide to profile a specific method's latency distribution using BTrace. "
            + "Captures timing data to understand performance characteristics.");

    List<Map<String, Object>> args = new ArrayList<>();
    args.add(promptArg("class_name", "Fully qualified class name", true));
    args.add(promptArg("method_name", "Method name to profile", true));
    args.add(promptArg("pid", "PID of the target JVM (optional)", false));
    prompt.put("arguments", args);
    return prompt;
  }

  private static Map<String, Object> profileMethod(Map<String, Object> arguments) {
    String className = getArg(arguments, "class_name", "com.example.Service");
    String methodName = getArg(arguments, "method_name", "process");
    String pid = getArg(arguments, "pid", null);

    StringBuilder text = new StringBuilder();
    text.append("# Profiling Method: ")
        .append(className)
        .append("::")
        .append(methodName)
        .append("\n\n");

    text.append("## Step 1: Find the target JVM\n");
    if (pid != null) {
      text.append("Target PID: ").append(pid).append("\n");
    } else {
      text.append("Use the `list_jvms` tool to find the target JVM PID.\n");
    }
    text.append("\n");

    text.append("## Step 2: Quick latency check with oneliner\n");
    text.append("Start with a simple oneliner to see individual call durations:\n");
    text.append("```\n");
    text.append(className)
        .append("::")
        .append(methodName)
        .append(" @return { print method, duration }\n");
    text.append("```\n\n");

    text.append("## Step 3: Detailed profiling with histogram\n");
    text.append("For a latency distribution, deploy this BTrace script:\n\n");
    text.append("```java\n");
    text.append("import io.btrace.core.annotations.*;\n");
    text.append("import io.btrace.core.BTraceUtils;\n");
    text.append("import static io.btrace.core.BTraceUtils.*;\n\n");
    text.append("@BTrace\n");
    text.append("public class MethodProfiler {\n");
    text.append("    private static long count;\n");
    text.append("    private static long totalTime;\n\n");
    text.append("    @OnMethod(\n");
    text.append("        clazz = \"").append(className).append("\",\n");
    text.append("        method = \"").append(methodName).append("\",\n");
    text.append("        location = @Location(Kind.RETURN)\n");
    text.append("    )\n");
    text.append("    public static void onReturn(@Duration long duration) {\n");
    text.append("        count++;\n");
    text.append("        totalTime += duration;\n");
    text.append("        println(\"Call #\" + count + \": \" + (duration / 1000000) + \"ms\");\n");
    text.append("    }\n\n");
    text.append("    @OnEvent\n");
    text.append("    public static void onEvent() {\n");
    text.append("        println(\"=== Summary ===\");\n");
    text.append("        println(\"Total calls: \" + count);\n");
    text.append("        if (count > 0) {\n");
    text.append(
        "            println(\"Avg duration: \" + ((totalTime / count) / 1000000) + \"ms\");\n");
    text.append("        }\n");
    text.append("    }\n");
    text.append("}\n");
    text.append("```\n\n");

    text.append("## Step 4: Get summary\n");
    text.append("Use `send_event` to trigger the @OnEvent handler and get a summary.\n\n");

    text.append("## Step 5: Clean up\n");
    text.append("Use `exit_probe` to remove the instrumentation when done.\n");

    return promptResult(text.toString());
  }

  // --- Helpers ---

  private static Map<String, Object> promptArg(String name, String description, boolean required) {
    Map<String, Object> arg = new LinkedHashMap<>();
    arg.put("name", name);
    arg.put("description", description);
    arg.put("required", required);
    return arg;
  }

  private static String getArg(Map<String, Object> args, String key, String defaultVal) {
    if (args == null) {
      return defaultVal;
    }
    Object val = args.get(key);
    if (val == null) {
      return defaultVal;
    }
    return val.toString();
  }

  private static Map<String, Object> promptResult(String text) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");

    Map<String, Object> content = new LinkedHashMap<>();
    content.put("type", "text");
    content.put("text", text);
    message.put("content", content);

    List<Object> messages = new ArrayList<>();
    messages.add(message);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("messages", messages);
    return result;
  }
}
