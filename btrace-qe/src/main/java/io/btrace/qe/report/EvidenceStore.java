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

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.exec.StepOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EvidenceStore {

  public void persist(ExecutionTrace trace, Path runDir) throws IOException {
    Files.createDirectories(runDir);
    write(runDir.resolve("stdout.log"), String.join("\n", trace.stdout()));
    write(runDir.resolve("stderr.log"), String.join("\n", trace.stderr()));
    StringBuilder steps = new StringBuilder();
    for (StepOutcome s : trace.steps()) {
      steps
          .append(s.step())
          .append('\t')
          .append(s.success() ? "ok" : "FAILED")
          .append('\t')
          .append(s.elapsedMs())
          .append("ms");
      if (s.error() != null) {
        steps.append('\t').append(s.error());
      }
      steps.append('\n');
    }
    steps
        .append("exitCode=")
        .append(trace.exitCode())
        .append(" timedOut=")
        .append(trace.timedOut())
        .append(" exitedPrematurely=")
        .append(trace.exitedPrematurely())
        .append(" canaryOk=")
        .append(trace.canaryOk())
        .append('\n');
    write(runDir.resolve("steps.log"), steps.toString());
    if (trace.threadDumpText() != null) {
      write(runDir.resolve("thread-dump.txt"), trace.threadDumpText());
    }
  }

  private static void write(Path file, String content) throws IOException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }
}
