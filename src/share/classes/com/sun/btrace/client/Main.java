/*
 * Copyright (c) 2008-2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
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

package com.sun.btrace.client;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import com.sun.btrace.CommandListener;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.PrintableCommand;
import com.sun.btrace.util.Messages;

/**
 * This is the main class for a simple command line
 * BTrace client. It is possible to create a GUI
 * client using the Client class.
 *
 * @author A. Sundararajan
 */
public final class Main {
    public static volatile boolean exiting;
    private static boolean DEBUG;
    private static boolean TRUSTED;
    private static boolean DUMP_CLASSES;
    private static String OUTPUT_FILE;
    private static String DUMP_DIR;
    private static String PROBE_DESC_PATH;
    public static final boolean TRACK_RETRANSFORM;
    public static final int BTRACE_DEFAULT_PORT = 2020;
    public static final String BTRACE_DEFAULT_HOST = "localhost";

    private static final Console con;
    private static final PrintWriter out;

    static {
        DEBUG = Boolean.getBoolean("com.sun.btrace.debug");
        if (isDebug()) debugPrint("btrace debug mode is set");
        TRACK_RETRANSFORM = Boolean.getBoolean("com.sun.btrace.trackRetransforms");
        if (isDebug() && TRACK_RETRANSFORM) debugPrint("trackRetransforms flag is set");
        TRUSTED = Boolean.getBoolean("com.sun.btrace.unsafe");
        TRUSTED |= Boolean.getBoolean("com.sun.btrace.trusted");
        if (isDebug() && TRUSTED) debugPrint("btrace trusted mode is set");
        DUMP_CLASSES = Boolean.getBoolean("com.sun.btrace.dumpClasses");
        if (isDebug() && DUMP_CLASSES) debugPrint("dumpClasses flag is set");
        DUMP_DIR = System.getProperty("com.sun.btrace.dumpDir", ".");
        if (DUMP_CLASSES) {
            if (isDebug()) debugPrint("dumpDir is " + DUMP_DIR);
        }
        PROBE_DESC_PATH = System.getProperty("com.sun.btrace.probeDescPath", ".");
        con = System.console();
        out = getOutWriter(con);
    }

    @SuppressWarnings("DefaultCharset")
    private static PrintWriter getOutWriter(Console con) {
        return (con != null)? con.writer() : new PrintWriter(System.out);
    }

    public static void main(String[] args) {
        int port = BTRACE_DEFAULT_PORT;
        String host = BTRACE_DEFAULT_HOST;
        String classPath = ".";
        String includePath = null;

        int count = 0;
        boolean hostDefined = false;
        boolean portDefined = false;
        boolean classpathDefined = false;
        boolean includePathDefined = false;
        String statsdDef = "";

        OUTER:
        for (String arg : args) {
            switch (arg) {
                case "-v":
                    DEBUG = true;
                    break OUTER;
                case "--version":
                    System.out.println(Messages.get("btrace.version"));
                    return;
            }
        }

        if (args.length < 2) {
            usage();
        }

        for (;;) {
            if (args[count].charAt(0) == '-') {
                if (args.length <= count+1) {
                    usage();
                }
                if (args[count].equals("-p") && !portDefined) {
                    try {
                        port = Integer.parseInt(args[++count]);
                        if (isDebug()) debugPrint("accepting port " + port);
                    } catch (NumberFormatException nfe) {
                        usage();
                    }
                    portDefined = true;
                } else if (args[count].equals("-u")) {
                    TRUSTED = true;
                    if (isDebug()) debugPrint("btrace trusted mode is set");
                } else if (args[count].equals("-o")) {
                    OUTPUT_FILE = args[++count];
                    if (isDebug()) debugPrint("outputFile is " + OUTPUT_FILE);
                } else if (args[count].equals("-d")) {
                    DUMP_CLASSES = true;
                    DUMP_DIR = args[++count];
                    if (isDebug()) debugPrint("dumpDir is " + DUMP_DIR);
                } else if (args[count].equals("-pd")) {
                    PROBE_DESC_PATH = args[++count];
                    if (isDebug()) debugPrint("probeDescDir is " + PROBE_DESC_PATH);
                } else if ((args[count].equals("-cp") ||
                    args[count].equals("-classpath"))
                    && !classpathDefined) {
                    classPath = args[++count];
                    if (isDebug()) debugPrint("accepting classpath " + classPath);
                    classpathDefined = true;
                } else if (args[count].equals("-I") && !includePathDefined) {
                    includePath = args[++count];
                    if (isDebug()) debugPrint("accepting include path " + includePath);
                    includePathDefined = true;
                } else if (args[count].equals("-statsd")) {
                    statsdDef = args[++count];
                } else if (args[count].equals("-v")) {
                    // already processed
                } else if (args[count].equals("-host") && !hostDefined) {
                    host = args[++count];
                    hostDefined = true;
                } else {
                    usage();
                }
                count++;
                if (count >= args.length) {
                    break;
                }
            } else {
                break;
            }
        }

        if (! portDefined) {
            if (isDebug()) debugPrint("assuming default port " + port);
        }

        if (! classpathDefined) {
            if (isDebug()) debugPrint("assuming default classpath '" + classPath + "'");
        }

        if (args.length < (count + 1)) {
            usage();
        }

        String pid = args[count];
        String fileName = args[count + 1];
        String[] btraceArgs = new String[args.length - count - 2];
        if (btraceArgs.length > 0) {
            System.arraycopy(args, count + 2, btraceArgs, 0, btraceArgs.length);
        }

        try {
            Client client = new Client(port, OUTPUT_FILE, PROBE_DESC_PATH,
                DEBUG, TRACK_RETRANSFORM, TRUSTED, DUMP_CLASSES, DUMP_DIR, statsdDef);
            if (! new File(fileName).exists()) {
                errorExit("File not found: " + fileName, 1);
            }
            byte[] code = client.compile(fileName, classPath, includePath);
            if (code == null) {
                errorExit("BTrace compilation failed", 1);
            }
            if (!hostDefined)
                client.attach(pid, null, classPath);
            registerExitHook(client);
            if (con != null) {
                registerSignalHandler(client);
            }
            if (isDebug()) debugPrint("submitting the BTrace program");
            client.submit(host, fileName, code, btraceArgs, createCommandListener(client));
        } catch (IOException exp) {
            errorExit(exp.getMessage(), 1);
        }
    }

