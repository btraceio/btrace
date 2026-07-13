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
package io.btrace.agent;

import static io.btrace.core.Args.ALLOW_EXTENSIONS;
import static io.btrace.core.Args.ALLOW_PRIVILEGED;
import static io.btrace.core.Args.BOOT_CLASS_PATH;
import static io.btrace.core.Args.CMD_QUEUE_LIMIT;
import static io.btrace.core.Args.CONFIG;
import static io.btrace.core.Args.DEBUG;
import static io.btrace.core.Args.DENY;
import static io.btrace.core.Args.DENY_EXTENSIONS;
import static io.btrace.core.Args.DUMP_CLASSES;
import static io.btrace.core.Args.DUMP_DIR;
import static io.btrace.core.Args.FILE_ROLL_MAX_ROLLS;
import static io.btrace.core.Args.FILE_ROLL_MILLISECONDS;
import static io.btrace.core.Args.GRANT;
import static io.btrace.core.Args.GRANT_ALL;
import static io.btrace.core.Args.HELP;
import static io.btrace.core.Args.LIBS;
import static io.btrace.core.Args.NO_SERVER;
import static io.btrace.core.Args.OUTPUT;
import static io.btrace.core.Args.PROBES;
import static io.btrace.core.Args.PROBE_DESC_PATH;
import static io.btrace.core.Args.SCRIPT;
import static io.btrace.core.Args.SCRIPT_DIR;
import static io.btrace.core.Args.SCRIPT_OUTPUT_DIR;
import static io.btrace.core.Args.SCRIPT_OUTPUT_FILE;
import static io.btrace.core.Args.STARTUP_RETRANSFORM;
import static io.btrace.core.Args.STATSD;
import static io.btrace.core.Args.STDOUT;
import static io.btrace.core.Args.SYSTEM_CLASS_PATH;
import static io.btrace.core.Args.TELEMETRY;
import static io.btrace.core.Args.TRACK_RETRANSFORMS;
import static io.btrace.core.Args.TRUSTED;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.DebugSupport;
import io.btrace.core.Messages;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.StatusCommand;
import io.btrace.core.comm.WireIO;
import io.btrace.core.extensions.ExtensionConfigurator;
import io.btrace.core.extensions.ProbeConfiguration;
import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.ExtensionLoader;
import io.btrace.extension.impl.ExtensionBridgeImpl;
import io.btrace.instr.BTraceProbeFactory;
import io.btrace.instr.BTraceTransformer;
import io.btrace.instr.Constants;
import io.btrace.runtime.BTraceBootstrap;
import io.btrace.runtime.BTraceRuntimes;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.Socket;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main class for BTrace java.lang.instrument agent.
 *
 * @author A. Sundararajan
 */
@SuppressWarnings("RedundantThrows")
public final class Main {
  public static final int BTRACE_DEFAULT_PORT = 2020;
  private static final boolean AGENT_DEBUG = Boolean.getBoolean("btrace.agent.debug");
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
  private static volatile ExtensionLoader extensionLoader;
  private static volatile boolean serverRunning = true;
  private static ControlServer controlServer;
  // Track appended jars to avoid duplicate classpath entries
  private static final Set<Path> BOOT_ADDED = Collections.synchronizedSet(new LinkedHashSet<>());
  private static final Set<Path> SYSTEM_ADDED = Collections.synchronizedSet(new LinkedHashSet<>());
  // Hold strong references to JarFiles passed to appendToBootstrap/SystemClassLoaderSearch so
  // that GC cannot close their file descriptors before the JVM is done reading class bytes.
  private static final List<JarFile> BOOT_JARS = new ArrayList<>();
  private static final List<JarFile> SYSTEM_JARS = new ArrayList<>();

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  private static volatile String agentMode = "unknown";

  public static void premain(String args, Instrumentation inst) {
    agentMode = "premain";
    startAgent(args, inst);
  }

  public static void agentmain(String args, Instrumentation inst) {
    agentMode = "agentmain";
    startAgent(args, inst);
  }

  private static void startAgent(String args, Instrumentation inst) {
    try {
      main(args, inst);
    } catch (Exception e) {
      System.err.println("BTrace agent initialization failed: " + e.getMessage());
      throw new RuntimeException("BTrace agent initialization failed", e);
    }
  }

