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
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.annotations.TLS;

/** @author Jaroslav Bachorik */
@BTrace
public class ArgsShared {
  @TLS private static int cntr = 15;

  @org.openjdk.btrace.core.annotations.Export private static long exported = 1;

  @OnMethod(clazz = "/.*\\.OnMethodTest/", method = "args")
  public static void args(@Self Object self, String a, long b, String[] c, int[] d) {
    println("this = " + self);
    println("args");
    println(str(cntr));
    cntr++;

    dumpExported();
  }

  private static void dumpExported() {
    println(str(exported));
    incExported();
  }

  private static void incExported() {
    exported++;
  }

  private static void unusedcode() {
    println("unused");
  }
}
