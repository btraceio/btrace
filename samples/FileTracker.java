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

package com.sun.btrace.samples;

import com.sun.btrace.annotations.*;
import static com.sun.btrace.BTraceUtils.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * This sample prints all files opened for read/write
 * by a Java process. Note that if you pass FileDescriptor
 * to File{Input/Output}Stream or File{Reader/Writer}, 
 * that is not tracked by this script.
 */
@BTrace public class FileTracker {
    @TLS private static String name;

    @OnMethod(
        clazz="java.io.FileInputStream",
        method="<init>"
    )
    public static void onNewFileInputStream(@Self FileInputStream self, File f) {
        name = Strings.str(f);
    }

    @OnMethod(
        clazz="java.io.FileInputStream",
        method="<init>",
        type="void (java.io.File)",
        location=@Location(Kind.RETURN)
    )
    public static void onNewFileInputStreamReturn() {
        if (name != null) {
            println(Strings.strcat("opened for read ", name));
            name = null;
        }
    }

    @OnMethod(
        clazz="java.io.FileOutputStream",
        method="<init>"
    )
    public static void onNewFileOutputStream(@Self FileOutputStream self, File f, boolean b) {
        name = str(f);
    }

    @OnMethod(
        clazz="java.io.FileOutputStream",
        method="<init>",
        type="void (java.io.File, boolean)",
        location=@Location(Kind.RETURN)
    )
    public static void OnNewFileOutputStreamReturn() {
        if (name != null) {
            println(Strings.strcat("opened for write ", name));
            name = null;
        }
    }
}
