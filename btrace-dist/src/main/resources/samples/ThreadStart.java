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
import org.openjdk.btrace.core.annotations.Self;

import static org.openjdk.btrace.core.BTraceUtils.*;

/*
 * This BTrace script inserts a probe into
 * method entry of java.lang.Thread.start() method.
 * At each Thread.start(), it raises a DTrace probe
 * in addition to printing the name of the thread.
 * A D-script like jthread.d may be used to get the
 * associated DTrace probe events.
 */
@BTrace
public class ThreadStart {
    @OnMethod(
            clazz = "java.lang.Thread",
            method = "start"
    )
    public static void onnewThread(@Self Thread t) {
        D.probe("jthreadstart", Threads.name(t));
        println("starting " + Threads.name(t));
    }
}
