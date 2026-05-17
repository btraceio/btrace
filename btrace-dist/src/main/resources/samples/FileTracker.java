/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Self;
import io.btrace.core.annotations.TLS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import static io.btrace.BTrace.*;

/**
 * This sample prints all files opened for read/write
 * by a Java process. Note that if you pass FileDescriptor
 * to File{Input/Output}Stream or File{Reader/Writer},
 * that is not tracked by this script.
 */
@BTrace
public class FileTracker {
    @TLS
    private static String name;

    @OnMethod(
            clazz = "java.io.FileInputStream",
            method = "<init>"
    )
    public static void onNewFileInputStream(@Self FileInputStream self, File f) {
        name = str(f);
    }

    @OnMethod(
            clazz = "java.io.FileInputStream",
            method = "<init>",
            type = "void (java.io.File)",
            location = @Location(Kind.RETURN)
    )
    public static void onNewFileInputStreamReturn() {
        if (name != null) {
            println("opened for read " + name);
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
            println("opened for write " + name);
            name = null;
        }
    }
}
