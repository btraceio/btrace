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

import static org.openjdk.btrace.core.BTraceUtils.Sys;
import static org.openjdk.btrace.core.BTraceUtils.println;

/*
 * A simple sample that dumps heap of the target at start and exits.
 * This BTrace program mimics the jmap tool (with -dump option).
 */
@BTrace
public class JMap {
    static {
        String name;
        if (Sys.$length() == 3) {
            name = Sys.$(2);
        } else {
            name = "heap.bin";
        }
        Sys.Memory.dumpHeap(name);
        println("heap dumped!");
        Sys.exit(0);
    }
}
