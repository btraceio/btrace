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

package traces;

import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Self;
import static com.sun.btrace.BTraceUtils.*;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.TLS;
import com.sun.btrace.annotations.Export;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace
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
        println(var + " [this, noargs]");
        var = "A";
    }

    @OnMethod(clazz = "resources.Main", method = "callB")
    public static void args(@Self Object self, int i, String s) {
        tls -= 1;
        ex--;
        println(var + " [this, args]");
        var = "B";
    }
}
