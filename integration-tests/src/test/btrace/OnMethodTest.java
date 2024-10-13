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
        long x = 10;
        if (timeNanos() > x) {
            println(x);
        }
    }

    private static void dump(String s) {
        println(s);
        println("heap:" + Sys.Memory.heapUsage());
    }
}
