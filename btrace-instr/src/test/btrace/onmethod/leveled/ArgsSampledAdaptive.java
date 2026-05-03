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


package traces.onmethod.leveled;

import static org.openjdk.btrace.core.BTraceUtils.*;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Level;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.core.annotations.Self;

/** @author Jaroslav Bachorik */
@BTrace
public class ArgsSampledAdaptive {
  @OnMethod(clazz = "/.*\\.OnMethodTest/", method = "args", enableAt = @Level(">=1"))
  @Sampled(kind = Sampled.Sampler.Adaptive)
  public static void args(@Self Object self, String a, long b, String[] c, int[] d) {
    println("this = " + self);
    println("args");
  }
}
