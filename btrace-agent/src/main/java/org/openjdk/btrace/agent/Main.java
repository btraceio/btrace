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
package org.openjdk.btrace.agent;

import static org.openjdk.btrace.core.Args.ALLOWED_CALLS;
import static org.openjdk.btrace.core.Args.BOOT_CLASS_PATH;
import static org.openjdk.btrace.core.Args.CMD_QUEUE_LIMIT;
import static org.openjdk.btrace.core.Args.CONFIG;
import static org.openjdk.btrace.core.Args.DEBUG;
import static org.openjdk.btrace.core.Args.DUMP_CLASSES;
import static org.openjdk.btrace.core.Args.DUMP_DIR;
import static org.openjdk.btrace.core.Args.FILE_ROLL_MAX_ROLLS;
import static org.openjdk.btrace.core.Args.FILE_ROLL_MILLISECONDS;
import static org.openjdk.btrace.core.Args.HELP;
import static org.openjdk.btrace.core.Args.LIBS;
import static org.openjdk.btrace.core.Args.NO_SERVER;
import static org.openjdk.btrace.core.Args.PORT;
import static org.openjdk.btrace.core.Args.PROBE_DESC_PATH;
import static org.openjdk.btrace.core.Args.SCRIPT;
import static org.openjdk.btrace.core.Args.SCRIPT_OUTPUT_DIR;
import static org.openjdk.btrace.core.Args.SCRIPT_OUTPUT_FILE;
import static org.openjdk.btrace.core.Args.STARTUP_RETRANSFORM;
import static org.openjdk.btrace.core.Args.STATSD;
import static org.openjdk.btrace.core.Args.STDOUT;
import static org.openjdk.btrace.core.Args.SYSTEM_CLASS_PATH;
import static org.openjdk.btrace.core.Args.TRACK_RETRANSFORMS;
import static org.openjdk.btrace.core.Args.TRUSTED;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.Function;
import org.openjdk.btrace.core.Messages;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.openjdk.btrace.core.comm.WireIO;
import org.openjdk.btrace.instr.BTraceTransformer;
import org.openjdk.btrace.instr.Constants;
import org.openjdk.btrace.runtime.BTraceRuntimes;

/**
 * This is the main class for BTrace java.lang.instrument agent.
 *
 * @author A. Sundararajan
 * @author Joachim Skeie (rolling output)
 */
