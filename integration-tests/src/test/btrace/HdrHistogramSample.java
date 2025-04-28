/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package traces;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnTimer;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;
import org.openjdk.btrace.core.annotations.TLS;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * This sample demonstrates the use of HDR Histogram in BTrace.
 * It tracks the execution time of all public methods in java.io package
 * and records the values in HDR Histograms.
 */
@BTrace
public class HdrHistogramSample {
    private static HdrHistogram.HdrHistogramHandle handle = HdrHistogram.create("method-latency");

    @TLS
    private static long startTime;

    @OnMethod(
        clazz="resources.Main",
        method="/.*call.*/")
    public static void onMethodEntry(@ProbeClassName String className, @ProbeMethodName String methodName) {
        // Record the start time in the thread local variable
        startTime = timeMillis();
    }

    @OnMethod(
        clazz="resources.Main",
        method="/.*call.*/" ,
        location=@Location(Kind.RETURN))
    public static void onMethodReturn(@ProbeClassName String className, @ProbeMethodName String methodName) {
        // Calculate the execution time using the thread local variable
        long executionTime = timeMillis() - startTime;

        // Record the execution time using the handle
        HdrHistogram.recordValue(handle, executionTime);

        // Print a message for methods that take more than 100ms
        if (executionTime > 100) {
            println(strcat(strcat(strcat(className, "."), methodName), 
                    strcat(" took ", strcat(str(executionTime), "ms"))));
        }
    }

    @OnTimer(5000)
    public static void onTimer() {
        // Print the histogram and recorder statistics every 5 seconds
        HdrHistogram.printStatistics(handle);
    }
}
