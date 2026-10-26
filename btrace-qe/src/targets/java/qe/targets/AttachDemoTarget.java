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
import java.util.Random;

/**
 * Fixture for attach-mode cases: starts without the agent, prints {@code ready:<pid>} and then
 * calls {@link #work(int)} every 50 ms so an attached probe has a method to trace. Exits on {@code
 * exit} on stdin. Runs on any supported JDK, so it uses Java 8 APIs only.
 */
public final class AttachDemoTarget {
  private static final Random RANDOM = new Random();

  private AttachDemoTarget() {}

  public static void main(String[] args) throws Exception {
    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.contains("@") ? vmName.substring(0, vmName.indexOf('@')) : vmName;
    System.out.println("ready:" + pid);
    System.out.flush();

    Thread stdinThread = new Thread(AttachDemoTarget::watchStdin, "qe-target-stdin");
    stdinThread.setDaemon(true);
    stdinThread.start();

    int iteration = 0;
    while (true) {
      work(iteration++);
      Thread.sleep(50);
    }
  }

  /** Instrumentation point: does between 1 and 20 ms of "work". */
  public static void work(int iteration) {
    try {
      Thread.sleep(1 + RANDOM.nextInt(20));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
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
}
