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

/**
 * This script traces method/block entry into every method of every class in javax.swing package!
 * Think before using this script -- this will slow down your app significantly!! Note tha
 * Where.BEFORE is default. For synchronized blocks, BEFORE means before "monitorenter" bytecode.
 * For synchronized methods, we can not have probe point Where.BEFORE. Lock is acquired before
 * entering synchronized method. By making the probe point Where.AFTER for SYNC_ENTER, we probe
 * after monitorenter bytecode or synchronized method entry.
 */
@BTrace
public class BTRACE87 {
  @OnMethod(
      clazz = "/.*\\.BTRACE87/",
      method = "/.*/",
      location = @Location(value = Kind.CALL, clazz = "/.*/", method = "/.*/"))
  public static void o(
      @Self Object self, @ProbeMethodName String pmn, AnyType[] args) { // all calls to methods
    // self - this for the method call
    // pmn - textual representation of the method
    // contents of args array:
    // [0]..[n] - original method call arguments
    printArray(args);
  }
}
