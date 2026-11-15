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
package btrace;

import static io.btrace.core.BTraceUtils.*;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnError;
import io.btrace.core.annotations.OnLowMemory;
import io.btrace.core.annotations.OnTimer;
import java.lang.management.MemoryUsage;

/**
 * An {@code @OnLowMemory} handler that throws. The exception must reach {@code @OnError} and the
 * probe must keep running afterwards.
 */
@BTrace
public class OnLowMemoryErrorTest {
  private static boolean failed;
  private static boolean reported;

  @OnLowMemory(pool = "Metaspace", threshold = 1048576)
  public static void onLowMemory(MemoryUsage usage) {
    println("lowmem-handler-entered");
    failed = true;
    substr("x", 2);
  }

  @OnError
  public static void onError(Throwable t) {
    println(strcat("lowmem-error-handler:", str(t)));
  }

  @OnTimer(250)
  public static void onTimer() {
    if (failed && !reported) {
      reported = true;
      println("lowmem-probe-alive-after-failure");
    }
  }
}
