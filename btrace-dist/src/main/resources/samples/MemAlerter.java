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
import io.btrace.core.annotations.OnLowMemory;

import java.lang.management.MemoryUsage;

import static io.btrace.BTrace.println;
import static io.btrace.BTrace.str;

/**
 * This sample traces memory threshold exceeds.
 * You need to specify the memory pool to watch
 * out and the usage threshold. You can write
 * script that dumps heap by dumpHeap on crossing
 * the threshold instead of just printing a message.
 * Note that the name of the old gen is dependent on
 * GC algorithm. "G1 Old Gen" is the G1 collector's, the
 * default since JDK 9; with the Parallel collector (the
 * JDK 8 default) it is "PS Old Gen", with the Serial
 * collector "Tenured Gen". If the pool does not exist
 * the agent warns and lists the pools it can watch.
 */
@BTrace
public class MemAlerter {
    @OnLowMemory(
            pool = "G1 Old Gen",
            threshold = 6000000
    )
    public static void onLowMem(MemoryUsage mu) {
        println(str(mu));
    }
}
