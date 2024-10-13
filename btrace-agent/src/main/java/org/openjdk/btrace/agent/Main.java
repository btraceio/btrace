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
import static org.openjdk.btrace.core.Args.SCRIPT_DIR;
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
import java.util.ArrayList;
import java.util.List;
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
import org.openjdk.btrace.core.Messages;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.openjdk.btrace.core.comm.WireIO;
import org.openjdk.btrace.instr.BTraceProbeFactory;
import org.openjdk.btrace.instr.BTraceTransformer;
import org.openjdk.btrace.instr.Constants;
import org.openjdk.btrace.runtime.BTraceRuntimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main class for BTrace java.lang.instrument agent.
 *
 * @author A. Sundararajan
 * @author Joachim Skeie (rolling output)
 */
@SuppressWarnings("RedundantThrows")
public final class Main {
  public static final int BTRACE_DEFAULT_PORT = 2020;
  private static final Pattern KV_PATTERN = Pattern.compile(",");
  private static final SharedSettings settings = SharedSettings.GLOBAL;
  private static final BTraceTransformer transformer =
      new BTraceTransformer(new DebugSupport(settings));
  // #BTRACE-42: Non-daemon thread prevents traced application from exiting
  private static final ThreadFactory qProcessorThreadFactory =
      r -> {
        Thread result = new Thread(r, "BTrace Command Queue Processor");
        result.setDaemon(true);
        return result;
      };
  private static final ExecutorService serializedExecutor =
      Executors.newSingleThreadExecutor(qProcessorThreadFactory);
  private static final long ts = System.nanoTime();
  private static volatile ArgsMap argMap;
  private static volatile Instrumentation inst;
  private static volatile Long fileRollMilliseconds;

  private static final Logger log = LoggerFactory.getLogger(Main.class);

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
      boolean isDebug = Boolean.parseBoolean(argMap.get(DEBUG));
      // set the debug level based on cmdline config
      settings.setDebug(isDebug);
      DebugSupport.initLoggers(isDebug, log);

      parseArgs();
      // settings are all built-up; set the logging system properties accordingly
      DebugSupport.initLoggers(settings.isDebug(), log);

      String tmp = argMap.get(NO_SERVER);
      // noServer is defaulting to true if startup scripts are defined
      boolean noServer = tmp != null ? Boolean.parseBoolean(tmp) : hasScripts();
      Thread agentThread = null;
      if (noServer) {
        log.debug("noServer is true, server not started");
      } else {
        agentThread =
            new Thread(
                () -> {
                  BTraceRuntime.enter();
                  try {
                    startServer();
                  } finally {
                    BTraceRuntime.leave();
                  }
                });
      }
      // force back-registration of BTraceRuntimeImpl in BTraceRuntime
      BTraceRuntimes.getDefault();
      // init BTraceRuntime
      BTraceRuntime.initUnsafe();
      if (agentThread != null) {
        BTraceRuntime.enter();
        try {
          agentThread.setDaemon(true);
          log.debug("starting agent thread");

          agentThread.start();
        } finally {
          BTraceRuntime.leave();
        }
      }

