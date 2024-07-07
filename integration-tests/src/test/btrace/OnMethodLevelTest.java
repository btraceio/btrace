/*
 * Copyright (c) 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */

package btrace;

import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Reflective.*;
import org.openjdk.btrace.core.annotations.Export;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class OnMethodLevelTest {
    @TLS
    private static int tls = 10;

    @Export
    private static long ex = 1;

    private static String var = "none";

    @OnMethod(clazz = "resources.Main", method = "callA", enableAt = @Level("100"))
    public static void noargs(@Self Object self) {
        tls++;
        ex += 1;
        dump(var + " [this, noargs]");
        dump("{" + get("id", self) + "}");
        var = "A";
    }

    @OnMethod(clazz = "resources.Main", method = "callB", enableAt = @Level("150"), location = @Location(Kind.RETURN))
    public static void args(@Self Object self, int i, String s, @Duration long duration) {
        tls -= 1;
        ex--;
        dump(var + " [this, args]");
        var = "B";
    }

    private static void dump(String s) {
        println(s);
    }
}
