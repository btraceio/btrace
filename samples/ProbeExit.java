/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.samples;

import com.sun.btrace.annotations.*;
import static com.sun.btrace.BTraceUtils.*;

/**
 * This program demonstrates OnExit probe.
 * When some BTrace action method calls "exit(int)"
 * built-in function, method annotated by @OnExit
 * (if found) is called. In this method, BTrace script
 * print summary information of tracing and/or do clean-up.
 */

@BTrace public class ProbeExit {
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
