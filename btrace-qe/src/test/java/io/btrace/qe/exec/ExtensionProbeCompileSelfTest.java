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
package io.btrace.qe.exec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.oracle.OutputCheck;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The harness must look like an installation to the client: a source probe that imports an
 * extension's API (as the metrics, LLM and RAG demos do) compiles against the bundled {@code
 * extensions/*}-api jars.
 */
@Tag("qe-self-test")
class ExtensionProbeCompileSelfTest {
  private static final String PROBE =
      "import static io.btrace.core.BTraceUtils.println;\n"
          + "import io.btrace.core.annotations.*;\n"
          + "import io.btrace.metrics.MetricsService;\n"
          + "@BTrace public class QeMetricsProbe {\n"
          + "  @Injected(optional = true) private static MetricsService metrics;\n"
          + "  @OnMethod(clazz = \"qe.targets.AttachDemoTarget\", method = \"work\")\n"
          + "  public static void onWork() { println(\"qe-metrics-probe-ok\"); }\n"
          + "}\n";

  @Test
  void sourceProbeImportingAnExtensionApiCompilesThroughTheHarnessClient() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    assertTrue(
        Files.isDirectory(env.btraceHome().resolve("extensions")),
        "BTRACE_HOME has no extensions/: " + env.btraceHome());
    Path runDir = Files.createTempDirectory("qe-ext-probe");
    Files.write(runDir.resolve("QeMetricsProbe.java"), PROBE.getBytes(StandardCharsets.UTF_8));
    List<String> output = List.of("^qe-metrics-probe-ok$");
    CaseDef def =
        new CaseDef(
            "e.metrics",
            "fixture",
            Intent.RESILIENCE,
            "metrics probe compiles",
            null,
            GivenDef.attach(
                "AttachDemoTarget",
                List.of("-p", "${port}", "${pid}", "${runDir}/QeMetricsProbe.java")),
            List.of(
                "launch", "awaitReady", "attach", "awaitOutput", "detach", "shutdown", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList(), output),
            CaseDef.TIER_FULL);
    ExecutionTrace trace = new CaseExecutor(env).execute(def, runDir);
    assertNull(trace.harnessError(), "client=" + trace.clientStderr());
    assertFalse(
        trace.clientStderr().stream().anyMatch(l -> l.contains("does not exist")),
        "client stderr=" + trace.clientStderr());
    assertNull(
        OutputCheck.firstUnmatched(output, trace.clientStdout()),
        "client stdout=" + trace.clientStdout() + " stderr=" + trace.clientStderr());
  }
}
