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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class TargetProcess {
  private final Process process;
  private final List<String> stdout = Collections.synchronizedList(new ArrayList<String>());
  private final List<String> stderr = Collections.synchronizedList(new ArrayList<String>());
  private final int port;
  private final Thread stdoutPump;
  private final Thread stderrPump;
  private volatile long pid;

  TargetProcess(Process process, long pid, int port) {
    this.process = process;
    this.pid = pid;
    this.port = port;
    this.stdoutPump = new OutputPump(process.getInputStream(), stdout, "qe-process-stdout");
    this.stderrPump = new OutputPump(process.getErrorStream(), stderr, "qe-process-stderr");
    stdoutPump.start();
    stderrPump.start();
  }

  /** Waits (bounded, per stream) for the output pumps to reach EOF so snapshots are complete. */
  public void awaitOutputDrained(long ms) throws InterruptedException {
    stdoutPump.join(ms);
    stderrPump.join(ms);
  }

  void assignPid(long pid) {
    this.pid = pid;
  }

  public long pid() {
    return pid;
  }

  public int assignedPort() {
    return port;
  }

  public Process process() {
    return process;
  }

  public List<String> stdoutSnapshot() {
    synchronized (stdout) {
      return new ArrayList<>(stdout);
    }
  }

  public List<String> stderrSnapshot() {
    synchronized (stderr) {
      return new ArrayList<>(stderr);
    }
  }

  /** Returns the first stdout line with the prefix, or null on timeout or process death. */
  public String awaitLine(String prefix, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    synchronized (stdout) {
      while (true) {
        for (String line : stdout) {
          if (line.startsWith(prefix)) {
            return line;
          }
        }
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0 || !process.isAlive()) {
          return null;
        }
        stdout.wait(Math.min(remaining, 200L));
      }
    }
  }

  /**
   * Waits for a stdout line at index {@code from} or later that the pattern finds; returns its
   * index, or -1 on timeout or process death (a dead process still gets its already-pumped lines
   * scanned).
   */
  public int awaitMatch(Pattern pattern, int from, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    synchronized (stdout) {
      while (true) {
        for (int i = from; i < stdout.size(); i++) {
          if (pattern.matcher(stdout.get(i)).find()) {
            return i;
          }
        }
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0 || !process.isAlive()) {
          return -1;
        }
        stdout.wait(Math.min(remaining, 200L));
      }
    }
  }

  public void writeStdin(String line) throws IOException {
    OutputStream os = process.getOutputStream();
    os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
    os.flush();
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  public void destroyForcibly() {
    process.destroyForcibly();
  }
}
