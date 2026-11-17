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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Startup probes ({@code script=}). One the agent cannot load must be reported in the target's
 * output: it used to be skipped with no message at any log level, so a typo in a launch script left
 * the operator believing the probe was running. And {@code scriptOutputDir=} must decide where a
 * loaded probe's output file goes.
 */
public class AgentStartupScriptIntegrationTest extends RuntimeTest {
  @TempDir Path work;

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void missingScriptIsReported() throws Exception {
    String output = launch(work.resolve("NoSuchProbe.class").toString());
    assertTrue(
        output.contains("NoSuchProbe.class was not loaded: no such file"),
        "missing script= probe was not reported:\n" + output);
  }

  @Test
  void sourceScriptIsReported() throws Exception {
    Path source = Files.write(work.resolve("Probe.java"), "class Probe {}".getBytes("UTF-8"));
    String output = launch(source.toString());
    assertTrue(
        output.contains("Probe.java was not loaded: script= takes a compiled probe"),
        "source script= probe was not reported:\n" + output);
  }

  /**
   * {@code scriptOutputDir=} decides where a startup probe's output file goes. The agent parsed the
   * argument but never read it, so the file landed in the target's working directory regardless.
   */
  @Test
  void scriptOutputDirReceivesTheOutputFile() throws Exception {
    Path probe =
        Paths.get(System.getProperty("project.dir"))
            .resolve("build/classes/btrace/OnTimerTest.class");
    assertTrue(Files.isRegularFile(probe), "compiled test probe missing: " + probe);
    Path outDir = Files.createDirectories(work.resolve("out"));
    launch(probe.toString(), "scriptOutputDir=" + outDir, 1500);
    try (Stream<Path> files = Files.list(outDir)) {
      List<Path> outputs =
          files
              .filter(f -> f.getFileName().toString().startsWith("OnTimerTest.class-"))
              .collect(Collectors.toList());
      assertEquals(
          1, outputs.size(), "expected one probe output file in " + outDir + ": " + outputs);
      String content = new String(Files.readAllBytes(outputs.get(0)), StandardCharsets.UTF_8);
      assertTrue(content.contains("timer"), "probe output file has no timer line:\n" + content);
    }
    try (Stream<Path> files = Files.list(work)) {
      assertTrue(
          files.noneMatch(f -> f.getFileName().toString().startsWith("OnTimerTest.class-")),
          "output file was written to the working directory instead of scriptOutputDir");
    }
  }

  private static final String METRICS_PROBE =
      "import io.btrace.core.annotations.*;\n"
          + "import io.btrace.metrics.MetricsService;\n"
          + "@BTrace\n"
          + "public class MetricsStartupProbe {\n"
          + "  @Injected private static MetricsService metrics;\n"
          + "  @OnTimer(200)\n"
          + "  public static void t() { println(\"metrics-linked \" + (metrics != null)); }\n"
          + "}\n";

  /**
   * A startup probe that requires a bundled extension. The agent used to start the probes before it
   * initialized the extension system, so the required service could not link and the probe was
   * refused even with the extension allowed ("ExtensionIndy.bridge not initialized").
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void startupProbeLinksAnAllowedExtension() throws Exception {
    Path probe = compileWithDistribution(METRICS_PROBE, "MetricsStartupProbe");
    String output = launch(probe.toString(), "allowExtensions=btrace-metrics,stdout=true", 1500);
    assertTrue(
        output.contains("metrics-linked true"),
        "startup probe did not link the allowed btrace-metrics extension:\n" + output);
  }

  /** Refused, a startup probe has no client to tell: the reason must reach the target's log. */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void blockedExtensionReasonIsLogged() throws Exception {
    Path probe = compileWithDistribution(METRICS_PROBE, "MetricsStartupProbe");
    String output = launch(probe.toString(), "stdout=true", 500);
    assertTrue(
        output.contains("Blocked privileged extension"),
        "the reason a startup probe was refused is missing:\n" + output);
    assertTrue(!output.contains("metrics-linked"), "a blocked extension linked:\n" + output);
  }

  private Path compileWithDistribution(String source, String className) throws Exception {
    Path home = Paths.get(System.getProperty("btrace.libs")).toAbsolutePath().getParent();
    Path src =
        Files.write(work.resolve(className + ".java"), source.getBytes(StandardCharsets.UTF_8));
    Path out = Files.createDirectories(work.resolve("classes"));
    ProcessBuilder pb =
        new ProcessBuilder(
                "sh", home.resolve("bin/btracec").toString(), "-d", out.toString(), src.toString())
            .redirectErrorStream(true);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().put("JAVA_HOME", javaHome);
    Process p = pb.start();
    ByteArrayOutputStream log = new ByteArrayOutputStream();
    try (InputStream in = p.getInputStream()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) {
        log.write(buf, 0, n);
      }
    }
    assertTrue(p.waitFor(2, TimeUnit.MINUTES), "btracec did not finish");
    Path cls = out.resolve(className + ".class");
    assertTrue(
        Files.isRegularFile(cls),
        "btracec failed:\n" + new String(log.toByteArray(), StandardCharsets.UTF_8));
    return cls;
  }

  private String launch(String script) throws Exception {
    return launch(script, null, 200);
  }

  private String launch(String script, String extraAgentArg, int sleepMs) throws Exception {
    assumeTrue(testJavaMajor() >= 11, "single-file source launch (java App.java) needs JDK 11+");
    Path agent = locateAgent();
    assertNotNull(agent, "Missing btrace.jar");
    Path app =
        Paths.get(System.getProperty("project.dir"))
            .resolve("src/test/resources/sourcelaunch/HelloSource.java");
    List<String> command =
        Arrays.asList(
            resolveTestJavaHome() + "/bin/java",
            "-Dbtrace.suppressJavaDeprecationWarning=true",
            "-Dhello.sleep=" + sleepMs,
            "-javaagent:"
                + agent
                + "=script="
                + script
                + ",noServer=true,telemetry=false"
                + (extraAgentArg != null ? "," + extraAgentArg : ""),
            app.toString());
    ProcessBuilder pb = new ProcessBuilder(command).directory(work.toFile());
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.redirectErrorStream(true);
    Process process = pb.start();
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    Thread reader =
        new Thread(
            () -> {
              try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                  captured.write(buf, 0, n);
                }
              } catch (Exception ignored) {
                // the stream closes when the process is destroyed
              }
            });
    reader.setDaemon(true);
    reader.start();
    try {
      if (!process.waitFor(120, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
      reader.join(5_000);
    } finally {
      process.destroyForcibly();
    }
    String output = new String(captured.toByteArray(), StandardCharsets.UTF_8);
    assertTrue(output.contains("ready"), "target did not reach main:\n" + output);
    return output;
  }
}
