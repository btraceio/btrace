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


import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnTimer;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;

import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * This script demonstrates the possibility to intercept
 * array creations that are about to be executed from the body of
 * a certain method. This is achieved by using the {@linkplain Kind#NEWARRAY}
 * location value.
 */
@BTrace
public class NewArray {
    // component count
    private static volatile long count;

    @OnMethod(
            clazz = "/.*/", // tracking in all classes; can be restricted to specific user classes
            method = "/.*/", // tracking in all methods; can be restricted to specific user methods
            location = @Location(value = Kind.NEWARRAY, clazz = "char")
    )
    public static void onnew(@ProbeClassName String pcn, @ProbeMethodName String pmn, String arrType, int dim) {
        // pcn - allocation place class name
        // pmn - allocation place method name
        // **** following two parameters MUST always be in this order
        // arrType - the actual array type
        // dim - the array dimension

        // increment counter on new array
        count++;
    }

    @OnTimer(2000)
    public static void print() {
        // print the counter
        println("char[] count = " + count);
    }
}
