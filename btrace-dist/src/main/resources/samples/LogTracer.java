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

import java.lang.reflect.Field;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.openjdk.btrace.core.BTraceUtils.Reflective;
import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * Simple log message tracer class. This class
 * prints all log messages regardless of log Level.
 * Note that we read LogRecord's private "message"
 * field using "field()" and "objectValue()" built-ins.
 */
@BTrace
public class LogTracer {
    private static Field msgField = Reflective.field("java.util.logging.LogRecord", "message");

    @OnMethod(
            clazz = "+java.util.logging.Logger",
            method = "log"
    )
    public static void onLog(@Self Logger self, LogRecord record) {
        println(Reflective.get(msgField, record));
    }
}
