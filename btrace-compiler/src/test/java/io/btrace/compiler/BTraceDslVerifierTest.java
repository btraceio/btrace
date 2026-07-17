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
package io.btrace.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BTraceDslVerifierTest {

  private static final String FLAT_PRINTLN_SCRIPT =
      "import io.btrace.core.annotations.BTrace;\n"
          + "import io.btrace.core.annotations.OnMethod;\n"
          + "import static io.btrace.BTrace.*;\n"
          + "@BTrace\n"
          + "public class FlatPrintProbe {\n"
          + "    @OnMethod(clazz=\"java.io.FileInputStream\", method=\"<init>\")\n"
          + "    public static void onOpen(String fileName) {\n"
          + "        println(\"opened: \" + fileName);\n"
          + "    }\n"
          + "}\n";

  private static final String HANDLER_FAILURE_SCRIPT =
      "import io.btrace.core.BTraceUtils;\n"
          + "import io.btrace.core.annotations.BTrace;\n"
          + "import io.btrace.core.annotations.OnError;\n"
          + "import io.btrace.core.annotations.OnEvent;\n"
          + "import io.btrace.core.annotations.OnMethod;\n"
          + "@BTrace public class HandlerFailureProbe {\n"
          + "  @OnEvent public static void event() { BTraceUtils.substr(\"x\", 2); }\n"
          + "  @OnMethod(clazz=\"resources.Main\", method=\"callA\")\n"
          + "  public static void method() { BTraceUtils.substr(\"x\", 2); }\n"
          + "  @OnError public static void error(Throwable t) { BTraceUtils.substr(\"x\", 2); }\n"
          + "}\n";

  @Test
  void flatPrintln_passesSourceVerifier() {
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "FlatPrintProbe.java",
                FLAT_PRINTLN_SCRIPT,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed");
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  @Test
  void handlerFailureTriggerPassesSourceVerifier() {
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "HandlerFailureProbe.java",
                HANDLER_FAILURE_SCRIPT,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }
}
