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
import org.openjdk.btrace.core.types.AnyType;

@BTrace
public class TezSplitter {
  @OnMethod(
      clazz = "org.apache.hadoop.mapred.split.TezMapredSplitsGrouper",
      method = "getGroupedSplits")
  public static void getGroupedSplitsHook(AnyType[] args) {
    println("here");
    //        Object[] vals = (Object[])(Object)args[1];
    //        for (Object o : vals) {
    //            println(o);
    //        }
  }
}
