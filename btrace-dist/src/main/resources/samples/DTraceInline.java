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
import io.btrace.core.annotations.DTrace;
import io.btrace.core.annotations.OnMethod;

import static io.btrace.core.BTraceUtils.*;

/*
 * This sample demonstrates DTrace/BTrace integration.
 * A one-liner D-script is started by BTrace client
 * because of @DTrace annotation. In this example
 * on new Java Thread starts, BTrace action raises a
 * DTrace probe. The D-script prints mixed mode stack
 * trace on receiving this probe.
 */
@DTrace("btrace$1:::event / copyinstr(arg0) == \"mstack\" / { jstack(); }")
@BTrace
public class DTraceInline {
    @OnMethod(
            clazz = "java.lang.Thread",
            method = "start"
    )
    public static void newThread(Thread th) {
        println(Threads.name(th));
        D.probe("mstack", "");
    }
}
