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
package io.btrace.runtime;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.BTraceRuntimeBridge;
import io.btrace.core.BTraceUtils;
import io.btrace.core.Profiler;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.CommandListener;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.EventCommand;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.GridDataCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.comm.NumberDataCommand;
import io.btrace.core.comm.NumberMapDataCommand;
import io.btrace.core.comm.StringMapDataCommand;
import io.btrace.core.extensions.Extension;
import io.btrace.core.handlers.ErrorHandler;
import io.btrace.core.handlers.EventHandler;
import io.btrace.core.handlers.ExitHandler;
import io.btrace.core.handlers.LowMemoryHandler;
import io.btrace.core.handlers.TimerHandler;
import io.btrace.runtime.profiling.MethodInvocationProfiler;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
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
public abstract class BTraceRuntimeImplBase implements BTraceRuntime.Impl, BTraceRuntimeBridge {
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
      while (b.hasRemaining() && (t = b.get()) != '\0') {
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

  private final class ConsumerWrapper implements MessagePassingQueue.Consumer<Command> {
    private final CommandListener cmdHandler;
    private final AtomicBoolean exitSignal;

    public ConsumerWrapper(CommandListener cmdHandler, AtomicBoolean exitSignal) {
      this.cmdHandler = cmdHandler;
      this.exitSignal = exitSignal;
    }

    @Override
    public void accept(Command t) {
      boolean dispatched = false;
      try {
        cmdHandler.onCommand(t);
        dispatched = true;
      } catch (IOException e) {
        log.warn("Command handler I/O error", e);
      }
      if (dispatched) {
        acknowledgeTerminalMarker(t);
        if (t.getType() == Command.EXIT) {
          terminalExitDispatched.countDown();
        }
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

  /**
   * The probe class (after {@link #init}) or {@code null}. Callers that need reflective access
   * bypass {@link Class#forName}, which can't see probes defined in isolated or hidden class
   * loaders.
   */
  Class<?> getProbeClass() {
    return clazz;
  }

  // instrumentation level field for each runtime (legacy, may not exist).
  // Only used for backward compatibility with old bytecode-based level checks.
  // Primary storage is now in levelValue (see below).
  private Field level;

  // instrumentation level value (PRIMARY source of truth for level checking).
  // This is the canonical level storage for MethodHandle-based guards.
  // The legacy $btrace$$level field in the probe class is only updated for
  // backward compatibility; level checking now happens at the MethodHandle layer.
  // See HandlerRepositoryImpl.applyLevelGuard() for how this value is used.
  private volatile int levelValue = 0;

  // array of timer callback methods
  private TimerHandler[] timerHandlers;
  private EventHandler[] eventHandlers;
  private ErrorHandler[] errorHandlers;
  private ExitHandler[] exitHandlers;
  private LowMemoryHandler[] lowMemoryHandlers;

  // map of client event handling methods
  private volatile Map<String, Method> eventHandlerMap;
  private Map<String, LowMemoryHandler> lowMemoryHandlerMap;

  // timer to run profile provider actions
  private volatile Timer timer;

  // executer to run low memory handlers
  private volatile ExecutorService threadPool;
  // Memory MBean listener
  private volatile NotificationListener memoryListener;

  // Extension registry for this runtime
  // Extension instances are now resolved via the manifest-based bridge when injected.
  private final Set<Extension> extensions = Collections.newSetFromMap(new IdentityHashMap<>());
  private volatile boolean extensionsClosed = false;

  // Command queue for the client
  private final CommandQueue queue;

  static final class SpeculativeQueueManager {
    // maximum number of speculative buffers
    private static final int MAX_SPECULATIVE_BUFFERS = Short.MAX_VALUE;
    // per buffer message limit
    private static final int MAX_SPECULATIVE_MSG_LIMIT = Short.MAX_VALUE;
    // next speculative buffer id
    private int nextSpeculationId;
    // speculative buffers map
    private final ConcurrentHashMap<Integer, MpmcArrayQueue<Command>> speculativeQueues;
    // per thread current speculative buffer id
    private final ThreadLocal<Integer> currentSpeculationId;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private volatile boolean closed;
    private volatile TestHook testHook;

    interface TestHook {
      void afterOpenCheck(String operation);
    }

    SpeculativeQueueManager() {
      speculativeQueues = new ConcurrentHashMap<>();
      currentSpeculationId = new ThreadLocal<>();
    }

    void clear(CommandQueue result) {
      lifecycleLock.writeLock().lock();
      try {
        closed = true;
        speculativeQueues.clear();
        result.clear();
      } finally {
        lifecycleLock.writeLock().unlock();
      }
    }

    int speculation() {
      lifecycleLock.readLock().lock();
      try {
        if (closed) return -1;
        afterOpenCheck("speculation");
        int nextId = getNextSpeculationId();
        if (nextId != -1) {
          speculativeQueues.put(nextId, new MpmcArrayQueue<>(MAX_SPECULATIVE_MSG_LIMIT));
        }
        return nextId;
      } finally {
        lifecycleLock.readLock().unlock();
      }
    }

    void send(Command cmd, CommandQueue result) {
      lifecycleLock.readLock().lock();
      try {
        if (closed) return;
        afterOpenCheck("send");
        Integer curId = currentSpeculationId.get();
        if ((curId != null) && (cmd.getType() != Command.EXIT)) {
          MpmcArrayQueue<Command> sb = speculativeQueues.get(curId);
          if (sb != null) {
            if (!sb.offer(cmd)) {
              sb.clear();
              sb.offer(new MessageCommand("speculative buffer overflow: " + curId));
            }
            return;
          }
        }
        result.enqueue(cmd);
      } finally {
        lifecycleLock.readLock().unlock();
      }
    }

    void speculate(int id) {
      lifecycleLock.readLock().lock();
      try {
        if (closed) return;
        afterOpenCheck("speculate");
        validateId(id);
        currentSpeculationId.set(id);
      } finally {
        lifecycleLock.readLock().unlock();
      }
    }

    void commit(int id, CommandQueue result) {
      lifecycleLock.readLock().lock();
      try {
        if (closed) return;
        afterOpenCheck("commit");
        validateId(id);
        currentSpeculationId.set(null);
        MpmcArrayQueue<Command> sb = speculativeQueues.get(id);
        if (sb != null) {
          result.addAll(sb);
          sb.clear();
          speculativeQueues.remove(id);
        }
      } finally {
        lifecycleLock.readLock().unlock();
      }
    }

    void discard(int id) {
      lifecycleLock.readLock().lock();
      try {
        if (closed) return;
        afterOpenCheck("discard");
        validateId(id);
        currentSpeculationId.set(null);
        MpmcArrayQueue<Command> sb = speculativeQueues.get(id);
        if (sb != null) {
          sb.clear();
          speculativeQueues.remove(id);
        }
      } finally {
        lifecycleLock.readLock().unlock();
      }
    }

    // -- Internals only below this point
    void setTestHook(TestHook testHook) {
      this.testHook = testHook;
    }

    int speculativeQueueCountForTest() {
      return speculativeQueues.size();
    }

    private void afterOpenCheck(String operation) {
      TestHook hook = testHook;
      if (hook != null) {
        hook.afterOpenCheck(operation);
      }
    }

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
  private volatile BTraceMBean.Registration mbeanRegistration;

  private final AtomicBoolean exitting = new AtomicBoolean(false);
  private static final long TERMINAL_MARKER_ACK_TIMEOUT_MILLIS = 2000L;
  private final AtomicBoolean terminalShutdownRequested = new AtomicBoolean(false);
  private final AtomicBoolean terminalExitQueued = new AtomicBoolean(false);
  private final CountDownLatch terminalMarkerAcknowledged = new CountDownLatch(1);
  private final CountDownLatch terminalExitDispatched = new CountDownLatch(1);
  private volatile MessageCommand terminalMarker;
  private volatile int terminalExitCode;
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
  protected static final Map<String, ByteBuffer> counters = new ConcurrentHashMap<>();

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
    queue = new CommandQueue(CMD_QUEUE_LIMIT);
    specQueueManager = new SpeculativeQueueManager();
    this.className = className;
    instrumentation = inst;

    BTraceRuntimeAccessImpl.addRuntime(className, this);

    cmdThread =
        new Thread(
            () -> {
              try {
                enter();
                queue.drain(
                    new ConsumerWrapper(cmdListener, exitting), waitStrategy, exitCondition);
              } finally {
                specQueueManager.clear(queue);
                leave();
                disabled = true;
              }
            });
    cmdThread.setDaemon(true);
    cmdThread.start();
  }

  @Override
  public final String getClassName() {
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

    // Level is now stored on the runtime instance instead of the probe class field.
    // This allows level checking to happen at MethodHandle linking time without relying
    // on a bytecode-level field that was never properly initialized.
    int levelVal = BTraceRuntime.parseInt(args.get("level"), Integer.MIN_VALUE);
    if (levelVal > Integer.MIN_VALUE) {
      setLevel(levelVal);
    }

    // Attempt to set the legacy $btrace$$level field if it exists (for backward compatibility)
    try {
      level = cl.getDeclaredField("$btrace$$level");
      level.setAccessible(true);
      if (levelVal > Integer.MIN_VALUE) {
        level.set(null, levelVal);
      }
    } catch (Throwable e) {
      log.debug(
          "Instrumentation level field not available (this is OK with MethodHandle-based guards)",
          e);
    }

    mbeanRegistration = BTraceMBean.registerMBean(clazz);
  }

  /**
   * start method is called by every BTrace (preprocesed) class just at the end of it's class
   * initializer.
   */
  public final void start() {
    initMBeans();
    if (timerHandlers != null) {
      try {
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
      } catch (Exception e) {
        log.error("Timer creation/scheduling failed", e);
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
  public final void handleEvent(Object cmd) {
    if (!(cmd instanceof EventCommand)) {
      return;
    }
    EventCommand ecmd = (EventCommand) cmd;
    if (eventHandlers != null) {
      Map<String, Method> localMap = eventHandlerMap;
      if (localMap == null) {
        synchronized (this) {
          if (eventHandlerMap == null) {
            Map<String, Method> m = new HashMap<>();
            for (EventHandler eh : eventHandlers) {
              try {
                String eventName = args.template(eh.getEvent());
                m.put(eventName, eh.getMethod(clazz));
              } catch (NoSuchMethodException ignored) {
              }
            }
            eventHandlerMap = m;
            localMap = m;
          } else {
            localMap = eventHandlerMap;
          }
        }
      }
      String event = ecmd.getEvent();
      event = event != null ? event : EventHandler.ALL_EVENTS;

      Method eventHandler = localMap.get(event);
      if (eventHandler != null) {
        BTraceRuntimeAccessImpl.doWithCurrent(
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
    requestTerminalShutdown(0);
  }

  /**
   * Leave method is called by every probed method just before the probe actions end (and actual
   * probed method continues).
   */
  @Override
  public final void leave() {
    BTraceRuntimeAccessImpl.leaveInternal();
  }

  /** Handles exception from BTrace probe actions. */
  @Override
  public final void handleException(Throwable th) {
    if (currentException.get() != null) {
      // A throwing @OnError handler re-enters through the woven exception bridge while the
      // original failure is still being handled. Do not recurse into user code, but do not make
      // that second failure invisible to the target operator either.
      log.error("BTrace error-handler execution failed", th);
      return;
    }
    boolean entered = BTraceRuntimeAccessImpl.enterInternal(this);
    try {
      currentException.set(th);

      if (th instanceof ExitException) {
        requestTerminalShutdown(((ExitException) th).exitCode());
      } else {
        log.error("BTrace handler execution failed", th);
        if (errorHandlers != null) {
          for (ErrorHandler eh : errorHandlers) {
            // @OnError handlers are guarded: their woven prologue calls enter(runtime),
            // which refuses while this thread is already entered (we are inside the probe's
            // catch block). Dispatch through doWithCurrent so the thread-local runtime is
            // escaped to null and the handler can enter itself. Without this the handler
            // silently bails out and the error is lost.
            BTraceRuntimeAccessImpl.doWithCurrent(
                (Callable<Void>)
                    () -> {
                      eh.getMethod(clazz).invoke(null, th);
                      return null;
                    });
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

  /**
   * Returns the script arguments map.
   *
   * @return the args map, or null if no arguments
   */
  public final ArgsMap getArgsMap() {
    return args;
  }

  // Direct access to extension implementations is not supported; services are injected via
  // invokedynamic and resolved by the bridge.

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
          log.warn("Failed to load DOTWriter properties from classpath", ioExp);
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
        log.warn("Failed to load DOTWriter properties from user home", exp);
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
    return BTraceRuntimeAccessImpl.enterInternal(this);
  }

  @Override
  public final void handleExit(int exitCode) {
    requestTerminalShutdown(exitCode);
  }

  /**
   * Starts the one-way runtime-owned terminal handshake. The command queue deliberately remains
   * live until its consumer has delivered the marker and queued the corresponding {@link
   * ExitCommand}; closing it here would race the diagnostic with transport teardown.
   */
  private void requestTerminalShutdown(int exitCode) {
    if (terminalShutdownRequested.compareAndSet(false, true)) {
      terminalExitCode = exitCode;
      String cleanupOutcome = completeTerminalCleanup(exitCode);
      MessageCommand marker = new MessageCommand("[BTRACE] terminal cleanup: " + cleanupOutcome);
      terminalMarker = marker;
      if (queue != null) {
        queue.enqueue(marker);
      } else {
        log.warn("Cannot dispatch terminal cleanup marker without a command queue");
      }
    }

    // The queue consumer cannot acknowledge a marker while it is executing this call. An
    // application/agent caller may wait briefly for the diagnostic and generated Exit to be
    // dispatched, but timeout is not terminal failure: the consumer remains responsible for
    // eventually producing Exit.
    if (Thread.currentThread() != cmdThread) {
      try {
        terminalMarkerAcknowledged.await(TERMINAL_MARKER_ACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        terminalExitDispatched.await(TERMINAL_MARKER_ACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private String completeTerminalCleanup(int exitCode) {
    StringBuilder outcome = new StringBuilder();
    try {
      exitImpl(exitCode);
      outcome.append("runtime=cleaned");
    } catch (Throwable t) {
      log.error("BTrace exit cleanup failed", t);
      outcome.append("runtime=failed");
    }
    try {
      cleanupRuntime();
    } catch (Throwable t) {
      log.error("BTrace runtime cleanup failed", t);
      outcome.append(",runtime-finalizer=failed");
    }

    BTraceMBean.Registration registration = mbeanRegistration;
    if (registration == null) {
      outcome.append(",mbean=absent");
    } else {
      outcome.append(",mbean=").append(registration.closeAndReport());
    }
    return outcome.toString();
  }

  /** Called only by the command-thread consumer after successful marker delivery. */
  private void acknowledgeTerminalMarker(Command command) {
    MessageCommand marker = terminalMarker;
    if (command != marker || marker == null || !terminalExitQueued.compareAndSet(false, true)) {
      return;
    }
    terminalMarkerAcknowledged.countDown();
    if (queue != null && !queue.enqueue(new ExitCommand(terminalExitCode))) {
      log.error("Unable to enqueue terminal BTrace exit command");
    }
  }

  public final int getLevel() {
    // First try to read from the probe class field (legacy, for backward compatibility)
    if (level != null) {
      try {
        return (int) level.get(null);
      } catch (IllegalAccessException e) {
        // Field exists but cannot be accessed; use fallback
        log.debug("Cannot access legacy level field, using runtime value", e);
      }
    }
    // Fall back to runtime-stored level value (primary source)
    return levelValue;
  }

  public final void setLevel(int level) {
    // Always store in runtime value field (primary source)
    this.levelValue = level;
    // Also try to set on probe class field if it exists (legacy, for backward compatibility)
    if (this.level != null) {
      try {
        this.level.set(null, level);
      } catch (IllegalAccessException e) {
        // Field exists but cannot be accessed; that's okay, levelValue is primary
        log.debug("Cannot update legacy level field (will use runtime value)", e);
      }
    }
  }

  protected void cleanupRuntime() {
    // to be overridden by concrete implementations
  }

  final void registerExtension(Extension ext) {
    if (ext == null) {
      return;
    }
    synchronized (extensions) {
      extensions.add(ext);
    }
  }

  private void cleanupExtensions() {
    if (extensionsClosed) {
      return;
    }
    synchronized (extensions) {
      if (extensionsClosed) {
        return;
      }
      extensionsClosed = true;
      for (Extension ext : extensions) {
        try {
          ext.close();
        } catch (Throwable ignore) {
          // best effort cleanup
        }
      }
      extensions.clear();
    }
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
          btracePkg = loader.getResource("io/btrace");
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
    return BTraceRuntimeAccessImpl.getCurrent();
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
                          handler.invoke(clazz, info.getUsage());
                        } catch (Throwable th) {
                          log.debug("Low-memory handler {} failed", handler.method, th);
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
  public final void sendCommand(Object cmd) {
    if (cmd instanceof Command) {
      send((Command) cmd);
    }
  }

  @Override
  public final void sendNumberData(String name, Number value) {
    send(new NumberDataCommand(name, value));
  }

  @Override
  public final void sendNumberMapData(String name, Map<String, ? extends Number> data) {
    send(new NumberMapDataCommand(name, data));
  }

  @Override
  public final void sendStringMapData(String name, Map<String, String> data) {
    send(new StringMapDataCommand(name, data));
  }

  @Override
  public final void sendGridData(String name, List<Object[]> data) {
    send(new GridDataCommand(name, data));
  }

  @Override
  public final void sendGridData(String name, List<Object[]> data, String format) {
    send(new GridDataCommand(name, data, format));
  }

  public final void send(Command cmd) {
    specQueueManager.send(cmd, queue);
  }

  private void enqueue(Command cmd) {
    if (queue != null) {
      queue.enqueue(cmd);
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
              } catch (Throwable t) {
                log.warn("Failed to acquire timer handler method", t);
              }
              mthd = m;
            }

            @Override
            public void run() {
              if (mthd != null) {
                try {
                  mthd.invoke(null, (Object[]) null);
                } catch (Throwable th) {
                  log.warn("Timer task execution failed", th);
                }
              }
            }
          };
    }
  }

  @Override
  public final void exit(int exitCode) {
    requestTerminalShutdown(exitCode);
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

      cleanupExtensions();
    } finally {
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
      String arg0 = args.get(0);
      String sanitized = sanitizePathSegment(arg0);
      if (!sanitized.isEmpty()) {
        buf.append(sanitized);
      }
    }
    buf.append(File.separatorChar);
    buf.append(className);
    new File(buf.toString()).mkdirs();
    buf.append(File.separatorChar);
    buf.append(name);
    return buf.toString();
  }

  private static String sanitizePathSegment(String s) {
    if (s == null) return "";
    // replace path separators and control chars with underscore; allow alnum, dash, underscore, dot
    String sanitized = s.replace(File.separatorChar, '_').replace('/', '_').replace('\\', '_');
    sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
    // collapse multiple underscores
    sanitized = sanitized.replaceAll("_+", "_");
    return sanitized;
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
    // Probe for jvmstat availability by attempting to load a core jvmstat class. The previous
    // check used String.class.getResource("sun/jvmstat/monitor/MonitoredHost.class"), a relative
    // resource path resolved against java/lang/ (and module-confined on JDK 9+), which is null on
    // every JDK - so the real reader was never used and every perf* built-in threw.
    try {
      Class.forName("sun.jvmstat.monitor.MonitoredHost");
      Class<?> implClz = Class.forName("io.btrace.agent.PerfReaderImpl");
      return (PerfReader) implClz.getDeclaredConstructor().newInstance();
    } catch (Throwable exp) {
      // jvmstat not available (e.g. a minimal jlink image) or the reader could not be created
    }
    // no luck, create null implementation
    return new NullPerfReaderImpl();
  }
}
