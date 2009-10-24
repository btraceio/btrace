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

package com.sun.btrace.client;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import com.sun.btrace.CommandListener;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.DataCommand;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.util.Messages;
import java.net.Socket;

/**
 * This is the main class for a simple command line
 * BTrace client. It is possible to create a GUI
 * client using the Client class.
 *
 * @author A. Sundararajan
 */
public final class Main {
    public static volatile boolean exiting;
    public static final boolean DEBUG;
    public static final boolean UNSAFE;
    public static final boolean DUMP_CLASSES;
    public static final String DUMP_DIR;
    public static final String PROBE_DESC_PATH;
    public static final int BTRACE_DEFAULT_PORT = 2020;
    
    private static final Console con;
    private static final PrintWriter out;
    static {
        DEBUG = Boolean.getBoolean("com.sun.btrace.debug");
        if (isDebug()) debugPrint("btrace debug mode is set");
        UNSAFE = Boolean.getBoolean("com.sun.btrace.unsafe");
        if (isDebug()) debugPrint("btrace unsafe mode is set");
        DUMP_CLASSES = Boolean.getBoolean("com.sun.btrace.dumpClasses");
        if (isDebug()) debugPrint("dumpClasses flag is set");
        DUMP_DIR = System.getProperty("com.sun.btrace.dumpDir", ".");
        if (DUMP_CLASSES) {
            if (isDebug()) debugPrint("dumpDir is " + DUMP_DIR);
        }
        PROBE_DESC_PATH = System.getProperty("com.sun.btrace.probeDescPath", ".");
        con = System.console();
        out = (con != null)? con.writer() : new PrintWriter(System.out);
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            usage();
        }

        int port = BTRACE_DEFAULT_PORT;
        String classPath = ".";
        String includePath = null;
        
        int count = 0;
        boolean portDefined = false;
        boolean classpathDefined = false;
        boolean includePathDefined = false;

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
        checkPortAvailable(port);

        String pid = args[count];
        String fileName = args[count + 1];
        String[] btraceArgs = new String[args.length - count];
        if (btraceArgs.length > 0) {
            System.arraycopy(args, count, btraceArgs, 0, btraceArgs.length);
        }

        try {
            Client client = new Client(port, PROBE_DESC_PATH, 
                DEBUG, UNSAFE, DUMP_CLASSES, DUMP_DIR);
            if (! new File(fileName).exists()) {
                errorExit("File not found: " + fileName, 1);
            }
            byte[] code = client.compile(fileName, classPath, includePath);
            if (code == null) { 
                errorExit("BTrace compilation failed", 1);
            }
            client.attach(pid);
            registerExitHook(client);
            if (con != null) {
                registerSignalHandler(client);
            }
            if (isDebug()) debugPrint("submitting the BTrace program");
            client.submit(fileName, code, btraceArgs,
                createCommandListener(client));
        } catch (IOException exp) {
            errorExit(exp.getMessage(), 1);
        }
    }

    private static void checkPortAvailable(int port) {
        Socket clSocket = null;
        try {
            clSocket = new Socket("127.0.0.1", port);
        } catch (UnknownHostException ex) {
        } catch (IOException ex) {
            clSocket = null;
        }
        if (clSocket != null) {
            try {
                clSocket.close();
            } catch (IOException e) {
            }
            errorExit("port " + port + " not available. exiting", -1);
        }
    }

    private static CommandListener createCommandListener(Client client) {
        return new CommandListener() {
            public void onCommand(Command cmd) throws IOException {
                int type = cmd.getType();
                if (cmd instanceof DataCommand) {
                    ((DataCommand)cmd).print(out);
                    out.flush();
                } else if (type == Command.EXIT) {
                    exiting = true;
                    ExitCommand ecmd = (ExitCommand)cmd;
                    System.exit(ecmd.getExitCode());
                } else if (type == Command.ERROR) {
                    ErrorCommand ecmd = (ErrorCommand)cmd;
                    Throwable cause = ecmd.getCause();
                    if (cause != null) {
                        cause.printStackTrace();
                    }
                }
            }
        };
    }

    private static void registerExitHook(final Client client) {
        if (isDebug()) debugPrint("registering shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(
            new Runnable() {
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
                public void handle(Signal sig) {
                    try {
                        con.printf("Please enter your option:\n");
                        con.printf("\t1. exit\n\t2. send an event\n\t3. send a named event\n");
                        con.flush();
                        String option = con.readLine();
                        option = option.trim();
                        if (option == null) {
                            return;
                        }
                        if (option.equals("1")) {                            
                            System.exit(0);
                        } else if (option.equals("2")) {
                            if (isDebug()) debugPrint("sending event command");
                            client.sendEvent();
                        } else if (option.equals("3")) {
                            con.printf("Please enter the event name: ");
                            String name = con.readLine();
                            if (name != null) {
                                if (isDebug()) debugPrint("sending event command");
                                client.sendEvent(name);
                            }
                        } else {
                            con.printf("invalid option!\n");
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
        return UNSAFE;
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
