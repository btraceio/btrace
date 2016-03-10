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

import java.lang.instrument.Instrumentation;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.DataCommand;
import com.sun.btrace.comm.EventCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import com.sun.btrace.comm.PrintableCommand;
import com.sun.btrace.comm.SetSettingsCommand;
import com.sun.btrace.comm.WireIO;

/**
 * Represents a remote client communicated by socket.
 *
 * @author A. Sundararajan
 */
class RemoteClient extends Client {
    private volatile Socket sock;
    private volatile ObjectInputStream ois;
    private volatile ObjectOutputStream oos;

    RemoteClient(Instrumentation inst, Socket sock) throws IOException {
        super(inst);
        this.sock = sock;
        this.ois = new ObjectInputStream(sock.getInputStream());
        this.oos = new ObjectOutputStream(sock.getOutputStream());
        boolean hasInstrument = false;
        while (!hasInstrument) {
            Command cmd = WireIO.read(ois);
            switch (cmd.getType()) {
                case Command.INSTRUMENT: {
                    debugPrint("got instrument command");
                    Class btraceClazz = loadClass((InstrumentCommand)cmd);
                    if (btraceClazz == null) {
                        throw new RuntimeException("can not load BTrace class");
                    }
                    hasInstrument = true;
                    break;
                }
                case Command.SET_PARAMS: {
                    settings.from(((SetSettingsCommand)cmd).getParams());
                    setupWriter();
                    break;
                }
                default: {
                    errorExit(new IllegalArgumentException("expecting instrument or settings command!"));
                    throw new IOException("expecting instrument or settings command!");
                }
            }
        }

        BTraceRuntime.initUnsafe();
        Thread cmdHandler = new Thread(new Runnable() {
            public void run() {
                BTraceRuntime.enter();
                while (true) {
                    try {
                        Command cmd = WireIO.read(ois);
                        switch (cmd.getType()) {
                        case Command.EXIT: {
                            ExitCommand ecmd = (ExitCommand)cmd;
                            debugPrint("received exit command");
                            BTraceRuntime.leave();
                            BTraceRuntime.enter(getRuntime());
                            try {
                                debugPrint("calling BTraceUtils.exit()");
                                cleanupTransformers();
                                BTraceUtils.Sys.exit(ecmd.getExitCode());
                            } catch (Throwable th) {
                                debugPrint(th);
                                BTraceRuntime.handleException(th);
                            }
                            return;
                        }
                        case Command.EVENT: {
                            getRuntime().handleEvent((EventCommand)cmd);
                            break;
                        }
                        default:
                            if (isDebug()) {
                                debugPrint("received " + cmd);
                            }
                            // ignore any other command
                        }
                    } catch (Exception exp) {
                        debugPrint(exp);
                        break;
                    }
                }
            }
        });
        cmdHandler.setDaemon(true);
        debugPrint("starting client command handler thread");
        cmdHandler.start();
    }

    @Override
    public void onCommand(Command cmd) throws IOException {
        if (oos == null) {
            throw new IOException("no output stream");
        }
        oos.reset();
        switch (cmd.getType()) {
            case Command.EXIT:
                if (isDebug()) {
                    debugPrint("client " + getClassName() + ": got " + cmd);
                }
                WireIO.write(oos, cmd);
                onExit(((ExitCommand)cmd).getExitCode());
                break;
            default:
                if (isDebug()) {
                    debugPrint("client " + getClassName() + ": got " + cmd);
                }
                if (out != null) {
                    if (cmd instanceof PrintableCommand) {
                        ((PrintableCommand) cmd).print(out);
                        return;
                    }
                }
                WireIO.write(oos, cmd);
        }
    }

    @Override
    protected synchronized void closeAll() throws IOException {
        super.closeAll();

        if (oos != null) {
            oos.close();
        }
        if (ois != null) {
            ois.close();
        }
        if (sock != null) {
            sock.close();
        }
    }
}