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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the btrace client the same way btrace-dist's bin/btrace launcher does. */
public final class ClientLauncher {

  public TargetProcess launch(List<String> clientArgs, QeEnvironment env, Path runDir)
      throws IOException {
    List<String> cmd = new ArrayList<>();
    cmd.add(env.javaExecutable());
    cmd.add("-XX:+IgnoreUnrecognizedVMOptions");
    if (env.isModularJdk()) {
      cmd.add("-XX:+AllowRedefinitionToAddDeleteMethods");
      cmd.add("--add-exports");
      cmd.add("jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED");
    }
    // bin/btrace points the client at <home>/libs so it finds <home>/extensions/*-api.jar when it
    // compiles a probe that imports an extension's API
    cmd.add("-Dbtrace.libs=" + env.btraceJarPath().getParent());
    cmd.add("-cp");
    cmd.add(env.btraceJarPath().toString());
    cmd.add("io.btrace.boot.Loader");
    cmd.addAll(clientArgs);

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.environment().put("BTRACE_HOME", env.btraceHome().toString());
    pb.directory(runDir.toFile());
    return new TargetProcess(pb.start(), -1, -1);
  }
}