    private static CommandListener createCommandListener(Client client) {
        return new CommandListener() {
            @Override
            public void onCommand(Command cmd) throws IOException {
                int type = cmd.getType();
                if (cmd instanceof PrintableCommand) {
                    ((PrintableCommand)cmd).print(out);
                    out.flush();
                } else if (type == Command.EXIT) {
                    exiting = true;
                    out.flush();
                    ExitCommand ecmd = (ExitCommand)cmd;
                    System.exit(ecmd.getExitCode());
                }
            }
        };
    }

    private static void registerExitHook(final Client client) {
        if (isDebug()) debugPrint("registering shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(
            new Runnable() {
                @Override
                public void run() {
                    if (! exiting) {
                        try {
                            if (isDebug()) debugPrint("sending exit command");
                            client.sendExit(0);
                        } catch (IOException ioexp) {
                            if (isDebug()) debugPrint(ioexp.toString());
                        }
                    }
                }
            }));
    }

    private static void registerSignalHandler(final Client client) {
        if (isDebug()) debugPrint("registering signal handler for SIGINT");
        Signal.handle(new Signal("INT"),
            new SignalHandler() {
                @Override
                public void handle(Signal sig) {
                    try {
                        con.printf("Please enter your option:\n");
                        con.printf("\t1. exit\n\t2. send an event\n\t3. send a named event\n\t4. flush console output\n");
                        con.flush();
                        String option = con.readLine();
                        option = option.trim();
                        if (option == null) {
                            return;
                        }
                        switch (option) {
                            case "1":
                                System.exit(0);
                            case "2":
                                if (isDebug()) debugPrint("sending event command");
                                client.sendEvent();
                                break;
                            case "3":
                                con.printf("Please enter the event name: ");
                                String name = con.readLine();
                                if (name != null) {
                                    if (isDebug()) debugPrint("sending event command");
                                    client.sendEvent(name);
                                }
                                break;
                            case "4":
                                out.flush();
                                break;
                            default:
                                con.printf("invalid option!\n");
                                break;
                        }
                    } catch (IOException ioexp) {
                        if (isDebug()) debugPrint(ioexp.toString());
                    }
                }
            });
    }

    private static void usage() {
        System.err.println(Messages.get("btrace.usage"));
        System.exit(1);
    }

    private static boolean isDebug() {
        return DEBUG;
    }

    private static boolean isUnsafe() {
        return TRUSTED;
    }

    private static void debugPrint(String msg) {
        System.out.println("DEBUG: " + msg);
    }

    private static void errorExit(String msg, int code) {
        exiting = true;
        System.err.println(msg);
        System.exit(code);
    }
}
