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
import org.openjdk.btrace.core.annotations.Where;

import static org.openjdk.btrace.core.BTraceUtils.identityStr;
import static org.openjdk.btrace.core.BTraceUtils.println;

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
public class AllSync {
    @OnMethod(
            clazz = "/javax\\.swing\\..*/",
            method = "/.*/",
            location = @Location(value = Kind.SYNC_ENTRY, where = Where.AFTER)
    )
    public static void onSyncEntry(Object obj) {
        println("after synchronized entry: " + identityStr(obj));
    }

    @OnMethod(
            clazz = "/javax\\.swing\\..*/",
            method = "/.*/",
            location = @Location(Kind.SYNC_EXIT)
    )
    public static void onSyncExit(Object obj) {
        println("before synchronized exit: " + identityStr(obj));
    }
}
