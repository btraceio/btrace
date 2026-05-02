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
package org.openjdk.btrace.instr;

import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.instr.random.SharedRandomIntProvider;
import org.openjdk.btrace.instr.random.ThreadLocalRandomIntProvider;

/**
 * @author Jaroslav Bachorik
 */
@SuppressWarnings("LiteralClassName")
public abstract class RandomIntProvider {
  private static final RandomIntProvider rndIntProvider;
  // for the testability purposes; BTraceRuntime initializes Unsafe instance
  // and fails under JUnit
  private static boolean useBtraceEnter = true;

  static {
    boolean entered = false;
    try {
      if (useBtraceEnter) {
        entered = BTraceRuntime.enter();
      }
      Class<?> clz = null;
      try {
        clz = Class.forName("java.util.concurrent.ThreadLocalRandom");
      } catch (Throwable e) {
        // ThreadLocalRandom not accessible -> pre JDK8
      }
      if (clz != null) {
        rndIntProvider = new ThreadLocalRandomIntProvider();
      } else {
        //noinspection StaticInitializerReferencesSubClass
        rndIntProvider = new SharedRandomIntProvider();
      }
    } finally {
      if (entered) BTraceRuntime.leave();
    }
  }

  protected RandomIntProvider() {}

  public static RandomIntProvider getInstance() {
    return rndIntProvider;
  }

  public abstract int nextInt(int bound);
}
