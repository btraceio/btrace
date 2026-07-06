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

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.BTraceRuntimeBridge;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.CommandListener;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.InstrumentCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.comm.RenameCommand;
import io.btrace.core.comm.RetransformationStartNotification;
import io.btrace.core.comm.StatusCommand;
import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;
import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.ExtensionLoader;
import io.btrace.extension.ExtensionRegistry;
import io.btrace.instr.BTraceProbe;
import io.btrace.instr.BTraceProbeFactory;
import io.btrace.instr.BTraceProbePersisted;
import io.btrace.instr.BTraceTransformer;
import io.btrace.instr.ClassCache;
import io.btrace.instr.ClassFilter;
import io.btrace.instr.ClassInfo;
import io.btrace.instr.InstrumentUtils;
import io.btrace.instr.Instrumentor;
import io.btrace.instr.MethodTrackingContext;
import io.btrace.runtime.BTraceRuntimeAccess;
import io.btrace.runtime.BTraceRuntimes;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class that represents a BTrace client at the BTrace agent.
 *
 * @author A. Sundararajan
 * @author J. Bachorik (j.bachorik@btrace.io)
 */
abstract class Client implements CommandListener {
  private static final Logger log = LoggerFactory.getLogger(Client.class);

  private static final int RETRANSFORM_BATCH_SIZE =
      Math.max(1, Integer.getInteger("btrace.retransform.batchSize", 1));
  private static final Map<UUID, Client> CLIENTS = new ConcurrentHashMap<>();
  private static final Map<String, PrintWriter> WRITER_MAP = new HashMap<>();
  private static final Pattern SYSPROP_PTN = Pattern.compile("\\$\\{(.+?)}");

  static {
    ClassFilter.class.getClassLoader();
    InstrumentUtils.class.getClassLoader();
    Instrumentor.class.getClassLoader();
    ClassReader.class.getClassLoader();
    ClassWriter.class.getClassLoader();
    Annotation.class.getClassLoader();
    MethodTrackingContext.class.getClassLoader();
    ClassCache.getInstance();
    ClassInfo.class.getClassLoader();
  }

  private final Instrumentation inst;
  final SharedSettings settings;
  final ArgsMap argsMap;
  private final BTraceTransformer transformer;
  volatile PrintWriter out;
  private volatile BTraceRuntime.Impl runtime;
  private volatile String outputName;
  private BTraceProbe probe;
  private Timer flusher;
  private volatile boolean initialized = false;
  private volatile boolean shuttingDown = false;
  final UUID id = UUID.randomUUID();

  Client(ClientContext ctx) {
    this(ctx.getInstr(), ctx.getArguments(), ctx.getSettings(), ctx.getTransformer());
  }

  private Client(Instrumentation inst, ArgsMap argsMap, SharedSettings s, BTraceTransformer t) {
    this.inst = inst;
    this.argsMap = argsMap;
    settings = s != null ? s : SharedSettings.GLOBAL;
    transformer = t;

    setupWriter();
    CLIENTS.put(id, this);
  }

  private static String pid() {
    String pName = ManagementFactory.getRuntimeMXBean().getName();
    if (pName != null && pName.length() > 0) {
      String[] parts = pName.split("@");
      if (parts.length == 2) {
        return parts[0];
      }
    }

    return "-1";
  }

  protected final void initialize() {
    initialized = true;
  }

  @SuppressWarnings("DefaultCharset")
  private final void setupWriter() {
    String outputFile = settings.getOutputFile();
    if (outputFile == null || outputFile.equals("::null") || outputFile.equals("/dev/null")) return;

    if (!outputFile.equals("::stdout")) {
      String outputDir = settings.getScriptDir();
      String output = (outputDir != null ? outputDir + File.separator : "") + outputFile;
      outputFile = templateOutputFileName(output);
      log.info("Redirecting output to {}", outputFile);
    }
    // WRITER_MAP is shared across concurrently-attaching/detaching clients (accept thread,
    // command-handler threads, serialized executor). Guard the get-or-create check-then-act and
    // the removal in closeAll() under the same monitor so the map is not corrupted and two clients
    // resolving the same output file share a single writer atomically.
    synchronized (WRITER_MAP) {
      out = WRITER_MAP.get(outputFile);
      if (out == null) {
        if (outputFile.equals("::stdout")) {
          out = new PrintWriter(System.out);
        } else {
          if (settings.getFileRollMilliseconds() > 0) {
            out =
                new PrintWriter(
                    new BufferedWriter(
                        TraceOutputWriter.rollingFileWriter(new File(outputFile), settings)));
          } else {
            out =
                new PrintWriter(
                    new BufferedWriter(TraceOutputWriter.fileWriter(new File(outputFile))));
          }
        }
        WRITER_MAP.put(outputFile, out);
        out.append("### BTrace Log: ")
            .append(DateFormat.getInstance().format(new Date()))
            .append("\n\n");
        startFlusher();
      }
    }
    outputName = outputFile;
  }

