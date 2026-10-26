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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class QeEnvironment {
  public static final long DEFAULT_TIMEOUT_MS = 30_000L;

  /** The canary step reserves ~6 s of its budget for detach, so shorter timeouts starve it. */
  public static final long MIN_TIMEOUT_MS = 10_000L;

  private final String javaHome;
  private final Path btraceJarPath;
  private final Path probeClassesDir;
  private final String targetsClasspath;
  private final Path repoRoot;
  private final long defaultTimeoutMs;

  private QeEnvironment(
      String javaHome,
      Path btraceJarPath,
      Path probeClassesDir,
      String targetsClasspath,
      Path repoRoot,
      long defaultTimeoutMs) {
    this.javaHome = javaHome;
    this.btraceJarPath = btraceJarPath;
    this.probeClassesDir = probeClassesDir;
    this.targetsClasspath = targetsClasspath;
    this.repoRoot = repoRoot;
    this.defaultTimeoutMs = defaultTimeoutMs;
  }

  public static QeEnvironment fromSystemProperties() {
    String javaHome = require("qe.javaHome").replace(File.separator + "jre", "");
    Path btraceJarPath = Paths.get(require("btrace.libs"), "btrace.jar");
    if (!Files.isRegularFile(btraceJarPath)) {
      throw new IllegalStateException("btrace.jar not found at " + btraceJarPath);
    }
    Path probeClassesDir = Paths.get(require("qe.probeClassesDir"));
    String targetsClasspath = require("qe.targetsClasspath");
    Path repoRoot = Paths.get(require("qe.repoRoot"));
    if (!Files.isDirectory(repoRoot)) {
      throw new IllegalStateException("qe.repoRoot is not a directory: " + repoRoot);
    }
    long timeout =
        Long.parseLong(System.getProperty("qe.timeout", String.valueOf(DEFAULT_TIMEOUT_MS)));
    if (timeout < MIN_TIMEOUT_MS) {
      throw new IllegalStateException(
          "qe.timeout must be >= " + MIN_TIMEOUT_MS + " ms (canary budget needs headroom)");
    }
    return new QeEnvironment(
        javaHome, btraceJarPath, probeClassesDir, targetsClasspath, repoRoot, timeout);
  }

  private static String require(String key) {
    String value = System.getProperty(key);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException(key + " system property is not set");
    }
    return value;
  }

  public String javaHome() {
    return javaHome;
  }

  public String javaExecutable() {
    return Paths.get(javaHome, "bin", "java").toString();
  }

  public String jcmdPath() {
    return Paths.get(javaHome, "bin", "jcmd").toString();
  }

  public boolean isModularJdk() {
    return Files.isDirectory(Paths.get(javaHome, "jmods"));
  }

  public Path btraceJarPath() {
    return btraceJarPath;
  }

  /**
   * The installation root the client and agent expect: {@code btrace.jar} sits in {@code
   * <home>/libs/} and the exploded extensions in {@code <home>/extensions/}.
   */
  public Path btraceHome() {
    return btraceJarPath.getParent().getParent();
  }

  public Path probeClassesDir() {
    return probeClassesDir;
  }

  public String targetsClasspath() {
    return targetsClasspath;
  }

  /** Root of the repository checkout; source-file targets are resolved against it. */
  public Path repoRoot() {
    return repoRoot;
  }

  public long defaultTimeoutMs() {
    return defaultTimeoutMs;
  }

  public int allocateFreePort() {
    try (ServerSocket ss = new ServerSocket(0)) {
      return ss.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("failed to find a free port", e);
    }
  }
}
