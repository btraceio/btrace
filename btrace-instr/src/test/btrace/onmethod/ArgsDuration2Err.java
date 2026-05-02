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


package traces.onmethod;

import static org.openjdk.btrace.core.BTraceUtils.*;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.annotations.TargetInstance;

/** @author Jaroslav Bachorik */
@BTrace
public class ArgsDuration2Err {
  @OnMethod(
      clazz = "/.*\\.OnMethodTest/",
      method = "args",
      location = @Location(value = Kind.ERROR))
  public static void args(@Self Object self, @Duration long dur, @TargetInstance Throwable err) {
    println("args");
  }

  @OnMethod(
      clazz = "/.*\\.OnMethodTest/",
      method = "args",
      location = @Location(value = Kind.ERROR))
  public static void args2(@Self Object self, @Duration long dur, @TargetInstance Throwable err) {
    println("args");
  }
}
