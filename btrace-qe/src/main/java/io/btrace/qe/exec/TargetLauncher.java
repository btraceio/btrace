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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TargetLauncher {

  public TargetProcess launch(GivenDef given, QeEnvironment env, Path runDir) throws IOException {
    int port = env.allocateFreePort();
    Path source = null;
    Path targetDir = null;
    if (given.isSourceTarget()) {
      source = sourceFile(env.repoRoot(), given.target());
      targetDir = targetDir(env.repoRoot(), given.target());
      if (!Files.isRegularFile(source)) {
        throw new IOException(
            "given.target source file not found: " + source + " (resolved against qe.repoRoot)");
      }
    }

    List<String> cmd = new ArrayList<>();
    cmd.add(env.javaExecutable());
    cmd.addAll(resolvePlaceholders(given.jvmArgs(), env.btraceJarPath(), runDir, port, targetDir));
    if (source == null) {
      cmd.add("-cp");
      cmd.add(env.targetsClasspath());
    }
    cmd.add("-Dbtrace.comm.protocol=2");
    cmd.add("-Dbtrace.comm.autoNegotiate=false");
    cmd.add("-Dbtrace.comm.forceVersion=true");
    if (given.mode() != GivenDef.Mode.ATTACH) {
      // attach mode starts a plain JVM; the client loads the agent into it later
      String agentArgs = resolveAgentArgs(given.agentArgs(), port, env.probeClassesDir(), runDir);
      cmd.add("-javaagent:" + env.btraceJarPath() + "=" + agentArgs);
    }
    // a demo app runs from its own directory, as its run.sh does (relative paths in its options)
    cmd.add(source == null ? "qe.targets." + given.target() : source.getFileName().toString());

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    // the agent discovers the bundled extensions from the installation root, as under bin/btrace
    pb.environment().put("BTRACE_HOME", env.btraceHome().toString());
    pb.directory((source == null ? runDir : targetDir).toFile());
    return new TargetProcess(pb.start(), -1, port);
  }

  /** The source file a {@code .java} target names, resolved against the repository root. */
  static Path sourceFile(Path repoRoot, String target) {
    return repoRoot.resolve(target).normalize();
  }

  /** The directory a {@code .java} target lives in: the value of {@code ${targetDir}}. */
  static Path targetDir(Path repoRoot, String target) {
    return sourceFile(repoRoot, target).getParent();
  }

  /**
   * Substitutes {@code ${btraceJar}}, {@code ${runDir}}, {@code ${port}} and {@code ${targetDir}}
   * (left untouched when there is no target directory, which validation rules out).
   */
  static List<String> resolvePlaceholders(
      List<String> raw, Path btraceJar, Path runDir, int port, Path targetDir) {
    List<String> out = new ArrayList<>(raw.size());
    for (String arg : raw) {
      String resolved =
          arg.replace("${btraceJar}", btraceJar.toString())
              .replace("${runDir}", runDir.toString())
              .replace("${port}", String.valueOf(port));
      if (targetDir != null) {
        resolved = resolved.replace("${targetDir}", targetDir.toString());
      }
      out.add(resolved);
    }
    return out;
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
