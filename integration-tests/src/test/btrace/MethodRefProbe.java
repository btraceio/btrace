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

package btrace;

import org.openjdk.btrace.core.annotations.*;

import java.util.Deque;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace(trusted = true)
public class MethodRefProbe {
    private static Deque<String> deque = newDeque();

    @OnMethod(clazz = "resources.Main", method = "callB")
    public static void args(@Self Object self, int i, String s) {
        push(deque, s);
        forEach(deque, MethodRefProbe::printStr1);

        String[] x = new String[] {"one", "two"};
        forEach(x, MethodRefProbe::printStr3);
    }

    public static void printStr1(String val) {
        println("===> [1] " + val);
    }
    public static void printStr2(String val) {
        println("===> [2] " + val);
    }
    public static void printStr3(String val) {
        println("===> [3] " + val);
    }

    public static void printInt(int val) {
        println("===> [4] " + val);
    }

    public static int mapToInt(String val) {
        return strlen(val);
    }

    public static java.util.LongSummaryStatistics reduceOp(String val, java.util.LongSummaryStatistics prev) {
        addToSummaryStatistics(prev, strlen(val));
        return prev;
    }

    private static boolean checkWithHello(String val) {
        return indexOf(val, "Hello") > -1;
    }

    @OnTimer(1000)
    public static void onTimer() {
        println("Timer::");
        forEach(deque, MethodRefProbe::printStr2);

        forEach(map(deque, MethodRefProbe::mapToInt), MethodRefProbe::printInt);

        java.util.LongSummaryStatistics stats = newSummaryStatistics();
        stats = reduce(deque, stats, MethodRefProbe::reduceOp);
        println("===> [5] stats: " + str(stats));

        int s = size(filter(deque, MethodRefProbe::checkWithHello));
        println("===> [6] filtered: " + s);
    }
}
