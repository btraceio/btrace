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


import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnExit;
import org.openjdk.btrace.core.annotations.OnTimer;

import static org.openjdk.btrace.core.BTraceUtils.Sys;
import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * This program demonstrates OnExit probe.
 * When some BTrace action method calls "exit(int)"
 * built-in function, method annotated by @OnExit
 * (if found) is called. In this method, BTrace script
 * print summary information of tracing and/or do clean-up.
 */

@BTrace
public class ProbeExit {
    private static volatile int i;

    // @OnExit is called when some BTrace method
    // calls exit(int) method
    @OnExit
    public static void onexit(int code) {
        println("BTrace program exits!");
    }

    // We just put @OnTimer probe and exit BTrace
    // program when the count reaches 5.

    @OnTimer(1000)
    public static void ontime() {
        println("hello");
        i++;
        if (i == 5) {
            // note that this exits the BTrace client
            // and not the traced program (which would
            // be a destructive action!).
            Sys.exit(0);
        }
    }
}
