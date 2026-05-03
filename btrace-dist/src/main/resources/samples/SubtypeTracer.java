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
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.ProbeClassName;
import io.btrace.core.annotations.ProbeMethodName;

import static io.btrace.core.BTraceUtils.print;
import static io.btrace.core.BTraceUtils.println;

/**
 * A simple example that demonstrates subtype matching by +foo pattern
 * in "clazz" attribute of @OnMethod annotation.
 */
@BTrace
public class SubtypeTracer {
    @OnMethod(
            clazz = "+java.lang.Runnable",
            method = "run"
    )
    public static void onRun(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        // on every Runnable.run() method entry print class.method
        print(pcn);
        print('.');
        println(pmn);
    }
}
