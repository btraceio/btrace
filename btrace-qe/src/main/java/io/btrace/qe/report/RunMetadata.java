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
package io.btrace.qe.report;

import io.btrace.qe.exec.QeEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class RunMetadata {
  private final long seed;
  private final String gitSha;
  private final String btraceVersion;
  private final String jdk;
  private final String os;
  private final int cpuCount;
  private final String commandLine;
  private final long timeoutMs;

  public RunMetadata(
      long seed,
      String gitSha,
      String btraceVersion,
      String jdk,
      String os,
      int cpuCount,
      String commandLine) {
    this(
        seed,
        gitSha,
        btraceVersion,
        jdk,
        os,
        cpuCount,
        commandLine,
        QeEnvironment.DEFAULT_TIMEOUT_MS);
  }

  public RunMetadata(
      long seed,
      String gitSha,
      String btraceVersion,
      String jdk,
      String os,
      int cpuCount,
      String commandLine,
      long timeoutMs) {
    this.timeoutMs = timeoutMs;
    this.seed = seed;
    this.gitSha = gitSha;
    this.btraceVersion = btraceVersion;
    this.jdk = jdk;
    this.os = os;
    this.cpuCount = cpuCount;
    this.commandLine = commandLine;
  }

  /** Records the JDK the targets run on ({@code qe.javaHome}), not the harness JVM. */
  public static RunMetadata capture(long seed, String commandLine, long timeoutMs) {
    return new RunMetadata(
        seed,
        readGitSha(),
        System.getProperty("qe.btraceVersion", "unknown"),
        System.getProperty("qe.javaHome", "unknown"),
        System.getProperty("os.name") + " " + System.getProperty("os.version"),
        Runtime.getRuntime().availableProcessors(),
        commandLine,
        timeoutMs);
  }

  private static String readGitSha() {
    try {
      Process p = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
      if (!p.waitFor(5, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return "unknown";
      }
      if (p.exitValue() != 0) {
        return "unknown";
      }
      try (BufferedReader r =
          new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line = r.readLine();
        return line != null ? line.trim() : "unknown";
      }
    } catch (Exception e) {
      return "unknown";
    }
  }

  public long seed() {
    return seed;
  }

  public String gitSha() {
    return gitSha;
  }

  public String btraceVersion() {
    return btraceVersion;
  }

  public String jdk() {
    return jdk;
  }

  public String os() {
    return os;
  }

  public int cpuCount() {
    return cpuCount;
  }

  public String commandLine() {
    return commandLine;
  }

  /** The per-step timeout the run used; part of every repro command when non-default. */
  public long timeoutMs() {
    return timeoutMs;
  }
}
