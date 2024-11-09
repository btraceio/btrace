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

import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.BTraceUtils;
import static org.openjdk.btrace.core.BTraceUtils.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace(trusted = false)
public class TraceAllTest {

    private static final AtomicLong hitCnt = BTraceUtils.newAtomicLong(0);

    @OnMethod(clazz = "/.*/")
    public static void doall(@ProbeMethodName(fqn = true) String pmn) {
        BTraceUtils.getAndIncrement(hitCnt);
//        BTraceUtils.println("invoked: " + pmn);
    }

    @OnTimer(500)
    public static void doRecurrent() {
        long cnt = BTraceUtils.get(hitCnt);
        if (cnt > 0) {
            println("[invocations=" + cnt + "]");
        }
    }
}
