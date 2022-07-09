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

package org.openjdk.btrace.client;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.Messages;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.PrintableCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;

/**
 * This is the main class for a simple command line BTrace client. It is possible to create a GUI
 * client using the Client class.
 *
 * @author A. Sundararajan
 */
@SuppressWarnings("RedundantThrows")
public final class Main {
  private static final Logger log;

  public static final boolean TRACK_RETRANSFORM;
  public static final int BTRACE_DEFAULT_PORT = 2020;
  public static final String BTRACE_DEFAULT_HOST = "localhost";
  private static final Console con;
  private static final PrintWriter out;
  public static volatile boolean exiting;
  private static boolean DEBUG;
  private static boolean TRUSTED;
  private static boolean DUMP_CLASSES;
  private static String OUTPUT_FILE;
  private static String DUMP_DIR;
  private static String PROBE_DESC_PATH;

  static {
    DebugSupport.initLoggers(Boolean.getBoolean("com.sun.btrace.debug"), null);
    // initialize the logger instance
    log = LoggerFactory.getLogger(Main.class);

    if (isDebug()) {
      log.debug("btrace debug mode is set");
    }
    TRACK_RETRANSFORM = Boolean.getBoolean("com.sun.btrace.trackRetransforms");
    if (TRACK_RETRANSFORM) log.debug("trackRetransforms flag is set");
    TRUSTED = Boolean.getBoolean("com.sun.btrace.unsafe");
    TRUSTED |= Boolean.getBoolean("com.sun.btrace.trusted");
    if (TRUSTED) log.debug("btrace trusted mode is set");
    DUMP_CLASSES = Boolean.getBoolean("com.sun.btrace.dumpClasses");
    if (DUMP_CLASSES) log.debug("dumpClasses flag is set");
    DUMP_DIR = System.getProperty("com.sun.btrace.dumpDir", ".");
    if (DUMP_CLASSES) {
      if (log.isDebugEnabled()) log.debug("dumpDir is {}", DUMP_DIR);
    }
    PROBE_DESC_PATH = System.getProperty("com.sun.btrace.probeDescPath", ".");
    con = System.console();
    out = getOutWriter(con);
  }

  @SuppressWarnings("DefaultCharset")
  private static PrintWriter getOutWriter(Console con) {
    return (con != null) ? con.writer() : new PrintWriter(System.out);
  }

