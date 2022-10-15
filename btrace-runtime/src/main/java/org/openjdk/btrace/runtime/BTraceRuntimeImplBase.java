/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace.runtime;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.Profiler;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.openjdk.btrace.core.comm.EventCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.openjdk.btrace.core.handlers.ErrorHandler;
import org.openjdk.btrace.core.handlers.EventHandler;
import org.openjdk.btrace.core.handlers.ExitHandler;
import org.openjdk.btrace.core.handlers.LowMemoryHandler;
import org.openjdk.btrace.core.handlers.TimerHandler;
import org.openjdk.btrace.runtime.profiling.MethodInvocationProfiler;
import org.openjdk.btrace.services.api.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class form multiple Java version specific implementation.
 *
 * <p>Helper class used by BTrace built-in functions and also acts runtime "manager" for a specific
 * BTrace client and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
@SuppressWarnings("unchecked")
public abstract class BTraceRuntimeImplBase implements BTraceRuntime.Impl, RuntimeContext {
  private static final Logger log = LoggerFactory.getLogger(BTraceRuntimeImplBase.class);

  private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

  private static final int CMD_QUEUE_LIMIT_DEFAULT = 100;
  private static int CMD_QUEUE_LIMIT;
  private boolean shouldInitializeMBeans =
      true; // mbean initialization guard; synchronized over *this*

  /**
   * Utility to create a new jvmstat perf counter. Called by preprocessed BTrace class to create
   * perf counter for each @Export variable.
   */
  public abstract void newPerfCounter(Object value, String name, String desc);

  /** Return the value of integer perf. counter of given name. */
  public final int getPerfInt(String name) {
    return (int) getPerfLong(name);
  }

  /** Write the value of integer perf. counter of given name. */
  public final void putPerfInt(int value, String name) {
    putPerfLong(value, name);
  }

  /** Return the value of float perf. counter of given name. */
  public final float getPerfFloat(String name) {
    int val = getPerfInt(name);
    return Float.intBitsToFloat(val);
  }

  /** Write the value of float perf. counter of given name. */
  public final void putPerfFloat(float value, String name) {
    int i = Float.floatToRawIntBits(value);
    putPerfInt(i, name);
  }

  /** Return the value of long perf. counter of given name. */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public final long getPerfLong(String name) {
    ByteBuffer b = counters.get(name);
    synchronized (b) {
      long l = b.getLong();
      b.rewind();
      return l;
    }
  }

  /** Write the value of float perf. counter of given name. */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public final void putPerfLong(long value, String name) {
    ByteBuffer b = counters.get(name);
    synchronized (b) {
      b.putLong(value);
      b.rewind();
    }
  }

  /** Return the value of String perf. counter of given name. */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public final String getPerfString(String name) {
    ByteBuffer b = counters.get(name);
    byte[] buf = new byte[b.limit()];
    byte t = 0;
    int i = 0;
    synchronized (b) {
      while ((t = b.get()) != '\0') {
        buf[i++] = t;
      }
      b.rewind();
    }
    return new String(buf, 0, i, StandardCharsets.UTF_8);
  }

  /** Write the value of float perf. counter of given name. */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public final void putPerfString(String value, String name) {
    ByteBuffer b = counters.get(name);
    byte[] v = getStringBytes(value);
    synchronized (b) {
      b.put(v);
      b.rewind();
    }
  }

  private static final class ConsumerWrapper implements MessagePassingQueue.Consumer<Command> {
    private final CommandListener cmdHandler;
    private final AtomicBoolean exitSignal;

    public ConsumerWrapper(CommandListener cmdHandler, AtomicBoolean exitSignal) {
      this.cmdHandler = cmdHandler;
      this.exitSignal = exitSignal;
    }

    @Override
    public void accept(Command t) {
      try {
        cmdHandler.onCommand(t);
      } catch (IOException e) {
        e.printStackTrace(System.err);
      }
      if (t.getType() == Command.EXIT) {
        exitSignal.set(true);
      }
    }
  }

  private static Properties dotWriterProps;
  private static final boolean messageTimestamp = false;
  // are we running with DTrace support enabled?
  private static volatile boolean dtraceEnabled;

