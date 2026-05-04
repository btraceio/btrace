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

public class AutoImportTest {

  // Script with NO explicit import of io.btrace.BTrace — relies on auto-injection
  private static final String SCRIPT_NO_IMPORT =
      "import io.btrace.core.annotations.BTrace;\n"
          + "import io.btrace.core.annotations.OnMethod;\n"
          + "@BTrace\n"
          + "public class NoImportProbe {\n"
          + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
          + "    public static void onLength() {\n"
          + "        println(\"called\");\n"
          + "    }\n"
          + "}\n";

  @Test
  void scriptWithoutImport_compilesAndResolvesFlat() {
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "NoImportProbe.java",
                SCRIPT_NO_IMPORT,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }
}
