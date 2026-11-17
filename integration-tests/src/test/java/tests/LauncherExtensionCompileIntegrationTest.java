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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles a script that injects a bundled extension through the distribution's own {@code
 * bin/btracec}, with {@code BTRACE_HOME} absent from the environment as it is for a user who only
 * put {@code bin/} on the PATH. The launcher resolves the home for itself but does not export it,
 * so the compiler has to find the extension APIs from {@code libs/btrace.jar} on its classpath.
 * Before that, the compile failed with "package io.btrace.metrics does not exist".
 */
@DisabledOnOs(OS.WINDOWS)
public class LauncherExtensionCompileIntegrationTest extends RuntimeTest {

  private static final String SCRIPT =
      "import io.btrace.core.annotations.*;\n"
          + "import io.btrace.metrics.MetricsService;\n"
          + "\n"
          + "@BTrace\n"
          + "public class UsesMetrics {\n"
          + "  @Injected private static MetricsService metrics;\n"
          + "\n"
          + "  @OnMethod(clazz = \"resources.Main\", method = \"callA\")\n"
          + "  public static void onCall() {\n"
          + "    println(\"hit\");\n"
          + "  }\n"
          + "}\n";

  @TempDir Path work;

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void extensionScriptCompilesWithoutBtraceHome() throws Exception {
    Path home = Paths.get(System.getProperty("btrace.libs")).toAbsolutePath().getParent();
    Path btracec = home.resolve("bin/btracec");
    assertTrue(Files.isRegularFile(btracec), "no launcher at " + btracec);
    assertTrue(
        Files.isDirectory(home.resolve("extensions/btrace-metrics")),
        "the distribution has no exploded btrace-metrics extension under " + home);

    Path script = work.resolve("UsesMetrics.java");
    Files.write(script, SCRIPT.getBytes(StandardCharsets.UTF_8));
    Path out = Files.createDirectories(work.resolve("out"));

    ProcessBuilder pb =
        new ProcessBuilder(
                Arrays.asList("sh", btracec.toString(), "-d", out.toString(), script.toString()))
            .directory(work.toFile())
            .redirectErrorStream(true);
    pb.environment().remove("BTRACE_HOME");
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().put("JAVA_HOME", javaHome);
    Process p = pb.start();
    String output = readAll(p);
    assertTrue(p.waitFor(2, TimeUnit.MINUTES), "btracec did not finish; output:\n" + output);

    assertEquals(0, p.exitValue(), "btracec failed; output:\n" + output);
    assertTrue(
        Files.isRegularFile(out.resolve("UsesMetrics.class")),
        "btracec produced no UsesMetrics.class; output:\n" + output);
  }

  /**
   * {@code btracex list} reads the installed extensions from the BTRACE_HOME environment variable.
   * The launcher resolved the home but did not export it, so the list came out empty.
   */
  @Test
  void btracexListsBundledExtensionsWithoutBtraceHome() throws Exception {
    Path home = Paths.get(System.getProperty("btrace.libs")).toAbsolutePath().getParent();
    ProcessBuilder pb =
        new ProcessBuilder(Arrays.asList("sh", home.resolve("bin/btracex").toString(), "list"))
            .directory(work.toFile())
            .redirectErrorStream(true);
    pb.environment().remove("BTRACE_HOME");
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().put("JAVA_HOME", javaHome);
    // keep a user's ~/.btrace out of the listing
    pb.environment().put("HOME", work.toString());
    Process p = pb.start();
    String output = readAll(p);
    assertTrue(p.waitFor(2, TimeUnit.MINUTES), "btracex did not finish; output:\n" + output);
    assertEquals(0, p.exitValue(), "btracex list failed; output:\n" + output);
    assertTrue(
        output.contains("btrace-metrics"),
        "btracex list did not list the bundled btrace-metrics; output:\n" + output);
  }

  /**
   * Every sample the distribution ships compiles with its own btracec. Three did not
   * (AWTEventTracer, SubtypeTracer, ThreadCounterBean passed non-String values to the flat-DSL
   * print/println, which take a String).
   */
  @Test
  void everyBundledSampleCompiles() throws Exception {
    Path home = Paths.get(System.getProperty("btrace.libs")).toAbsolutePath().getParent();
    List<String> command =
        new ArrayList<>(
            Arrays.asList(
                "sh",
                home.resolve("bin/btracec").toString(),
                "-d",
                Files.createDirectories(work.resolve("samples-out")).toString()));
    try (Stream<Path> samples = Files.list(home.resolve("samples"))) {
      samples
          .filter(p -> p.getFileName().toString().endsWith(".java"))
          .sorted()
          .forEach(p -> command.add(p.toString()));
    }
    assertTrue(command.size() > 4, "the distribution ships no samples under " + home);
    ProcessBuilder pb =
        new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().put("JAVA_HOME", javaHome);
    Process p = pb.start();
    String output = readAll(p);
    assertTrue(p.waitFor(5, TimeUnit.MINUTES), "btracec did not finish; output:\n" + output);
    assertEquals(0, p.exitValue(), "a bundled sample does not compile; output:\n" + output);
  }

  private static String readAll(Process p) throws IOException {
    byte[] bytes = new byte[8192];
    StringBuilder sb = new StringBuilder();
    int n;
    while ((n = p.getInputStream().read(bytes)) > 0) {
      sb.append(new String(bytes, 0, n, StandardCharsets.UTF_8));
    }
    return sb.toString();
  }
}