  // Few MBeans used to implement certain built-in functions
  private static volatile MemoryMXBean memoryMBean;
  private static volatile List<MemoryPoolMXBean> memPoolList;
  private static volatile HotSpotDiagnosticMXBean hotspotMBean;
  private static volatile RuntimeMXBean runtimeMBean;
  private static volatile ThreadMXBean threadMBean;
  private static volatile List<GarbageCollectorMXBean> gcBeanList;
  private static volatile OperatingSystemMXBean operatingSystemMXBean;

  // Per-client state starts here.
  // current thread's exception
  private final ThreadLocal<Throwable> currentException = new ThreadLocal<>();

  // "command line" args supplied by client
  private final ArgsMap args;

  // whether current runtime has been disabled?
  protected volatile boolean disabled;

  // Class object of the BTrace class [of this client]
  private final String className;

  // BTrace Class object corresponding to this client
  private Class clazz;

  // instrumentation level field for each runtime
  private Field level;

  // array of timer callback methods
  private TimerHandler[] timerHandlers;
  private EventHandler[] eventHandlers;
  private ErrorHandler[] errorHandlers;
  private ExitHandler[] exitHandlers;
  private LowMemoryHandler[] lowMemoryHandlers;

  // map of client event handling methods
  private Map<String, Method> eventHandlerMap;
  private Map<String, LowMemoryHandler> lowMemoryHandlerMap;

  // timer to run profile provider actions
  private volatile Timer timer;

  // executer to run low memory handlers
  private volatile ExecutorService threadPool;
  // Memory MBean listener
  private volatile NotificationListener memoryListener;

  // Command queue for the client
  private final MpscChunkedArrayQueue<Command> queue;

  private static class SpeculativeQueueManager {
    // maximum number of speculative buffers
    private static final int MAX_SPECULATIVE_BUFFERS = Short.MAX_VALUE;
    // per buffer message limit
    private static final int MAX_SPECULATIVE_MSG_LIMIT = Short.MAX_VALUE;
    // next speculative buffer id
    private int nextSpeculationId;
    // speculative buffers map
    private ConcurrentHashMap<Integer, MpmcArrayQueue<Command>> speculativeQueues;
    // per thread current speculative buffer id
    private ThreadLocal<Integer> currentSpeculationId;

    SpeculativeQueueManager() {
      speculativeQueues = new ConcurrentHashMap<>();
      currentSpeculationId = new ThreadLocal<>();
    }

    void clear() {
      speculativeQueues.clear();
      speculativeQueues = null;
      currentSpeculationId.remove();
      currentSpeculationId = null;
    }

    int speculation() {
      int nextId = getNextSpeculationId();
      if (nextId != -1) {
        speculativeQueues.put(nextId, new MpmcArrayQueue<>(MAX_SPECULATIVE_MSG_LIMIT));
      }
      return nextId;
    }

    boolean send(Command cmd) {
      if (currentSpeculationId != null) {
        Integer curId = currentSpeculationId.get();
        if ((curId != null) && (cmd.getType() != Command.EXIT)) {
          MpmcArrayQueue<Command> sb = speculativeQueues.get(curId);
          if (sb != null) {
            if (!sb.offer(cmd)) {
              sb.clear();
              sb.offer(new MessageCommand("speculative buffer overflow: " + curId));
            }
            return true;
          }
        }
      }
      return false;
    }

    void speculate(int id) {
      validateId(id);
      currentSpeculationId.set(id);
    }

    void commit(int id, MpscChunkedArrayQueue<Command> result) {
      validateId(id);
      currentSpeculationId.set(null);
      MpmcArrayQueue<Command> sb = speculativeQueues.get(id);
      if (sb != null) {
        result.addAll(sb);
        sb.clear();
      }
    }

    void discard(int id) {
      validateId(id);
      currentSpeculationId.set(null);
      speculativeQueues.get(id).clear();
    }

    // -- Internals only below this point
    private synchronized int getNextSpeculationId() {
      if (nextSpeculationId == MAX_SPECULATIVE_BUFFERS) {
        return -1;
      }
      return nextSpeculationId++;
    }

    private void validateId(int id) {
      if (!speculativeQueues.containsKey(id)) {
        throw new RuntimeException("invalid speculative buffer id: " + id);
      }
    }
  }
  // per client speculative buffer manager
  private final SpeculativeQueueManager specQueueManager;
  // background thread that sends Commands to the handler
  private volatile Thread cmdThread;
  private final Instrumentation instrumentation;

