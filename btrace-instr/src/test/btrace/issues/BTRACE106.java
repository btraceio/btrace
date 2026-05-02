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

import static org.openjdk.btrace.core.BTraceUtils.*;

import org.openjdk.btrace.core.annotations.*;

@BTrace
public class BTRACE106 {
  @OnMethod(clazz = "@/.*\\.Deprecated/", method = "aMethod")
  public static void o1(@Self Object self, @ProbeMethodName String pmn) { // all calls to methods
    println(pmn);
  }

  @OnMethod(clazz = "@/.*\\.Deprecated/", method = "bMethod", location = @Location(Kind.RETURN))
  public static void o2(
      @Self Object self, @ProbeMethodName String pmn, @Duration long dur) { // all calls to methods
    println(pmn);
  }

  @OnMethod(
      clazz = "@/.*\\.Deprecated/",
      method = "@/.*\\.Deprecated/",
      location = @Location(Kind.RETURN))
  public static void o3(
      @Self Object self, @ProbeMethodName String pmn, @Duration long dur) { // all calls to methods
    println(pmn);
  }
}
