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

package traces.onmethod;

import static io.btrace.core.BTraceUtils.*;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.TargetInstance;
import io.btrace.core.annotations.TargetMethodOrField;
import io.btrace.core.annotations.Where;

/** Times the calls of a method whose calls sit on separate branches. */
@BTrace
public class MethodCallInBranchDuration {
  @OnMethod(
      clazz = "/.*\\.CallInBranchTarget/",
      method = "/call.*/",
      location = @Location(value = Kind.CALL, clazz = "/.*/", method = "/.*/", where = Where.AFTER))
  public static void args(
      @TargetInstance Object target, @TargetMethodOrField String method, @Duration long dur) {
    println("args");
  }
}