  private final AtomicBoolean exitting = new AtomicBoolean(false);
  private final MessagePassingQueue.WaitStrategy waitStrategy =
      i -> {
        if (exitting.get()) return 0;
        try {
          if (i < 3000) {
            Thread.yield();
          } else if (i < 3100) {
            Thread.sleep(1);
          } else {
            Thread.sleep(500);
          }
        } catch (InterruptedException e) {
          return 0;
        }
        return i + 1;
      };
  private final MessagePassingQueue.ExitCondition exitCondition = () -> !exitting.get();

  // jvmstat related stuff
  // interface to read perf counters of this process
  protected static final PerfReader perfReader = createPerfReaderImpl();
  // performance counters created by this client
  protected static final Map<String, ByteBuffer> counters = new HashMap<>();

  private static final BTraceRuntimeImplFactory<BTraceRuntime.Impl> factory = null;

  static {
    setupCmdQueueParams();
    loadLibrary(perfReader.getClass().getClassLoader());
  }

  BTraceRuntimeImplBase() {
    args = null;
    queue = null;
    specQueueManager = null;
    className = null;
    instrumentation = null;
  }

  BTraceRuntimeImplBase(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    this.args = args;
    queue = new MpscChunkedArrayQueue<>(CMD_QUEUE_LIMIT);
    specQueueManager = new SpeculativeQueueManager();
    this.className = className;
    instrumentation = inst;

    BTraceRuntimeAccess.addRuntime(className, this);

    cmdThread =
        new Thread(
            () -> {
              try {
                enter();
                queue.drain(
                    new ConsumerWrapper(cmdListener, exitting), waitStrategy, exitCondition);
              } finally {
                queue.clear();
                specQueueManager.clear();
                leave();
                disabled = true;
              }
            });
    cmdThread.setDaemon(true);
    cmdThread.start();
  }

  protected final String getClassName() {
    return className;
  }

  private static void setupCmdQueueParams() {
    String maxQLen = System.getProperty(BTraceRuntime.CMD_QUEUE_LIMIT_KEY, null);
    if (maxQLen == null) {
      CMD_QUEUE_LIMIT = CMD_QUEUE_LIMIT_DEFAULT;
    } else {
      try {
        CMD_QUEUE_LIMIT = Integer.parseInt(maxQLen);
        if (log.isDebugEnabled()) {
          log.debug("The cmd queue limit set to {}", CMD_QUEUE_LIMIT);
        }
      } catch (NumberFormatException e) {
        if (log.isDebugEnabled()) {
          log.debug(
              "\"{}\" is not a valid int number. " + "Using the default cmd queue limit of {}",
              maxQLen,
              CMD_QUEUE_LIMIT_DEFAULT);
        }
        CMD_QUEUE_LIMIT = CMD_QUEUE_LIMIT_DEFAULT;
      }
    }
  }

  final void init(
      Class cl,
      TimerHandler[] tHandlers,
      EventHandler[] evHandlers,
      ErrorHandler[] errHandlers,
      ExitHandler[] eHandlers,
      LowMemoryHandler[] lmHandlers) {
    if (log.isDebugEnabled()) {
      log.debug("init: clazz = {}, cl = {}", clazz, cl);
    }
    if (clazz != null) {
      return;
    }

    clazz = cl;

    if (log.isDebugEnabled()) {
      log.debug("init: timerHandlers = {}", Arrays.deepToString(tHandlers));
    }
    timerHandlers = tHandlers;
    eventHandlers = evHandlers;
    errorHandlers = errHandlers;
    exitHandlers = eHandlers;
    lowMemoryHandlers = lmHandlers;

    try {
      level = cl.getDeclaredField("$btrace$$level");
      level.setAccessible(true);
      int levelVal = BTraceRuntime.parseInt(args.get("level"), Integer.MIN_VALUE);
      if (levelVal > Integer.MIN_VALUE) {
        level.set(null, levelVal);
      }
    } catch (Throwable e) {
      log.debug("Instrumentation level setting not available", e);
    }

    BTraceMBean.registerMBean(clazz);
  }

