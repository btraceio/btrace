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
package qe.targets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

/**
 * Long-running fixture for the QE harness. Runs on any supported JDK, so it uses Java 8 APIs only.
 */
public final class SteadyStateTarget {
  private SteadyStateTarget() {}

  public static void main(String[] args) throws Exception {
    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.contains("@") ? vmName.substring(0, vmName.indexOf('@')) : vmName;
    System.out.println("ready:" + pid);
    System.out.flush();
    startupMarker();

    Thread stdinThread = new Thread(SteadyStateTarget::watchStdin, "qe-target-stdin");
    stdinThread.setDaemon(true);
    stdinThread.start();

    long counter = 0;
    long lastChecksumAt = System.currentTimeMillis();
    while (true) {
      counter++;
      canaryTick();
      long now = System.currentTimeMillis();
      if (now - lastChecksumAt >= 200) {
        System.out.println("checksum:" + counter);
        System.out.flush();
        lastChecksumAt = now;
      }
      Thread.sleep(10);
    }
  }

  private static void watchStdin() {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if ("exit".equals(line.trim())) {
          System.out.println("exiting:0");
          System.out.flush();
          System.exit(0);
        }
      }
    } catch (IOException ignored) {
      // parent closed stdin; keep running until terminated
    }
  }

  /** Instrumentation point for the startup probe. Called exactly once. */
  public static void startupMarker() {}

  /** Instrumentation point for the canary probe. */
  public static void canaryTick() {}
}
