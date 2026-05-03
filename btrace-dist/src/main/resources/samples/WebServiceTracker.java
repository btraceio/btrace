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
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * A simple BTrace program that prints a class name
 * and method name whenever a webservice is called and
 * also prints time taken by service method. WebService
 * entry points are annotated javax.jws.WebService and
 * javax.jws.WebMethod. We insert tracing actions into
 * every class and method annotated by these annotations.
 * This way we don't need to know actual webservice
 * implementor class name.
 */
@BTrace
public class WebServiceTracker {
    @OnMethod(
            clazz = "@javax.jws.WebService",
            method = "@javax.jws.WebMethod"
    )
    public static void onWebserviceEntry(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        print("entering webservice ");
        println(Strings.strcat(Strings.strcat(pcn, "."), pmn));
    }

    @OnMethod(
            clazz = "@javax.jws.WebService",
            method = "@javax.jws.WebMethod",
            location = @Location(Kind.RETURN)
    )
    public static void onWebserviceReturn(@ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long d) {
        print("leaving web service ");
        println(Strings.strcat(Strings.strcat(pcn, "."), pmn));
        println(Strings.strcat("Time taken (msec) ", Strings.str(d / 1000)));
        println("==========================");
    }

}