public final class Main {
  public static final int BTRACE_DEFAULT_PORT = 2020;
  private static final Pattern KV_PATTERN = Pattern.compile(",");
  private static final SharedSettings settings = SharedSettings.GLOBAL;
  private static final DebugSupport debug = new DebugSupport(settings);
  private static final BTraceTransformer transformer = new BTraceTransformer(debug);
  // #BTRACE-42: Non-daemon thread prevents traced application from exiting
  private static final ThreadFactory qProcessorThreadFactory =
      new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          Thread result = new Thread(r, "BTrace Command Queue Processor");
          result.setDaemon(true);
          return result;
        }
      };
  private static final ExecutorService serializedExecutor =
      Executors.newSingleThreadExecutor(qProcessorThreadFactory);
  private static final long ts = System.nanoTime();
  private static volatile ArgsMap argMap;
  private static volatile Instrumentation inst;
  private static volatile Long fileRollMilliseconds;

  public static void premain(String args, Instrumentation inst) {
    main(args, inst);
  }

  public static void agentmain(String args, Instrumentation inst) {
    main(args, inst);
  }

  private static synchronized void main(String args, Instrumentation inst) {
    if (Main.inst != null) {
      return;
    } else {
      Main.inst = inst;
    }

    try {
      loadArgs(args);
      // set the debug level based on cmdline config
      settings.setDebug(Boolean.parseBoolean(argMap.get(DEBUG)));
      if (isDebug()) {
        debugPrint("parsed command line arguments");
      }
      parseArgs();

      if (isDebug()) {
        debugPrint("Adding class transformer");
      }
      inst.addTransformer(transformer, true);
      int startedScripts = startScripts();

      String tmp = argMap.get(NO_SERVER);
      // noServer is defaulting to true if startup scripts are defined
      boolean noServer = tmp != null ? Boolean.parseBoolean(tmp) : hasScripts();
      if (noServer) {
        if (isDebug()) {
          debugPrint("noServer is true, server not started");
        }
        return;
      }
      Thread agentThread =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  BTraceRuntime.enter();
                  try {
                    startServer();
                  } finally {
                    BTraceRuntime.leave();
                  }
                }
              });
      // force back-registration of BTraceRuntimeImpl in BTraceRuntime
      BTraceRuntimes.getDefault();
      // init BTraceRuntime
      BTraceRuntime.initUnsafe();
      BTraceRuntime.enter();
      try {
        agentThread.setDaemon(true);
        if (isDebug()) {
          debugPrint("starting agent thread");
        }
        agentThread.start();
      } finally {
        BTraceRuntime.leave();
      }
    } finally {
      debugPrint("Agent init took: " + (System.nanoTime() - ts) + "ns");
    }
  }

  private static boolean hasScripts() {
    return argMap.containsKey(SCRIPT)
        || argMap.containsKey(SCRIPT_OUTPUT_DIR)
        || argMap.containsKey(SCRIPT_OUTPUT_FILE);
  }

  private static void loadDefaultArguments(String config) {
    try {
      String propTarget = Constants.EMBEDDED_BTRACE_SECTION_HEADER + "agent.properties";
      InputStream is = ClassLoader.getSystemResourceAsStream(propTarget);
      if (is != null) {
        Properties ps = new Properties();
        ps.load(is);
        StringBuilder log = new StringBuilder();
        for (Map.Entry<Object, Object> entry : ps.entrySet()) {
          String keyConfig = "";
          String argKey = (String) entry.getKey();
          int configPos = argKey.lastIndexOf('#');
          if (configPos > -1) {
            keyConfig = argKey.substring(0, configPos);
            argKey = argKey.substring(configPos + 1);
          }
          if (config == null || keyConfig.isEmpty() || config.equals(keyConfig)) {
            String argVal = (String) entry.getValue();
            switch (argKey) {
              case SCRIPT:
                {
                  // special treatment for the 'script' parameter
                  boolean replace = false;
                  String scriptVal = argVal;
                  if (scriptVal.startsWith("!")) {
                    scriptVal = scriptVal.substring(1);
                    replace = true;
                  } else {
                    String oldVal = argMap.get(argKey);
                    if (oldVal != null && !oldVal.isEmpty()) {
                      scriptVal = oldVal + ":" + scriptVal;
                    } else {
                      replace = true;
                    }
                  }
                  if (replace) {
                    log.append("setting default agent argument '")
                        .append(argKey)
                        .append("' to '")
                        .append(scriptVal)
                        .append("'\n");
                  } else {
                    log.append("augmenting default agent argument '")
                        .append(argKey)
                        .append("':'")
                        .append(argMap.get(argKey))
                        .append("' with '")
                        .append(argVal)
                        .append("'\n");
                  }

                  argMap.put(argKey, scriptVal);
                  break;
                }
              case ALLOWED_CALLS:
                {
                  if (Boolean.parseBoolean(argMap.get(argKey))) {
                    // merge allowed calls from command line and agent.properties
                    String oldVal = argMap.get(argKey);
                    String newVal = oldVal + "|" + argVal;
                    log.append("merging default agent argument '")
                        .append(argKey)
                        .append("':'")
                        .append(oldVal)
                        .append("' with '")
                        .append(argVal)
                        .append("'");
                    argMap.put(argKey, newVal);
                  } else {
                    log.append("argument '")
                        .append(argKey)
                        .append("' is applicable only in sandboxed mode");
                  }
                  break;
                }
              case SYSTEM_CLASS_PATH: // fall through
              case BOOT_CLASS_PATH: // fall through
              case CONFIG:
                {
                  log.append("argument '").append(argKey).append("' is not overridable\n");
                  break;
                }
              default:
                {
                  if (!argMap.containsKey(argKey)) {
                    log.append("applying default agent argument '")
                        .append(argKey)
                        .append("'='")
                        .append(argVal)
                        .append("'\n");
                    argMap.put(argKey, argVal);
                  }
                }
            }
          }
        }
        if (Boolean.parseBoolean(argMap.get(DEBUG))) {
          DebugSupport.info(log.toString());
        }
      }
    } catch (IOException e) {
      debug.debug(e);
    }
  }

  private static int startScripts() {
    int scriptCount = 0;

    String p = argMap.get(STDOUT);
    boolean traceToStdOut = p != null && !"false".equals(p);
    if (isDebug()) {
      debugPrint("stdout is " + traceToStdOut);
    }

    String script = argMap.get(SCRIPT);
    String scriptDir = argMap.get(SCRIPT_OUTPUT_DIR);

    if (script != null) {
      StringTokenizer tokenizer = new StringTokenizer(script, ":");
      if (isDebug()) {
        debugPrint(
            ((tokenizer.countTokens() == 1) ? "initial script is " : "initial scripts are ")
                + script);
      }
      while (tokenizer.hasMoreTokens()) {
        if (loadBTraceScript(tokenizer.nextToken(), traceToStdOut)) {
          scriptCount++;
        }
      }
    }
    if (scriptDir != null) {
      File dir = new File(scriptDir);
      if (dir.isDirectory()) {
        if (isDebug()) {
          debugPrint("found scriptdir: " + dir.getAbsolutePath());
        }
        File[] files = dir.listFiles();
        if (files != null) {
          for (File file : files) {
            if (loadBTraceScript(file.getAbsolutePath(), traceToStdOut)) {
              scriptCount++;
            }
          }
        }
      }
    }
    return scriptCount;
  }

  private static void usage() {
    System.out.println(Messages.get("btrace.agent.usage"));
    System.exit(0);
  }

  private static void loadArgs(String args) {
    if (args == null) {
      args = "";
    }
    String[] pairs = KV_PATTERN.split(args);
    argMap = new ArgsMap();
    for (String s : pairs) {
      int i = s.indexOf('=');
      String key, value = "";
      if (i != -1) {
        key = s.substring(0, i).trim();
        if (i + 1 < s.length()) {
          value = s.substring(i + 1).trim();
        }
      } else {
        key = s;
      }
      argMap.put(key, value);
    }
  }

  private static void parseArgs() {
    String p = argMap.get(HELP);
    if (p != null) {
      usage();
    }

    String libs = argMap.get(LIBS);
    String config = argMap.get(CONFIG);
    processClasspaths(libs);
    loadDefaultArguments(config);

    p = argMap.get(DEBUG);
    settings.setDebug(p != null && !"false".equals(p));
    if (isDebug()) {
      debugPrint("debugMode is " + settings.isDebug());
    }

    for (Map.Entry<String, String> e : argMap) {
      String key = e.getKey();
      p = e.getValue();
      switch (key) {
        case STARTUP_RETRANSFORM:
          {
            if (!p.isEmpty()) {
              settings.setRetransformStartup(Boolean.parseBoolean(p));
              if (isDebug()) {
                debugPrint(STARTUP_RETRANSFORM + " is " + settings.isRetransformStartup());
              }
            }
            break;
          }
        case DUMP_DIR:
          {
            String dumpClassesVal = argMap.get(DUMP_CLASSES);
            if (dumpClassesVal != null) {
              boolean dumpClasses = Boolean.parseBoolean(dumpClassesVal);
              if (isDebug()) {
                debugPrint(DUMP_CLASSES + " is " + dumpClasses);
              }
              if (dumpClasses) {
                String dumpDir = argMap.get(DUMP_DIR);
                settings.setDumpDir(dumpDir != null ? dumpDir : ".");
                if (isDebug()) {
                  debugPrint(DUMP_DIR + " is " + dumpDir);
                }
              }
            }
            break;
          }
        case CMD_QUEUE_LIMIT:
          {
            if (!p.isEmpty()) {
              System.setProperty(BTraceRuntime.CMD_QUEUE_LIMIT_KEY, p);
              if (isDebug()) {
                debugPrint(CMD_QUEUE_LIMIT + " provided: " + p);
              }
            }

            break;
          }
        case TRACK_RETRANSFORMS:
          {
            if (!p.isEmpty()) {
              settings.setTrackRetransforms(Boolean.parseBoolean(p));
              if (settings.isTrackRetransforms()) {
                debugPrint(TRACK_RETRANSFORMS + " is on");
              }
            }
            break;
          }
        case SCRIPT_OUTPUT_FILE:
          {
            if (!p.isEmpty()) {
              settings.setOutputFile(p);
              if (isDebug()) {
                debugPrint(SCRIPT_OUTPUT_FILE + " is " + p);
              }
            }
            break;
          }
        case SCRIPT_OUTPUT_DIR:
          {
            if (!p.isEmpty()) {
              settings.setOutputDir(p);
              if (isDebug()) {
                debugPrint(SCRIPT_OUTPUT_DIR + " is " + p);
              }
            }
            break;
          }
        case FILE_ROLL_MILLISECONDS:
          {
            if (!p.isEmpty()) {
              Long msParsed = null;
              try {
                msParsed = Long.parseLong(p);
                fileRollMilliseconds = msParsed;
              } catch (NumberFormatException nfe) {
                fileRollMilliseconds = null;
              }
              if (fileRollMilliseconds != null) {
                settings.setFileRollMilliseconds(fileRollMilliseconds.intValue());
                if (isDebug()) {
                  debugPrint(FILE_ROLL_MILLISECONDS + " is " + fileRollMilliseconds);
                }
              }
            }
            break;
          }
        case FILE_ROLL_MAX_ROLLS:
          {
            if (!p.isEmpty()) {
              Integer rolls = null;
              try {
                rolls = Integer.parseInt(p);
              } catch (NumberFormatException ignored) {
                // ignore
              }

              if (rolls != null) {
                settings.setFileRollMaxRolls(rolls);
              }
            }
            break;
          }
        case TRUSTED:
          {
            if (!p.isEmpty()) {
              settings.setTrusted(Boolean.parseBoolean(p));
              if (isDebug()) {
                debugPrint("trustedMode is " + settings.isTrusted());
              }
            }
            break;
          }
        case STATSD:
          {
            if (!p.isEmpty()) {
              String[] parts = p.split(":");
              if (parts.length == 2) {
                settings.setStatsdHost(parts[0].trim());
                try {
                  settings.setStatsdPort(Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ex) {
                  DebugSupport.warning("Invalid statsd port number: " + parts[1]);
                  // leave the port unconfigured
                }
              } else if (parts.length == 1) {
                settings.setStatsdHost(parts[0].trim());
              }
            }
            break;
          }
        case PROBE_DESC_PATH:
          {
            settings.setProbeDescPath(!p.isEmpty() ? p : ".");
            if (isDebug()) {
              debugPrint("probe descriptor path is " + settings.getProbeDescPath());
            }
            break;
          }
        case BOOT_CLASS_PATH:
          {
            settings.setBootClassPath(!p.isEmpty() ? p : "");
            if (isDebug()) {
              debugPrint("probe boot class path is " + settings.getBootClassPath());
            }
            break;
          }

        default:
          {
            if (key.startsWith("$")) {
              String pKey = key.substring(1);
              System.setProperty(pKey, p);
              if (isDebug()) {
                debugPrint("Setting system property: " + pKey + "=" + p);
              }
            }
          }
      }
    }
  }

  private static void processClasspaths(String libs) {
    URL agentJar = Main.class.getResource("Main.class");
    String bootPath = agentJar.toString().replace("jar:file:", "");
    int idx = bootPath.indexOf("btrace-agent.jar");
    if (idx > -1) {
      bootPath = bootPath.substring(0, idx) + "btrace-boot.jar";
    }
    String bootClassPath = argMap.get(BOOT_CLASS_PATH);
    if (bootClassPath == null) {
      bootClassPath = bootPath;
    } else {
      if (".".equals(bootClassPath)) {
        bootClassPath = bootPath;
      } else {
        bootClassPath = bootPath + File.pathSeparator + bootClassPath;
      }
    }
    if (isDebug()) {
      debugPrint("Bootstrap ClassPath: " + bootClassPath);
    }
    StringTokenizer tokenizer = new StringTokenizer(bootClassPath, File.pathSeparator);
    try {
      while (tokenizer.hasMoreTokens()) {
        String path = tokenizer.nextToken();
        File f = new File(path);
        if (!f.exists()) {
          DebugSupport.warning(
              "BTrace bootstrap classpath resource [ " + path + "] does not exist");
        } else {
          if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
            JarFile jf = asJarFile(f);
            debugPrint("Adding jar: " + jf.toString());
            inst.appendToBootstrapClassLoaderSearch(jf);
          } else {
            debugPrint("ignoring boot classpath element '" + path + "' - only jar files allowed");
          }
        }
      }
    } catch (IOException ex) {
      debugPrint("adding to boot classpath failed!");
      debugPrint(ex);
      return;
    }

    String systemClassPath = argMap.get(SYSTEM_CLASS_PATH);
    if (systemClassPath != null) {
      if (isDebug()) {
        debugPrint("System ClassPath: " + systemClassPath);
      }
      tokenizer = new StringTokenizer(systemClassPath, File.pathSeparator);
      try {
        while (tokenizer.hasMoreTokens()) {
          String path = tokenizer.nextToken();
          File f = new File(path);
          if (!f.exists()) {
            DebugSupport.warning("BTrace system classpath resource [" + path + "] does not exist.");
          } else {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
              JarFile jf = asJarFile(f);
              inst.appendToSystemClassLoaderSearch(jf);
            } else {
              debugPrint(
                  "ignoring system classpath element '" + path + "' - only jar files allowed");
            }
          }
        }
      } catch (IOException ex) {
        debugPrint("adding to boot classpath failed!");
        debugPrint(ex);
        return;
      }
    }

    addPreconfLibs(libs);
  }

  private static JarFile asJarFile(File path) throws IOException {
    try {
      Class.forName("java.lang.Module"); // bail out early if on pre Java 9 version
      Class<Runtime> rtClass = Runtime.class;
      Method m = rtClass.getMethod("version");
      Object version = m.invoke(null);
      // JPMS enabled version of JarFile has different constructor signature
      return JarFile.class
          .getConstructor(File.class, boolean.class, int.class, version.getClass())
          .newInstance(path, true, ZipFile.OPEN_READ, version);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | SecurityException ignore) {
    }

    return new JarFile(path);
  }

  private static void addPreconfLibs(String libs) {
    URL u =
        Main.class.getClassLoader().getResource(Main.class.getName().replace('.', '/') + ".class");
    if (u != null) {
      String path = u.toString();
      int delimiterPos = path.lastIndexOf('!');
      if (delimiterPos > -1) {
        String jar = path.substring(9, delimiterPos);
        File jarFile = new File(jar);
        Path libRoot = new File(jarFile.getParent() + File.separator + "btrace-libs").toPath();
        Path libFolder = libs != null ? libRoot.resolve(libs) : libRoot;
        if (Files.exists(libFolder)) {
          appendToBootClassPath(libFolder);
          appendToSysClassPath(libFolder);
        } else {
          if (libs != null && !libs.isEmpty()) {
            DebugSupport.warning(
                "Invalid 'libs' configuration ["
                    + libs
                    + "]. "
                    + "Path '"
                    + libFolder.toAbsolutePath()
                    + "' does not exist.");
          }
        }
      }
    }
  }

  private static void appendToBootClassPath(Path libFolder) {
    Path bootLibs = libFolder.resolve("boot");
    if (Files.exists(bootLibs)) {
      try {
        Files.walkFileTree(
            bootLibs,
            new FileVisitor<Path>() {
              @Override
              public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                if (file.toString().toLowerCase().endsWith(".jar")) {
                  if (isDebug()) {
                    debugPrint("Adding " + file + " to bootstrap classpath");
                  }
                  inst.appendToBootstrapClassLoaderSearch(new JarFile(file.toFile()));
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (IOException e) {
        debugPrint(e);
      }
    }
  }

  private static void appendToSysClassPath(Path libFolder) {
    Path sysLibs = libFolder.resolve("system");
    if (Files.exists(sysLibs)) {
      try {
        Files.walkFileTree(
            sysLibs,
            new FileVisitor<Path>() {
              @Override
              public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                if (file.toString().toLowerCase().endsWith(".jar")) {
                  if (isDebug()) {
                    debugPrint("Adding " + file + " to system classpath");
                  }
                  inst.appendToSystemClassLoaderSearch(new JarFile(file.toFile()));
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (IOException e) {
        debugPrint(e);
      }
    }
  }

  private static boolean loadBTraceScript(String filePath, boolean traceToStdOut) {
    try {
      String scriptName = "";
      String scriptParent = "";
      File traceScript = new File(filePath);
      scriptName = traceScript.getName();
      scriptParent = traceScript.getParent();

      if (!traceScript.exists()) {
        traceScript = new File(Constants.EMBEDDED_BTRACE_SECTION_HEADER + filePath);
      }

      if (scriptName.endsWith(".java")) {
        if (isDebug()) {
          debugPrint("refusing " + filePath + " - script should be a pre-compiled class file");
        }
        return false;
      }

      SharedSettings clientSettings = new SharedSettings();
      clientSettings.from(settings);
      clientSettings.setClientName(scriptName);
      if (traceToStdOut) {
        clientSettings.setOutputFile("::stdout");
      } else {
        String traceOutput = clientSettings.getOutputFile();
        String outDir = clientSettings.getOutputDir();
        if (traceOutput == null || traceOutput.length() == 0) {
          clientSettings.setOutputFile("${client}-${agent}.${ts}.btrace[default]");
          if (outDir == null || outDir.length() == 0) {
            clientSettings.setOutputDir(scriptParent);
          }
        }
      }
      ClientContext ctx = new ClientContext(inst, transformer, argMap, clientSettings);
      Client client = new FileClient(ctx, traceScript);
      if (client.isInitialized()) {
        handleNewClient(client).get();
        return true;
      }
    } catch (NullPointerException e) {
      if (isDebug()) {
        debugPrint("script " + filePath + " does not exist!");
      }
    } catch (RuntimeException | IOException | ExecutionException re) {
      if (isDebug()) {
        debugPrint(re);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  // -- Internals only below this point
  private static void startServer() {
    int port = BTRACE_DEFAULT_PORT;
    String p = argMap.get(PORT);
    if (p != null) {
      try {
        port = Integer.parseInt(p);
      } catch (NumberFormatException exp) {
        error("invalid port assuming default..");
      }
    }
    ServerSocket ss;
    try {
      if (isDebug()) {
        debugPrint("starting server at " + port);
      }
      System.setProperty("btrace.port", String.valueOf(port));
      System.setProperty("btrace.wireio", String.valueOf(WireIO.VERSION));

      String scriptOutputFile = settings.getOutputFile();
      if (scriptOutputFile != null && scriptOutputFile.length() > 0) {
        System.setProperty("btrace.output", scriptOutputFile);
      }
      ss = new ServerSocket(port);
      int localPort = ss.getLocalPort();

      if (isDebug()) {
        debugPrint("started server at " + localPort);
      }
      System.setProperty("btrace.localport", String.valueOf(localPort));
    } catch (IOException ioexp) {
      ioexp.printStackTrace();
      return;
    }

    while (true) {
      try {
        if (isDebug()) {
          debugPrint("waiting for clients");
        }
        Socket sock = ss.accept();
        if (isDebug()) {
          debugPrint("client accepted " + sock);
        }
        ClientContext ctx = new ClientContext(inst, transformer, argMap, settings);
        Client client =
            RemoteClient.getClient(
                ctx,
                sock,
                new Function<Client, Future<?>>() {
                  @Override
                  public Future<?> apply(Client value) {
                    return handleNewClient(value);
                  }
                });
      } catch (RuntimeException | IOException re) {
        if (isDebug()) {
          debugPrint(re);
        }
      }
    }
  }

  private static Future<?> handleNewClient(final Client client) {
    return serializedExecutor.submit(
        new Runnable() {

          @Override
          public void run() {
            try {
              boolean entered = BTraceRuntime.enter();
              try {
                client.debugPrint("new Client created " + client);
                if (client.retransformLoaded()) {
                  client.getRuntime().send(new StatusCommand((byte) 1));
                }
              } catch (UnmodifiableClassException uce) {
                if (isDebug()) {
                  debugPrint(uce);
                }
                client.getRuntime().send(new ErrorCommand(uce));
                client.getRuntime().send(new StatusCommand(-1 * InstrumentCommand.STATUS_FLAG));
              } finally {
                if (entered) {
                  BTraceRuntime.leave();
                }
              }
            } catch (Throwable t) {
              t.printStackTrace();
            }
          }
        });
  }

  private static void error(String msg) {
    System.err.println("btrace ERROR: " + msg);
  }

  private static boolean isDebug() {
    return settings.isDebug();
  }

  private static void debugPrint(String msg) {
    debug.debug(msg);
  }

  private static void debugPrint(Throwable th) {
    debug.debug(th);
  }
}
