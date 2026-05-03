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


package traces.issues;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.Profiler;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.statsd.Statsd;

@BTrace
class BTRACE256 {
  @Property Profiler swingProfiler = BTraceUtils.Profiling.newProfiler();

  @Injected
  private Statsd sd;

  @OnMethod(clazz = "/.*\\.BTRACE256/", method = "doStuff")
  void entry(@ProbeMethodName(fqn = true) String probeMethod) {
    BTraceUtils.Profiling.recordEntry(swingProfiler, probeMethod);
  }

  @OnMethod(
      clazz = "/.*\\.BTRACE256/",
      method = "doStuff",
      location = @Location(value = Kind.RETURN))
  void exit(@ProbeMethodName(fqn = true) String probeMethod, @Duration long duration) {
    BTraceUtils.Profiling.recordExit(swingProfiler, probeMethod, duration);
    sd.increment("my.metric.b", "regular,distribution:gaussian");
  }

  @OnTimer(5000)
  void timer() {
    BTraceUtils.Profiling.printSnapshot("AM performance profile", swingProfiler);
  }
}
