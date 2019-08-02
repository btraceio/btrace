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
import org.openjdk.btrace.core.annotations.OnTimer;

import static org.openjdk.btrace.core.BTraceUtils.Sys;
import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * Simple BTrace program that prints memory
 * usage once every 4 seconds. It is possible
 * to modify this to dump heap depending on
 * used memory crossing a threshold or some other
 * such condition. [dumpHeap is a built-in function].
 */
@BTrace
public class Memory {
    @OnTimer(4000)
    public static void printMem() {
        println("Heap:");
        println(Sys.Memory.heapUsage());
        println("Non-Heap:");
        println(Sys.Memory.nonHeapUsage());
    }
}
