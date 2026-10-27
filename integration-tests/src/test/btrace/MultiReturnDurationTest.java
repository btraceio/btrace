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
package btrace;

import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;

/**
 * RETURN probe with {@code @Duration} on a method that has more than one return instruction. The
 * duration must be measured at whichever return actually runs, not only at the first one in
 * bytecode order.
 */
@BTrace
public class MultiReturnDurationTest {
  @OnMethod(
      clazz = "resources.MultiReturnTarget",
      method = "earlyReturn",
      location = @Location(Kind.RETURN))
  public static void onReturn(@Duration long durationNs) {
    println("earlyReturn ms=" + (durationNs / 1000000));
  }
}
