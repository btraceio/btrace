/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnEvent;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.core.annotations.ProbeClassName;
import io.btrace.llm.LlmTraceService;

import static io.btrace.core.BTraceUtils.*;

/**
 * Sample BTrace script that traces LLM API calls using the btrace-llm-trace extension.
 *
 * <p>This is a generic template. It intercepts Langchain4j's ChatLanguageModel.generate()
 * method and records call metrics. Adapt the @OnMethod annotations to target your specific
 * LLM SDK (OpenAI Java SDK, Anthropic Java SDK, Spring AI, etc.).
 *
 * <p>Usage:
 *   btrace &lt;PID&gt; LlmTrace.java
 *
 * <p>Send a named event "summary" to print the current stats:
 *   (Ctrl-C, option 3, enter "summary")
 */
@BTrace
public class LlmTrace {

  @Injected
  private static LlmTraceService llm;

  /**
   * Trace Langchain4j ChatLanguageModel.generate() calls.
   * Captures latency on every call completion.
   */
  @OnMethod(
      clazz = "+dev.langchain4j.model.chat.ChatLanguageModel",
      method = "generate",
      location = @Location(Kind.RETURN))
  public static void onLangchain4jGenerate(
      @ProbeClassName String className,
      @Duration long duration) {
    // Model name extracted from the class; token counts need return value parsing
    // For a production script, parse the Response<AiMessage> return value
    llm.recordCall(className, duration);
    println(strcat(strcat("LLM call: ", className), strcat(" ", strcat(str(duration / 1000000L), "ms"))));
  }

  /**
   * Trace Langchain4j StreamingChatLanguageModel calls.
   */
  @OnMethod(
      clazz = "+dev.langchain4j.model.chat.StreamingChatLanguageModel",
      method = "generate",
      location = @Location(Kind.RETURN))
  public static void onLangchain4jStreaming(
      @ProbeClassName String className,
      @Duration long duration) {
    llm.call(className).streaming().duration(duration).record();
  }

  /**
   * Trace errors from any ChatLanguageModel implementation.
   */
  @OnMethod(
      clazz = "+dev.langchain4j.model.chat.ChatLanguageModel",
      method = "generate",
      location = @Location(Kind.ERROR))
  public static void onLangchain4jError(
      @ProbeClassName String className,
      @Duration long duration) {
    llm.recordError(className, "exception", duration);
    println(strcat("LLM ERROR in: ", className));
  }

  /**
   * Print summary on named event "summary".
   */
  @OnEvent("summary")
  public static void onSummary() {
    println(llm.getSummary());
  }

  /**
   * Print summary periodically (every 30 seconds).
   */
  @OnTimer(30000)
  public static void onTimer() {
    if (llm.getTotalCalls() > 0) {
      println(llm.getSummary());
    }
  }
}
