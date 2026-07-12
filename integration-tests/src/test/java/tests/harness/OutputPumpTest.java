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
package tests.harness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OutputPumpTest {

  /** Minimal fake Process exposing fixed stdout/stderr streams. */
  private static final class FakeProcess extends Process {
    private final InputStream out;
    private final InputStream err;

    FakeProcess(String stdout, String stderr) {
      this.out = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
      this.err = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return null;
    }

    @Override
    public InputStream getInputStream() {
      return out;
    }

    @Override
    public InputStream getErrorStream() {
      return err;
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {}
  }

  @Test
  void releasesWhenCompletionSatisfiedBeforeTimeout() throws Exception {
    FakeProcess p =
        new FakeProcess(
            "[main] INFO Attaching\n[main] INFO Started\ntag=ext-data-ok\nvalue=42\n", "");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("tag=ext-data-ok", "value=42"),
            TimeUnit.SECONDS.toMillis(5),
            false,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            out,
            err);
    assertTrue(completed, "completion satisfied by tag=/value= lines");
    assertTrue(out.toString().contains("value=42"), "stdout captured");
  }

  @Test
  void timesOutWhenConditionNeverMet() throws Exception {
    FakeProcess p = new FakeProcess("only noise\nmore noise\n", "");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("never-appears"),
            300L,
            false,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            out,
            err);
    assertFalse(completed, "condition never met -> timeout");
  }

  @Test
  void releasesEarlyOnStderrError() throws Exception {
    FakeProcess p = new FakeProcess("", "java.lang.RuntimeException: boom\n");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("never-appears"),
            TimeUnit.SECONDS.toMillis(5),
            false,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            out,
            err);
    assertTrue(completed, "stderr Exception releases the wait");
    assertTrue(err.toString().contains("boom"), "stderr captured");
  }

  @Test
  void appliesStderrSkipFilters() throws Exception {
    FakeProcess p = new FakeProcess("", "Server VM warning: ignore me\n");
    StringBuilder out = new StringBuilder();
    StringBuilder err = new StringBuilder();
    boolean completed =
        OutputPump.run(
            p,
            Completion.untilContains("never-appears"),
            300L,
            false,
            Arrays.asList("Server VM warning"),
            Collections.<String>emptyList(),
            out,
            err);
    assertFalse(completed, "skipped warning must not release or be treated as error");
    assertFalse(
        err.toString().contains("Server VM warning"), "skipped line not captured to stderr");
  }
}
