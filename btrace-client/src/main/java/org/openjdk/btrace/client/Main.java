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
package org.openjdk.btrace.client;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.Messages;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.ExitCommand;
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
  private static String AGENT_JAR_OVERRIDE;
  private static String BOOT_JAR_OVERRIDE;
  private static String EXTRACT_AGENT_DIR;
  private static boolean ONELINER_MODE;
  private static String ONELINER_SCRIPT;

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
    String javaVersion = getJavaVersion();
    // In Java 22 the console will write standard output to stderr :shrug:
    con = javaVersion == null || !javaVersion.startsWith("22") ? System.console() : null;
    out = getOutWriter(con);
  }

  private static String getJavaVersion() {
    Properties props = new Properties();
    try {
      props.load(Files.newInputStream(Paths.get(System.getenv("JAVA_HOME"), "release")));
      return props.getProperty("JAVA_VERSION").replace("\"", "");
    } catch (IOException ignored) {
      return null;
    }
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
    boolean listFailedExtensions = false;
    boolean unattended = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "-v":
          DEBUG = true;
          DebugSupport.initLoggers(true, log);
          break;
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
              return;
            }
          }
          break;
        case "--extract-agent":
          if (i < args.length - 1) {
            EXTRACT_AGENT_DIR = args[i + 1];
          } else {
            EXTRACT_AGENT_DIR = ".";
          }
          handleExtractAgent();
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
          if (probeCommand != null && log.isDebugEnabled()) {
            log.debug(
                "executing probe command '{}'{}",
                probeCommand,
                (probeCommandArg != null ? "(" + probeCommandArg + ")" : ""));
          }
        } else if (args[count].equals("-lp")) {
          log.debug("listing active probes");
          listProbes = true;
        } else if (args[count].equals("-le")) {
          log.debug("listing failed extensions");
          listFailedExtensions = true;
        } else if (args[count].equals("-x")) {
          log.debug("submitting probe in unattended mode");
          unattended = true;
        } else if (args[count].equals("--agent-jar")) {
          AGENT_JAR_OVERRIDE = args[++count];
          if (log.isDebugEnabled()) log.debug("agent JAR override: {}", AGENT_JAR_OVERRIDE);
        } else if (args[count].equals("--boot-jar")) {
          BOOT_JAR_OVERRIDE = args[++count];
          if (log.isDebugEnabled()) log.debug("boot JAR override: {}", BOOT_JAR_OVERRIDE);
        } else if (args[count].equals("-n") || args[count].equals("--oneliner")) {
          ONELINER_MODE = true;
          ONELINER_SCRIPT = args[++count];
          if (log.isDebugEnabled()) log.debug("oneliner: {}", ONELINER_SCRIPT);
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
              statsdDef,
              AGENT_JAR_OVERRIDE,
              BOOT_JAR_OVERRIDE);
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
        // Don't register exit hook for list-only operations - they shouldn't remove probes
        client.attach(pid.toString(), null, classPath);
        client.connectAndListProbes(host, createCommandListener(client));
        System.exit(0);
      } else if (listFailedExtensions) {
        // Don't register exit hook for list-only operations - they shouldn't remove probes
        client.attach(pid.toString(), null, classPath);
        client.connectAndListFailedExtensions(host, createCommandListener(client));
        System.exit(0);
      } else {
        String fileName;
        byte[] code;
        String[] btraceArgs;

        if (ONELINER_MODE) {
          // Oneliner mode: parse and generate Java source from oneliner
          try {
            org.openjdk.btrace.compiler.oneliner.OnelinerAST.OnelinerNode ast =
                org.openjdk.btrace.compiler.oneliner.OnelinerParser.parse(ONELINER_SCRIPT);
            org.openjdk.btrace.compiler.oneliner.OnelinerValidator.validate(ast, ONELINER_SCRIPT);
            String javaSource =
                org.openjdk.btrace.compiler.oneliner.OnelinerCodeGenerator.generate(ast);
            String className = "BTraceOneliner_" + System.currentTimeMillis();
            fileName = className + ".java";

            // Extract script args
            btraceArgs = new String[args.length - count - 1];
            if (btraceArgs.length > 0) {
              System.arraycopy(args, count + 1, btraceArgs, 0, btraceArgs.length);
            }

            // Write generated source to temp file
            if (log.isDebugEnabled()) {
              log.debug("Generated BTrace source:\n{}", javaSource);
            }
            java.io.File tempFile = java.io.File.createTempFile(className, ".java");
            try {
              java.nio.file.Files.write(
                  tempFile.toPath(), javaSource.getBytes(java.nio.charset.StandardCharsets.UTF_8));

              // Compile from temp file
              code =
                  client.compile(
                      tempFile.getAbsolutePath(), classPath, new PrintWriter(System.err), ".");
              if (code == null) {
                errorExit("Oneliner compilation failed", 1);
              }
            } finally {
              tempFile.delete();
            }
          } catch (org.openjdk.btrace.compiler.oneliner.OnelinerException e) {
            errorExit(e.getMessage(), 1);
            return;
          } catch (java.io.IOException e) {
            errorExit("Failed to create temp file for oneliner: " + e.getMessage(), 1);
            return;
          }
        } else {
          // File mode: compile from file
          fileName = args[count + 1];
          btraceArgs = new String[args.length - count - 2];
          if (btraceArgs.length > 0) {
            System.arraycopy(args, count + 2, btraceArgs, 0, btraceArgs.length);
          }
          if (!new File(fileName).exists()) {
            errorExit("File not found: " + fileName, 1);
          }
          code = client.compile(fileName, classPath, includePath);
          if (code == null) {
            errorExit("BTrace compilation failed", 1);
          }
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
                  && ((StatusCommand) cmd).getFlag() == StatusCommand.STATUS_FLAG) {
                // In unattended mode, initiate a graceful disconnect and
                // continue processing so the server can send DISCONNECT
                // with the probe id, which the client prints.
                try {
                  if (log.isDebugEnabled()) {
                    log.debug("initiating unattended disconnect after STATUS OK");
                  }
                  client.disconnect(); // marks disconnected and sends DISCONNECT
                } catch (IOException ioe) {
                  if (log.isDebugEnabled()) {
                    log.debug("error initiating unattended disconnect: {}", ioe.toString());
                  }
                }
                // Do not exit here; wait for DISCONNECT to arrive and be printed
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
                        // Disconnect already initiated; avoid duplicate sends on shutdown
                        log.debug("client already marked disconnected; skipping shutdown send");
                      }
                    } catch (IOException | IllegalStateException ioexp) {
                      // Streams may already be closed (e.g., unattended mode)
                      if (log.isDebugEnabled()) log.debug(ioexp.toString(), ioexp);
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
                "\t1. exit\n\t2. send an event\n\t3. send a named event\n\t4. flush console output\n\t5. list probes\n\t6. detach client\n\t7. list failed extensions\n");
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
              case "7":
                client.listFailedExtensions();
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

  /** Extracts embedded agent JARs from the uber JAR to the specified directory. */
  private static void handleExtractAgent() {
    if (EXTRACT_AGENT_DIR == null) {
      return;
    }

    File outputDir = new File(EXTRACT_AGENT_DIR);
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      errorExit("Failed to create output directory: " + outputDir.getAbsolutePath(), 1);
    }

    URL btraceLoc = Main.class.getProtectionDomain().getCodeSource().getLocation();
    try (JarFile btrace = new JarFile(new File(btraceLoc.toURI()))) {
      File agentFile = new File(outputDir, "btrace-agent.jar");
      File bootFile = new File(outputDir, "btrace-boot.jar");

      extractJar(btrace, "META-INF/embedded/btrace-agent.jar", agentFile);
      extractJar(btrace, "META-INF/embedded/btrace-boot.jar", bootFile);

      System.out.println("Extracted agent JARs to: " + outputDir.getAbsolutePath());
      System.out.println("  - " + agentFile.getName());
      System.out.println("  - " + bootFile.getName());
      System.exit(0);
    } catch (Exception e) {
      errorExit("Failed to extract agent JARs: " + e.getMessage(), 1);
    }
  }

  /** Extracts a single JAR entry from the source JAR to the target file. */
  private static void extractJar(JarFile source, String entryPath, File target) throws IOException {
    JarEntry entry = source.getJarEntry(entryPath);
    if (entry == null) {
      throw new IOException("Embedded JAR not found: " + entryPath);
    }

    try (InputStream in = source.getInputStream(entry);
        OutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
  }
}