  private void startFlusher() {
    int flushInterval;
    String flushIntervalStr = System.getProperty("io.btrace.FileClient.flush");
    if (flushIntervalStr == null) {
      flushIntervalStr = System.getProperty("com.sun.btrace.FileClient.flush", "5");
    }
    try {
      flushInterval = Integer.parseInt(flushIntervalStr);
    } catch (NumberFormatException e) {
      flushInterval = 5; // default
    }

    int flushSec = flushInterval;
    if (flushSec > -1) {
      flusher = new Timer("BTrace FileClient Flusher", true);
      flusher.scheduleAtFixedRate(
          new TimerTask() {
            @Override
            public void run() {
              try {
                if (out != null) {
                  boolean entered = BTraceRuntime.enter();
                  try {
                    out.flush();
                  } finally {
                    if (entered) {
                      BTraceRuntime.leave();
                    }
                  }
                }
              } catch (Throwable t) {
                log.error("Error during periodic flush", t);
              }
            }
          },
          flushSec,
          flushSec);
    } else {
      flusher = null;
    }
  }

  private String templateOutputFileName(String fName) {
    if (fName != null) {
      boolean dflt = fName.contains("[default]");
      String agentName = System.getProperty("btrace.agent", "default");
      String clientName = settings.getClientName();
      fName =
          fName
              .replace("${client}", clientName != null ? clientName : "")
              .replace("${ts}", String.valueOf(System.currentTimeMillis()))
              .replace("${pid}", pid())
              .replace("${agent}", agentName != null ? "." + agentName : "")
              .replace("[default]", "");

      fName = replaceSysProps(fName);
      if (dflt && log.isDebugEnabled()) {
        log.debug("scriptOutputFile not specified. defaulting to {}", fName);
      }
    }
    return fName;
  }

  private String replaceSysProps(String str) {
    int replaced = 0;
    do {
      StringBuffer sb = new StringBuffer();
      replaced = replaceSysProps(str, sb);
      str = sb.toString();
    } while (replaced > 0);
    return str;
  }

  private int replaceSysProps(String str, StringBuffer sb) {
    int cnt = 0;
    Matcher m = SYSPROP_PTN.matcher(str);
    while (m.find()) {
      String key = m.group(1);
      String val = System.getProperty(key);
      if (val != null) {
        cnt++;
        m.appendReplacement(sb, val);
      } else {
        m.appendReplacement(sb, m.group(0));
      }
    }
    m.appendTail(sb);
    return cnt;
  }

  static Collection<String> listProbes() {
    List<String> probes = new ArrayList<>(CLIENTS.size());
    for (Client client : CLIENTS.values()) {
      if (client instanceof RemoteClient) {
        if (((RemoteClient) client).isDisconnected()) {
          probes.add(client.id + " [" + client.getClassName() + "]");
        }
      }
    }
    return probes;
  }

  synchronized void onExit(int exitCode) {
    if (!shuttingDown) {
      shuttingDown = true;
      if (out != null) {
        out.flush();
      }

      BTraceRuntime.leave();
      try {
        log.debug("onExit:");
        log.debug("cleaning up transformers");
        cleanupTransformers();
        log.debug("removing instrumentation");
        retransformLoaded();
        log.debug("closing all I/O");
        // Send EXIT command to notify remote client before closing
        sendCommand(new ExitCommand(exitCode));
        Thread.sleep(300);
        try {
          closeAll();
        } catch (IOException e) {
          // ignore IOException when closing
        }
        log.debug("done");
      } catch (Throwable th) {
        // ExitException is expected here
        if (!th.getClass().getName().equals("ExitException")) {
          log.debug("Failed to gracefully exit BTrace probe", th);
          BTraceRuntime.handleException(th);
        }
      } finally {
        runtime.shutdownCmdLine();
        CLIENTS.remove(id);
      }
    }
  }

