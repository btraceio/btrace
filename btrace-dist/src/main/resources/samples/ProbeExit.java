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


import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnExit;
import io.btrace.core.annotations.OnTimer;

import static io.btrace.core.BTraceUtils.Sys;
import static io.btrace.core.BTraceUtils.println;

/**
 * This program demonstrates OnExit probe.
 * When some BTrace action method calls "exit(int)"
 * built-in function, method annotated by @OnExit
 * (if found) is called. In this method, BTrace script
 * print summary information of tracing and/or do clean-up.
 */

@BTrace
public class ProbeExit {
    private static volatile int i;

    // @OnExit is called when some BTrace method
    // calls exit(int) method
    @OnExit
    public static void onexit(int code) {
        println("BTrace program exits!");
    }

    // We just put @OnTimer probe and exit BTrace
    // program when the count reaches 5.

    @OnTimer(1000)
    public static void ontime() {
        println("hello");
        i++;
        if (i == 5) {
            // note that this exits the BTrace client
            // and not the traced program (which would
            // be a destructive action!).
            Sys.exit(0);
        }
    }
}
