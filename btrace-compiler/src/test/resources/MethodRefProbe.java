/*
 * Copyright (c) 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

package traces;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;

import java.util.Deque;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class MethodRefProbe {
    private static Deque<String> deque = newDeque();

    @OnMethod(clazz = "/.*\\.OnMethodTest/", method = "args")
    public static void args(@Self Object self, String a, long b, String[] c, int[] d) {
        push(deque, a);
        forEach(deque, MethodRefProbe::printStr);
    }

    private static void printStr(String val) {
        println("===> " + val);
    }
    private static int map(String val) {
        return strlen(val);
    }
    private static void printInt(int val) {
        println("===> " + val);
    }

    public static java.util.LongSummaryStatistics reduceOp(String val, java.util.LongSummaryStatistics prev) {
        addToSummaryStatistics(prev, strlen(val));
        return prev;
    }

    private static boolean checkWithHello(String val) {
        return indexOf(val, "Hello") > -1;
    }

    @OnTimer(5000)
    public static void onTimer() {
        forEach(deque, MethodRefProbe::printStr);
        forEach(BTraceUtils.map(deque, MethodRefProbe::map), MethodRefProbe::printInt);

        java.util.LongSummaryStatistics stats = newSummaryStatistics();
        stats = reduce(deque, stats, MethodRefProbe::reduceOp);
        println("===> [5] stats: " + str(stats));

        int s = size(filter(deque, MethodRefProbe::checkWithHello));
        println("===> [6] filtered: " + s);
    }
}
