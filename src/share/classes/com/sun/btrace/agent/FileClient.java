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
package com.sun.btrace.agent;

import com.sun.btrace.DebugSupport;
import com.sun.btrace.SharedSettings;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.DataCommand;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import java.io.BufferedWriter;
import java.lang.instrument.Instrumentation;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Represents a local client communicated by trace file.
 * The trace script is specified as a File of a .class file
 * or a byte array containing bytecode of the trace script.
 *
 * @author A. Sundararajan
 * @author J.Bachorik
 */
class FileClient extends Client {
    private final Timer flusher;

    private volatile PrintWriter out;

    FileClient(Instrumentation inst, byte[] code, String traceOutput) throws IOException {
        this(inst, code);
        setupWriter(traceOutput);
    }

    FileClient(Instrumentation inst, byte[] code, PrintWriter traceWriter) throws IOException {
        this(inst, code);
        out = traceWriter;
    }

    FileClient(Instrumentation inst, byte[] code) throws IOException {
        super(inst);
        setSettings(SharedSettings.GLOBAL);

        InstrumentCommand cmd = new InstrumentCommand(code, new String[0]);
        Class btraceClazz = loadClass(cmd);
        if (btraceClazz == null) {
            throw new RuntimeException("can not load BTrace class");
        }

        int flushInterval;
        String flushIntervalStr = System.getProperty("com.sun.btrace.FileClient.flush", "5");
        try {
            flushInterval = Integer.parseInt(flushIntervalStr);
        } catch (NumberFormatException e) {
            flushInterval = 5; // default
        }

        final int flushSec  = flushInterval;

        flusher = new Timer("BTrace FileClient Flusher", true);
        flusher.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                out.flush();
            }
        }, flushSec, flushSec);
    }

    FileClient(Instrumentation inst, File scriptFile, String traceOutput) throws IOException {
        this(inst, readAll(scriptFile), traceOutput);
    }

    private void setupWriter(String output) {
        if (output.equals("::stdout")) {
            out = new PrintWriter(System.out);
        } else {
            if (SharedSettings.GLOBAL.getFileRollMilliseconds() > 0) {
                out = new PrintWriter(new BufferedWriter(
                    TraceOutputWriter.rollingFileWriter(new File(output), settings)
                ));
            } else {
                out = new PrintWriter(new BufferedWriter(TraceOutputWriter.fileWriter(new File(output), settings)));
            }
        }
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
            case Command.ERROR: {
                ErrorCommand ecmd = (ErrorCommand) cmd;
                Throwable cause = ecmd.getCause();
                if (out == null) {
                    DebugSupport.warning("No output stream. Received ErrorCommand: " + cause.getMessage());
                } else {
                    cause.printStackTrace(out);
                }
                break;
            }
            default:
                if (cmd instanceof DataCommand) {
                    if (out == null) {
                        DebugSupport.warning("No output stream. Received DataCommand.");
                    } else {
                        ((DataCommand) cmd).print(out);
                    }
                }
                break;
        }
    }

    @Override
    protected synchronized void closeAll() throws IOException {
        flusher.cancel();
        if (out != null) {
            out.close();
        }
    }

    private static byte[] readAll(File file) throws IOException {
        int size = (int) file.length();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[size];
            fis.read(buf);
            return buf;
        }
    }
}
