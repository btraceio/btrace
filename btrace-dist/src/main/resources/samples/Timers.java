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
import io.btrace.core.annotations.OnTimer;

import static io.btrace.core.BTraceUtils.*;

/**
 * Demonstrates multiple timer probes with different periods to fire.
 */
@BTrace
public class Timers {

    // when starting print the target VM version and start time
    static {
        println("vm version " + Sys.VM.vmVersion());
        println("vm starttime " + Sys.VM.vmStartTime());
    }

    @OnTimer(1000)
    public static void f() {
        println("1000 msec: " + Sys.VM.vmUptime());
    }

    @OnTimer(3000)
    public static void f1() {
        println("3000 msec: " + Time.millis());
    }

}
