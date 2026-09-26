/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
 * A test target that reports readiness and then ignores every stop request.
 *
 * <p>Unlike {@link TestApp}, it never reads stdin, so the harness's {@code done} line is simply
 * never consumed: {@code RuntimeTest.TestApp#stop()} runs its full grace period, fires the
 * exit-wedge sentinel, and force-destroys the process. That is exactly the wedged-teardown shape
 * the sentinel exists to capture.
 */
public class IgnoreStopApp {
  public static void main(String[] args) throws Exception {
    System.out.println("ready:" + getPID());
    System.out.flush();
    Object lock = new Object();
    synchronized (lock) {
      while (true) {
        lock.wait();
      }
    }
  }

  private static long getPID() {
    String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    return Long.parseLong(processName.split("@")[0]);
  }
}
