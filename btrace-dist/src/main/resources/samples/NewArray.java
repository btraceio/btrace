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
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnTimer;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;

import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * This script demonstrates the possibility to intercept
 * array creations that are about to be executed from the body of
 * a certain method. This is achieved by using the {@linkplain Kind#NEWARRAY}
 * location value.
 */
@BTrace
public class NewArray {
    // component count
    private static volatile long count;

    @OnMethod(
            clazz = "/.*/", // tracking in all classes; can be restricted to specific user classes
            method = "/.*/", // tracking in all methods; can be restricted to specific user methods
            location = @Location(value = Kind.NEWARRAY, clazz = "char")
    )
    public static void onnew(@ProbeClassName String pcn, @ProbeMethodName String pmn, String arrType, int dim) {
        // pcn - allocation place class name
        // pmn - allocation place method name
        // **** following two parameters MUST always be in this order
        // arrType - the actual array type
        // dim - the array dimension

        // increment counter on new array
        count++;
    }

    @OnTimer(2000)
    public static void print() {
        // print the counter
        println("char[] count = " + count);
    }
}
