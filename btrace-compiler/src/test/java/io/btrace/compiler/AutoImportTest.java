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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
