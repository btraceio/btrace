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
import static io.btrace.core.Args.AUTH_TOKEN_FILE;
import static io.btrace.core.Args.BIND_ADDRESS;
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
import static io.btrace.core.Args.PORT;
import static io.btrace.core.Args.PROBES;
import static io.btrace.core.Args.PROBE_DESC_PATH;
import static io.btrace.core.Args.PROBE_MAX_RATE;
import static io.btrace.core.Args.PROBE_TTL;
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
import io.btrace.core.Function;
import io.btrace.core.JavaVersionCheck;
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
import io.btrace.instr.ProbeBudgets;
import io.btrace.runtime.BTraceBootstrap;
import io.btrace.runtime.BTraceRuntimeAccess;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
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
  // Agent argument keys whose value is a comma-separated list. The javaagent argument string is
  // itself comma-separated, so `grant=NETWORK,THREADS` arrives as the pair `grant=NETWORK` followed
  // by the bare token `THREADS`; parseAgentArgs re-attaches such tokens to the preceding list.
  private static final Set<String> LIST_VALUED_KEYS =
      Collections.unmodifiableSet(
          new HashSet<>(Arrays.asList(GRANT, DENY, ALLOW_EXTENSIONS, DENY_EXTENSIONS, PROBES)));
  private static final Pattern BUNDLED_PROBE_NAME =
      Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
  private static final Set<String> JAVA_KEYWORDS =
      Collections.unmodifiableSet(
          new LinkedHashSet<>(
              Arrays.asList(
                  "_",
                  "abstract",
                  "assert",
                  "boolean",
                  "break",
                  "byte",
                  "case",
                  "catch",
                  "char",
                  "class",
                  "const",
                  "continue",
                  "default",
                  "do",
                  "double",
                  "else",
                  "enum",
                  "extends",
                  "false",
                  "final",
                  "finally",
                  "float",
                  "for",
                  "goto",
                  "if",
                  "implements",
                  "import",
                  "instanceof",
                  "int",
                  "interface",
                  "long",
                  "native",
                  "new",
                  "null",
                  "package",
                  "private",
                  "protected",
                  "public",
                  "return",
                  "short",
                  "static",
                  "strictfp",
                  "super",
                  "switch",
                  "synchronized",
                  "this",
                  "throw",
                  "throws",
                  "transient",
                  "true",
                  "try",
                  "void",
                  "volatile",
                  "while")));
  private static final String BUNDLED_PROBE_ROOT = "META-INF/btrace-probes/";
  private static final String TRUSTED_PROPERTY = "btrace.trusted";
  private static final SharedSettings settings = SharedSettings.GLOBAL;
  private static final BTraceTransformer transformer =
      new BTraceTransformer(new DebugSupport(settings));
  // ClassFile API's StackMapGenerator can exhaust the default stack depth when processing
  // complex methods in JDK 26+ (class-file major >= 70). Use 4 MB on those JVMs only; on
  // older JVMs keep the default (0 = JVM-chosen) to avoid any OS-level stack-allocation
  // overhead that was intermittently delaying test startup on JDK 8/11/21 CI machines.
  static final long QUEUE_PROCESSOR_STACK_SIZE = resolveQueueProcessorStackSize();

  static long resolveQueueProcessorStackSize() {
    return resolveQueueProcessorStackSize(System.getProperty("java.class.version", "52.0"));
  }

  // Visible for testing.
  static long resolveQueueProcessorStackSize(String classVersion) {
    try {
      int dot = classVersion.indexOf('.');
      int major = Integer.parseInt(dot >= 0 ? classVersion.substring(0, dot) : classVersion);
      return major >= 70 ? 4L * 1024 * 1024 : 0L;
    } catch (RuntimeException ignored) {
      return 0L;
    }
  }

  // #BTRACE-42: Non-daemon thread prevents traced application from exiting
  // Deliberately an anonymous class, not a lambda: this static field initializer runs in
  // Main's <clinit>, unconditionally, before -javaagent premain()'s body even starts -- see
  // io.btrace.instr.BootstrapPathIndyFreedomTest and the investigation doc it references.
  private static final ThreadFactory qProcessorThreadFactory =
      new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          Thread result =
              new Thread(null, r, "BTrace Command Queue Processor", QUEUE_PROCESSOR_STACK_SIZE);
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
  private static volatile ExtensionLoader extensionLoader;
  private static volatile boolean serverRunning = true;
  private static volatile ControlServer controlServer;

  /**
   * Serializes this agent's initialization against a later agent load handed over to it (see {@link
   * #startServerOnAttach}); which load gets to initialize at all is decided by {@code
   * BTraceRuntimeAccess.claimAgent}. Deliberately an object lock and not the {@code Main} class
   * monitor: holding {@code Main.class} for the whole of initialization made the "BTrace Server
   * Shutdown" hook block on {@code Main.class} when a target exits while the agent is still
   * initializing -- the hook could never run, never close the control server, and the JVM hung in
   * exit with nothing to report (issue #932).
   */
  private static final Object INIT_LOCK = new Object();

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

  private static void main(String args, Instrumentation inst) {
    synchronized (INIT_LOCK) {
      mainLocked(args, inst);
    }
  }

  /** Runs {@code action} holding the same lock agent initialization holds. Tests only. */
  static void runUnderInitLockForTesting(Runnable action) {
    synchronized (INIT_LOCK) {
      action.run();
    }
  }

  static final String ALREADY_LOADED =
      "BTrace agent is already loaded in this JVM; this load and its arguments are ignored";

  static final String SERVER_STARTED_ON_ATTACH =
      "BTrace agent is already loaded in this JVM; its command server was started for this"
          + " attach, every other argument of this load is ignored";

  private static final String SERVER_NOT_STARTED =
      "BTrace agent is already loaded in this JVM; its command server was not started: ";

  // Written under INIT_LOCK.
  private static boolean initialized;
  private static boolean runtimeHooksInstalled;
  private static boolean budgetValidAtInit;

  // What a later agent load in this JVM calls instead of initializing an agent of its own. An
  // anonymous class, not a lambda: Main's static initializer runs on the premain path.
  private static final UnaryOperator<String> AGENT_CONTROL =
      new UnaryOperator<String>() {
        @Override
        public String apply(String args) {
          return startServerOnAttach(args);
        }
      };

  /**
   * Handles a later agent load on behalf of this, already loaded, agent. If this agent has no
   * command server, because it was launched without one, the server is started on the port and bind
   * address of the later load. Nothing else of that load is used: this agent's settings, startup
   * scripts and probe budget stay what they are. The clients of that server get those settings, a
   * launch-time {@code trusted=true} included, so the server of an agent loaded at JVM launch
   * requires the authentication token, as it would had it been started at launch.
   *
   * @return the line to print in the target
   */
  static String startServerOnAttach(String args) {
    // Also waits for an initialization that is still running.
    synchronized (INIT_LOCK) {
      if (!initialized) {
        // The lock is held, so initialization is not still running: it failed. Once this agent
        // has installed itself into the shared runtime classes no other load can replace it;
        // before that it gave the JVM back, and this load only asked a moment too early.
        return SERVER_NOT_STARTED
            + (runtimeHooksInstalled
                ? "the agent failed to initialize; restart the JVM to use BTrace in it"
                : "the agent failed to initialize; load the agent again");
      }
      if (controlServer != null) {
        return ALREADY_LOADED;
      }
      if (!budgetValidAtInit) {
        return SERVER_NOT_STARTED + "the probe budget set at launch is invalid";
      }
      ControlServer endpoint;
      try {
        endpoint =
            ControlServer.open(
                attachEndpointArgs(parseAgentArgs(args), argMap), "premain".equals(agentMode));
      } catch (IOException | RuntimeException e) {
        return SERVER_NOT_STARTED + (e.getMessage() != null ? e.getMessage() : e.toString());
      }
      return serveOnAttach(endpoint, serverThread(endpoint));
    }
  }

  /**
   * Makes {@code endpoint} this agent's command server and starts {@code agentThread} to serve it.
   * The caller holds {@code INIT_LOCK}.
   *
   * @return the line to print in the target
   */
  static String serveOnAttach(ControlServer endpoint, Thread agentThread) {
    System.setProperty("btrace.wireio", String.valueOf(WireIO.VERSION));
    controlServer = endpoint;
    serverRunning = true;
    agentThread.setDaemon(true);
    BTraceRuntime.enter();
    try {
      installServerShutdownHook();
      agentThread.start();
    } catch (Throwable t) {
      // Nothing accepts on the endpoint: close it and take back what it published.
      shutdownServer();
      return SERVER_NOT_STARTED + t;
    } finally {
      BTraceRuntime.leave();
    }
    return SERVER_STARTED_ON_ATTACH;
  }

  /**
   * The arguments of a command server started for a later agent load: where it listens comes from
   * that load, the token file from the arguments this agent was loaded with.
   */
  static ArgsMap attachEndpointArgs(ArgsMap laterLoad, ArgsMap ownLoad) {
    ArgsMap endpoint = new ArgsMap();
    copyArgument(laterLoad, endpoint, PORT);
    copyArgument(laterLoad, endpoint, BIND_ADDRESS);
    copyArgument(ownLoad, endpoint, AUTH_TOKEN_FILE);
    return endpoint;
  }

  private static void copyArgument(ArgsMap from, ArgsMap to, String key) {
    String value = from != null ? from.get(key) : null;
    if (value != null) {
      to.put(key, value);
    }
  }

  private static Thread serverThread(final ControlServer endpoint) {
    return new Thread(
        new Runnable() {
          @Override
          public void run() {
            BTraceRuntime.enter();
            try {
              runServer(endpoint);
            } finally {
              BTraceRuntime.leave();
            }
          }
        });
  }

  // Anonymous class, not a lambda: used on the premain path.
  private static final Supplier<UnaryOperator<String>> AGENT_HOLDER =
      new Supplier<UnaryOperator<String>>() {
        @Override
        public UnaryOperator<String> get() {
          return BTraceRuntimeAccess.agentControl();
        }
      };

  /**
   * Claims the JVM for the agent load that {@code own} stands for, or hands the load over to the
   * agent that holds the JVM already.
   *
   * @param holder looks up the control of the agent holding the JVM
   * @return {@code null} when this load holds the JVM now and must initialize; otherwise the line
   *     to print in the target, and this load must do nothing more
   */
  static String claimOrHandOver(
      String args, UnaryOperator<String> own, Supplier<UnaryOperator<String>> holder) {
    while (!BTraceRuntimeAccess.claimAgent(own)) {
      // Every agent load gets its own class loader, so this is a different Main than the one
      // that loaded first. That one decides what this load may still do.
      UnaryOperator<String> loaded = holder.get();
      if (loaded == null) {
        // The holder failed to initialize and gave the JVM back in between: claim it again.
        continue;
      }
      try {
        return loaded.apply(args);
      } catch (Throwable t) {
        return SERVER_NOT_STARTED + t;
      }
    }
    return null;
  }

  private static void mainLocked(String args, Instrumentation inst) {
    if (AGENT_DEBUG) System.err.println("[BTrace Agent] Initialization started");
    String handedOver = claimOrHandOver(args, AGENT_CONTROL, AGENT_HOLDER);
    if (handedOver != null) {
      System.err.println("[BTrace Agent] " + handedOver);
      return;
    }
    // The settings object is shared by every agent load in this JVM.
    SharedSettings settingsBefore = newClientSettings(settings);
    try {
      Main.inst = inst;

      // Running BTrace on Java < 17 is deprecated as of 3.0; warn exactly once per target JVM
      JavaVersionCheck.warnIfDeprecatedJvm();

      // Read the operator's budget properties before any agent argument can write system
      // properties.
      BudgetLimits.Snapshot budgetSnapshot = BudgetLimits.snapshot(System.getProperties());
      // Under dynamic attach an operator budget property, valid or not, always ends in trusted mode
      // being refused. Known before the arguments are parsed, so that nothing they do is trusted.
      attachTrustBarred = !"premain".equals(agentMode) && budgetSnapshot.present();
      budgetPropertySet = budgetSnapshot.present();
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

      // Budget enforcement must be on before the command server accepts clients and before
      // startup scripts load, so that no handler is resolved uncounted.
      boolean budgetValid = true;
      try {
        BudgetLimits limits =
            BudgetLimits.resolve(
                budgetSnapshot, argMap, "premain".equals(agentMode), BUDGET_REPORTER);
        if (limits.any()) {
          ProbeBudgets.setRateLimited(limits.maxRatePerSecond() > 0);
          BudgetGovernor.start(limits);
        }
        // A trusted flag seen here is the operator's launch-time grant: under dynamic attach with
        // a budget property the trusted argument is not applied at all.
        if (BudgetLimits.refusesClientTrust(limits, settings.isTrusted())) {
          Client.refuseTrustedMode(true);
          if (trustRequestedAndRefused) {
            BUDGET_REPORTER.accept(
                "ignoring agent argument trusted=true, every probe is verified: "
                    + BudgetLimits.TRUSTED_REFUSED);
          }
        }
      } catch (InvalidAgentOptionException e) {
        // Handled like a misconfigured command server: name the option on one line and run
        // without a command socket. The operator's startup scripts still run at launch; under
        // dynamic attach nothing is loaded and nothing may run unverified.
        budgetValid = false;
        String message = e.getMessage() + "; the command server was not started";
        if (!loadsStartupProbes(false, agentMode)) {
          Client.refuseTrustedMode(true);
          message += "; attach-time probes were not loaded";
        }
        System.err.println("[BTrace Agent] " + message);
        log.error(message);
      }
      budgetValidAtInit = budgetValid;
      boolean loadProbes = loadsStartupProbes(budgetValid, agentMode);

      Thread agentThread = null;
      if (!budgetValid) {
        log.debug("invalid probe budget, server not started");
      } else if (!shouldStartServer(argMap)) {
        log.debug("noServer is true, server not started");
      } else {
        System.setProperty("btrace.wireio", String.valueOf(WireIO.VERSION));
        String scriptOutputFile = settings.getOutputFile();
        if (scriptOutputFile != null && !scriptOutputFile.isEmpty()) {
          System.setProperty("btrace.output", scriptOutputFile);
        }
        try {
          controlServer = ControlServer.open(argMap, "premain".equals(agentMode));
        } catch (InvalidAgentOptionException e) {
          // A misconfigured command server must not take the application down: name the option
          // on one line, run without a command socket, and still execute any startup scripts.
          String message = e.getMessage() + "; the command server was not started";
          System.err.println("[BTrace Agent] " + message);
          log.error(message);
        }
        if (controlServer != null) {
          serverRunning = true;
          installServerShutdownHook();
          agentThread = serverThread(controlServer);
        }
      }
      // From here on this instance installs itself into the runtime classes all agent loads
      // share, so a failure no longer leaves the JVM free for another load.
      runtimeHooksInstalled = true;
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
      // Force io.btrace.instr.ClassFilter's static initializer to run to completion now, on
      // this single thread, before the transformer below can possibly be reached by any other
      // thread. ClassFilter.isSensitiveClass(...) is the first thing BTraceTransformer.transform()
      // calls for every class definition in the JVM (any thread) once installed -- including
      // classes loaded by the async Telemetry HTTP call (started above) and by this agent's own
      // connection-handling thread. ClassFilter's own <clinit> force-loads several ASM classes,
      // which are themselves subject to the same transformer once it's installed. If two threads
      // raced to trigger ClassFilter's <clinit> for the first time post-registration, the JVM's
      // class-initialization lock (JLS 12.4.2) plus the recursive ASM class loads inside it could
      // deadlock against another thread's classloader lock (observed: the telemetry thread stuck
      // in ClassFilter.<clinit>, the connection-handling thread stuck holding a classloader lock
      // waiting on the same class-init to finish). Doing it here, single-threaded, before
      // addTransformer, makes this race structurally impossible.
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Warming up ClassFilter");
      io.btrace.instr.ClassFilter.isSensitiveClass("");
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
      int startedScripts = loadProbes ? startScripts() : 0;
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
      initExtensions(loadProbes);
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
      initialized = true;
      if (AGENT_DEBUG)
        System.err.println(
            "[BTrace Agent] Initialization complete, " + startedScripts + " scripts started");
    } catch (Throwable t) {
      shutdownServer();
      if (!runtimeHooksInstalled) {
        // This instance has not installed itself into the shared runtime classes: take back the
        // settings its arguments wrote, a trusted=true included, and let a retry load an agent.
        // The system properties it set, the jars it appended to the class paths and a budget
        // governor thread it started stay behind; the governor has no client to act on.
        settings.from(settingsBefore);
        BTraceRuntimeAccess.releaseAgent(AGENT_CONTROL);
      }
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

  /**
   * {@code grant=}, {@code deny=} and {@code grantAll=} are parsed for compatibility with earlier
   * 3.0 builds, but nothing consults them: extension linking is decided by {@code
   * allowExtensions=}, {@code denyExtensions=}, {@code allowPrivileged=} and the permission policy
   * file. Say so instead of letting the option silently do nothing.
   */
  private static void warnIneffectivePermissionOption(String option) {
    log.warn(
        "{}= has no effect in BTrace 3.0; use allowExtensions=, denyExtensions= or"
            + " allowPrivileged= (or the permission policy file) to control extension linking",
        option);
  }

  static boolean shouldStartServer(ArgsMap args) {
    String configured = args.get(NO_SERVER);
    return configured != null
        ? !Boolean.parseBoolean(configured)
        : !(args.containsKey(SCRIPT) || args.containsKey(SCRIPT_DIR));
  }

  /**
   * Parses an on/off agent option. A bare option (empty value) means on.
   *
   * @return the flag value, or {@code null} when the value is neither {@code true} nor {@code
   *     false}
   */
  static Boolean parseFlag(String value) {
    if (value.isEmpty() || "true".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }
    return null;
  }

  private static boolean flagOption(String key, String value) {
    if (value == null) {
      return false;
    }
    Boolean flag = parseFlag(value);
    if (flag == null) {
      log.warn("Ignoring {}={}; expected true or false", key, value);
      return false;
    }
    return flag;
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
  private static void initExtensions(boolean loadProbes) {
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

      // Load standalone bundled probes or probes selected by embedded extension configurators.
      if (loadProbes) {
        loadBundledProbes();
      }

    } catch (BundledProbeException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to initialize extension system: {}", e.getMessage(), e);
    }
  }

  /** Load bundled probes from explicit agent arguments or embedded extension configurators. */
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
      loadStandaloneProbes(requested, toStdOut);
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
        loadExtensionProbes(ext, config.getEnabledProbes(), toStdOut);
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

  /** Load standalone probes staged by the fat-agent Gradle plugin. */
  static void loadStandaloneProbes(List<String> probeNames, boolean traceToStdOut) {
    for (String probeName : probeNames) {
      String resourcePath = bundledProbeResourcePath(probeName);
      ScriptLoad result = loadEmbeddedProbe(resourcePath, probeName, traceToStdOut);
      if (result != ScriptLoad.LOADED) {
        bundledProbeNotLoaded(
            probeName,
            result,
            new BundledProbeException(
                "Bundled probe '" + probeName + "' was not found or could not be loaded"));
        continue;
      }
      log.info("Loaded bundled probe: {}", probeName);
    }
  }

  /** Load probes selected by one embedded extension's configurator. */
  private static void loadExtensionProbes(
      ExtensionDescriptorDTO ext, List<String> probeNames, boolean traceToStdOut) {
    for (String probeName : probeNames) {
      String resolvedProbeName = resolveExtensionProbeName(ext, probeName);
      String path = bundledProbeResourcePath(resolvedProbeName);
      ScriptLoad result = loadEmbeddedProbe(path, resolvedProbeName, traceToStdOut);
      if (result != ScriptLoad.LOADED) {
        bundledProbeNotLoaded(
            resolvedProbeName,
            result,
            new BundledProbeException(
                "Bundled probe '"
                    + probeName
                    + "' from extension '"
                    + ext.getId()
                    + "' was not found or could not be loaded"));
        continue;
      }
      log.info("Loaded bundled probe: {} from extension {}", probeName, ext.getId());
    }
  }

  static String resolveExtensionProbeName(ExtensionDescriptorDTO ext, String probeName) {
    if (probeName != null && probeName.indexOf('.') == -1) {
      for (String bundledProbe : ext.getBundledProbes()) {
        int simpleNameStart = bundledProbe.lastIndexOf('.') + 1;
        if (bundledProbe.regionMatches(simpleNameStart, probeName, 0, probeName.length())
            && bundledProbe.length() - simpleNameStart == probeName.length()) {
          return bundledProbe;
        }
      }
    }
    return probeName;
  }

  static String bundledProbeResourcePath(String probeName) {
    validateBundledProbeName(probeName);
    return BUNDLED_PROBE_ROOT + probeName.replace('.', '/') + ".class";
  }

  static void validateBundledProbeName(String probeName) {
    boolean valid = probeName != null && BUNDLED_PROBE_NAME.matcher(probeName).matches();
    if (valid) {
      for (String part : probeName.split("\\.")) {
        if (JAVA_KEYWORDS.contains(part)) {
          valid = false;
          break;
        }
      }
    }
    if (!valid) {
      throw new BundledProbeException("Invalid bundled probe binary name: '" + probeName + "'");
    }
  }

  static final class BundledProbeException extends IllegalStateException {
    BundledProbeException(String message) {
      super(message);
    }
  }

  /** Load an embedded probe from classpath resources. */
  private static ScriptLoad loadEmbeddedProbe(
      String resourcePath, String probeName, boolean traceToStdOut) {
    ClassLoader loader = Main.class.getClassLoader();
    try (InputStream is =
        loader != null
            ? loader.getResourceAsStream(resourcePath)
            : ClassLoader.getSystemResourceAsStream(resourcePath)) {
      if (is == null) {
        log.debug("Probe resource not found: {}", resourcePath);
        return ScriptLoad.FAILED;
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
      return ScriptLoad.FAILED;
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

    boolean traceToStdOut = flagOption(STDOUT, argMap.get(STDOUT));
    if (log.isDebugEnabled()) {
      log.debug("stdout is {}", traceToStdOut);
    }

    List<String> scripts = locateScripts(argMap);
    for (String script : scripts) {
      if (loadBTraceScript(script, traceToStdOut) == ScriptLoad.LOADED) {
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
    argMap = parseAgentArgs(args);
  }

  /**
   * Parses the {@code -javaagent:btrace.jar=key=value,...} argument string. A bare token (no {@code
   * =}) directly after a list-valued key such as {@code grant} continues that key's comma-separated
   * list, so {@code grant=NETWORK,THREADS,grantAll=false} yields {@code grant=NETWORK,THREADS};
   * {@code help} stays a key of its own.
   */
  static ArgsMap parseAgentArgs(String args) {
    if (args == null) {
      args = "";
    }
    String[] pairs = KV_PATTERN.split(args);
    ArgsMap map = new ArgsMap();
    String listKey = null;
    for (String s : pairs) {
      int i = s.indexOf('=');
      String key, value = "";
      if (i != -1) {
        key = s.substring(0, i).trim();
        if (i + 1 < s.length()) {
          value = s.substring(i + 1).trim();
        }
      } else if (listKey != null && !HELP.equals(s.trim())) {
        map.put(listKey, map.get(listKey) + "," + s.trim());
        continue;
      } else {
        key = s;
      }
      map.put(key, value);
      listKey = LIST_VALUED_KEYS.contains(key) ? key : null;
    }
    return map;
  }

  private static void parseArgs() throws ClassNotFoundException {
    String p = argMap.get(HELP);
    if (p != null) {
      usage();
    }

    String libs = argMap.get(LIBS);
    if (libs != null && !libs.isEmpty()) {
      // Reported as an error, not a warning: this used to load jars and no longer does, so a
      // milder message would let a target start up looking healthy while every probe that relied
      // on those classes fails later with an unrelated-looking NoClassDefFoundError.
      log.error(
          "The 'libs' profile feature has been removed and has no effect: '{}' will not be loaded. "
              + "Package the integration as a BTrace extension instead. "
              + "See docs/architecture/migrating-from-libs-profiles.md for migration guidance.",
          libs);
    }
    // Read before processClasspaths, which consults it for the btrace.system.appendJar escape
    // hatch. The main argument loop below runs later and would leave the hatch permanently
    // unreachable.
    String trusted = argMap.get(TRUSTED);
    if (trusted != null && !trusted.isEmpty()) {
      applyTrustedArgument(Boolean.parseBoolean(trusted));
    }
    String config = argMap.get(CONFIG);
    processClasspaths();
    loadDefaultArguments(config);

    p = argMap.get(DEBUG);
    Boolean debugFlag = p != null ? parseFlag(p) : Boolean.FALSE;
    settings.setDebug(Boolean.TRUE.equals(debugFlag));
    DebugSupport.initLoggers(settings.isDebug(), log);
    if (debugFlag == null) {
      log.warn("Ignoring {}={}; expected true or false", DEBUG, p);
    }

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
              applyTrustedArgument(Boolean.parseBoolean(p));
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
              warnIneffectivePermissionOption(GRANT);
            }
            break;
          }
        case DENY:
          {
            if (!p.isEmpty()) {
              settings.setDeniedPermissions(SharedSettings.parsePermissions(p));
              warnIneffectivePermissionOption(DENY);
            }
            break;
          }
        case GRANT_ALL:
          {
            if (!p.isEmpty()) {
              settings.setGrantAll(Boolean.parseBoolean(p));
              warnIneffectivePermissionOption(GRANT_ALL);
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
              applyDollarProperty(key, p, BUDGET_REPORTER);
            }
          }
      }
    }
    String ineffective =
        ineffectiveTrustedPropertyWarning(
            System.getProperty(TRUSTED_PROPERTY), settings.isTrusted(), budgetRequested());
    if (ineffective != null) {
      log.warn(ineffective);
    }
  }

  /**
   * Trusted mode is enabled only by the {@code trusted=true} agent argument. A {@code
   * -Dbtrace.trusted} system property looks like the same switch and is read by nothing, so name
   * the argument that works instead of leaving a security-relevant setting silently ignored. One
   * agent load at a time initializes an agent in a target JVM, and a later load is handed over to
   * the agent already there, so this is reported once unless a failed first load is retried.
   *
   * @param budget whether a probe budget is requested for this JVM; the client's {@code -u} flag is
   *     then refused, so the warning does not recommend it
   * @return the warning, or {@code null} when the property is unset or trusted mode is on anyway
   */
  static String ineffectiveTrustedPropertyWarning(
      String propertyValue, boolean trusted, boolean budget) {
    if (propertyValue == null || trusted) {
      return null;
    }
    return "Ignoring system property "
        + TRUSTED_PROPERTY
        + "="
        + propertyValue
        + ": it has no effect. Trusted mode is enabled only by the trusted=true agent argument "
        + trustedHowTo(budget)
        + ".";
  }

  /** Where the {@code trusted=true} agent argument can come from, with or without a budget. */
  static String trustedHowTo(boolean budget) {
    return budget
        ? "(-javaagent:btrace.jar=trusted=true,... at JVM launch; this JVM has a probe budget, so"
            + " the client's -u flag is refused)"
        : "(-javaagent:btrace.jar=trusted=true,... or the client's -u flag)";
  }

  // Whether the operator set a budget property, valid or not; read before arguments are parsed.
  private static volatile boolean budgetPropertySet;

  /** Whether a probe budget is requested, by property or by a launch-time agent argument. */
  private static boolean budgetRequested() {
    if (budgetPropertySet) {
      return true;
    }
    return "premain".equals(agentMode)
        && (argMap.get(PROBE_TTL) != null || argMap.get(PROBE_MAX_RATE) != null);
  }

  /**
   * Applies a {@code $key=value} agent argument as a system property. The probe budget properties
   * are the operator's ceiling and cannot be written this way: the argument is skipped and
   * reported.
   */
  static void applyDollarProperty(String key, String value, Consumer<String> report) {
    String pKey = key.substring(1);
    if (BudgetLimits.TTL_PROPERTY.equals(pKey) || BudgetLimits.MAX_RATE_PROPERTY.equals(pKey)) {
      report.accept(
          "ignoring agent argument "
              + key
              + ": probe budget properties can only be set by the operator"
              + " with -D"
              + pKey);
      return;
    }
    System.setProperty(pKey, value);
    log.debug("Setting system property: {}={}", pKey, value);
  }

  // An anonymous class, not a method reference: this runs on the premain path, which must not
  // reach any invokedynamic call site (see BootstrapPathIndyFreedomTest).
  private static final Consumer<String> BUDGET_REPORTER =
      new Consumer<String>() {
        @Override
        public void accept(String line) {
          System.err.println("[BTrace Agent] " + line);
          log.warn(line);
        }
      };

  private static void processClasspaths() throws ClassNotFoundException {
    // Experimental: prefer manifest-driven libs when enabled
    boolean useManifestLibs = Boolean.getBoolean("btrace.feature.manifestLibs");
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

    // Explicit, last-resort escape hatch: append a single jar to system CL
    String sysAppend = System.getProperty("btrace.system.appendJar");
    if (sysAppend != null && !sysAppend.isEmpty()) {
      if (!settings.isTrusted()) {
        log.warn(
            "Ignoring btrace.system.appendJar: it requires the trusted=true agent argument {}; a"
                + " system property does not enable trusted mode."
                + " Use extensions or migration patterns instead.",
            trustedHowTo(budgetRequested()));
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

  private static ScriptLoad loadBTraceScript(String filePath, boolean traceToStdOut) {
    if (!BTraceProbeFactory.canLoad(filePath)) return ScriptLoad.FAILED;

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
        return ScriptLoad.FAILED;
      }

      SharedSettings clientSettings = newClientSettings(settings);
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
      Client client = new FileClient(ctx, traceScript, startupProbesExempt(agentMode));
      if (client.isInitialized()) {
        handleNewClient(client).get();
        return ScriptLoad.LOADED;
      }
      if (client.failedVerification()) {
        return ScriptLoad.UNVERIFIED;
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
    return ScriptLoad.FAILED;
  }

  /**
   * Whether probes the agent loads itself ({@code script=}, {@code scriptdir=}, bundled probes) are
   * exempt from probe budgets: only when the agent was loaded at JVM launch, where the operator
   * wrote those arguments. Under dynamic attach, or when the load mode is unknown, they are not.
   */
  static boolean startupProbesExempt(String mode) {
    return "premain".equals(mode);
  }

  /** How loading one probe the agent starts itself ended. */
  enum ScriptLoad {
    LOADED,
    /** The probe was read but did not pass verification. */
    UNVERIFIED,
    /** Missing, unreadable, or failed for any reason other than verification. */
    FAILED
  }

  /**
   * Whether a bundled probe that did not load is skipped instead of failing agent startup: only
   * when it failed verification while trusted mode was asked for in the agent arguments and refused
   * because of a probe budget. In trusted mode it would not have been verified at all. Every other
   * failure stays fatal.
   */
  static boolean skipsUnverifiedBundledProbe(
      ScriptLoad result, boolean trustRefused, boolean trustRequested) {
    return result == ScriptLoad.UNVERIFIED && trustRefused && trustRequested;
  }

  /**
   * Handles a bundled probe that did not load: skipped with one line when {@link
   * #skipsUnverifiedBundledProbe} allows it, otherwise {@code failure} is thrown.
   */
  private static void bundledProbeNotLoaded(
      String probeName, ScriptLoad result, BundledProbeException failure) {
    if (!skipsUnverifiedBundledProbe(
        result, Client.isTrustedModeRefused(), trustRequestedAndRefused)) {
      throw failure;
    }
    BUDGET_REPORTER.accept(
        "bundled probe '"
            + probeName
            + "' did not pass verification and was not loaded: "
            + BudgetLimits.TRUSTED_REFUSED);
  }

  /**
   * Whether the agent loads the probes it starts itself ({@code script=}, {@code scriptdir=},
   * bundled probes). Not when the operator's budget property is invalid and the agent was not
   * loaded at JVM launch: the attacher's probes would otherwise run with no limit at all. At launch
   * those probes are the operator's own and still run.
   */
  static boolean loadsStartupProbes(boolean budgetValid, String mode) {
    return budgetValid || "premain".equals(mode);
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
        ClientContext ctx = newRemoteClientContext();
        Client client =
            RemoteClient.getClient(
                ctx,
                sock,
                authenticationToken,
                new Function<Client, Future<?>>() {
                  @Override
                  public Future<?> apply(Client c) {
                    return handleNewClient(c);
                  }
                });
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
        .addShutdownHook(
            new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    shutdownServer();
                  }
                },
                "BTrace Server Shutdown"));
  }

  /**
   * Stops the control server. Deliberately not synchronized and deliberately not taking {@link
   * #INIT_LOCK}: this runs on the JVM's shutdown-hook thread and must always make progress, even
   * when agent initialization is still in flight and holds {@code INIT_LOCK}. Closing the server
   * also unblocks the accept loop parked in {@code accept()}; without it a target whose main exits
   * early can hang in exit (#932). The state it touches is {@code volatile}; the endpoint is
   * captured once, so a server that was never started, or is closed twice, is handled by the null
   * and closed-ness checks.
   */
  static void shutdownServer() {
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

  /**
   * Seeds a fresh per-client settings instance from the agent baseline.
   *
   * <p>Every client - local and remote alike - must get its own copy. A client's {@code SET_PARAMS}
   * command is applied by mutating its {@link ClientContext}'s settings in place, so handing out
   * the shared {@link SharedSettings#GLOBAL} instance lets one client's {@code debug}, {@code
   * dumpDir}, {@code outputFile}, granted permissions and - most importantly - {@code trusted} flag
   * leak into every other client and into the global transformer. {@code trusted} is deliberately
   * non-downgrading, so such a leak is permanent for the lifetime of the agent.
   *
   * @param baseline the agent-wide settings to seed from
   * @return a new settings instance, independent of {@code baseline}
   */
  static SharedSettings newClientSettings(SharedSettings baseline) {
    SharedSettings clientSettings = new SharedSettings();
    clientSettings.from(baseline);
    return clientSettings;
  }

  /**
   * Builds the context for a newly accepted remote connection.
   *
   * <p>Extracted from the accept loop so the settings isolation it depends on is reachable from a
   * unit test: inside the loop it sits under a {@code catch (RuntimeException | IOException)} that
   * would turn a re-introduced sharing bug into a per-connection warning rather than a test
   * failure.
   *
   * @return a context carrying settings private to this connection
   */
  static ClientContext newRemoteClientContext() {
    return new ClientContext(inst, transformer, argMap, newClientSettings(settings));
  }

  // Whether trusted=true in the agent arguments was refused because of a probe budget.
  private static volatile boolean trustRequestedAndRefused;

  // Whether attach-time agent arguments cannot turn trusted mode on: set before they are parsed.
  private static volatile boolean attachTrustBarred;

  /**
   * Applies the {@code trusted} agent argument. Under dynamic attach with an operator budget it is
   * not applied, whatever its value. A request to turn it on is recorded so it can be reported.
   */
  private static void applyTrustedArgument(boolean trusted) {
    applyTrustedArgument(settings, trusted, attachTrustBarred);
  }

  static void applyTrustedArgument(SharedSettings agentSettings, boolean trusted, boolean barred) {
    if (barred) {
      if (trusted) {
        trustRequestedAndRefused = true;
      }
      return;
    }
    agentSettings.setTrusted(trusted);
  }

  static boolean isTrustRequestedAndRefused() {
    return trustRequestedAndRefused;
  }

  /** Sets how far this agent's initialization got, as a failed one would leave it. Tests only. */
  static void setRuntimeHooksInstalledForTesting(boolean installed) {
    synchronized (INIT_LOCK) {
      runtimeHooksInstalled = installed;
    }
  }

  static void resetTrustRefusalForTesting() {
    trustRequestedAndRefused = false;
    attachTrustBarred = false;
    Client.refuseTrustedMode(false);
  }

  private static Future<?> handleNewClient(Client client) {
    // Deliberately an anonymous class, not a lambda: this method can run reachable from
    // -javaagent premain(), before the JVM's own java.lang.invoke bootstrap is guaranteed
    // complete. See io.btrace.instr.BootstrapPathIndyFreedomTest and the investigation doc it
    // references.
    return serializedExecutor.submit(
        new Runnable() {
          @Override
          public void run() {
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
