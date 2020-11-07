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
import org.openjdk.btrace.core.annotations.Event;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.annotations.TLS;
import org.openjdk.btrace.core.jfr.JfrEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Jfr.*;

/**
 * This sample prints all files opened for read/write
 * by a Java process. Note that if you pass FileDescriptor
 * to File{Input/Output}Stream or File{Reader/Writer},
 * that is not tracked by this script.
 */
@BTrace
public class FileTracker {
    @Event(name = "fileEvent", label = "BTrace File Event", description = "Sample BTrace file tracker", category = {"btrace", "samples"}, fields = "string fileName, string operation")
    private static JfrEvent.Factory eventFactory;

    @TLS
    private static String name;

    @OnMethod(
            clazz = "java.io.FileInputStream",
            method = "<init>"
    )
    public static void onNewFileInputStream(@Self FileInputStream self, File f) {
        name = Strings.str(f);
    }

    @OnMethod(
            clazz = "java.io.FileInputStream",
            method = "<init>",
            type = "void (java.io.File)",
            location = @Location(Kind.RETURN)
    )
    public static void onNewFileInputStreamReturn() {
        if (name != null) {
            JfrEvent event = prepareEvent(eventFactory);
            setEventField(event, "fileName", name);
            setEventField(event, "operation", "read");
            commit(event);
            name = null;
        }
    }

    @OnMethod(
            clazz = "java.io.FileOutputStream",
            method = "<init>"
    )
    public static void onNewFileOutputStream(@Self FileOutputStream self, File f, boolean b) {
        name = str(f);
    }

    @OnMethod(
            clazz = "java.io.FileOutputStream",
            method = "<init>",
            type = "void (java.io.File, boolean)",
            location = @Location(Kind.RETURN)
    )
    public static void OnNewFileOutputStreamReturn() {
        if (name != null) {
            JfrEvent event = prepareEvent(eventFactory);
            setEventField(event, "fileName", name);
            setEventField(event, "operation", "write");
            commit(event);
            name = null;
        }
    }
}
