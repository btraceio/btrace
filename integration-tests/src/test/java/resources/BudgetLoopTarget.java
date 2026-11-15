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
package resources;

/**
 * Target for the probe budget tests. {@link #hot} runs tens of thousands of times per second, far
 * above any {@code probeMaxRate} the tests set; {@link #quiet} runs four times per second, far
 * below it. A {@code heartbeat:<n>} line every second shows the JVM is still doing work.
 */
public class BudgetLoopTarget extends TestApp {
  private static final int HOT_CALLS_PER_PASS = 100;
  private static final long QUIET_PERIOD_MS = 250;
  private static final long HEARTBEAT_PERIOD_MS = 1000;

  private volatile long sink;

  public static void main(String[] args) throws Exception {
    new BudgetLoopTarget().start();
  }

  @Override
  protected void startWork() {
    long nextQuiet = 0;
    long nextHeartbeat = 0;
    long heartbeat = 0;
    while (!Thread.currentThread().isInterrupted()) {
      for (int i = 0; i < HOT_CALLS_PER_PASS; i++) {
        hot(i);
      }
      long now = System.currentTimeMillis();
      if (now >= nextQuiet) {
        quiet();
        nextQuiet = now + QUIET_PERIOD_MS;
      }
      if (now >= nextHeartbeat) {
        print("heartbeat:" + heartbeat++);
        nextHeartbeat = now + HEARTBEAT_PERIOD_MS;
      }
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  void hot(int i) {
    sink += i;
  }

  void quiet() {
    sink++;
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
