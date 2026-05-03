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

import static io.btrace.core.BTraceUtils.Sys;
import static io.btrace.core.BTraceUtils.println;

/**
 * This BTrace script demonstrates that it is okay
 * to trace bootstrap classes and call the same
 * inside the trace actions. In this example, we insert
 * a probe into System.getProperty() method and call
 * System.getProperty [through property() built-in function]
 * without getting into infinite recursion. A thread local
 * flag is used by BTrace to avoid infinite recursion here.
 */
@BTrace
public class SysProp {
    @OnMethod(
            clazz = "java.lang.System",
            method = "getProperty"
    )
    public static void onGetProperty(String name) {
        println(name);
        // call property safely here.
        println(Sys.Env.property(name));
    }
}	
