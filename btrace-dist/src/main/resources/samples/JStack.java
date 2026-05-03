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

import static io.btrace.core.BTraceUtils.Sys.exit;
import static io.btrace.core.BTraceUtils.Threads.deadlocks;
import static io.btrace.core.BTraceUtils.Threads.jstackAll;

/*
 * A simple sample prints stack traces and exits. This
 * BTrace program mimics the jstack command line tool in JDK.
 */
@BTrace
public class JStack {
    static {
        deadlocks(false);
        jstackAll();
        exit(0);
    }
}
