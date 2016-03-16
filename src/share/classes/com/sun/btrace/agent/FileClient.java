/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace.agent;

import com.sun.btrace.DebugSupport;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import com.sun.btrace.comm.PrintableCommand;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Represents a local client communicated by trace file.
 * The trace script is specified as a File of a .class file
 * or a byte array containing bytecode of the trace script.
 *
 * @author A. Sundararajan
 * @author J.Bachorik
 */
class FileClient extends Client {
    FileClient(ClientContext ctx, byte[] code) throws IOException {
        super(ctx);
        init(code);
    }

    private void init(byte[] code) throws IOException {
        InstrumentCommand cmd = new InstrumentCommand(code, new String[0]);
        Class btraceClazz = loadClass(cmd);
        if (btraceClazz == null) {
            throw new RuntimeException("can not load BTrace class");
        }
    }

    FileClient(ClientContext ctx, File scriptFile) throws IOException {
        this(ctx, readAll(scriptFile));
    }

    @Override
    public void onCommand(Command cmd) throws IOException {
        if (isDebug()) {
            debugPrint("client " + getClassName() + ": got " + cmd);
        }
        switch (cmd.getType()) {
            case Command.EXIT:
                onExit(((ExitCommand) cmd).getExitCode());
                break;
            default:
                if (cmd instanceof PrintableCommand) {
                    if (out == null) {
                        DebugSupport.warning("No output stream. Received DataCommand.");
                    } else {
                        ((PrintableCommand) cmd).print(out);
                    }
                }
                break;
        }
    }

    private static byte[] readAll(File file) throws IOException {
        String path = file.getPath();
        if (path.startsWith(Main.EMBEDDED_SCRIPT_HEADER)) {
            try (InputStream is = ClassLoader.getSystemResourceAsStream(path)) {
                return readAll(is, -1);
            }
        } else {
            int size = (int) file.length();
            try (FileInputStream fis = new FileInputStream(file)) {
                return readAll(fis, size);
            }
        }
    }

    private static byte[] readAll(InputStream is, int size) throws IOException {
        if (is == null) throw new NullPointerException();

        byte[] buf = new byte[size != -1 ? size : 1024];
        int bufsize = buf.length;
        int off = 0;
        int read;
        while ((read = is.read(buf, off, bufsize - off)) > -1) {
            off += read;
            if (off >= bufsize) {
                buf = Arrays.copyOf(buf, bufsize * 2);
                bufsize = buf.length;
            }
        }
        return Arrays.copyOf(buf, off);
    }
}
