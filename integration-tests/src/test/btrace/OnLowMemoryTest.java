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
import io.btrace.core.annotations.OnLowMemory;
import java.lang.management.MemoryUsage;

/**
 * Watches the Metaspace pool, which every JDK and GC has, with a threshold the target is past as
 * soon as the agent is loaded. The handler prints the usage it receives so the test can check the
 * value against the threshold.
 */
@BTrace
public class OnLowMemoryTest {
  @OnLowMemory(pool = "Metaspace", threshold = 1048576)
  public static void onLowMemory(MemoryUsage usage) {
    println(strcat("lowmem used=", str(Sys.Memory.used(usage))));
  }
}
