/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/** What a failed compile tells the author of a script with the wrong imports. */
class ImportDiagnosticsTest {
  private static final String BODY =
      "@BTrace\n"
          + "public class P {\n"
          + "  @OnMethod(clazz = \"a.B\", method = \"c\")\n"
          + "  public static void h() { println(\"x\"); }\n"
          + "}\n";

  @Test
  void importsFromBTrace2PointToTheNewPackage() {
    String err = failedCompile("import org.openjdk.btrace.core.annotations.*;\n" + BODY);

    assertTrue(err.contains("hint: BTrace 3 moved org.openjdk.btrace to io.btrace"), err);
  }

  @Test
  void wrongAnnotationsPackagePointsToTheRightOne() {
    String err = failedCompile("import io.btrace.annotations.*;\n" + BODY);

    assertTrue(err.contains("hint: the annotations are in io.btrace.core.annotations"), err);
  }

  @Test
  void rootPackageImportExplainsTheAmbiguousBTrace() {
    String err =
        failedCompile("import io.btrace.*;\nimport io.btrace.core.annotations.*;\n" + BODY);

    assertTrue(err.contains("hint: remove the wildcard import of io.btrace"), err);
  }

  @Test
  void unrelatedErrorGetsNoHint() {
    String err = failedCompile("import com.acme.Missing;\n" + BODY);

    assertFalse(err.contains("hint:"), err);
  }

  @Test
  void errorsReportTheLineOfTheOriginalSource() {
    assertTrue(
        failedCompile("import com.acme.Missing;\n" + BODY).contains("P.java:1:"),
        "with no package declaration");
    assertTrue(
        failedCompile("package x; // keeps its comment\nimport com.acme.Missing;\n" + BODY)
            .contains("P.java:2:"),
        "after a package declaration");
  }

  private static String failedCompile(String source) {
    StringWriter err = new StringWriter();
    assertNull(
        new Compiler()
            .compile(
                "P.java",
                source,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path")),
        "the script should not compile");
    return err.toString();
  }
}
