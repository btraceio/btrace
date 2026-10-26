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

import io.btrace.qe.model.GivenDef;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TargetLauncher {

  public TargetProcess launch(GivenDef given, QeEnvironment env, Path runDir) throws IOException {
    int port = env.allocateFreePort();

    List<String> cmd = new ArrayList<>();
    cmd.add(env.javaExecutable());
    cmd.add("-cp");
    cmd.add(env.targetsClasspath());
    cmd.add("-Dbtrace.comm.protocol=2");
    cmd.add("-Dbtrace.comm.autoNegotiate=false");
    cmd.add("-Dbtrace.comm.forceVersion=true");
    if (given.mode() != GivenDef.Mode.ATTACH) {
      // attach mode starts a plain JVM; the client loads the agent into it later
      String agentArgs = resolveAgentArgs(given.agentArgs(), port, env.probeClassesDir(), runDir);
      cmd.add("-javaagent:" + env.btraceJarPath() + "=" + agentArgs);
    }
    cmd.add("qe.targets." + given.target());

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.directory(runDir.toFile());
    return new TargetProcess(pb.start(), -1, port);
  }

  /**
   * Substitutes {@code ${port}}, {@code ${probeClassesDir}} and {@code ${runDir}}. The harness port
   * is appended as {@code port=N} only when the case sets no {@code port=} key itself, so a case
   * can place (or abuse) the port explicitly; empty agent args resolve to exactly {@code port=N}.
   */
  static String resolveAgentArgs(String raw, int port, Path probeClassesDir, Path runDir) {
    String resolved =
        (raw != null ? raw : "")
            .replace("${port}", String.valueOf(port))
            .replace("${probeClassesDir}", probeClassesDir.toString())
            .replace("${runDir}", runDir.toString());
    for (String entry : resolved.split(",")) {
      int eq = entry.indexOf('=');
      if (eq >= 0 && entry.substring(0, eq).trim().equals("port")) {
        return resolved;
      }
    }
    return resolved.isEmpty() ? "port=" + port : resolved + ",port=" + port;
  }
}
