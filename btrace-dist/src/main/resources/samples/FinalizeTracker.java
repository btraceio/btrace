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
import io.btrace.core.annotations.OnTimer;
import io.btrace.core.annotations.Self;

import java.lang.reflect.Field;

import static io.btrace.core.BTraceUtils.*;

@BTrace
public class FinalizeTracker {
    private static Field fdField =
            field("java.io.FileInputStream", "fd");

    @OnTimer(4000)
    public static void ontimer() {
        runFinalization();
    }

    @OnMethod(
            clazz = "java.io.FileInputStream",
            method = "finalize"
    )
    public static void onfinalize(@Self Object me) {
        println(concat("finalizing ", str(me)));
        printFields(me);
        printFields(get(fdField, me));
        println("==========");
    }

    @OnMethod(
            clazz = "java.io.FileInputStream",
            method = "close"
    )
    public static void onclose(@Self Object me) {
        println(concat("closing ", str(me)));
        println(concat("thread: ", str(currentThread())));
        printFields(me);
        printFields(get(fdField, me));
        jstack();
        println("=============");
    }
}
