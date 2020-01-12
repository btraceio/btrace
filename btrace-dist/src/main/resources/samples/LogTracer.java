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
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;

import java.lang.reflect.Field;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.openjdk.btrace.core.BTraceUtils.Reflective;
import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * Simple log message tracer class. This class
 * prints all log messages regardless of log Level.
 * Note that we read LogRecord's private "message"
 * field using "field()" and "objectValue()" built-ins.
 */
@BTrace
public class LogTracer {
    private static Field msgField = Reflective.field("java.util.logging.LogRecord", "message");

    @OnMethod(
            clazz = "+java.util.logging.Logger",
            method = "log"
    )
    public static void onLog(@Self Logger self, LogRecord record) {
        println(Reflective.get(msgField, record));
    }
}
