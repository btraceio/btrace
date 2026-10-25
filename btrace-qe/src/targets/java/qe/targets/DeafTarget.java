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

import java.lang.management.ManagementFactory;

/** QE fixture. Java 8 APIs only. */
public final class DeafTarget {
  private DeafTarget() {}

  public static void main(String[] args) throws Exception {
    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.contains("@") ? vmName.substring(0, vmName.indexOf('@')) : vmName;
    System.out.println("ready:" + pid);
    System.out.flush();
    while (true) {
      Thread.sleep(100);
    }
  }
}