  final synchronized Class<?> loadClass(InstrumentCommand instr) throws IOException {
    ArgsMap args = instr.getArguments();
    byte[] btraceCode = instr.getCode();
    try {
      probe = load(btraceCode, ArgsMap.merge(argsMap, args));
      if (probe == null) {
        log.debug("Failed to load BTrace probe code");
        return null;
      }

      if (!settings.isTrusted()) {
        probe.checkVerified();
      }

      // Check probe's required permissions against effective permissions
      Set<Permission> required = probe.getRequiredPermissions();
      if (!required.isEmpty()) {
        PermissionSet effective = settings.getEffectivePermissions();
        Set<Permission> missing =
            required.stream().filter(p -> !effective.has(p)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
          throw new SecurityException(formatPermissionError(missing));
        }
      }

      // Validate that all injected service types are declared by available extensions
      validateDeclaredServices(probe);
    } catch (Throwable th) {
      log.debug("Failed to load BTrace probe code", th);
      errorExit(th);
      return null;
    }
    if (log.isDebugEnabled()) {
      log.debug("creating BTraceRuntime instance for {}", probe.getClassName());
    }
    runtime = BTraceRuntimes.getRuntime(probe.getClassName(), args, this, inst);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (runtime != null) {
                    runtime.handleExit(0);
                  }
                }));
    if (probe.isClassRenamed()) {
      if (log.isDebugEnabled()) {
        log.debug("class renamed to {}", probe.getClassName());
      }
      sendCommand(new RenameCommand(probe.getClassName()));
    }
    if (log.isDebugEnabled()) {
      log.debug("created BTraceRuntime instance for {}", probe.getClassName());
      log.debug("sending Okay command");
    }

    sendCommand(new StatusCommand());

    // Warn about failed extensions
    Map<String, String> failed = ExtensionRegistry.getFailedExtensions();
    if (!failed.isEmpty()) {
      StringBuilder warning = new StringBuilder();
      warning
          .append("[BTRACE WARN] ")
          .append(failed.size())
          .append(" extension(s) failed to load:\n");
      for (Map.Entry<String, String> entry : failed.entrySet()) {
        String simpleName = entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1);
        warning
            .append("  - ")
            .append(simpleName)
            .append(": ")
            .append(entry.getValue())
            .append("\n");
      }
      warning.append("Use 'btrace -le <PID>' for details.\n");
      sendCommand(new MessageCommand(warning.toString()));
    }

    // Expose extension-declared permissions for integration visibility
    // Print extension permissions only when explicitly requested (debug or system property)
    if (settings.isDebug() || Boolean.getBoolean("btrace.list.extension.permissions")) {
      try {
        ExtensionLoader loader = Main.getExtensionLoader();
        if (loader != null) {
          StringBuilder info = new StringBuilder();
          info.append("[BTRACE INFO] Extensions and declared permissions:\n");
          for (ExtensionDescriptorDTO ext : loader.getAvailableExtensions()) {
            PermissionSet perms = ext.getRequiredPermissions();
            String pStr = perms != null && !perms.isEmpty() ? perms.toString() : "[]";
            info.append("  - ").append(ext.getId()).append(": ").append(pStr).append("\n");
          }
          sendCommand(new MessageCommand(info.toString()));
        }
      } catch (Throwable t) {
        // ignore, informational only
      }
    }

    boolean entered = false;
    try {
      entered = BTraceRuntimeAccess.enter((BTraceRuntimeBridge) runtime);
      return probe.register(runtime, transformer);
    } catch (Throwable th) {
      log.debug("Failed to load BTrace probe", th);
      errorExit(th);
      return null;
    } finally {
      if (entered) {
        BTraceRuntime.leave();
      }
    }
  }

  /**
   * Validates that all {@code @Injected} service field types used by the given probe are declared
   * by some available extension. This runs in the agent's runtime where the actual classloader and
   * JPMS module layer apply.
   *
   * <p>Why reflection here (vs. pure ASM): - Classloader identity: Ensures types are checked
   * against the agent's classes loaded by the correct loader. Name-only checks in ASM cannot detect
   * split-brain issues (same FQN, different loader/JAR) that would later cause ClassCastException.
   * - JPMS access rules: Surfaces missing exports/opens and other module access constraints that
   * cannot be proven by static bytecode analysis. - Linkage/loadability: Fails fast if a referenced
   * type is not actually resolvable on the agent's runtime path (NoClassDefFoundError/missing
   * transitive dependencies). - Assignability truth: Verifies that the service type corresponds to
   * something an extension actually declares in its manifest, avoiding false positives from shaded
   * or version-skewed classes.
   *
   * <p>Implementation notes: - We use reflection only to access the probe's internal service field
   * map (to avoid a direct compile-time dependency on the probe's delegate type) and to keep the
   * agent/probe boundary clean. We do not instantiate user classes or trigger class initializers. -
   * This check complements compile-time and bytecode-time validation (ASM-based) which enforce
   * structural rules without loading classes. Reflection here provides the necessary runtime
   * assurance in the actual environment where the agent will operate.
   */
  private void validateDeclaredServices(BTraceProbe probe) throws IOException {
    if (!(probe instanceof BTraceProbePersisted)) {
      return;
    }
    ExtensionLoader loader = Main.getExtensionLoader();
    if (loader == null) {
      return;
    }
    // Reflectively access serviceFields() from the delegate to get injected service types
    try {
      java.lang.reflect.Field delF = BTraceProbePersisted.class.getDeclaredField("delegate");
      delF.setAccessible(true);
      Object delegate = delF.get(probe);
      java.lang.reflect.Method svcM = delegate.getClass().getDeclaredMethod("serviceFields");
      svcM.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, String> svcMap = (Map<String, String>) svcM.invoke(delegate);
      if (svcMap != null) {
        for (String internalName : svcMap.values()) {
          String fqcn = internalName.replace('/', '.');
          if (loader.findExtensionForService(fqcn) == null) {
            throw new IOException("Injected service type not declared by any extension: " + fqcn);
          }
        }
      }
    } catch (ReflectiveOperationException e) {
      log.debug("Unable to inspect injected services for validation", e);
    }
  }

  protected void closeAll() throws IOException {
    if (flusher != null) {
      flusher.cancel();
    }
    if (out != null) {
      out.close();
    }
    synchronized (WRITER_MAP) {
      WRITER_MAP.remove(outputName);
    }
  }

  private void errorExit(Throwable th) throws IOException {
    log.debug("sending error command");
    sendCommand(new ErrorCommand(th));
    log.debug("sending exit command");
    sendCommand(new ExitCommand(1));
    closeAll();
  }

  private void cleanupTransformers() {
    if (probe != null) {
      String probeName = probe.getClassName();
      probe.unregister();
      // Drop the registry's strong reference to the BTraceRuntime.Impl created in
      // initialize() via BTraceRuntimes.getRuntime(probe.getClassName(), ...). Without
      // this, the registry keeps the Impl (and, transitively, the probe Class<?> and
      // its per-probe ClassLoader) reachable forever, defeating probe class unloading.
      // Must use the same key that was used to register — here, the dotted class name.
      BTraceRuntimes.removeRuntime(probeName);
    }
  }

  // package privates below this point
  final boolean isInitialized() {
    return initialized;
  }

  final BTraceRuntime.Impl getRuntime() {
    return runtime;
  }

  final String getClassName() {
    return probe != null ? probe.getClassName() : "<unknown>";
  }

  private final boolean isCandidate(Class<?> c) {
    String cname = c.getName().replace('.', '/');
    if (c.isInterface() || c.isPrimitive() || c.isArray()) {
      return false;
    }
    if (ClassFilter.isSensitiveClass(cname)) {
      return false;
    } else {
      return probe.willInstrument(c);
    }
  }

  private final void startRetransformClasses(int numClasses) {
    sendCommand(new RetransformationStartNotification(numClasses));
    if (log.isDebugEnabled()) {
      log.debug("calling retransformClasses ({} classes to be retransformed)", numClasses);
    }
  }

  final void endRetransformClasses() {
    sendCommand(new StatusCommand());
    log.debug("finished retransformClasses");
  }

  // Internals only below this point
  private BTraceProbe load(byte[] buf, ArgsMap args) {
    BTraceProbeFactory f = new BTraceProbeFactory(settings);
    log.debug("loading BTrace class");
    BTraceProbe cn = f.createProbe(buf, args);

    if (cn != null) {
      if (cn.isVerified()) {
        if (log.isDebugEnabled()) {
          log.debug("loaded '{}' successfully", cn.getClassName());
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("{} failed verification", cn.getClassName());
        }
        return null;
      }
    }
    return BTraceProbePersisted.from(cn);
  }

  boolean retransformLoaded() throws UnmodifiableClassException {
    if (runtime == null) {
      return false;
    }
    if (probe.isTransforming() && settings.isRetransformStartup()) {
      ArrayList<Class<?>> list = new ArrayList<>();
      log.debug("retransforming loaded classes");
      log.debug("filtering loaded classes");
      for (Class<?> c : inst.getAllLoadedClasses()) {
        if (c != null) {
          if (inst.isModifiableClass(c) && isCandidate(c)) {
            if (log.isDebugEnabled()) {
              log.debug("candidate {} added", c);
            }
            list.add(c);
          }
        }
      }
      list.trimToSize();
      int size = list.size();
      if (size > 0) {
        Class<?>[] classes = new Class[size];
        list.toArray(classes);
        startRetransformClasses(size);
        if (log.isDebugEnabled()) {
          retransformIndividually(classes);
        } else {
          retransformBatches(classes);
        }
      }
    }
    return true;
  }

  private void retransformBatches(Class<?>[] classes) throws UnmodifiableClassException {
    for (int start = 0; start < classes.length; start += RETRANSFORM_BATCH_SIZE) {
      int end = Math.min(start + RETRANSFORM_BATCH_SIZE, classes.length);
      int batchSize = end - start;
      Class<?>[] batch = new Class<?>[batchSize];
      System.arraycopy(classes, start, batch, 0, batchSize);
      try {
        inst.retransformClasses(batch);
      } catch (ClassFormatError | VerifyError e) {
        /*
         * If a batch retransformation fails because of verification, retry that batch
         * class-by-class. Otherwise the whole batch is rolled back and no instrumentation
         * is applied to classes that could have been transformed successfully.
         */
        retransformIndividually(batch);
      }
    }
  }

  private void retransformIndividually(Class<?>[] classes) throws UnmodifiableClassException {
    for (Class<?> c : classes) {
      try {
        if (log.isDebugEnabled()) {
          log.debug("Attempting to retransform class: {}", c.getName());
        }
        inst.retransformClasses(c);
      } catch (ClassFormatError | VerifyError e) {
        // Avoid printing full stack traces in debug to keep target stderr clean
        log.debug("Class '{}' verification failed: {}", c.getName(), e.toString());
        sendCommand(
            new MessageCommand(
                "[BTRACE WARN] Class verification failed: "
                    + c.getName()
                    + " ("
                    + e.getMessage()
                    + ")"));
      }
    }
  }

  protected void sendCommand(Command command) {
    if (runtime == null) {
      log.warn(
          "Cannot send command {}, runtime not initialized", command.getClass().getSimpleName());
      return;
    }
    runtime.sendCommand(command);
  }

  static Client findClient(String uuid) {
    try {
      UUID id = UUID.fromString(uuid);
      return CLIENTS.get(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "BTrace Client: " + id + "[" + probe.getClassName() + "]";
  }

  private static String formatPermissionError(Set<Permission> missing) {
    StringBuilder sb = new StringBuilder();
    sb.append("Probe requires permissions that are not granted:\n\n");
    for (Permission p : missing) {
      sb.append("  - ").append(p.name()).append("\n");
      sb.append("    ").append(p.getRiskDescription()).append("\n");
    }
    sb.append("\nTo allow these permissions, use:\n");
    sb.append("  --grant=")
        .append(missing.stream().map(Permission::name).collect(Collectors.joining(",")))
        .append("\n");
    sb.append("\nOr use --grantAll=true to allow all permissions (not recommended).\n");
    return sb.toString();
  }
}
