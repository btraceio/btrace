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


package btrace;

import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.types.AnyType;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.Export;
import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Reflective.*;

import dummy.SimplePeriodicEvent;
import dummy.SimpleEvent;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace(trusted = false)
public class OnMethodTest {
    @TLS
    private static int tls = 10;

    @Export
    private static long ex = 1;

    private static String var = "none";

    @OnMethod(clazz = "resources.Main", method = "callA")
    public static void noargs(@Self Object self) {
        tls++;
        ex += 1;
        dump(var + " [this, noargs]");
        dump("{" + get("id", self) + "}");
        var = "A";
        println("prop: " + property("btrace.test"));
    }

    @OnMethod(clazz = "resources.Main", method = "callB")
    public static void args(@Self Object self, int i, String s) {
        tls -= 1;
        ex--;
        dump(var + " [this, args]");
        var = "B";
        println("prop: " + property("btrace.test"));
    }

    @OnMethod(clazz = "+resources.Main", method = "startWork")
    public static void onSubtype() {
        println("subtype");
    }

    @OnMethod(clazz = "resources.Main", method = "/^call.*/",
              location = @Location(value = Kind.FIELD_GET, clazz = "resources.Main", field = "/^s?[fF]ield$/"))
    public static void fieldGet(@TargetMethodOrField(fqn = true) String fldName) {
        println("fieldGet: " + fldName);
    }

    @OnMethod(clazz = "resources.Main", method = "/^call.*/",
        location = @Location(value = Kind.FIELD_SET, clazz = "resources.Main", field = "/^s?[fF]ield$/"))
    public static void fieldSet(@TargetMethodOrField(fqn = true) String fldName) {
        println("fieldSet: " + fldName);
    }

    @OnTimer(500)
    public static void doRecurrent() {
        // Print only once to avoid unbounded output in unattended mode
        // which can cause CI to wait for excessive line counts.
        if (timerHits == 0) {
            println(10);
        }
        timerHits++;
    }

    private static int timerHits = 0;

    private static void dump(String s) {
        println(s);
        println("heap:" + Sys.Memory.heapUsage());
    }
}
