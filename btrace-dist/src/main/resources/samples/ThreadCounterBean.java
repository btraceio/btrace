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
import io.btrace.core.annotations.Property;
import io.btrace.core.annotations.Self;

import static io.btrace.BTrace.println;

/**
 * This sample demonstrates that you can expose a BTrace
 * class as a JMX MBean. After connecting BTrace to the
 * target application, connect VisualVM or jconsole or
 * any other JMX client to the same application.
 */
@BTrace
public class ThreadCounterBean {

    // @Property makes the count field to be exposed
    // as an attribute of this MBean.
    @Property
    private static long count;

    @OnMethod(
            clazz = "java.lang.Thread",
            method = "start"
    )
    public static void onnewThread(@Self Thread t) {
        count++;
    }

    @OnTimer(2000)
    public static void ontimer() {
        println(count);
    }
}
