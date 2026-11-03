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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AutoImportTest {

  @TempDir File tempDir;

  // Minimal script with ZERO explicit imports — relies on both auto-injections
  private static final String SCRIPT_ZERO_IMPORTS =
      "@BTrace\n"
          + "public class ZeroImportProbe {\n"
          + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
          + "    public static void onLength() {\n"
          + "        println(\"zero imports\");\n"
          + "    }\n"
          + "}\n";

  @Test
  void scriptWithZeroImports_compilesViaBothAutoInjections() {
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "ZeroImportProbe.java",
                SCRIPT_ZERO_IMPORTS,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  // Script with NO explicit import of io.btrace.BTrace — relies on DSL auto-injection
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
  void scriptWithoutDslImport_compilesAndResolvesFlat() {
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

  @Test
  void scriptWithPackageDeclaration_compilesAfterInjection() {
    String script =
        "package com.example;\n"
            + "@BTrace\n"
            + "public class PkgProbe {\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(\"called\");\n"
            + "    }\n"
            + "}\n";
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "PkgProbe.java",
                script,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  @Test
  void scriptAlreadyHasBothImports_notDoubleInjected() {
    String script =
        "import static io.btrace.BTrace.*;\n"
            + "import io.btrace.core.annotations.*;\n"
            + "@BTrace\n"
            + "public class AlreadyImportedProbe {\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(\"called\");\n"
            + "    }\n"
            + "}\n";
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "AlreadyImportedProbe.java",
                script,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  @Test
  void scriptAlreadyHasAnnotationsWildcard_dslInjectedAnnotationsSkipped() {
    String script =
        "import io.btrace.core.annotations.*;\n"
            + "@BTrace\n"
            + "public class WildcardAnnotationsProbe {\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(\"wildcard annotations\");\n"
            + "    }\n"
            + "}\n";
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "WildcardAnnotationsProbe.java",
                script,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  @Test
  void fileBasedCompile_injectsBothImports() throws Exception {
    String script =
        "@BTrace\n"
            + "public class FileProbe {\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(\"file path\");\n"
            + "    }\n"
            + "}\n";
    File scriptFile = new File(tempDir, "FileProbe.java");
    Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(scriptFile, new PrintWriter(err), null, System.getProperty("java.class.path"));
    assertNotNull(result, "File-based compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  private static String compileErrors(String fileName, String script) {
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                fileName,
                script,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNull(result, "Compilation should fail");
    return err.toString();
  }

  @Test
  void oneLineScript_errorReportsLineOne() {
    String errors = compileErrors("Bad.java", "@BTrace public class Bad { int x = ; }\n");
    assertTrue(errors.contains("Bad.java:1:"), "Expected the error on line 1:\n" + errors);
  }

  @Test
  void scriptWithoutImports_errorReportsUserLine() {
    String script =
        "@BTrace\n"
            + "public class LineProbe {\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(undefinedName);\n"
            + "    }\n"
            + "}\n";
    String errors = compileErrors("LineProbe.java", script);
    assertTrue(errors.contains("LineProbe.java:5:"), "Expected the error on line 5:\n" + errors);
  }

  @Test
  void scriptWithPackage_errorReportsUserLine() {
    String script =
        "package com.example; // probes\n"
            + "@BTrace\n"
            + "public class PkgLineProbe {\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(undefinedName);\n"
            + "    }\n"
            + "}\n";
    String errors = compileErrors("PkgLineProbe.java", script);
    assertTrue(errors.contains("PkgLineProbe.java:6:"), "Expected the error on line 6:\n" + errors);
  }

  @Test
  void importsQuotedInCommentsDoNotDisableAutoImport() {
    String script =
        "// package com.example.commented;\n"
            + "/**\n"
            + " * Use it with: import static io.btrace.BTrace.*;\n"
            + " * and import io.btrace.core.annotations.*;\n"
            + " */\n"
            + "@BTrace\n"
            + "public class CommentedImportProbe {\n"
            + "    // import static io.btrace.core.BTraceUtils.*;\n"
            + "    @OnMethod(clazz=\"java.lang.String\", method=\"length\")\n"
            + "    public static void onLength() {\n"
            + "        println(\"import static io.btrace.BTrace.*;\");\n"
            + "    }\n"
            + "}\n";
    StringWriter err = new StringWriter();
    Map<String, byte[]> result =
        new Compiler()
            .compile(
                "CommentedImportProbe.java",
                script,
                new PrintWriter(err),
                null,
                System.getProperty("java.class.path"));
    assertNotNull(result, "Compilation should succeed: " + err);
    assertFalse(result.isEmpty(), "Should produce class bytes");
  }

  @Test
  void explicitImportOnPackageLineIsRecognized() {
    String source =
        "package p; import static io.btrace.BTrace.*;\nimport io.btrace.core.annotations.*;\n";
    assertTrue(source.equals(Compiler.injectDslImport(source)));
  }
}
