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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Single shared implementation of the "read a BTrace client process's stdout/stderr, wait until a
 * {@link Completion} is satisfied (or timeout)" logic. Replaces the three near-identical
 * client-reader loops in {@code RuntimeTest} (attach, attachOneliner, runBTrace); {@code
 * testStartup} keys off a target-process {@code ready:} line rather than a separate client process
 * and is not migrated onto this utility.
 *
 * <p>The provided {@code stdout}/{@code stderr} builders are live references: the reader threads
 * keep appending to them after {@link #run} returns, so output that arrives slightly after the
 * completion condition is met (or after a timeout) is still captured for the caller's assertions.
 */
public final class OutputPump {

  private OutputPump() {}

  public static boolean run(
      final Process p,
      final Completion completion,
      final long timeoutMs,
      final boolean skipDebugLines,
      final List<String> stderrSkipSubstrings,
      final List<String> stderrSkipPrefixes,
      final StringBuilder stdout,
      final StringBuilder stderr)
      throws InterruptedException {

    final CountDownLatch done = new CountDownLatch(1);

    Thread outT =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try (BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = br.readLine()) != null) {
                    stdout.append(line).append('\n');
                    System.out.println("[btrace out] " + line);
                    if (skipDebugLines && line.contains("DEBUG")) {
                      continue;
                    }
                    if (completion.onStdout(line)) {
                      done.countDown();
                    }
                  }
                } catch (Exception e) {
                  done.countDown();
                }
              }
            },
            "Stdout Reader");

    Thread errT =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try (BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = br.readLine()) != null) {
                    System.out.println("[btrace err] " + line);
                    if (isSkipped(line, stderrSkipSubstrings, stderrSkipPrefixes)) {
                      continue;
                    }
                    stderr.append(line).append('\n');
                    if (completion.onStderr(line)) {
                      done.countDown();
                    }
                    if (line.contains("Exception") || line.contains("Error")) {
                      done.countDown();
                    }
                  }
                } catch (Exception e) {
                  done.countDown();
                }
              }
            },
            "Stderr Reader");

    outT.setDaemon(true);
    errT.setDaemon(true);
    outT.start();
    errT.start();

    return done.await(timeoutMs, TimeUnit.MILLISECONDS);
  }

  private static boolean isSkipped(
      String line, List<String> skipSubstrings, List<String> skipPrefixes) {
    for (String s : skipSubstrings) {
      if (line.contains(s)) {
        return true;
      }
    }
    for (String pfx : skipPrefixes) {
      if (line.startsWith(pfx)) {
        return true;
      }
    }
    return false;
  }
}