  /**
   * start method is called by every BTrace (preprocesed) class just at the end of it's class
   * initializer.
   */
  public final void start() {
    initMBeans();
    if (timerHandlers != null) {
      timer = new Timer(true);
      TimerTask[] timerTasks = new TimerTask[timerHandlers.length];
      wrapToTimerTasks(timerTasks);
      for (int index = 0; index < timerHandlers.length; index++) {
        TimerHandler th = timerHandlers[index];
        long period = th.period;
        String periodArg = th.periodArg;
        if (periodArg != null) {
          period = BTraceRuntime.parseLong(args.template(periodArg), period);
        }
        timer.schedule(timerTasks[index], period, period);
      }
    }

    if (lowMemoryHandlers != null) {
      lowMemoryHandlerMap = new HashMap<>();
      for (LowMemoryHandler lmh : lowMemoryHandlers) {
        String poolName = args.template(lmh.pool);
        lowMemoryHandlerMap.put(poolName, lmh);
      }
      for (MemoryPoolMXBean mpoolBean : getMemoryPoolMXBeans()) {
        String name = mpoolBean.getName();
        LowMemoryHandler lmh = lowMemoryHandlerMap.get(name);
        if (lmh != null) {
          if (mpoolBean.isUsageThresholdSupported()) {
            mpoolBean.setUsageThreshold(lmh.threshold);
          }
        }
      }
      NotificationEmitter emitter = (NotificationEmitter) memoryMBean;
      emitter.addNotificationListener(memoryListener, null, null);
    }

    leave();
  }

  @Override
  public final void handleEvent(EventCommand ecmd) {
    if (eventHandlers != null) {
      if (eventHandlerMap == null) {
        eventHandlerMap = new HashMap<>();
        for (EventHandler eh : eventHandlers) {
          try {
            String eventName = args.template(eh.getEvent());
            eventHandlerMap.put(eventName, eh.getMethod(clazz));
          } catch (NoSuchMethodException ignored) {
          }
        }
      }
      String event = ecmd.getEvent();
      event = event != null ? event : EventHandler.ALL_EVENTS;

      Method eventHandler = eventHandlerMap.get(event);
      if (eventHandler != null) {
        BTraceRuntimeAccess.doWithCurrent(
            (Callable<Void>)
                () -> {
                  eventHandler.invoke(null, (Object[]) null);
                  return null;
                });
      }
    }
  }

