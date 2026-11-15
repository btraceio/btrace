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

/** Watches a pool no JVM has; the agent must tell the client instead of staying silent. */
@BTrace
public class OnLowMemoryUnknownPoolTest {
  @OnLowMemory(pool = "No Such Pool", threshold = 1048576)
  public static void onLowMemory() {
    println("lowmem-unknown-pool-handler-ran");
  }
}
