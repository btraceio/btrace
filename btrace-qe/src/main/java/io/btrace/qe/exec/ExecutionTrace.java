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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class ExecutionTrace {
  private final List<StepOutcome> steps;
  private final boolean timedOut;
  private final boolean exitedPrematurely;
  private final Integer exitCode;
  private final List<String> stdout;
  private final List<String> stderr;
  private final Boolean canaryOk;
  private final String threadDumpText;
  private final boolean hasHsErrLog;
  private final Path runDir;
  private final List<String> clientStdout;
  private final List<String> clientStderr;
  private final Integer clientExitCode;

  public ExecutionTrace(
      List<StepOutcome> steps,
      boolean timedOut,
      boolean exitedPrematurely,
      Integer exitCode,
      List<String> stdout,
      List<String> stderr,
      Boolean canaryOk,
      String threadDumpText,
      boolean hasHsErrLog,
      Path runDir) {
    this(
        steps,
        timedOut,
        exitedPrematurely,
        exitCode,
        stdout,
        stderr,
        canaryOk,
        threadDumpText,
        hasHsErrLog,
        runDir,
        Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  /** The two trailing lists are the attached client's output; empty outside attach mode. */
  public ExecutionTrace(
      List<StepOutcome> steps,
      boolean timedOut,
      boolean exitedPrematurely,
      Integer exitCode,
      List<String> stdout,
      List<String> stderr,
      Boolean canaryOk,
      String threadDumpText,
      boolean hasHsErrLog,
      Path runDir,
      List<String> clientStdout,
      List<String> clientStderr) {
    this(
        steps,
        timedOut,
        exitedPrematurely,
        exitCode,
        stdout,
        stderr,
        canaryOk,
        threadDumpText,
        hasHsErrLog,
        runDir,
        clientStdout,
        clientStderr,
        null);
  }

  /**
   * {@code clientExitCode} is the attached client's exit code when it ended before the harness
   * reaped it; null outside attach mode or when the harness had to kill it.
   */
  public ExecutionTrace(
      List<StepOutcome> steps,
      boolean timedOut,
      boolean exitedPrematurely,
      Integer exitCode,
      List<String> stdout,
      List<String> stderr,
      Boolean canaryOk,
      String threadDumpText,
      boolean hasHsErrLog,
      Path runDir,
      List<String> clientStdout,
      List<String> clientStderr,
      Integer clientExitCode) {
    this.steps = steps;
    this.timedOut = timedOut;
    this.exitedPrematurely = exitedPrematurely;
    this.exitCode = exitCode;
    this.stdout = stdout;
    this.stderr = stderr;
    this.canaryOk = canaryOk;
    this.threadDumpText = threadDumpText;
    this.hasHsErrLog = hasHsErrLog;
    this.runDir = runDir;
    this.clientStdout = clientStdout;
    this.clientStderr = clientStderr;
    this.clientExitCode = clientExitCode;
  }

  public List<StepOutcome> steps() {
    return steps;
  }

  public boolean timedOut() {
    return timedOut;
  }

  public boolean exitedPrematurely() {
    return exitedPrematurely;
  }

  public Integer exitCode() {
    return exitCode;
  }

  public List<String> stdout() {
    return stdout;
  }

  public List<String> stderr() {
    return stderr;
  }

  public Boolean canaryOk() {
    return canaryOk;
  }

  public String threadDumpText() {
    return threadDumpText;
  }

  public boolean hasHsErrLog() {
    return hasHsErrLog;
  }

  public Path runDir() {
    return runDir;
  }

  /** Stdout of the client attached by an {@code attach} step; empty in other modes. */
  public List<String> clientStdout() {
    return clientStdout;
  }

  /** Stderr of the client attached by an {@code attach} step; empty in other modes. */
  public List<String> clientStderr() {
    return clientStderr;
  }

  /**
   * Exit code of the client attached by an {@code attach} step, if it ended before the harness
   * reaped it (on its own, or through {@code detach}); null in other modes or when it was killed.
   */
  public Integer clientExitCode() {
    return clientExitCode;
  }

  /**
   * A step that failed without timing out and without the target dying is a fault of the harness,
   * not of the product. Returns {@code "<step>: <error>"} for the first such step, or {@code null}.
   */
  public String harnessError() {
    if (timedOut || exitedPrematurely) {
      return null;
    }
    for (StepOutcome s : steps) {
      if (!s.success()) {
        return s.step() + ": " + s.error();
      }
    }
    return null;
  }
}