  @Override
  public final int getInstrumentationLevel() {
    BTraceRuntimeImplBase cur = getCurrent();

    try {
      return cur.getLevel();
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public final void setInstrumentationLevel(int level) {
    BTraceRuntimeImplBase cur = getCurrent();
    try {
      cur.setLevel(level);
    } catch (Exception e) {
      // ignore
    }
  }

  public final void shutdownCmdLine() {
    exitting.set(true);
  }

  /**
   * Leave method is called by every probed method just before the probe actions end (and actual
   * probed method continues).
   */
  @Override
  public final void leave() {
    BTraceRuntimeAccess.leave();
  }

  /** Handles exception from BTrace probe actions. */
  @Override
  public final void handleException(Throwable th) {
    if (currentException.get() != null) {
      return;
    }
    boolean entered = BTraceRuntimeAccess.enter(this);
    try {
      currentException.set(th);

      if (th instanceof ExitException) {
        exitImpl(((ExitException) th).exitCode());
      } else {
        if (errorHandlers != null) {
          for (ErrorHandler eh : errorHandlers) {
            try {
              eh.getMethod(clazz).invoke(null, th);
            } catch (Throwable ignored) {
            }
          }
        } else {
          // Do not call send(Command). Exception messages should not
          // go to speculative buffers!
          enqueue(new ErrorCommand(th));
        }
      }
    } finally {
      currentException.set(null);
      if (entered) {
        leave();
      }
    }
  }
  // package-private interface to BTraceUtils class.

  @Override
  public final int speculation() {
    return specQueueManager.speculation();
  }

  @Override
  public final void speculate(int id) {
    specQueueManager.speculate(id);
  }

  @Override
  public final void discard(int id) {
    specQueueManager.discard(id);
  }

  @Override
  public final void commit(int id) {
    specQueueManager.commit(id, queue);
  }

  @Override
  public final long sizeof(Object obj) {
    return instrumentation.getObjectSize(obj);
  }

  // BTrace command line argument functions
  @Override
  public final int $length() {
    return args == null ? 0 : args.size();
  }

  @Override
  public final String $(int n) {
    if (args == null) {
      return null;
    } else {
      return args.get(n);
    }
  }

  @Override
  public final String $(String key) {
    BTraceRuntime.Impl runtime = getCurrent();
    if (args == null) {
      return null;
    } else {
      return args.get(key);
    }
  }

  // BTrace perf counter reading functions
  @Override
  public final int perfInt(String name) {
    return getPerfReader().perfInt(name);
  }

  @Override
  public final long perfLong(String name) {
    return getPerfReader().perfLong(name);
  }

  @Override
  public final String perfString(String name) {
    return getPerfReader().perfString(name);
  }

  @Override
  public final String toXML(Object obj) {
    try {
      return XMLSerializer.toXML(obj);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception exp) {
      throw new RuntimeException(exp);
    }
  }

  @Override
  public final void writeXML(Object obj, String fileName) {
    try {
      Path p = FileSystems.getDefault().getPath(resolveFileName(fileName));
      try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
        XMLSerializer.write(obj, bw);
      }
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception exp) {
      throw new RuntimeException(exp);
    }
  }

  private static synchronized void initDOTWriterProps() {
    if (dotWriterProps == null) {
      dotWriterProps = new Properties();
      InputStream is =
          BTraceRuntime.class.getResourceAsStream("resources/btrace.dotwriter.properties");
      if (is != null) {
        try {
          dotWriterProps.load(is);
        } catch (IOException ioExp) {
          ioExp.printStackTrace();
        }
      }
      try {
        String home = System.getProperty("user.home");
        File file = new File(home, "btrace.dotwriter.properties");
        if (file.exists() && file.isFile()) {
          is = new BufferedInputStream(new FileInputStream(file));
          dotWriterProps.load(is);
        }
      } catch (Exception exp) {
        exp.printStackTrace();
      }
    }
  }

  @Override
  public final void writeDOT(Object obj, String fileName) {
    DOTWriter writer = new DOTWriter(resolveFileName(fileName));
    initDOTWriterProps();
    writer.customize(dotWriterProps);
    writer.addNode(null, obj);
    writer.close();
  }

  // profiling related methods
  /**
   * @see BTraceUtils.Profiling#newProfiler()
   */
  @Override
  public final Profiler newProfiler() {
    return new MethodInvocationProfiler(600);
  }

  /**
   * @see BTraceUtils.Profiling#newProfiler(int)
   */
  @Override
  public final Profiler newProfiler(int expectedMethodCnt) {
    return new MethodInvocationProfiler(expectedMethodCnt);
  }

  @Override
  public final RuntimeMXBean getRuntimeMXBean() {
    initMBeans();
    return runtimeMBean;
  }

  @Override
  public final ThreadMXBean getThreadMXBean() {
    initMBeans();
    return threadMBean;
  }

  @Override
  public final OperatingSystemMXBean getOperatingSystemMXBean() {
    initMBeans();
    return operatingSystemMXBean;
  }

  @Override
  public final List<GarbageCollectorMXBean> getGCMBeans() {
    initMBeans();
    return gcBeanList;
  }

  @Override
  public final HotSpotDiagnosticMXBean getHotspotMBean() {
    initMBeans();
    return hotspotMBean;
  }

  public final boolean isDisabled() {
    return disabled;
  }

  @Override
  public final boolean enter() {
    return BTraceRuntimeAccess.enter(this);
  }

  @Override
  public final void handleExit(int exitCode) {
    exitImpl(exitCode);
    try {
      cmdThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    cleanupRuntime();
  }

  public final int getLevel() {
    try {
      return (int) level.get(null);
    } catch (IllegalAccessException ignored) {
    }

    return 0;
  }

  public final void setLevel(int level) {
    try {
      this.level.set(null, level);
    } catch (IllegalAccessException ignored) {
    }
  }

  protected void cleanupRuntime() {
    // to be overridden by concrete implementations
  }

  protected static void loadLibrary(ClassLoader cl) {
    AccessController.doPrivileged(
        (PrivilegedAction<Void>)
            () -> {
              loadBTraceLibrary(cl);
              return null;
            });
  }

  private static void loadBTraceLibrary(ClassLoader loader) {
    boolean isSolaris = System.getProperty("os.name").equals("SunOS");
    if (isSolaris) {
      try {
        System.loadLibrary("btrace");
        dtraceEnabled = true;
      } catch (LinkageError le) {
        URL btracePkg = null;
        if (loader != null) {
          btracePkg = loader.getResource("org/openjdk/btrace");
        }

        if (btracePkg == null) {
          log.debug("cannot load libbtrace.so, will miss DTrace probes from BTrace");
          return;
        }

        String path = btracePkg.toString();
        int archSeparator = path.indexOf('!');
        if (archSeparator != -1) {
          path = path.substring(0, archSeparator);
          path = path.substring("jar:".length(), path.lastIndexOf('/'));
        } else {
          int buildSeparator = path.indexOf("/classes/");
          if (buildSeparator != -1) {
            path = path.substring(0, buildSeparator);
          }
        }
        String cpu = System.getProperty("os.arch");
        if (cpu.equals("x86")) {
          cpu = "i386";
        }
        path += "/" + cpu + "/libbtrace.so";
        try {
          path = new File(new URI(path)).getAbsolutePath();
        } catch (RuntimeException re) {
          throw re;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        try {
          System.load(path);
          dtraceEnabled = true;
        } catch (LinkageError le1) {
          log.debug("cannot load libbtrace.so, will miss DTrace probes from BTrace");
        }
      }
    }
  }

  private BTraceRuntimeImplBase getCurrent() {
    return BTraceRuntimeAccess.getCurrent();
  }

  private void initThreadPool() {
    threadPool =
        Executors.newFixedThreadPool(
            1,
            r -> {
              Thread th = new Thread(r, "BTrace Worker");
              th.setDaemon(true);
              return th;
            });
  }

  /** Must be called exactly once before the runtime starts */
  private synchronized void initMBeans() {
    if (shouldInitializeMBeans) {
      initMemoryMBean();
      initOperatingSystemMBean();
      initRuntimeMBean();
      initThreadMBean();
      initHotspotMBean();
      initGcMBeans();
      initMemoryPoolList();
      initMemoryListener();
      shouldInitializeMBeans = false;
    }
  }

  private void initMemoryListener() {
    initThreadPool();
    memoryListener =
        (notif, handback) -> {
          boolean entered = enter();
          try {
            String notifType = notif.getType();
            if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
              CompositeData cd = (CompositeData) notif.getUserData();
              MemoryNotificationInfo info = MemoryNotificationInfo.from(cd);
              String name = info.getPoolName();
              LowMemoryHandler handler = lowMemoryHandlerMap.get(name);
              if (handler != null) {
                threadPool.submit(
                    new Runnable() {
                      @Override
                      public void run() {
                        boolean entered = enter();
                        try {
                          if (handler.trackUsage) {
                            handler.invoke(clazz, null, info.getUsage());
                          } else {
                            handler.invoke(clazz, null, null);
                          }
                        } catch (Throwable th) {
                        } finally {
                          if (entered) {
                            BTraceRuntime.leave();
                          }
                        }
                      }
                    });
              }
            }
          } finally {
            if (entered) {
              BTraceRuntime.leave();
            }
          }
        };
  }

  @Override
  public final void send(String msg) {
    send(new MessageCommand(messageTimestamp ? System.nanoTime() : 0L, msg));
  }

  @Override
  public final void send(Command cmd) {
    boolean speculated = specQueueManager.send(cmd);
    if (!speculated) {
      enqueue(cmd);
    }
  }

  private void enqueue(Command cmd) {
    int backoffCntr = 0;
    while (!Thread.interrupted() && queue != null && !queue.relaxedOffer(cmd)) {
      if (backoffCntr < 3000) {
        Thread.yield();
      } else if (backoffCntr < 3100) {
        LockSupport.parkNanos(1_000_000);
      } else {
        LockSupport.parkNanos(100_000_000);
      }
      backoffCntr++;
    }
  }

  private void wrapToTimerTasks(TimerTask[] tasks) {
    for (int index = 0; index < timerHandlers.length; index++) {
      TimerHandler th = timerHandlers[index];
      tasks[index] =
          new TimerTask() {
            final Method mthd;

            {
              Method m = null;
              try {
                m = th.getMethod(clazz);
              } catch (NoSuchMethodException e) {
                e.printStackTrace();
              }
              mthd = m;
            }

            @Override
            public void run() {
              if (mthd != null) {
                try {
                  mthd.invoke(null, (Object[]) null);
                } catch (Throwable th) {
                  th.printStackTrace();
                }
              }
            }
          };
    }
  }

  @Override
  public final void exit(int exitCode) {
    exitImpl(exitCode);
  }

  private synchronized void exitImpl(int exitCode) {
    boolean entered = enter();
    try {
      if (timer != null) {
        timer.cancel();
      }

      if (memoryListener != null && memoryMBean != null) {
        NotificationEmitter emitter = (NotificationEmitter) memoryMBean;
        try {
          emitter.removeNotificationListener(memoryListener);
        } catch (ListenerNotFoundException ignored) {
        }
      }

      if (threadPool != null) {
        threadPool.shutdownNow();
      }

      if (exitHandlers != null) {
        for (ExitHandler eh : exitHandlers) {
          try {
            eh.getMethod(clazz).invoke(null, exitCode);
          } catch (Throwable ignored) {
          }
        }
        exitHandlers = null;
      }

      send(new ExitCommand(exitCode));
    } finally {
      disabled = true;
      if (entered) {
        BTraceRuntime.leave();
      }
    }
  }

  @Override
  public final String resolveFileName(String name) {
    if (name.indexOf(File.separatorChar) != -1) {
      throw new IllegalArgumentException("directories are not allowed");
    }
    StringBuilder buf = new StringBuilder();
    buf.append('.');
    buf.append(File.separatorChar);
    buf.append("btrace");
    if (args != null && args.size() > 0) {
      buf.append(args.get(0));
    }
    buf.append(File.separatorChar);
    buf.append(className);
    new File(buf.toString()).mkdirs();
    buf.append(File.separatorChar);
    buf.append(name);
    return buf.toString();
  }

  private static void initMemoryPoolList() {
    try {
      memPoolList =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<List<MemoryPoolMXBean>>)
                  ManagementFactory::getMemoryPoolMXBeans);
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  private static void initMemoryMBean() {
    try {
      memoryMBean =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<MemoryMXBean>) ManagementFactory::getMemoryMXBean);
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  private static void initOperatingSystemMBean() {
    try {
      operatingSystemMXBean =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<OperatingSystemMXBean>)
                  ManagementFactory::getOperatingSystemMXBean);
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
  }

  private static void initRuntimeMBean() {
    try {
      runtimeMBean =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<RuntimeMXBean>) ManagementFactory::getRuntimeMXBean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void initThreadMBean() {
    try {
      threadMBean =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<ThreadMXBean>) ManagementFactory::getThreadMXBean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void initGcMBeans() {
    try {
      gcBeanList =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<List<GarbageCollectorMXBean>>)
                  ManagementFactory::getGarbageCollectorMXBeans);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void initHotspotMBean() {
    try {
      hotspotMBean =
          AccessController.doPrivileged(
              (PrivilegedExceptionAction<HotSpotDiagnosticMXBean>)
                  () -> {
                    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                    Set<ObjectName> s = server.queryNames(new ObjectName(HOTSPOT_BEAN_NAME), null);
                    Iterator<ObjectName> itr = s.iterator();
                    if (itr.hasNext()) {
                      ObjectName name = itr.next();
                      return ManagementFactory.newPlatformMXBeanProxy(
                          server, name.toString(), HotSpotDiagnosticMXBean.class);
                    } else {
                      return null;
                    }
                  });
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
  }

  @Override
  public boolean isDTraceEnabled() {
    return dtraceEnabled;
  }

  @Override
  public List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
    initMBeans();
    return memPoolList;
  }

  @Override
  public MemoryMXBean getMemoryMXBean() {
    initMBeans();
    return memoryMBean;
  }

  @SuppressWarnings("SameReturnValue")
  protected static PerfReader getPerfReader() {
    return perfReader;
  }

  protected static byte[] getStringBytes(String value) {
    byte[] v = null;
    v = value.getBytes(StandardCharsets.UTF_8);
    byte[] v1 = new byte[v.length + 1];
    System.arraycopy(v, 0, v1, 0, v.length);
    v1[v.length] = '\0';
    return v1;
  }

  @SuppressWarnings("LiteralClassName")
  private static PerfReader createPerfReaderImpl() {
    // see if we can access any jvmstat class
    try {
      if (String.class.getResource("sun/jvmstat/monitor/MonitoredHost.class") != null) {
        return (PerfReader)
            Class.forName("org.openjdk.btrace.agent.PerfReaderImpl")
                .getDeclaredConstructor()
                .newInstance();
      }
    } catch (Exception exp) {
      // can happen if jvmstat is not available
    }
    // no luck, create null implementation
    return new NullPerfReaderImpl();
  }
}
