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
import io.btrace.core.annotations.DTraceRef;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;

import static io.btrace.core.BTraceUtils.*;

/*
 * This sample demonstrates associating a D-script
 * with a BTrace program using @DTraceRef annotation.
 * BTrace client looks for absolute or relative path for
 * the D-script and submits it to kernel *before* submitting
 * BTrace program to BTrace agent.
 */
@DTraceRef("classload.d")
@BTrace
public class DTraceRefDemo {
    @OnMethod(
            clazz = "java.lang.ClassLoader",
            method = "defineClass"
    )
    public static void defineClass() {
        println("user defined loader load start");
    }

    @OnMethod(
            clazz = "java.lang.ClassLoader",
            method = "defineClass",
            location = @Location(Kind.RETURN)
    )
    public static void defineclass(Class<?> cl) {
        println("loaded " + Reflective.name(cl));
        Threads.jstack();
        println("==========================");
    }
}