  public static void main(String[] args) throws Exception {
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
    String resumeProbe = null;
    String probeCommand = null;
    String probeCommandArg = null;
    boolean listProbes = false;
    boolean unattended = false;

    OUTER:
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "-v":
          DEBUG = true;
          break OUTER;
        case "--version":
          System.out.println(Messages.get("btrace.version"));
          return;
        case "-l":
          for (String vm : JpsUtils.listVms()) {
            System.out.println(vm);
          }
          return;
        case "-r":
          if (i < args.length - 1) {
            if (args[i + 1].equalsIgnoreCase("help")) {
              System.out.println(Messages.get("remote.commands.help"));
            }
          }
          return;
      }
    }

    if (args.length < 2) {
      usage();
    }

    for (; ; ) {
      if (args[count].isEmpty()) {
        continue;
      }
      if (args[count].charAt(0) == '-') {
        if (args.length <= count + 1) {
          usage();
        }
        if (args[count].equals("-p") && !portDefined) {
          try {
            port = Integer.parseInt(args[++count]);
            if (log.isDebugEnabled()) log.debug("accepting port {}", port);
          } catch (NumberFormatException nfe) {
            usage();
          }
          portDefined = true;
        } else if (args[count].equals("-u")) {
          TRUSTED = true;
          log.debug("btrace trusted mode is set");
        } else if (args[count].equals("-o")) {
          OUTPUT_FILE = args[++count];
          if (log.isDebugEnabled()) log.debug("outputFile is {}", OUTPUT_FILE);
        } else if (args[count].equals("-d")) {
          DUMP_CLASSES = true;
          DUMP_DIR = args[++count];
          if (log.isDebugEnabled()) log.debug("dumpDir is {}", DUMP_DIR);
        } else if (args[count].equals("-pd")) {
          PROBE_DESC_PATH = args[++count];
          if (log.isDebugEnabled()) log.debug("probeDescDir is {}", PROBE_DESC_PATH);
        } else if ((args[count].equals("-cp") || args[count].equals("-classpath"))
            && !classpathDefined) {
          classPath = args[++count];
          if (log.isDebugEnabled()) log.debug("accepting classpath {}", classPath);
          classpathDefined = true;
        } else if (args[count].equals("-I") && !includePathDefined) {
          includePath = args[++count];
          if (log.isDebugEnabled()) log.debug("accepting include path {}", includePath);
          includePathDefined = true;
        } else if (args[count].equals("-statsd")) {
          statsdDef = args[++count];
        } else if (args[count].equals("-v")) {
          // already processed
        } else if (args[count].equals("-host") && !hostDefined) {
          host = args[++count];
          hostDefined = true;
        } else if (args[count].equals("-r")) {
          if (log.isDebugEnabled())
            log.debug("reconnecting to an already active probe {}", resumeProbe);
          resumeProbe = args[++count];
          if (count < args.length - 2 && !args[count + 1].startsWith("-")) {
            probeCommand = args[++count].toLowerCase();
            if (probeCommand.equalsIgnoreCase("event") && count < args.length - 2) {
              probeCommandArg = args[++count];
            }
          }
          if (isDebug() && probeCommand != null) {
            debugPrint(
                "executing probe command '"
                    + probeCommand
                    + "'"
                    + (probeCommandArg != null ? "(" + probeCommandArg + ")" : ""));
          }
        } else if (args[count].equals("-lp")) {
          log.debug("listing active probes");
          listProbes = true;
        } else if (args[count].equals("-x")) {
          log.debug("submitting probe in unattended mode");
          unattended = true;
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

    if (!portDefined) {
      if (log.isDebugEnabled()) log.debug("assuming default port {}", port);
    }

    if (!classpathDefined) {
      if (log.isDebugEnabled()) log.debug("assuming default classpath '{}'", classPath);
    }

    if (args.length < (count + 1)) {
      usage();
    }

    String pidArg = args[count];
    Integer pid = JpsUtils.findVmByName(pidArg);
    if (pid == null) {
      errorExit("Unable to find JVM with either PID or name: " + pidArg, 1);
    } else {
      log.info("Attaching BTrace to PID: {}", pid);
    }

    try {
      Client client =
          new Client(
              port,
              OUTPUT_FILE,
              PROBE_DESC_PATH,
              DEBUG,
              TRACK_RETRANSFORM,
              TRUSTED,
              DUMP_CLASSES,
              DUMP_DIR,
              statsdDef);
      if (resumeProbe != null) {
        registerExitHook(client);
        if (con != null) {
          registerSignalHandler(client);
        }
        client.reconnect(
            host,
            resumeProbe,
            createCommandListener(client),
            new String[] {probeCommand, probeCommandArg});
      } else if (listProbes) {
        registerExitHook(client);
        client.attach(pid.toString(), null, classPath);
        client.connectAndListProbes(host, createCommandListener(client));
        System.exit(0);
      } else {
        String fileName = args[count + 1];
        String[] btraceArgs = new String[args.length - count - 2];
        if (btraceArgs.length > 0) {
          System.arraycopy(args, count + 2, btraceArgs, 0, btraceArgs.length);
        }
        if (!new File(fileName).exists()) {
          errorExit("File not found: " + fileName, 1);
        }
        byte[] code = client.compile(fileName, classPath, includePath);
        if (code == null) {
          errorExit("BTrace compilation failed", 1);
        }
        if (log.isDebugEnabled()) {
          log.debug("Boot classpath: {}", classPath);
        }
        if (!hostDefined) client.attach(pid.toString(), null, classPath);
        registerExitHook(client);
        if (con != null) {
          registerSignalHandler(client);
        }
        log.debug("submitting the BTrace program");
        CommandListener listener = createCommandListener(client);

        boolean isUnattended = unattended;
        client.submit(
            host,
            fileName,
            code,
            btraceArgs,
            cmd -> {
              if (isUnattended
                  && cmd.getType() == Command.STATUS
                  && ((StatusCommand) cmd).getFlag() == InstrumentCommand.STATUS_FLAG) {
                client.sendDisconnect();
              } else {
                listener.onCommand(cmd);
              }
            });
      }
    } catch (IOException exp) {
      errorExit(exp.getMessage(), 1);
    }
  }

  private static CommandListener createCommandListener(Client client) {
    return cmd -> {
      int type = cmd.getType();
      if (cmd instanceof PrintableCommand) {
        ((PrintableCommand) cmd).print(out);
        out.flush();
      } else if (type == Command.EXIT) {
        exiting = true;
        out.flush();
        ExitCommand ecmd = (ExitCommand) cmd;
        System.exit(ecmd.getExitCode());
      }
      if (type == Command.DISCONNECT) {
        System.exit(0);
      }
    };
  }

  private static void registerExitHook(Client client) {
    log.debug("registering shutdown hook");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (!exiting) {
                    try {
                      if (!client.isDisconnected()) {
                        log.debug("sending exit command");
                        client.sendExit(0);
                      } else {
                        log.debug("sending disconnect command");
                        client.sendDisconnect();
                      }
                    } catch (IOException ioexp) {
                      log.debug(ioexp.toString(), ioexp);
                    }
                  }
                }));
  }

  private static void registerSignalHandler(Client client) {
    log.debug("registering signal handler for SIGINT");
    Signal.handle(
        new Signal("INT"),
        sig -> {
          try {
            con.printf("Please enter your option:\n");
            con.printf(
                "\t1. exit\n\t2. send an event\n\t3. send a named event\n\t4. flush console output\n\t5. list probes\n\t6. detach client\n");
            con.flush();
            String option = con.readLine();
            if (option == null) {
              return;
            }
            option = option.trim();
            switch (option) {
              case "1":
                System.exit(0);
              case "2":
                log.debug("sending event command");
                client.sendEvent();
                break;
              case "3":
                con.printf("Please enter the event name: ");
                String name = con.readLine();
                if (name != null) {
                  log.debug("sending event command");
                  client.sendEvent(name);
                }
                break;
              case "4":
                out.flush();
                break;
              case "5":
                client.listProbes();
                break;
              case "6":
                client.disconnect();
                break;
              default:
                con.printf("invalid option!\n");
                break;
            }
          } catch (IOException ioexp) {
            log.debug(ioexp.toString(), ioexp);
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

  private static void errorExit(String msg, int code) {
    exiting = true;
    System.err.println(msg);
    System.exit(code);
  }
}
