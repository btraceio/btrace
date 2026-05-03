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

import static io.btrace.core.BTraceUtils.Sys;
import static io.btrace.core.BTraceUtils.println;

/**
 * Simple BTrace program that prints memory
 * usage once every 4 seconds. It is possible
 * to modify this to dump heap depending on
 * used memory crossing a threshold or some other
 * such condition. [dumpHeap is a built-in function].
 */
@BTrace
public class Memory {
    @OnTimer(4000)
    public static void printMem() {
        println("Heap:");
        println(Sys.Memory.heapUsage());
        println("Non-Heap:");
        println(Sys.Memory.nonHeapUsage());
    }
}
