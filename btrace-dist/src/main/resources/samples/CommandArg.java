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
import org.openjdk.btrace.core.annotations.OnMethod;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * This BTrace program demonstrates command line
 * arguments. $ method helps in getting command line
 * arguments. In this example, desired thread name is
 * passed from the command line (of the BTrace client).
 */
@BTrace
public class CommandArg {
    @OnMethod(
            clazz = "java.lang.Thread",
            method = "run"
    )
    public static void started() {
        if (Strings.strcmp(Threads.name(Threads.currentThread()), Sys.$(2)) == 0) {
            println("started " + Sys.$(2));
        }
    }
}