  private static synchronized void main(String args, Instrumentation inst) {
    if (AGENT_DEBUG) System.err.println("[BTrace Agent] Initialization started");
    if (Main.inst != null) {
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Agent already initialized, skipping");
      return;
    } else {
      Main.inst = inst;
    }

    // Running BTrace on Java < 17 is deprecated as of 3.0; warn exactly once per target JVM
    io.btrace.core.JavaVersionCheck.warnIfDeprecatedJvm();

    try {
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Loading arguments");
      loadArgs(args);
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Arguments loaded");
      boolean isDebug = Boolean.parseBoolean(argMap.get(DEBUG));
      // set the debug level based on cmdline config
      settings.setDebug(isDebug);
      DebugSupport.initLoggers(isDebug, log);

      // Load defaults from file-based permission policy first
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Loading permission policy");
      io.btrace.extension.PermissionPolicy.get().loadFromDefaults();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Permission policy loaded");
      // Then parse and apply agent args (which override file policy)
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Parsing arguments");
      parseArgs();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Arguments parsed");
      Telemetry.fireAsync(argMap.get(TELEMETRY), readBTraceVersion(), agentMode);
      // settings are all built-up; set the logging system properties accordingly
      DebugSupport.initLoggers(settings.isDebug(), log);

      Thread agentThread = null;
      if (!shouldStartServer(argMap)) {
        log.debug("noServer is true, server not started");
      } else {
        System.setProperty("btrace.wireio", String.valueOf(WireIO.VERSION));
        String scriptOutputFile = settings.getOutputFile();
        if (scriptOutputFile != null && !scriptOutputFile.isEmpty()) {
          System.setProperty("btrace.output", scriptOutputFile);
        }
        controlServer = ControlServer.open(argMap, "premain".equals(agentMode));
        serverRunning = true;
        installServerShutdownHook();
        ControlServer endpoint = controlServer;
        agentThread =
            new Thread(
                () -> {
                  BTraceRuntime.enter();
                  try {
                    runServer(endpoint);
                  } finally {
                    BTraceRuntime.leave();
                  }
                });
      }
      // set the fall-back instrumentation object to BTraceRuntime
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Setting up runtime");
      BTraceRuntime.instrumentation = inst;
      // force back-registration of BTraceRuntimeImpl in BTraceRuntime
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Initializing BTraceRuntimes");
      BTraceRuntimes.getDefault();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] BTraceRuntimes initialized");
      registerCoreOps();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Core DSL ops registered");
      // ensure runtime accessor is registered
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Registering runtime accessor");
      BTraceRuntimes.ensureAccessorRegistered();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Runtime accessor registered");
      // init BTraceRuntime
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Initializing unsafe");
      BTraceRuntime.initUnsafe();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Unsafe initialized");
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Adding class transformer");
      log.debug("Adding class transformer");
      inst.addTransformer(transformer, true);
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Transformer added");
      try {
        // the MethodHandleNatives must be instrumented to track start-end of indy linking to avoid
        // deadlocking
        if (AGENT_DEBUG) System.err.println("[BTrace Agent] Instrumenting MethodHandleNatives");
        Class<?> clz =
            ClassLoader.getSystemClassLoader().loadClass("java.lang.invoke.MethodHandleNatives");
        inst.retransformClasses(clz);
        if (AGENT_DEBUG) System.err.println("[BTrace Agent] MethodHandleNatives instrumented");
      } catch (Throwable t) {
        if (AGENT_DEBUG)
          System.err.println(
              "[BTrace Agent] Failed to instrument MethodHandleNatives: " + t.getMessage());
        log.debug("Failed to instrument MethodHandleNatives", t);
      }
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Starting scripts");
      int startedScripts = startScripts();
      // Thread.class retransform fires after startScripts() but before initExtensions()
      // deliberately: retransformation only rewrites bytecode; extension service calls
      // happen at invocation time (Thread.start events). No race with extension init.
      // Ensure early hooks (e.g., Thread.start) are applied even if Thread was already loaded
      if (startedScripts > 0) {
        try {
          inst.retransformClasses(Thread.class);
          if (log.isDebugEnabled()) {
            log.debug("Proactively retransformed java.lang.Thread after startup scripts");
          }
        } catch (Throwable t) {
          log.warn(
              "Thread.class retransform failed; early Thread hooks may be inactive: {}",
              t.toString());
        }
      }

      // initialize extension system after transformer is installed so early app code is not delayed
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Initializing extensions");
      initExtensions();
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
      if (AGENT_DEBUG)
        System.err.println(
            "[BTrace Agent] Initialization complete, " + startedScripts + " scripts started");
    } catch (Throwable t) {
      shutdownServer();
      // FATAL errors should always be printed
      System.err.println(
          "[BTrace Agent] FATAL: Initialization failed: "
              + t.getClass().getName()
              + ": "
              + t.getMessage());
      t.printStackTrace(System.err);
      log.error("Failed to initialize BTrace agent", t);
      throw new RuntimeException("BTrace agent initialization failed", t);
    } finally {
      log.debug("Agent init took: {}", (System.nanoTime() - ts) + "ns");
    }
  }

  static boolean shouldStartServer(ArgsMap args) {
    String configured = args.get(NO_SERVER);
    return configured != null
        ? !Boolean.parseBoolean(configured)
        : !(args.containsKey(SCRIPT) || args.containsKey(SCRIPT_DIR));
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

  /**
   * Initialize the extension system by discovering and loading extensions from configured extension
   * directories or embedded resources.
   */
  private static void initExtensions() {
    try {
      log.info("Initializing BTrace extension system");

      // Determine BTRACE_HOME from agent JAR location (null enables embedded-only mode)
      String btraceHome = getBTraceHome();
      if (log.isDebugEnabled()) {
        log.debug("BTRACE_HOME={}", btraceHome != null ? btraceHome : "(embedded-only mode)");
      }

      // Initialize extension loader with boot classloader as parent, configuration, and
      // instrumentation
      // Passing null btraceHome enables embedded-only mode (extensions from JAR resources)
      ClassLoader bootClassLoader = Main.class.getClassLoader();
      if (bootClassLoader == null) {
        bootClassLoader = ClassLoader.getSystemClassLoader();
      }
      extensionLoader =
          ExtensionLoader.initialize(btraceHome, bootClassLoader, inst, readBTraceVersion());

      // Initialize invokedynamic bridge for extensions after loader is ready
      ExtensionBridgeImpl.initialize(extensionLoader);

      // Load bundled probes from embedded extensions if configured
      loadBundledProbes();

    } catch (Exception e) {
      log.error("Failed to initialize extension system: {}", e.getMessage(), e);
    }
  }

  /** Load bundled probes from embedded extensions based on agent args or configurator. */
  private static void loadBundledProbes() {
    if (extensionLoader == null) {
      return;
    }

    String probesArg = argMap.get(PROBES);
    String outputArg = argMap.get(OUTPUT);

    if (probesArg != null && !probesArg.isEmpty()) {
      // Explicit probes= argument: honour it directly.
      log.info("Loading bundled probes: {}", probesArg);
      boolean toStdOut = "stdout".equalsIgnoreCase(outputArg);
      if (outputArg != null && !outputArg.isEmpty()) {
        if ("jfr".equalsIgnoreCase(outputArg)) {
          log.info("Bundled probes will output to JFR");
        } else if ("file".equalsIgnoreCase(outputArg)) {
          log.info("Bundled probes will output to file");
        } else if (toStdOut) {
          log.info("Bundled probes will output to stdout");
        }
      }
      List<String> requested = new ArrayList<>();
      for (String p : probesArg.split(",")) {
        requested.add(p.trim());
      }
      loadProbesFromNames(requested, toStdOut);
    } else {
      // No explicit probes= — ask each extension's configurator.
      AgentRuntimeEnvironment env = new AgentRuntimeEnvironment();
      Map<String, String> agentArgs = new LinkedHashMap<>();
      for (Map.Entry<String, String> e : argMap) {
        agentArgs.put(e.getKey(), e.getValue());
      }
      for (ExtensionDescriptorDTO ext : extensionLoader.getAvailableExtensions()) {
        if (!ext.isEmbedded()) {
          continue;
        }
        String configuratorClass = ext.getConfiguratorClass();
        if (configuratorClass == null) {
          continue;
        }
        ProbeConfiguration config = runConfigurator(ext, configuratorClass, env, agentArgs);
        if (config == null || !config.hasEnabledProbes()) {
          continue;
        }
        boolean toStdOut = config.getOutput() == ProbeConfiguration.Output.STDOUT;
        log.info(
            "Extension {} configurator enabled probes: {}", ext.getId(), config.getEnabledProbes());
        loadProbesFromNames(config.getEnabledProbes(), toStdOut);
      }
    }
  }

  /**
   * Instantiate and run the named {@link ExtensionConfigurator} class from the given extension,
   * returning its {@link ProbeConfiguration} or null on failure.
   */
  private static ProbeConfiguration runConfigurator(
      ExtensionDescriptorDTO ext,
      String configuratorClass,
      AgentRuntimeEnvironment env,
      Map<String, String> agentArgs) {
    try {
      ClassLoader cl = ext.getClassLoader();
      if (cl == null) {
        cl = Main.class.getClassLoader();
      }
      Class<?> cls = Class.forName(configuratorClass, true, cl);
      ExtensionConfigurator configurator =
          (ExtensionConfigurator) cls.getDeclaredConstructor().newInstance();
      return configurator.configure(env, agentArgs);
    } catch (Exception e) {
      log.warn(
          "Configurator {} for extension {} failed: {}",
          configuratorClass,
          ext.getId(),
          e.toString());
      return null;
    }
  }

  /** Load the named probes from embedded extensions. */
  private static void loadProbesFromNames(List<String> probeNames, boolean traceToStdOut) {
    for (ExtensionDescriptorDTO ext : extensionLoader.getAvailableExtensions()) {
      if (!ext.isEmbedded()) {
        continue;
      }
      List<String> bundledProbes = ext.getBundledProbes();
      if (bundledProbes.isEmpty()) {
        continue;
      }
      for (String probeName : probeNames) {
        if (!probeName.matches("[A-Za-z0-9_.]+")) {
          log.warn("Ignoring probe with invalid name: '{}'", probeName);
          continue;
        }
        for (String bundledProbe : bundledProbes) {
          if (bundledProbe.endsWith("." + probeName) || bundledProbe.equals(probeName)) {
            String path = ext.getResourceBasePath() + "/probes/" + probeName + ".class";
            if (loadEmbeddedProbe(path, probeName, traceToStdOut)) {
              log.info("Loaded bundled probe: {} from extension {}", probeName, ext.getId());
              break;
            }
          }
        }
      }
    }
  }

  /** Load an embedded probe from classpath resources. */
  private static boolean loadEmbeddedProbe(
      String resourcePath, String probeName, boolean traceToStdOut) {
    ClassLoader loader = Main.class.getClassLoader();
    try (InputStream is =
        loader != null
            ? loader.getResourceAsStream(resourcePath)
            : ClassLoader.getSystemResourceAsStream(resourcePath)) {
      if (is == null) {
        log.debug("Probe resource not found: {}", resourcePath);
        return false;
      }
      byte[] probeBytes = readAllBytes(is);
      File tmp = File.createTempFile("btrace-probe-", ".class");
      try {
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
          fos.write(probeBytes);
        }
        return loadBTraceScript(tmp.getAbsolutePath(), traceToStdOut);
      } finally {
        if (!tmp.delete()) {
          tmp.deleteOnExit();
        }
      }
    } catch (IOException e) {
      log.warn("Failed to load embedded probe {}: {}", probeName, e.getMessage());
      return false;
    }
  }

  private static byte[] readAllBytes(InputStream is) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] data = new byte[4096];
    int nRead;
    while ((nRead = is.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, nRead);
    }
    return buffer.toByteArray();
  }

  /**
   * Get BTRACE_HOME directory by locating the agent JAR.
   *
   * @return BTRACE_HOME path, or null if not found
   */
  private static String getBTraceHome() {
    try {
      // Get the agent JAR location
      String agentPath = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();

      // Agent is typically at BTRACE_HOME/libs/btrace-agent.jar or BTRACE_HOME/libs/btrace.jar
      File agentJar = new File(agentPath);
      String jarName = agentJar.getName();
      if (agentJar.exists()
          && (jarName.equals("btrace-agent.jar") || jarName.equals("btrace.jar"))) {
        File libsDir = agentJar.getParentFile();
        if (libsDir != null && libsDir.getName().equals("libs")) {
          File btraceHome = libsDir.getParentFile();
          if (btraceHome != null) {
            return btraceHome.getAbsolutePath();
          }
        }
      }

      // Try BTRACE_HOME environment variable
      String envHome = System.getenv("BTRACE_HOME");
      if (envHome != null && new File(envHome).exists()) {
        return envHome;
      }

    } catch (Exception e) {
      log.debug("Failed to determine BTRACE_HOME: {}", e.getMessage());
    }

    return null;
  }

  private static String readBTraceVersion() {
    try {
      String agentPath = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      try (JarFile jar = new JarFile(new File(agentPath))) {
        Manifest mf = jar.getManifest();
        if (mf != null) {
          String v = mf.getMainAttributes().getValue("BTrace-Version");
          if (v != null && !v.isEmpty()) {
            return v;
          }
        }
      }
    } catch (Exception e) {
      log.debug("Could not read BTrace-Version from agent JAR manifest: {}", e.getMessage());
    }
    return "unknown";
  }

  /**
   * Get the extension loader instance.
   *
   * @return extension loader, or null if not initialized
   */
  public static ExtensionLoader getExtensionLoader() {
    return extensionLoader;
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

  private static void parseArgs() throws ClassNotFoundException {
    String p = argMap.get(HELP);
    if (p != null) {
      usage();
    }

    String libs = argMap.get(LIBS);
    if (libs != null && !libs.isEmpty()) {
      log.warn(
          "The 'libs' profile feature is deprecated and will be removed in a future release. "
              + "Prefer packaging integrations as BTrace extensions (API on bootstrap, impl isolated). "
              + "See docs/architecture/agent-manifest-libs.md for migration guidance.");
    }
    String config = argMap.get(CONFIG);
    processClasspaths(libs);
    loadDefaultArguments(config);

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
        case GRANT:
          {
            if (!p.isEmpty()) {
              settings.setGrantedPermissions(SharedSettings.parsePermissions(p));
              log.debug("granted permissions: {}", settings.getGrantedPermissions());
            }
            break;
          }
        case DENY:
          {
            if (!p.isEmpty()) {
              settings.setDeniedPermissions(SharedSettings.parsePermissions(p));
              log.debug("denied permissions: {}", settings.getDeniedPermissions());
            }
            break;
          }
        case GRANT_ALL:
          {
            if (!p.isEmpty()) {
              settings.setGrantAll(Boolean.parseBoolean(p));
              log.debug("grantAll: {}", settings.isGrantAll());
            }
            break;
          }
        case ALLOW_EXTENSIONS:
          {
            if (!p.isEmpty()) {
              io.btrace.extension.PermissionPolicy.get().setAllowExtensionsCsv(p);
              log.debug("allowExtensions: {}", p);
            }
            break;
          }
        case DENY_EXTENSIONS:
          {
            if (!p.isEmpty()) {
              io.btrace.extension.PermissionPolicy.get().setDenyExtensionsCsv(p);
              log.debug("denyExtensions: {}", p);
            }
            break;
          }
        case ALLOW_PRIVILEGED:
          {
            if (!p.isEmpty()) {
              io.btrace.extension.PermissionPolicy.get()
                  .setAllowPrivileged(Boolean.parseBoolean(p));
              log.debug("allowPrivileged: {}", p);
            }
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

  private static void processClasspaths(String libs) throws ClassNotFoundException {
    // Experimental: prefer manifest-driven libs when enabled
    boolean useManifestLibs = Boolean.getBoolean("btrace.feature.manifestLibs");
    boolean hasLegacyLibs = libs != null && !libs.isEmpty();
    boolean hasManifestLibs = useManifestLibs;
    if (hasManifestLibs && hasLegacyLibs) {
      log.warn(
          "Both libs= and manifest-attributes are present; libs= is deprecated and will be removed in N+2. Prefer manifest-based declaration.");
    }
    if (useManifestLibs) {
      if (log.isDebugEnabled()) log.debug("Using manifest-driven libs resolution");
      AgentManifestLibs.ResolvedLibs libsFromMf = AgentManifestLibs.resolveFromManifest(Main.class);
      // Append manifest-declared boot jars
      for (Path p : libsFromMf.bootJars) {
        appendBootJar(p);
      }
      // Append manifest-declared system jars
      for (Path p : libsFromMf.systemJars) {
        appendSystemJar(p);
      }
    }
    // Try to find JAR via Loader.class (unmasked bootstrap class)
    // Main.class won't work because it's loaded from .classdata
    String bootPath = null;
    Class<?> loaderClass = Class.forName("io.btrace.boot.Loader");
    URL loaderResource = loaderClass.getResource("Loader.class");
    if (loaderResource != null) {
      bootPath = loaderResource.toString();
      if (bootPath.startsWith("jar:file:")) {
        // Extract JAR path from
        // jar:file:/path/to/btrace.jar!/org/openjdk/btrace/boot/Loader.class
        bootPath = bootPath.substring("jar:file:".length());
        int idx = bootPath.indexOf("!");
        if (idx > -1) {
          bootPath = bootPath.substring(0, idx);
        }
      }
    }

    String bootClassPath = argMap.get(BOOT_CLASS_PATH);
    if (bootClassPath == null && bootPath != null) {
      bootClassPath = bootPath;
    } else if (bootClassPath != null && bootPath != null) {
      if (".".equals(bootClassPath)) {
        bootClassPath = bootPath;
      } else {
        bootClassPath = bootPath + File.pathSeparator + bootClassPath;
      }
    }

    if (bootClassPath == null || bootClassPath.isEmpty()) {
      log.debug("No boot classpath configured; skipping bootstrap jar setup");
    } else {
      log.debug("Bootstrap ClassPath: {}", bootClassPath);
      StringTokenizer tokenizer = new StringTokenizer(bootClassPath, File.pathSeparator);
      while (tokenizer.hasMoreTokens()) {
        String path = tokenizer.nextToken();
        File f = new File(path);
        if (!f.exists()) {
          log.debug("BTrace bootstrap classpath resource [{}] does not exist", path);
        } else {
          if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
            appendBootJar(f.toPath());
          } else {
            log.debug("ignoring boot classpath element '{}' - only jar files allowed", path);
          }
        }
      }
    }

    String systemClassPath = argMap.get(SYSTEM_CLASS_PATH);
    if (systemClassPath != null) {
      log.debug("System ClassPath: {}", systemClassPath);
      StringTokenizer tokenizer = new StringTokenizer(systemClassPath, File.pathSeparator);
      while (tokenizer.hasMoreTokens()) {
        String path = tokenizer.nextToken();
        File f = new File(path);
        if (!f.exists()) {
          log.debug("BTrace system classpath resource [{}] does not exist.", path);
        } else {
          if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
            appendSystemJar(f.toPath());
          } else {
            log.debug("ignoring system classpath element '{}' - only jar files allowed", path);
          }
        }
      }
    }

    // Skip preconfigured libs only when both the test knob is set and manifest-libs feature is
    // enabled
    boolean skipPreconfLibs = Boolean.getBoolean("btrace.test.skipLibs");
    if (!(skipPreconfLibs && useManifestLibs)) {
      addPreconfLibs(libs);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "Skipping addPreconfLibs due to btrace.test.skipLibs=true and manifestLibs enabled");
    }

    // Explicit, last-resort escape hatch: append a single jar to system CL
    String sysAppend = System.getProperty("btrace.system.appendJar");
    if (sysAppend != null && !sysAppend.isEmpty()) {
      if (!settings.isTrusted()) {
        log.warn(
            "Ignoring btrace.system.appendJar: requires trusted=true. "
                + "Use extensions or migration patterns instead.");
      } else {
        try {
          Path p = Paths.get(sysAppend).toAbsolutePath().normalize();
          if (!Files.exists(p) || !p.getFileName().toString().toLowerCase().endsWith(".jar")) {
            log.warn("btrace.system.appendJar invalid or not found: {}", p);
          } else {
            boolean allowExternal = Boolean.getBoolean("btrace.allowExternalLibs");
            String homeStr = getBTraceHome();
            if (!allowExternal && homeStr != null) {
              try {
                Path home = Paths.get(homeStr).toRealPath();
                Path rp = p.toRealPath();
                if (!rp.startsWith(home)) {
                  log.warn(
                      "Rejecting btrace.system.appendJar outside BTRACE_HOME ({}): {}. "
                          + "Override with -Dbtrace.allowExternalLibs=true",
                      home,
                      rp);
                } else {
                  appendSystemJar(p);
                }
              } catch (Exception e) {
                // Fallback to basic check if realpath resolution fails
                if (!p.startsWith(homeStr)) {
                  log.warn(
                      "Rejecting btrace.system.appendJar outside BTRACE_HOME ({}): {}. "
                          + "Override with -Dbtrace.allowExternalLibs=true",
                      homeStr,
                      p);
                } else {
                  appendSystemJar(p);
                }
              }
            } else {
              if (!allowExternal) {
                log.warn(
                    "Cannot determine BTRACE_HOME; proceeding to append system jar (btrace.system.appendJar): {}",
                    p);
              }
              appendSystemJar(p);
            }
          }
        } catch (Exception e) {
          log.warn("Failed to process btrace.system.appendJar: {}", e.toString());
        }
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("Effective bootstrap jars: {}", BOOT_ADDED);
      log.debug("Effective system jars: {}", SYSTEM_ADDED);
    }
  }

  // Resolved once; null means pre-JPMS JarFile constructor should be used.
  private static final java.lang.reflect.Constructor<?> JPMS_JAR_CTOR = resolveJpmsJarCtor();

  @SuppressWarnings("JavaReflectionMemberAccess")
  private static java.lang.reflect.Constructor<?> resolveJpmsJarCtor() {
    try {
      Class.forName("java.lang.Module");
      Class<Runtime> rtClass = Runtime.class;
      java.lang.reflect.Method m = rtClass.getMethod("version");
      Object version = m.invoke(null);
      return JarFile.class.getConstructor(File.class, boolean.class, int.class, version.getClass());
    } catch (Exception ignore) {
      return null;
    }
  }

  @SuppressWarnings("JavaReflectionMemberAccess")
  private static JarFile asJarFile(File path) throws IOException {
    if (JPMS_JAR_CTOR != null) {
      try {
        Class<Runtime> rtClass = Runtime.class;
        Object version = rtClass.getMethod("version").invoke(null);
        return (JarFile) JPMS_JAR_CTOR.newInstance(path, true, ZipFile.OPEN_READ, version);
      } catch (Exception ignore) {
      }
    }
    return new JarFile(path);
  }

  // Centralized helpers with dedup and logging
  private static void appendBootJar(Path jarPath) {
    try {
      Path rp = jarPath.toAbsolutePath().normalize();
      if (!Files.exists(rp)) {
        if (log.isDebugEnabled()) log.debug("Bootstrap jar does not exist: {}", rp);
        return;
      }
      // Synchronize the check-then-act so two threads cannot both pass the "not yet added"
      // check and both call appendToBootstrapClassLoaderSearch for the same jar.
      synchronized (BOOT_ADDED) {
        if (BOOT_ADDED.contains(rp)) {
          if (log.isDebugEnabled()) log.debug("Skipping duplicate bootstrap jar: {}", rp);
          return;
        }
        JarFile jf = asJarFile(rp.toFile());
        inst.appendToBootstrapClassLoaderSearch(jf);
        BOOT_ADDED.add(rp);
        BOOT_JARS.add(jf);
      }
      if (log.isDebugEnabled()) log.debug("Added to bootstrap: {}", rp);
    } catch (IOException e) {
      log.warn("Failed to append bootstrap jar {}: {}", jarPath, e.toString());
    }
  }

  private static void appendSystemJar(Path jarPath) {
    try {
      Path rp = jarPath.toAbsolutePath().normalize();
      if (!Files.exists(rp)) {
        if (log.isDebugEnabled()) log.debug("System jar does not exist: {}", rp);
        return;
      }
      synchronized (SYSTEM_ADDED) {
        if (SYSTEM_ADDED.contains(rp)) {
          if (log.isDebugEnabled()) log.debug("Skipping duplicate system jar: {}", rp);
          return;
        }
        JarFile jf = asJarFile(rp.toFile());
        inst.appendToSystemClassLoaderSearch(jf);
        SYSTEM_ADDED.add(rp);
        SYSTEM_JARS.add(jf);
      }
      if (log.isDebugEnabled()) log.debug("Added to system: {}", rp);
    } catch (IOException e) {
      log.warn("Failed to append system jar {}: {}", jarPath, e.toString());
    }
  }

  private static void addPreconfLibs(String libs) {
    ClassLoader cl = Main.class.getClassLoader();
    String resourceName = Main.class.getName().replace('.', '/') + ".class";
    // Handle bootstrap classloader case (returns null) by using system classloader
    URL u =
        (cl != null) ? cl.getResource(resourceName) : ClassLoader.getSystemResource(resourceName);
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
                  appendBootJar(file);
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
                  appendSystemJar(file);
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
    if (!BTraceProbeFactory.canLoad(filePath)) return false;

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
      log.warn("Failed to load BTrace script {}", filePath, re);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  // -- Internals only below this point
  @SuppressWarnings("InfiniteLoopStatement")
  private static void runServer(ControlServer endpoint) {
    if (log.isDebugEnabled()) {
      log.debug(
          "starting server at {}:{} with authentication {}",
          endpoint.getAddress().getHostAddress(),
          endpoint.getPort(),
          endpoint.isAuthenticationRequired() ? "required" : "disabled");
    }
    while (serverRunning) {
      Socket sock = null;
      boolean handedOff = false;
      byte[] authenticationToken = null;
      try {
        log.debug("waiting for clients");
        sock = endpoint.accept();
        if (log.isDebugEnabled()) {
          log.debug("client accepted from {}", sock.getRemoteSocketAddress());
        }
        authenticationToken = endpoint.copyAuthenticationToken();
        ClientContext ctx = new ClientContext(inst, transformer, argMap, settings);
        Client client =
            RemoteClient.getClient(ctx, sock, authenticationToken, Main::handleNewClient);
        handedOff = client != null;
      } catch (RuntimeException | IOException re) {
        if (serverRunning) {
          log.warn("BTrace server connection rejected: {}", re.getMessage());
        }
      } finally {
        if (authenticationToken != null) {
          Arrays.fill(authenticationToken, (byte) 0);
        }
        if (!handedOff && sock != null) {
          try {
            sock.close();
          } catch (IOException ignored) {
          }
        }
      }
    }
  }

  private static void installServerShutdownHook() {
    Runtime.getRuntime()
        .addShutdownHook(new Thread(Main::shutdownServer, "BTrace Server Shutdown"));
  }

  private static synchronized void shutdownServer() {
    serverRunning = false;
    ControlServer endpoint = controlServer;
    controlServer = null;
    if (endpoint != null && !endpoint.isClosed()) {
      try {
        endpoint.close();
        log.debug("BTrace server closed");
      } catch (IOException e) {
        log.debug("Error closing BTrace server", e);
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
                client.getRuntime().sendCommand(new StatusCommand((byte) 1));
              }
            } catch (UnmodifiableClassException uce) {
              log.debug("BTrace class retransformation failed", uce);
              client.getRuntime().sendCommand(new ErrorCommand(uce));
              client.getRuntime().sendCommand(new StatusCommand(-1 * StatusCommand.STATUS_FLAG));
            } finally {
              if (entered) {
                BTraceRuntime.leave();
              }
            }
          } catch (Throwable t) {
            log.warn("Unhandled exception in client handler", t);
          }
        });
  }

  /**
   * Register all public static methods from {@link io.btrace.BTrace} into {@link BTraceBootstrap}.
   * Called at agent startup before any probe fires so that INVOKEDYNAMIC bootstrap lookups succeed.
   */
  static void registerCoreOps() {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      // --- Output ---
      reg(lookup, "print", MethodType.methodType(void.class, String.class));
      reg(lookup, "println", MethodType.methodType(void.class, String.class));
      reg(lookup, "println", MethodType.methodType(void.class));
      reg(lookup, "printf", MethodType.methodType(void.class, String.class, Object[].class));
      // --- Strings ---
      reg(lookup, "str", MethodType.methodType(String.class, Object.class));
      reg(lookup, "str", MethodType.methodType(String.class, boolean.class));
      reg(lookup, "str", MethodType.methodType(String.class, int.class));
      reg(lookup, "str", MethodType.methodType(String.class, long.class));
      reg(lookup, "str", MethodType.methodType(String.class, float.class));
      reg(lookup, "str", MethodType.methodType(String.class, double.class));
      reg(lookup, "concat", MethodType.methodType(String.class, String.class, String.class));
      reg(
          lookup,
          "substr",
          MethodType.methodType(String.class, String.class, int.class, int.class));
      reg(lookup, "matches", MethodType.methodType(boolean.class, String.class, String.class));
      reg(lookup, "startsWith", MethodType.methodType(boolean.class, String.class, String.class));
      reg(lookup, "endsWith", MethodType.methodType(boolean.class, String.class, String.class));
      reg(lookup, "length", MethodType.methodType(int.class, String.class));
      // --- Numbers ---
      reg(lookup, "abs", MethodType.methodType(long.class, long.class));
      reg(lookup, "abs", MethodType.methodType(double.class, double.class));
      reg(lookup, "min", MethodType.methodType(long.class, long.class, long.class));
      reg(lookup, "max", MethodType.methodType(long.class, long.class, long.class));
      reg(lookup, "min", MethodType.methodType(double.class, double.class, double.class));
      reg(lookup, "max", MethodType.methodType(double.class, double.class, double.class));
      // --- Time ---
      reg(lookup, "timestamp", MethodType.methodType(long.class));
      reg(lookup, "monotonic", MethodType.methodType(long.class));
      // --- Threads ---
      reg(lookup, "currentThread", MethodType.methodType(Thread.class));
      reg(lookup, "threadName", MethodType.methodType(String.class, Thread.class));
      reg(lookup, "threadId", MethodType.methodType(long.class, Thread.class));
      // --- Stack ---
      reg(lookup, "stackTrace", MethodType.methodType(String.class));
      reg(lookup, "printStack", MethodType.methodType(void.class));
      reg(lookup, "stackDepth", MethodType.methodType(int.class));
      // --- Object ---
      reg(lookup, "className", MethodType.methodType(String.class, Object.class));
      reg(lookup, "identity", MethodType.methodType(int.class, Object.class));
      reg(lookup, "size", MethodType.methodType(long.class, Object.class));
      // --- Control ---
      reg(lookup, "exit", MethodType.methodType(void.class, int.class));
    } catch (Exception e) {
      throw new RuntimeException("BTrace core op registration failed", e);
    }
  }

  private static void reg(MethodHandles.Lookup lookup, String name, MethodType type)
      throws NoSuchMethodException, IllegalAccessException {
    BTraceBootstrap.registerCoreOp(
        name, type, lookup.findStatic(io.btrace.BTrace.class, name, type));
  }

  private static void error(String msg) {
    System.err.println("btrace ERROR: " + msg);
  }

  private static boolean isDebug() {
    return settings.isDebug();
  }
}
