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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A probe annotated {@code @BTrace(trusted = true)} and deployed without {@code -u} is refused by
 * the agent. The refusal has to reach the client: the agent used to drop the connection with only a
 * log line in the target, and the client exited 255 after printing nothing but "Attaching".
 */
public class TrustedProbeRefusalIntegrationTest extends RuntimeTest {
  private static final String SCRIPT =
      "import io.btrace.core.annotations.*;\n"
          + "\n"
          + "@BTrace(trusted = true)\n"
          + "public class TrustedRefused {\n"
          + "  @OnTimer(200)\n"
          + "  public static void tick() {\n"
          + "    println(\"trusted-ran\");\n"
          + "  }\n"
          + "}\n";

  @TempDir Path work;

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void refusalReachesTheClient() throws Exception {
    assertRefused(
        Files.write(work.resolve("TrustedRefused.java"), SCRIPT.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * The same probe compiled ahead of time with {@code btracec} into a pack, its default output. The
   * agent read packs through a weaker check that ignored the trusted annotation (and, because it
   * never recorded labels, loops), so the precompiled probe ran without {@code -u}.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void precompiledPackIsRefusedToo() throws Exception {
    Path script =
        Files.write(work.resolve("TrustedRefused.java"), SCRIPT.getBytes(StandardCharsets.UTF_8));
    Path home = Paths.get(System.getProperty("btrace.libs")).toAbsolutePath().getParent();
    Path out = Files.createDirectories(work.resolve("classes"));
    ProcessBuilder pb =
        new ProcessBuilder(
                "sh",
                home.resolve("bin/btracec").toString(),
                "-d",
                out.toString(),
                script.toString())
            .redirectErrorStream(true);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().put("JAVA_HOME", javaHome);
    Process p = pb.start();
    String compileOutput = new String(readAllBytes(p.getInputStream()), StandardCharsets.UTF_8);
    assertTrue(p.waitFor(2, TimeUnit.MINUTES), "btracec did not finish:\n" + compileOutput);
    Path pack = out.resolve("TrustedRefused.class");
    assertTrue(Files.isRegularFile(pack), "btracec produced no pack:\n" + compileOutput);
    assertRefused(pack);
  }

  private static byte[] readAllBytes(InputStream in) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) > 0) {
      buf.write(b, 0, n);
    }
    return buf.toByteArray();
  }

  private void assertRefused(Path script) throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    TestApp app = launchTestApp("resources.Main");
    List<String> output = Collections.synchronizedList(new ArrayList<String>());
    try {
      runBTrace(
          new String[] {
            "-p", String.valueOf(port), String.valueOf(app.getPid()), script.toString()
          },
          new ProcessOutputProcessor() {
            @Override
            public boolean onStdout(int lineno, String line) {
              output.add(line);
              return true;
            }

            @Override
            public boolean onStderr(int lineno, String line) {
              output.add(line);
              return true;
            }
          });
    } finally {
      app.stop();
    }
    String all = String.join("\n", output);
    assertTrue(
        all.contains("Trusted mode, requested by the script"),
        "the client did not report why the trusted probe was refused:\n" + all);
    assertTrue(all.contains("btrace -u"), "the refusal does not name the remedy:\n" + all);
    assertTrue(!all.contains("trusted-ran"), "a trusted probe ran without -u:\n" + all);
  }
}
