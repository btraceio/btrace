/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.btrace.agent;

import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.DataCommand;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import java.lang.instrument.Instrumentation;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Represents a local client communicated by trace file.
 *
 * @author A. Sundararajan
 */
class FileClient extends Client {

    private volatile PrintWriter out;

    FileClient(Instrumentation inst, File scriptFile, PrintWriter traceWriter) throws IOException {
        super(inst);
        this.out = traceWriter;
        byte[] code = readAll(scriptFile);
        if (debug) {
            Main.debugPrint("read " + scriptFile);
        }
        InstrumentCommand cmd = new InstrumentCommand(code, new String[0]);
        Class btraceClazz = loadClass(cmd);
        if (btraceClazz == null) {
            throw new RuntimeException("can not load BTrace class");
        }
    }

    public void onCommand(Command cmd) throws IOException {
        if (out == null) {
            throw new IOException("no output stream");
        }
        if (debug) {
            
            Main.debugPrint("client " + getClassName() + ": got " + cmd);
        }
        switch (cmd.getType()) {
            case Command.EXIT:
                onExit(((ExitCommand) cmd).getExitCode());
                break;
            case Command.ERROR: {
                ErrorCommand ecmd = (ErrorCommand) cmd;
                Throwable cause = ecmd.getCause();
                if (cause != null) {
                    cause.printStackTrace(out);
                    out.flush();
                }
                break;
            }
            default:
                if (cmd instanceof DataCommand) {
                    ((DataCommand) cmd).print(out);
                    out.flush();
                }
                break;
        }
    }

    protected synchronized void closeAll() throws IOException {
        if (out != null) {
            out.close();
        }
    }

    private static byte[] readAll(File file) throws IOException {
        int size = (int) file.length();
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buf = new byte[size];
            fis.read(buf);
            return buf;
        } finally {
            fis.close();
        }
    }
}
