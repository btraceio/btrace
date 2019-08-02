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

package traces.issues;

import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * This script traces method/block entry into every method of
 * every class in javax.swing package! Think before using
 * this script -- this will slow down your app significantly!!
 * Note tha Where.BEFORE is default. For synchronized blocks, BEFORE
 * means before "monitorenter" bytecode. For synchronized methods, we
 * can not have probe point Where.BEFORE. Lock is acquired before entering
 * synchronized method. By making the probe point Where.AFTER for SYNC_ENTER,
 * we probe after monitorenter bytecode or synchronized method entry.
 */
@BTrace
public class BTRACE69 {
    @OnMethod(
            clazz = "/.*\\.OnMethodTest/",
            method = "sync",
            location = @Location(value = Kind.SYNC_ENTRY, where = Where.AFTER)
    )
    public static void onSyncEntry(@TargetInstance Object obj) {
        println(Strings.strcat("after synchronized entry: ", identityStr(obj)));
    }

    @OnMethod(
            clazz = "/.*\\.OnMethodTest/",
            method = "sync",
            location = @Location(Kind.SYNC_EXIT)
    )
    public static void onSyncExit(@TargetInstance Object obj) {
        println(Strings.strcat("before synchronized exit: ", identityStr(obj)));
    }
}