      log.debug("Adding class transformer");
      inst.addTransformer(transformer, true);
      try {
        // the MethodHandleNatives must be instrumented to track start-end of indy linking to avoid deadlocking
        Class<?> clz = ClassLoader.getSystemClassLoader().loadClass("java.lang.invoke.MethodHandleNatives");
        inst.retransformClasses(clz);
      } catch (Throwable t) {
        log.debug("Failed to instrument MethodHandleNatives", t);
      }
      int startedScripts = startScripts();
    } finally {
      log.debug("Agent init took: {}", (System.nanoTime() - ts) + "ns");
    }
  }

  private static boolean hasScripts() {
    return argMap.containsKey(SCRIPT) || argMap.containsKey(SCRIPT_DIR);
  }

  private static final class LogValue {
    final String logLine;
    final Throwable throwable;

    public LogValue(String logLine, Throwable throwable) {
      this.logLine = logLine;
      this.throwable = throwable;
    }
  }

  private static void loadDefaultArguments(String config) {
    try {
      String propTarget = Constants.EMBEDDED_BTRACE_SECTION_HEADER + "agent.properties";
      InputStream is = ClassLoader.getSystemResourceAsStream(propTarget);
      if (is != null) {
        Properties ps = new Properties();
        ps.load(is);
        StringBuilder logMsg = new StringBuilder();
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
                    logMsg
                        .append("setting default agent argument '")
                        .append(argKey)
                        .append("' to '")
                        .append(scriptVal)
                        .append("'\n");
                  } else {
                    logMsg
                        .append("augmenting default agent argument '")
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
                    logMsg
                        .append("merging default agent argument '")
                        .append(argKey)
                        .append("':'")
                        .append(oldVal)
                        .append("' with '")
                        .append(argVal)
                        .append("'");
                    argMap.put(argKey, newVal);
                  } else {
                    logMsg
                        .append("argument '")
                        .append(argKey)
                        .append("' is applicable only in sandboxed mode");
                  }
                  break;
                }
              case SYSTEM_CLASS_PATH: // fall through
              case BOOT_CLASS_PATH: // fall through
              case CONFIG:
                {
                  logMsg.append("argument '").append(argKey).append("' is not overridable\n");
                  break;
                }
              default:
                {
                  if (!argMap.containsKey(argKey)) {
                    logMsg
                        .append("applying default agent argument '")
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
        DebugSupport.initLoggers(Boolean.parseBoolean(argMap.get(DEBUG)), log);
        if (log.isDebugEnabled()) {
          log.debug(logMsg.toString());
        }
      }
    } catch (IOException e) {
      if (log.isDebugEnabled()) {
        log.debug(e.toString(), e);
      }
    }
  }

  private static int startScripts() {
    int scriptCount = 0;

    String p = argMap.get(STDOUT);
    boolean traceToStdOut = p != null && !"false".equals(p);
    if (log.isDebugEnabled()) {
      log.debug("stdout is {}", traceToStdOut);
    }

    List<String> scripts = locateScripts(argMap);
    for (String script : scripts) {
      if (loadBTraceScript(script, traceToStdOut)) {
        scriptCount++;
      }
    }
    return scriptCount;
  }

  static List<String> locateScripts(ArgsMap argsMap) {
    String script = argsMap.get(SCRIPT);
    String scriptDir = argsMap.get(SCRIPT_DIR);

    List<String> scripts = new ArrayList<>();
    if (script != null) {
      StringTokenizer tokenizer = new StringTokenizer(script, ":");
      if (log.isDebugEnabled()) {
        log.debug(
            ((tokenizer.countTokens() == 1) ? "initial script is {}" : "initial scripts are {}"),
            script);
      }
      while (tokenizer.hasMoreTokens()) {
        scripts.add(tokenizer.nextToken());
      }
    }
    if (scriptDir != null) {
      File dir = new File(scriptDir);
      if (dir.isDirectory()) {
        if (log.isDebugEnabled()) {
          log.debug("found scriptdir: {}", dir.getAbsolutePath());
        }
        File[] files = dir.listFiles();
        if (files != null) {
          for (File file : files) {
            scripts.add(file.getAbsolutePath());
          }
        }
      }
    }
    return scripts;
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
    loadDefaultArguments(config);
    processClasspaths(libs);

    p = argMap.get(DEBUG);
    settings.setDebug(p != null && !"false".equals(p));
    DebugSupport.initLoggers(settings.isDebug(), log);

    log.debug("debugMode is {}", settings.isDebug());

    for (Map.Entry<String, String> e : argMap) {
      String key = e.getKey();
      p = e.getValue();
      switch (key) {
        case STARTUP_RETRANSFORM:
          {
            if (!p.isEmpty()) {
              settings.setRetransformStartup(Boolean.parseBoolean(p));
              log.debug(STARTUP_RETRANSFORM + " is {}", settings.isRetransformStartup());
            }
            break;
          }
        case DUMP_DIR:
          {
            String dumpClassesVal = argMap.get(DUMP_CLASSES);
            if (dumpClassesVal != null) {
              boolean dumpClasses = Boolean.parseBoolean(dumpClassesVal);
              log.debug(DUMP_CLASSES + " is {}", dumpClasses);
              if (dumpClasses) {
                String dumpDir = argMap.get(DUMP_DIR);
                settings.setDumpDir(dumpDir != null ? dumpDir : ".");
                if (isDebug()) {
                  log.debug(DUMP_DIR + " is {}", dumpDir);
                }
              }
            }
            break;
          }
        case CMD_QUEUE_LIMIT:
          {
            if (!p.isEmpty()) {
              System.setProperty(BTraceRuntime.CMD_QUEUE_LIMIT_KEY, p);
              log.debug(CMD_QUEUE_LIMIT + " provided: {}", p);
            }

            break;
          }
        case TRACK_RETRANSFORMS:
          {
            if (!p.isEmpty()) {
              settings.setTrackRetransforms(Boolean.parseBoolean(p));
              if (settings.isTrackRetransforms()) {
                log.debug(TRACK_RETRANSFORMS + " is on");
              }
            }
            break;
          }
        case SCRIPT_OUTPUT_FILE:
          {
            if (!p.isEmpty()) {
              settings.setOutputFile(p);
              log.debug(SCRIPT_OUTPUT_FILE + " is {}", p);
            }
            break;
          }
        case SCRIPT_OUTPUT_DIR:
          {
            if (!p.isEmpty()) {
              settings.setScriptOutputDir(p);
              log.debug(SCRIPT_OUTPUT_DIR + " is {}", p);
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
                log.debug(FILE_ROLL_MILLISECONDS + " is {}", fileRollMilliseconds);
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
              log.debug("trustedMode is {}", settings.isTrusted());
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
                  log.warn("Invalid statsd port number: {}", parts[1]);
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
            log.debug("probe descriptor path is {}", settings.getProbeDescPath());
            break;
          }
        case BOOT_CLASS_PATH:
          {
            settings.setBootClassPath(!p.isEmpty() ? p : "");
            log.debug("probe boot class path is {}", settings.getBootClassPath());
            break;
          }

        default:
          {
            if (key.startsWith("$")) {
              String pKey = key.substring(1);
              System.setProperty(pKey, p);
              log.debug("Setting system property: {}={}", pKey, p);
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
    log.debug("Bootstrap ClassPath: {}", bootClassPath);

    StringTokenizer tokenizer = new StringTokenizer(bootClassPath, File.pathSeparator);
    try {
      while (tokenizer.hasMoreTokens()) {
        String path = tokenizer.nextToken();
        File f = new File(path);
        if (!f.exists()) {
          log.warn("BTrace bootstrap classpath resource [{}] does not exist", path);
        } else {
          if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
            JarFile jf = asJarFile(f);
            log.debug("Adding jar: {}", jf);
            inst.appendToBootstrapClassLoaderSearch(jf);
          } else {
            log.debug("ignoring boot classpath element '{}' - only jar files allowed", path);
          }
        }
      }
    } catch (IOException ex) {
      log.debug("adding to boot classpath failed!", ex);
      return;
    }

    String systemClassPath = argMap.get(SYSTEM_CLASS_PATH);
    if (systemClassPath != null) {
      log.debug("System ClassPath: {}", systemClassPath);
      tokenizer = new StringTokenizer(systemClassPath, File.pathSeparator);
      try {
        while (tokenizer.hasMoreTokens()) {
          String path = tokenizer.nextToken();
          File f = new File(path);
          if (!f.exists()) {
            log.warn("BTrace system classpath resource [{}] does not exist.", path);
          } else {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
              JarFile jf = asJarFile(f);
              inst.appendToSystemClassLoaderSearch(jf);
            } else {
              log.debug("ignoring system classpath element '{}' - only jar files allowed", path);
            }
          }
        }
      } catch (IOException ex) {
        log.debug("adding to boot classpath failed!", ex);
        return;
      }
    }

    addPreconfLibs(libs);
  }

  @SuppressWarnings("JavaReflectionMemberAccess")
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
            log.warn(
                "Invalid 'libs' configuration [{}]. Path '{}' does not exist.",
                libs,
                libFolder.toAbsolutePath());
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
                  if (log.isDebugEnabled()) {
                    log.debug("Adding {} to bootstrap classpath", file);
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
        log.debug("Failed to enhance bootstrap classpath", e);
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
                  if (log.isDebugEnabled()) {
                    log.debug("Adding {} to system classpath", file);
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
        log.debug("Failed to enhance sytem classpath", e);
      }
    }
  }

  private static boolean loadBTraceScript(String filePath, boolean traceToStdOut) {
    if (!BTraceProbeFactory.canLoad(filePath)) {
      return false;
    }

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
        if (log.isDebugEnabled()) {
          log.debug("refusing {} - script should be a pre-compiled class file", filePath);
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
        String outDir = clientSettings.getScriptOutputDir();
        if (traceOutput == null || traceOutput.isEmpty()) {
          clientSettings.setOutputFile("${client}-${agent}.${ts}.btrace[default]");
          if (outDir == null || outDir.isEmpty()) {
            clientSettings.setScriptOutputDir(scriptParent);
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
      if (log.isDebugEnabled()) {
        log.debug("script {} does not exist!", filePath, e);
      }
    } catch (RuntimeException | IOException | ExecutionException re) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to load BTrace script {}", filePath, re);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  // -- Internals only below this point
  @SuppressWarnings("InfiniteLoopStatement")
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
      if (log.isDebugEnabled()) {
        log.debug("starting server at port {}", port);
      }
      System.setProperty("btrace.wireio", String.valueOf(WireIO.VERSION));

      String scriptOutputFile = settings.getOutputFile();
      if (scriptOutputFile != null && !scriptOutputFile.isEmpty()) {
        System.setProperty("btrace.output", scriptOutputFile);
      }
      ss = new ServerSocket(port);
      System.setProperty("btrace.port", String.valueOf(ss.getLocalPort()));
    } catch (IOException ioexp) {
      ioexp.printStackTrace();
      return;
    }

    while (true) {
      try {
        log.debug("waiting for clients");
        Socket sock = ss.accept();
        if (log.isDebugEnabled()) {
          log.debug("client accepted {}", sock);
        }
        ClientContext ctx = new ClientContext(inst, transformer, argMap, settings);
        Client client = RemoteClient.getClient(ctx, sock, Main::handleNewClient);
      } catch (RuntimeException | IOException re) {
        if (log.isDebugEnabled()) {
          log.debug("BTrace server failed", re);
        }
      }
    }
  }

  private static Future<?> handleNewClient(Client client) {
    return serializedExecutor.submit(
        () -> {
          try {
            boolean entered = BTraceRuntime.enter();
            try {
              if (log.isDebugEnabled()) {
                log.debug("new Client created {}", client);
              }
              if (client.retransformLoaded()) {
                client.getRuntime().send(new StatusCommand((byte) 1));
              }
            } catch (UnmodifiableClassException uce) {
              log.debug("BTrace class retransformation failed", uce);
              client.getRuntime().send(new ErrorCommand(uce));
              client.getRuntime().send(new StatusCommand(-1 * StatusCommand.STATUS_FLAG));
            } finally {
              if (entered) {
                BTraceRuntime.leave();
              }
            }
          } catch (Throwable t) {
            t.printStackTrace();
          }
        });
  }

  private static void error(String msg) {
    System.err.println("btrace ERROR: " + msg);
  }

  private static boolean isDebug() {
    return settings.isDebug();
  }
}
