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

import java.util.ArrayList;
import java.util.List;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.types.AnyType;

/** @author Jaroslav Bachorik */
@BTrace
public class ArgsSigMatch {
  @OnMethod(clazz = "/.*\\.OnMethodTest/", method = "argsTypeMatch")
  public static void m1(@Self Object self, List<String> a) {
    println("m1");
  }

  @OnMethod(clazz = "/.*\\.OnMethodTest/", method = "argsTypeMatch", exactTypeMatch = true)
  public static void m2(@Self AnyType self, List<String> a) {
    println("m2");
  }

  @OnMethod(clazz = "/.*\\.OnMethodTest/", method = "argsTypeMatch", exactTypeMatch = true)
  public static void m3(@Self AnyType self, ArrayList<String> a) {
    println("m3");
  }
}
