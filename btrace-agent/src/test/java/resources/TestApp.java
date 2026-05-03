/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package resources;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Jaroslav Bachorik
 */
public abstract class TestApp implements TestPrinter {
  protected final AtomicBoolean finished = new AtomicBoolean(false);

  public final void start() throws Exception {
    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                startWork();
              }
            },
            "Worker Thread");
    System.out.println("ready:" + getPID());
    System.out.flush();
    t.setDaemon(true);
    t.start();

    do {
      String resp =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
      System.out.println("Received " + resp + " - " + "done".contains(resp));
      if ("done".contains(resp)) {
        System.out.flush();
        System.out.println(System.currentTimeMillis() + ":  Interrupting the worker thread");
        finished.set(true);
        System.out.println(
            System.currentTimeMillis() + ": Waiting for the worker thread to finish");
        t.join(1000);
        if (t.isAlive()) {
          Thread.dumpStack();
          throw new RuntimeException("Dangling worker thread");
        }
        System.out.println(System.currentTimeMillis() + ": Worker thread finished");
        break;
      }
    } while (true);
  }

  private static long getPID() {
    String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    return Long.parseLong(processName.split("@")[0]);
  }

  /** The work here should be done repeatedly until the thread gets interrupted */
  protected abstract void startWork();
}
