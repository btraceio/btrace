/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace;

import com.sun.btrace.instr.RunnableGenerator;
import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.btrace.aggregation.Aggregation;
import com.sun.btrace.aggregation.AggregationKey;
import com.sun.btrace.aggregation.AggregationFunction;
import com.sun.btrace.annotations.OnError;
import com.sun.btrace.annotations.OnExit;
import com.sun.btrace.annotations.OnTimer;
import com.sun.btrace.annotations.OnEvent;
import com.sun.btrace.annotations.OnLowMemory;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.EventCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.NumberDataCommand;
import com.sun.btrace.comm.NumberMapDataCommand;
import com.sun.btrace.comm.StringMapDataCommand;
import com.sun.btrace.comm.GridDataCommand;
import com.sun.btrace.org.jctools.queues.MessagePassingQueue;
import com.sun.btrace.org.jctools.queues.MpmcArrayQueue;
import com.sun.btrace.org.jctools.queues.MpscArrayQueue;
import com.sun.btrace.profiling.MethodInvocationProfiler;

import java.lang.management.GarbageCollectorMXBean;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.lang.management.LockInfo;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import sun.misc.Perf;
import sun.misc.Unsafe;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import sun.security.action.GetPropertyAction;


/**
 * Helper class used by BTrace built-in functions and
 * also acts runtime "manager" for a specific BTrace client
 * and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
public final class BTraceRuntime  {
    private static final class RTWrapper {
       private BTraceRuntime rt = null;

        boolean set(BTraceRuntime other) {
            if (rt != null && other != null) return false;
            rt = other;
            return true;
        }

        void escape(Callable<Void> c) {
            BTraceRuntime oldRuntime = rt;
            rt = null;
            try {
                c.call();
            } catch (Exception ignored) {
            } finally {
                if (oldRuntime != null) {
                    rt = oldRuntime;
                }
            }
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
    // we need Unsafe to load BTrace class bytes as
    // bootstrap class
    private static volatile Unsafe unsafe = null;

    private static Properties dotWriterProps;

    // a dummy BTraceRuntime instance
    private static BTraceRuntime dummy;

    // are we running with DTrace support enabled?
    private static boolean dtraceEnabled;

    private static final boolean messageTimestamp = false;
    private static final String LINE_SEPARATOR;

    private static boolean isNewerThan8 = false;

    // the command FIFO queue related settings
    private static final int CMD_QUEUE_LIMIT_DEFAULT = 100;
    public static final String CMD_QUEUE_LIMIT_KEY = "com.sun.btrace.runtime.cmdQueueLimit";

    // the command FIFO queue upper limit
    private static int CMD_QUEUE_LIMIT;

    static {
        setupCmdQueueParams();

        try {
            Reflection.class.getMethod("getCallerClass");
            isNewerThan8 = true;
        } catch (NoSuchMethodException | SecurityException ex) {
            // ignore
        }
        // ignore


        dummy = new BTraceRuntime();
        LINE_SEPARATOR = System.getProperty("line.separator");
    }

    private static ThreadLocal<RTWrapper> rt = new ThreadLocal<RTWrapper>() {
        @Override
        protected RTWrapper initialValue() {
            return new RTWrapper();
        }
    };

    // BTraceRuntime against BTrace class name
    private static Map<String, BTraceRuntime> runtimes = new ConcurrentHashMap<>();

    // a set of all the client names connected so far
    private static Set<String> clients = new HashSet<>();

    // jvmstat related stuff
    // to read and write perf counters
    private static volatile Perf perf;
    // interface to read perf counters of this process
    private static volatile PerfReader perfReader;
    // performance counters created by this client
    private static Map<String, ByteBuffer> counters = new HashMap<>();

    // Few MBeans used to implement certain built-in functions
    private static volatile HotSpotDiagnosticMXBean hotspotMBean;
    private static volatile MemoryMXBean memoryMBean;
    private static volatile RuntimeMXBean runtimeMBean;
    private static volatile ThreadMXBean threadMBean;
    private static volatile List<GarbageCollectorMXBean> gcBeanList;
    private static volatile List<MemoryPoolMXBean> memPoolList;
    private static volatile OperatingSystemMXBean operatingSystemMXBean;

    // bytecode generator that generates Runnable implementations
    private static RunnableGenerator runnableGenerator;

    // Per-client state starts here.

    private final DebugSupport debug;

    // current thread's exception
    private ThreadLocal<Throwable> currentException = new ThreadLocal<>();

    // "command line" args supplied by client
    private final String[] args;

    // whether current runtime has been disabled?
    private volatile boolean disabled;

    // Class object of the BTrace class [of this client]
    private final String className;

    // BTrace Class object corresponding to this client
    private Class clazz;

    // instrumentation level field for each runtime
    private Field level;

    // does the client have exit action?
    private Method exitHandler;

    // does the client have exception handler action?
    private Method exceptionHandler;

    // array of timer callback methods
    private Method[] timerHandlers;

    // map of client event handling methods
    private Map<String, Method> eventHandlers;

    // low memory handlers
    private Map<String, Method> lowMemHandlers;

    // timer to run profile provider actions
    private volatile Timer timer;

    // executer to run low memory handlers
    private volatile ExecutorService threadPool;
    // Memory MBean listener
    private volatile NotificationListener memoryListener;

    // Command queue for the client
    private final MpscArrayQueue<Command> queue;

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
                speculativeQueues.put(nextId,
                        new MpmcArrayQueue<Command>(MAX_SPECULATIVE_MSG_LIMIT));
            }
            return nextId;
        }

        boolean send(Command cmd) {
            if (currentSpeculationId != null){
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

        void commit(int id, MpscArrayQueue<Command> result) {
            validateId(id);
            currentSpeculationId.set(null);
            final MpmcArrayQueue<Command> sb = speculativeQueues.get(id);
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
            if (! speculativeQueues.containsKey(id)) {
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
    private final MessagePassingQueue.WaitStrategy waitStrategy = new MessagePassingQueue.WaitStrategy() {
        @Override
        public int idle(int i) {
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
            }
            return i+1;
        }
    };
    private final MessagePassingQueue.ExitCondition exitCondition = new MessagePassingQueue.ExitCondition() {
        @Override
        public boolean keepRunning() {
            return !exitting.get();
        }
    };

    private BTraceRuntime() {
        debug = new DebugSupport(null);
        args = null;
        queue = null;
        specQueueManager = null;
        className = null;
        instrumentation = null;
    }

    public BTraceRuntime(final String className, String[] args,
                         final CommandListener cmdListener,
                         DebugSupport ds, Instrumentation inst) {
        this.args = args;
        this.queue = new MpscArrayQueue<>(CMD_QUEUE_LIMIT_DEFAULT);
        this.specQueueManager = new SpeculativeQueueManager();
        this.className = className;
        this.instrumentation = inst;
        this.debug = ds != null ? ds : new DebugSupport(null);

        runtimes.put(className, this);
        clients.add(className);
        this.cmdThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BTraceRuntime.enter();
                    queue.drain(
                        new ConsumerWrapper(cmdListener, exitting),
                        waitStrategy, exitCondition
                    );
                } finally {
                    runtimes.remove(className);
                    queue.clear();
                    specQueueManager.clear();
                    BTraceRuntime.leave();
                    disabled = true;
                }
            }
        });
        cmdThread.setDaemon(true);
        cmdThread.start();
    }

    public static void initUnsafe() {
        if (unsafe == null) {
            unsafe = Unsafe.getUnsafe();
        }
    }

    static int getInstrumentationLevel() {
        BTraceRuntime cur = getCurrent();

        try {
            return cur.level.getInt(cur);
        } catch (Exception e) {
            return 0;
        }
    }

    static void setInstrumentationLevel(int level) {
        BTraceRuntime cur = getCurrent();
        try {
            cur.level.set(cur, level);
        } catch (Exception e) {
            // ignore
        }
    }

    public static boolean classNameExists(String name) {
        return clients.contains(name);
    }

    @CallerSensitive
    public static void init(PerfReader perfRead, RunnableGenerator runGen) {
        initUnsafe();

        Class caller = isNewerThan8 ? Reflection.getCallerClass() : Reflection.getCallerClass(2);
        if (! caller.getName().equals("com.sun.btrace.agent.Client")) {
            throw new SecurityException("unsafe init");
        }
        perfReader = perfRead;
        runnableGenerator = runGen;
        loadLibrary(perfRead.getClass().getClassLoader());
    }

    @CallerSensitive
    public Class defineClass(byte[] code) {
        Class caller = isNewerThan8 ? Reflection.getCallerClass() : Reflection.getCallerClass(2);
        if (! caller.getName().equals("com.sun.btrace.agent.Client")) {
            throw new SecurityException("unsafe defineClass");
        }
        return defineClassImpl(code, true);
    }

    @CallerSensitive
    public Class defineClass(byte[] code, boolean mustBeBootstrap) {
        Class caller = isNewerThan8 ? Reflection.getCallerClass() : Reflection.getCallerClass(2);
        if (! caller.getName().equals("com.sun.btrace.agent.Client")) {
            throw new SecurityException("unsafe defineClass");
        }
        return defineClassImpl(code, mustBeBootstrap);
    }

    /**
     * Enter method is called by every probed method just
     * before the probe actions start.
     */
    public static boolean enter(BTraceRuntime current) {
        if (current.disabled) return false;
        return rt.get().set(current);
    }

    public static boolean enter() {
        return enter(dummy);
    }

    /**
     * Leave method is called by every probed method just
     * before the probe actions end (and actual probed
     * method continues).
     */
    public static void leave() {
        rt.get().set(null);
    }

    /**
     * start method is called by every BTrace (preprocesed) class
     * just at the end of it's class initializer.
     */
    public static void start() {
        BTraceRuntime current = getCurrent();
        if (current != null) {
           current.startImpl();
        }
    }

    public void handleExit(int exitCode) {
        exitImpl(exitCode);
        try {
            cmdThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void handleEvent(EventCommand ecmd) {
        if (eventHandlers != null) {
            String event = ecmd.getEvent();
            final Method eventHandler = eventHandlers.get(event);
            if (eventHandler != null) {
                rt.get().escape(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        eventHandler.invoke(null, (Object[])null);
                        return null;
                    }
                });
            }
        }
    }

    /**
     * One instance of BTraceRuntime is created per-client.
     * This forClass method creates it. Class passed is the
     * preprocessed BTrace program of the client.
     */
    public static BTraceRuntime forClass(Class cl) {
        BTraceRuntime runtime = runtimes.get(cl.getName());
        runtime.init(cl);
        return runtime;
    }

    /**
     * Utility to create a new ThreadLocal object. Called
     * by preprocessed BTrace class to create ThreadLocal
     * for each @TLS variable.
     * @param initValue Initial value.
     *                  This value must be either a boxed primitive or {@linkplain Cloneable}.
     *                  In case a {@linkplain Cloneable} value is provided the value is never used directly
     *                  - instead, a new clone of the value is created per thread.
     */
    public static ThreadLocal newThreadLocal(
                final Object initValue) {
        return new ThreadLocal() {
            @Override
            protected Object initialValue() {
                if (initValue == null) return initValue;

                if (initValue instanceof Cloneable) {
                    try {
                        Class clz = initValue.getClass();
                        Method m = clz.getDeclaredMethod("clone");
                        m.setAccessible(true);
                        return m.invoke(initValue);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                return initValue;
            }
        };
    }

    // The following constants are copied from VM code
    // for jvmstat.

    // perf counter variability - we always variable variability
    private static final int V_Variable = 3;
    // perf counter units
    private static final int V_None = 1;
    private static final int V_String = 5;
    private static final int PERF_STRING_LIMIT = 256;

    /**
     * Utility to create a new jvmstat perf counter. Called
     * by preprocessed BTrace class to create perf counter
     * for each @Export variable.
     */
    public static void newPerfCounter(String name, String desc, Object value) {
        newPerfCounter(value, name, desc);
    }

    public static void newPerfCounter(Object value, String name, String desc) {
        Perf perf = getPerf();
        char tc = desc.charAt(0);
        switch (tc) {
            case 'C':
            case 'Z':
            case 'B':
            case 'S':
            case 'I':
            case 'J':
            case 'F':
            case 'D': {
                long initValue = (value != null)? ((Number)value).longValue() : 0L;
                ByteBuffer b = perf.createLong(name, V_Variable, V_None, initValue);
                b.order(ByteOrder.nativeOrder());
                counters.put(name, b);
            }
            break;

            case '[':
                break;
            case 'L': {
                if (desc.equals("Ljava/lang/String;")) {
                    byte[] buf;
                    if (value != null) {
                        buf = getStringBytes((String)value);
                    } else {
                        buf = new byte[PERF_STRING_LIMIT];
                        buf[0] = '\0';
                    }
                    ByteBuffer b = perf.createByteArray(name, V_Variable, V_String,
                        buf, buf.length);
                    counters.put(name, b);
                }
            }
            break;
        }
    }

    /**
     * Return the value of integer perf. counter of given name.
     */
    public static int getPerfInt(String name) {
        return (int) getPerfLong(name);
    }

    /**
     * Write the value of integer perf. counter of given name.
     */
    public static void putPerfInt(int value, String name) {
        long l = (long)value;
        putPerfLong(l, name);
    }

    /**
     * Return the value of float perf. counter of given name.
     */
    public static float getPerfFloat(String name) {
        int val = getPerfInt(name);
        return Float.intBitsToFloat(val);
    }

    /**
     * Write the value of float perf. counter of given name.
     */
    public static void putPerfFloat(float value, String name) {
        int i = Float.floatToRawIntBits(value);
        putPerfInt(i, name);
    }

    /**
     * Return the value of long perf. counter of given name.
     */
    public static long getPerfLong(String name) {
        ByteBuffer b = counters.get(name);
        synchronized(b) {
            long l = b.getLong();
            b.rewind();
            return l;
        }
    }

    /**
     * Write the value of float perf. counter of given name.
     */
    public static void putPerfLong(long value, String name) {
        ByteBuffer b = counters.get(name);
        synchronized (b) {
            b.putLong(value);
            b.rewind();
        }
    }

    /**
     * Return the value of double perf. counter of given name.
     */
    public static double getPerfDouble(String name) {
        long val = getPerfLong(name);
        return Double.longBitsToDouble(val);
    }

    /**
     * write the value of double perf. counter of given name.
     */
    public static void putPerfDouble(double value, String name) {
        long l = Double.doubleToRawLongBits(value);
        putPerfLong(l, name);
    }

    /**
     * Return the value of String perf. counter of given name.
     */
    public static String getPerfString(String name) {
        ByteBuffer b = counters.get(name);
        byte[] buf = new byte[b.limit()];
        byte t = (byte)0;
        int i = 0;
        synchronized (b) {
            while ((t = b.get()) != '\0') {
                buf[i++] = t;
            }
            b.rewind();
        }
        try {
            return new String(buf, 0, i, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // ignore, UTF-8 encoding is always known
        }
        return "";
    }

    /**
     * Write the value of float perf. counter of given name.
     */
    public static void putPerfString(String value, String name) {
        ByteBuffer b = counters.get(name);
        byte[] v = getStringBytes(value);
        synchronized (b) {
           b.put(v);
           b.rewind();
        }
    }


    /**
     * Handles exception from BTrace probe actions.
     */
    public static void handleException(Throwable th) {
        BTraceRuntime current = getCurrent();
        if (current != null) {
            current.handleExceptionImpl(th);
        } else {
            th.printStackTrace();
        }
    }

    public static String safeStr(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return (String) obj;
        } else if (obj.getClass().getClassLoader() == null) {
            try {
                String str = obj.toString();
                return str;
            } catch (NullPointerException e) {
                // NPE can be thrown from inside the toString() method we have no control over
                return "null";
            } catch (Throwable e) {
                e.printStackTrace();
                return "error";
            }
        } else {
            return identityStr(obj);
        }
    }

    private static String identityStr(Object obj) {
        int hashCode = java.lang.System.identityHashCode(obj);
        return obj.getClass().getName() + "@" + Integer.toHexString(hashCode);
    }

    // package-private interface to BTraceUtils class.

    static int speculation() {
        BTraceRuntime current = getCurrent();
        return current.specQueueManager.speculation();
    }

    static void speculate(int id) {
        BTraceRuntime current = getCurrent();
        current.specQueueManager.speculate(id);
    }

    static void discard(int id) {
        BTraceRuntime current = getCurrent();
        current.specQueueManager.discard(id);
    }

    static void commit(int id) {
        BTraceRuntime current = getCurrent();
        current.specQueueManager.commit(id, current.queue);
    }

    /**
     * Indicates whether two given objects are "equal to" one another.
     * For bootstrap classes, returns the result of calling Object.equals()
     * override. For non-bootstrap classes, the reference identity comparison
     * is done.
     *
     * @param  obj1 first object to compare equality
     * @param  obj2 second object to compare equality
     * @return <code>true</code> if the given objects are equal;
     *         <code>false</code> otherwise.
     */
    static boolean compare(Object obj1, Object obj2) {
        if (obj1 instanceof String) {
            return obj1.equals(obj2);
        } else if (obj1.getClass().getClassLoader() == null) {
            if (obj2 == null || obj2.getClass().getClassLoader() == null) {
                return obj1.equals(obj2);
            } // else fall through..
        }
        return obj1 == obj2;
    }

    // BTrace map functions
    static <K, V> Map<K, V> newHashMap() {
        return new BTraceMap(new HashMap<K, V>());
    }

    static <K, V> Map<K, V> newWeakMap() {
        return new BTraceMap(new WeakHashMap<K, V>());
    }

    static <V> Deque<V> newDeque() {
        return new BTraceDeque<>(new ArrayDeque<V>());
    }

    static Appendable newStringBuilder(boolean threadSafe) {
    	return threadSafe ? new StringBuffer() : new StringBuilder();
    }

    static Appendable newStringBuilder() {
    	return newStringBuilder(false);
    }

    static <E> int size(Collection<E> coll) {
        if (coll instanceof BTraceCollection || coll.getClass().getClassLoader() == null) {
            return coll.size();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <E> boolean isEmpty(Collection<E> coll) {
        if (coll instanceof BTraceCollection || coll.getClass().getClassLoader() == null) {
            return coll.isEmpty();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <E> boolean contains(Collection<E> coll, Object obj) {
        if (coll instanceof BTraceCollection || coll.getClass().getClassLoader() == null) {
            for (E e : coll) {
                if (compare(e, obj)) {
                    return true;
                }
            }
            return false;
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <E >Object[] toArray(Collection<E> collection) {
    	if (collection == null) {
    		return new Object[0];
    	} else {
    		return collection.toArray();
    	}
    }

    static <K, V> V get(Map<K, V> map, K key) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.get(key);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K, V> boolean containsKey(Map<K, V> map, K key) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.containsKey(key);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K, V> boolean containsValue(Map<K, V> map, V value) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.containsValue(value);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K, V> V put(Map<K, V> map, K key, V value) {
        if (map instanceof BTraceMap) {
            return map.put(key, value);
        } else {
            throw new IllegalArgumentException("not a btrace map");
        }
    }

    static <K, V> V remove(Map<K, V> map, K key) {
        if (map instanceof BTraceMap) {
            return map.remove(key);
        } else {
            throw new IllegalArgumentException("not a btrace map");
        }
    }

    static <K, V> void clear(Map<K, V> map) {
        if (map instanceof BTraceMap) {
            map.clear();
        } else {
            throw new IllegalArgumentException("not a btrace map");
        }
    }

    static <K, V> int size(Map<K, V> map) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.size();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K, V> boolean isEmpty(Map<K, V> map) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.isEmpty();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K,V> void putAll(Map<K,V> src, Map<K,V> dst) {
        dst.putAll(src);
    }

    static <K, V> void copy(Map<K,V> src, Map<K,V> dst) {
        dst.clear();
        dst.putAll(src);
    }

    static void printMap(Map map) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            synchronized(map) {
                Map<String, String> m = new HashMap<>();
                Set<Map.Entry<Object, Object>> entries = map.entrySet();
                for (Map.Entry<Object, Object> e : entries) {
                   m.put(BTraceUtils.Strings.str(e.getKey()), BTraceUtils.Strings.str(e.getValue()));
                }
                printStringMap(null, m);
            }
        } else {
            print(BTraceUtils.Strings.str(map));
        }
    }

    public static <V> void push(Deque<V> queue, V value) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            queue.push(value);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> void addLast(Deque<V> queue, V value) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            queue.addLast(value);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> V peekFirst(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            return queue.peekFirst();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> V peekLast(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            return queue.peekLast();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> V removeLast(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            return queue.removeLast();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> V removeFirst(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            return queue.removeFirst();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> V poll(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            return queue.poll();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> V peek(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            return queue.peek();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <V> void clear(Deque<V> queue) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            queue.clear();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static Appendable append(Appendable buffer, String strToAppend) {
    	try {
            if (buffer != null && strToAppend != null) {
                return buffer.append(strToAppend);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static int length(Appendable buffer) {
    	if (buffer != null && buffer instanceof CharSequence) {
            return ((CharSequence)buffer).length();
    	} else {
            throw new IllegalArgumentException();
    	}
    }

    static void printNumber(String name, Number value) {
        getCurrent().send(new NumberDataCommand(name, value));
    }

    static void printNumberMap(String name, Map<String, ? extends Number> data) {
        getCurrent().send(new NumberMapDataCommand(name, data));
    }

    static void printStringMap(String name, Map<String, String> data) {
        getCurrent().send(new StringMapDataCommand(name, data));
    }

    // BTrace exit built-in function
    static void exit(int exitCode) {
        BTraceRuntime runtime = getCurrent();
        if (runtime != null) {
            Throwable th = runtime.currentException.get();
            if (! (th instanceof ExitException)) {
                runtime.currentException.set(null);
            }
            throw new ExitException(exitCode);
        }
    }

    public static void retransform(String runtimeName, Class<?> clazz) {
        try {
            BTraceRuntime rt = runtimes.get(runtimeName);
            if (rt != null && rt.instrumentation.isModifiableClass(clazz)) {
                rt.instrumentation.retransformClasses(clazz);
            }
        } catch (Throwable e) {
            warning(e);
        }
    }

    static long sizeof(Object obj) {
        BTraceRuntime runtime = getCurrent();
        return runtime.instrumentation.getObjectSize(obj);
    }

    // BTrace command line argument functions
    static int $length() {
        BTraceRuntime runtime = getCurrent();
        return runtime.args == null? 0 : runtime.args.length;
    }

    static String $(int n) {
        BTraceRuntime runtime = getCurrent();
        if (runtime.args == null) {
            return null;
        } else {
            if (n >= 0 && n < runtime.args.length) {
                return runtime.args[n];
            } else {
                return null;
            }
        }
    }

    /**
     * @see BTraceUtils#instanceOf(java.lang.Object, java.lang.String)
     */
    static boolean instanceOf(Object obj, String className) {
        if (obj instanceof AnyType) {
            // the only time we can have AnyType on stack
            // is if it was passed in as a placeholder
            // for void @Return parameter value
            if (className.equalsIgnoreCase("void")) {
                return obj.equals(AnyType.VOID);
            }
            return false;
        }
        Class objClass = obj.getClass();
        ClassLoader cl = objClass.getClassLoader();
        cl = cl != null ? cl : ClassLoader.getSystemClassLoader();
        try {
            Class target = cl.loadClass(className);
            return target.isAssignableFrom(objClass);
        } catch (ClassNotFoundException e) {
            // non-existing class
            getCurrent().debugPrint(e);
            return false;
        }
    }

    private final static class BTraceAtomicInteger extends AtomicInteger {
        BTraceAtomicInteger(int initVal) {
            super(initVal);
        }
    }

    static AtomicInteger newAtomicInteger(int initVal) {
        return new BTraceAtomicInteger(initVal);
    }

    static int get(AtomicInteger ai) {
        if (ai instanceof BTraceAtomicInteger ||
            ai.getClass().getClassLoader() == null) {
            return ai.get();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static void set(AtomicInteger ai, int i) {
        if (ai instanceof BTraceAtomicInteger) {
            ai.set(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static void lazySet(AtomicInteger ai, int i) {
        if (ai instanceof BTraceAtomicInteger) {
            ai.lazySet(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static boolean compareAndSet(AtomicInteger ai, int i, int j) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.compareAndSet(i, j);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static boolean weakCompareAndSet(AtomicInteger ai, int i, int j) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.weakCompareAndSet(i, j);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int getAndIncrement(AtomicInteger ai) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.getAndIncrement();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int getAndDecrement(AtomicInteger ai) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.getAndDecrement();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int incrementAndGet(AtomicInteger ai) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.incrementAndGet();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int decrementAndGet(AtomicInteger ai) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.decrementAndGet();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int getAndAdd(AtomicInteger ai, int i) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.getAndAdd(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int addAndGet(AtomicInteger ai, int i) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.addAndGet(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static int getAndSet(AtomicInteger ai, int i) {
        if (ai instanceof BTraceAtomicInteger) {
            return ai.getAndSet(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private final static class BTraceAtomicLong extends AtomicLong {
        BTraceAtomicLong(long initVal) {
            super(initVal);
        }
    }

    static AtomicLong newAtomicLong(long initVal) {
        return new BTraceAtomicLong(initVal);
    }

    static long get(AtomicLong al) {
        if (al instanceof BTraceAtomicLong ||
            al.getClass().getClassLoader() == null) {
            return al.get();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static void set(AtomicLong al, long i) {
        if (al instanceof BTraceAtomicLong) {
            al.set(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static void lazySet(AtomicLong al, long i) {
        if (al instanceof BTraceAtomicLong) {
            al.lazySet(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static boolean compareAndSet(AtomicLong al, long i, long j) {
        if (al instanceof BTraceAtomicLong) {
            return al.compareAndSet(i, j);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static boolean weakCompareAndSet(AtomicLong al, long i, long j) {
        if (al instanceof BTraceAtomicLong) {
            return al.weakCompareAndSet(i, j);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long getAndIncrement(AtomicLong al) {
        if (al instanceof BTraceAtomicLong) {
            return al.getAndIncrement();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long getAndDecrement(AtomicLong al) {
        if (al instanceof BTraceAtomicLong) {
            return al.getAndDecrement();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long incrementAndGet(AtomicLong al) {
        if (al instanceof BTraceAtomicLong) {
            return al.incrementAndGet();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long decrementAndGet(AtomicLong al) {
        if (al instanceof BTraceAtomicLong) {
            return al.decrementAndGet();
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long getAndAdd(AtomicLong al, long i) {
        if (al instanceof BTraceAtomicLong) {
            return al.getAndAdd(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long addAndGet(AtomicLong al, long i) {
        if (al instanceof BTraceAtomicLong) {
            return al.addAndGet(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static long getAndSet(AtomicLong al, long i) {
        if (al instanceof BTraceAtomicLong) {
            return al.getAndSet(i);
        } else {
            throw new IllegalArgumentException();
        }
    }

    // BTrace perf counter reading functions
    static int perfInt(String name) {
        return getPerfReader().perfInt(name);
    }

    static long perfLong(String name) {
        return getPerfReader().perfLong(name);
    }

    static String perfString(String name) {
        return getPerfReader().perfString(name);
    }

    // the number of stack frames taking a thread dump adds
    private static final int THRD_DUMP_FRAMES = 1;

    // stack trace functions
    private static String stackTraceAllStr(int numFrames, boolean printWarning) {
        Set<Map.Entry<Thread, StackTraceElement[]>> traces =
                Thread.getAllStackTraces().entrySet();
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> t : traces) {
            buf.append(t.getKey().toString());
            buf.append(LINE_SEPARATOR);
            buf.append(LINE_SEPARATOR);
            StackTraceElement[] st = t.getValue();
            buf.append(stackTraceStr("\t", st, 0, numFrames, printWarning));
            buf.append(LINE_SEPARATOR);
        }
        return buf.toString();
    }

    static String stackTraceAllStr(int numFrames) {
        return stackTraceAllStr(numFrames, false);
    }

    static void stackTraceAll(int numFrames) {
        getCurrent().send(stackTraceAllStr(numFrames, true));
    }

    static String stackTraceStr(StackTraceElement[] st,
                                 int strip, int numFrames) {
        return stackTraceStr(null, st, strip, numFrames, false);
    }

    static String stackTraceStr(String prefix, StackTraceElement[] st,
                                 int strip, int numFrames) {
        return stackTraceStr(prefix, st, strip, numFrames, false);
    }

    private static String stackTraceStr(String prefix, StackTraceElement[] st,
                                 int strip, int numFrames, boolean printWarning) {
        strip = strip > 0 ? strip + THRD_DUMP_FRAMES : 0;
        numFrames = numFrames > 0 ? numFrames : st.length - strip;

        int limit = strip + numFrames;
        limit = limit <= st.length ? limit : st.length;

        if (prefix == null) { prefix = ""; }

        StringBuilder buf = new StringBuilder();
        for (int i = strip; i < limit; i++) {
            buf.append(prefix);
            buf.append(st[i].toString());
            buf.append(LINE_SEPARATOR);
        }
        if (printWarning && limit < st.length) {
            buf.append(prefix);
            buf.append(st.length - limit);
            buf.append(" more frame(s) ...");
            buf.append(LINE_SEPARATOR);
        }
        return buf.toString();
    }

    static void stackTrace(StackTraceElement[] st,
                           int strip, int numFrames) {
        stackTrace(null, st, strip, numFrames);
    }

    static void stackTrace(String prefix, StackTraceElement[] st,
                                 int strip, int numFrames) {
        getCurrent().send(stackTraceStr(prefix, st, strip, numFrames, true));
    }

    // print/println functions
    static void print(String str) {
        getCurrent().send(str);
    }

    static void println(String str) {
        getCurrent().send(str + LINE_SEPARATOR);
    }

    static void println() {
        getCurrent().send(LINE_SEPARATOR);
    }

    static String property(String name) {
        return AccessController.doPrivileged(
            new GetPropertyAction(name));
    }

    static Properties properties() {
        return AccessController.doPrivileged(
            new PrivilegedAction<Properties>() {
                @Override
                public Properties run() {
                    return System.getProperties();
                }
            }
        );
    }

    static String getenv(final String name) {
        return AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getenv(name);
                }
            }
        );
    }

    static Map<String, String> getenv() {
        return AccessController.doPrivileged(
            new PrivilegedAction<Map<String, String>>() {
                @Override
                public Map<String, String> run() {
                    return System.getenv();
                }
            }
        );
    }

    static MemoryUsage heapUsage() {
        initMemoryMBean();
        return memoryMBean.getHeapMemoryUsage();
    }

    static MemoryUsage nonHeapUsage() {
        initMemoryMBean();
        return memoryMBean.getNonHeapMemoryUsage();
    }

    static long finalizationCount() {
        initMemoryMBean();
        return memoryMBean.getObjectPendingFinalizationCount();
    }

    static long vmStartTime() {
        initRuntimeMBean();
        return runtimeMBean.getStartTime();
    }

    static long vmUptime() {
        initRuntimeMBean();
        return runtimeMBean.getUptime();
    }

    static List<String> getInputArguments() {
        initRuntimeMBean();
        return runtimeMBean.getInputArguments();
    }

    static String getVmVersion() {
        initRuntimeMBean();
        return runtimeMBean.getVmVersion();
    }

    static boolean isBootClassPathSupported() {
        initRuntimeMBean();
        return runtimeMBean.isBootClassPathSupported();
    }

    static String getBootClassPath() {
        initRuntimeMBean();
        return runtimeMBean.getBootClassPath();
    }

    static long getThreadCount() {
        initThreadMBean();
        return threadMBean.getThreadCount();
    }

    static long getPeakThreadCount() {
        initThreadMBean();
        return threadMBean.getPeakThreadCount();
    }

    static long getTotalStartedThreadCount() {
        initThreadMBean();
        return threadMBean.getTotalStartedThreadCount();
    }

    static long getDaemonThreadCount() {
        initThreadMBean();
        return threadMBean.getDaemonThreadCount();
    }

    static long getCurrentThreadCpuTime() {
        initThreadMBean();
        threadMBean.setThreadCpuTimeEnabled(true);
        return threadMBean.getCurrentThreadCpuTime();
    }

    static long getCurrentThreadUserTime() {
        initThreadMBean();
        threadMBean.setThreadCpuTimeEnabled(true);
        return threadMBean.getCurrentThreadUserTime();
    }

    static void dumpHeap(String fileName, boolean live) {
        initHotspotMBean();
        try {
            String name = resolveFileName(fileName);
            hotspotMBean.dumpHeap(name, live);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    static long getTotalGcTime() {
    	initGarbageCollectionBeans();
    	long totalGcTime = 0;
    	for (GarbageCollectorMXBean gcBean : gcBeanList) {
    		totalGcTime += gcBean.getCollectionTime();
    	}
    	return totalGcTime;
    }

    static String getMemoryPoolUsage(String poolFormat) {
        if (poolFormat == null) {
            poolFormat = "%1$s;%2$d;%3$d;%4$d;%5$d";
        }
    	Object[][] poolOutput = new Object[memPoolList.size()][5];

    	StringBuilder membuffer = new StringBuilder();

    	for (int i = 0; i < memPoolList.size(); i++) {
            MemoryPoolMXBean memPool = memPoolList.get(i);
            poolOutput[i][0] = memPool.getName();
            poolOutput[i][1] = memPool.getUsage().getMax();
            poolOutput[i][2] = memPool.getUsage().getUsed();
            poolOutput[i][3] = memPool.getUsage().getCommitted();
            poolOutput[i][4] = memPool.getUsage().getInit();

    	}
    	for (Object[] memPoolOutput : poolOutput) {
            membuffer.append(String.format(poolFormat, memPoolOutput)).append("\n");
        }

    	return membuffer.toString();
     }

    static double getSystemLoadAverage() {
        initOperatingSystemBean();
        return operatingSystemMXBean.getSystemLoadAverage();
    }

    static long getProcessCPUTime() {
        initOperatingSystemBean();
        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean)operatingSystemMXBean).getProcessCpuTime();
        }

        return -1;
    }

    static void serialize(Object obj, String fileName) {
        try {
            BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(resolveFileName(fileName)));
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(obj);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    static String toXML(Object obj) {
        try {
            return XMLSerializer.toXML(obj);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    static void writeXML(Object obj, String fileName) {
        try {
            try (BufferedWriter bw = new BufferedWriter(
                    new FileWriter(resolveFileName(fileName)))) {
                XMLSerializer.write(obj, bw);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    private synchronized static void initDOTWriterProps() {
        if (dotWriterProps == null) {
            dotWriterProps = new Properties();
            InputStream is = BTraceRuntime.class.getResourceAsStream("resources/btrace.dotwriter.properties");
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

    static void writeDOT(Object obj, String fileName) {
        DOTWriter writer = new DOTWriter(resolveFileName(fileName));
        initDOTWriterProps();
        writer.customize(dotWriterProps);
        writer.addNode(null, obj);
        writer.close();
    }

    private static String INDENT = "    ";
    static void deadlocks(boolean stackTrace) {
        initThreadMBean();
        if (threadMBean.isSynchronizerUsageSupported()) {
            long[] tids = threadMBean.findDeadlockedThreads();
            if (tids != null && tids.length > 0) {
                ThreadInfo[] infos = threadMBean.getThreadInfo(tids, true, true);
                StringBuilder sb = new StringBuilder();
                for (ThreadInfo ti : infos) {
                    sb.append("\"").append(ti.getThreadName()).append("\"" + " Id=").append(ti.getThreadId()).append(" in ").append(ti.getThreadState());
                    if (ti.getLockName() != null) {
                        sb.append(" on lock=").append(ti.getLockName());
                    }
                    if (ti.isSuspended()) {
                        sb.append(" (suspended)");
                    }
                    if (ti.isInNative()) {
                        sb.append(" (running in native)");
                    }
                    if (ti.getLockOwnerName() != null) {
                        sb.append(INDENT).append(" owned by ").append(ti.getLockOwnerName()).append(" Id=").append(ti.getLockOwnerId());
                        sb.append(LINE_SEPARATOR);
                    }

                    if (stackTrace) {
                        // print stack trace with locks
                        StackTraceElement[] stacktrace = ti.getStackTrace();
                        MonitorInfo[] monitors = ti.getLockedMonitors();
                        for (int i = 0; i < stacktrace.length; i++) {
                            StackTraceElement ste = stacktrace[i];
                            sb.append(INDENT).append("at ").append(ste.toString());
                            sb.append(LINE_SEPARATOR);
                            for (MonitorInfo mi : monitors) {
                                if (mi.getLockedStackDepth() == i) {
                                    sb.append(INDENT).append("  - locked ").append(mi);
                                    sb.append(LINE_SEPARATOR);
                                }
                            }
                        }
                        sb.append(LINE_SEPARATOR);
                    }

                    LockInfo[] locks = ti.getLockedSynchronizers();
                    sb.append(INDENT).append("Locked synchronizers: count = ").append(locks.length);
                    sb.append(LINE_SEPARATOR);
                    for (LockInfo li : locks) {
                        sb.append(INDENT).append("  - ").append(li);
                        sb.append(LINE_SEPARATOR);
                    }
                    sb.append(LINE_SEPARATOR);
                }
                getCurrent().send(sb.toString());
            }
        }
    }

    static int dtraceProbe(String s1, String s2, int i1, int i2) {
        if (dtraceEnabled) {
            return dtraceProbe0(s1, s2, i1, i2);
        } else {
            return 0;
        }
    }

    // BTrace aggregation support
    static Aggregation newAggregation(AggregationFunction type) {
        return new Aggregation(type);
    }

    static AggregationKey newAggregationKey(Object... elements) {
        return new AggregationKey(elements);
    }

    static void addToAggregation(Aggregation aggregation, long value) {
        aggregation.add(value);
    }

    static void addToAggregation(Aggregation aggregation, AggregationKey key, long value) {
        aggregation.add(key, value);
    }

    static void clearAggregation(Aggregation aggregation) {
        aggregation.clear();
    }

    static void truncateAggregation(Aggregation aggregation, int count) {
        aggregation.truncate(count);
    }

    static void printAggregation(String name, Aggregation aggregation) {
        getCurrent().send(new GridDataCommand(name, aggregation.getData()));
    }

    static void printSnapshot(String name, Profiler.Snapshot snapshot) {
        getCurrent().send(new GridDataCommand(name, snapshot.getGridData()));
    }

    /**
     * Prints profiling snapshot using the provided format
     * @param name The name of the aggregation to be used in the textual output
     * @param snapshot The snapshot to print
     * @param format The format to use. It mimics {@linkplain String#format(java.lang.String, java.lang.Object[]) } behaviour
     *               with the addition of the ability to address the key title as a 0-indexed item
     * @see String#format(java.lang.String, java.lang.Object[])
     */
    static void printSnapshot(String name, Profiler.Snapshot snapshot, String format) {
        getCurrent().send(new GridDataCommand(name, snapshot.getGridData(), format));
    }
    /**
     * Precondition: Only values from the first Aggregation are printed. If the subsequent aggregations have
     * values for keys which the first aggregation does not have, these rows are ignored.
     * @param name
     * @param format
     * @param aggregationArray
     */
    static void printAggregation(String name, String format, Aggregation[] aggregationArray) {
    	if (aggregationArray.length > 1 && aggregationArray[0].getKeyData().size() > 1) {
    		int aggregationDataSize = aggregationArray[0].getKeyData().get(0).getElements().length + aggregationArray.length;

    		List<Object[]> aggregationData = new ArrayList<>();

    		//Iterate through all keys in the first Aggregation and build up an array of aggregationData
    		for (AggregationKey aggKey : aggregationArray[0].getKeyData()) {
    			int aggDataIndex = 0;
    			Object[] currAggregationData = new Object[aggregationDataSize];

    			//Add the key to the from of the current aggregation Data
    			for (Object obj : aggKey.getElements()) {
    				currAggregationData[aggDataIndex] = obj;
    				aggDataIndex++;
    			}

    			for (Aggregation agg : aggregationArray) {
    				currAggregationData[aggDataIndex] = agg.getValueForKey(aggKey);
    				aggDataIndex++;
            	}

    			aggregationData.add(currAggregationData);
    		}

    		getCurrent().send(new GridDataCommand(name, aggregationData, format));
    	}
    }

    /**
     * Prints aggregation using the provided format
     * @param name The name of the aggregation to be used in the textual output
     * @param aggregation The aggregation to print
     * @param format The format to use. It mimics {@linkplain String#format(java.lang.String, java.lang.Object[]) } behaviour
     *               with the addition of the ability to address the key title as a 0-indexed item
     * @see String#format(java.lang.String, java.lang.Object[])
     */
    static void printAggregation(String name, Aggregation aggregation, String format) {
        getCurrent().send(new GridDataCommand(name, aggregation.getData(), format));
    }

    // profiling related methods
    /**
     * @see BTraceUtils.Profiling#newProfiler()
     */
    static Profiler newProfiler() {
        return new MethodInvocationProfiler(600);
    }

    /**
     * @see BTraceUtils.Profiling#newProfiler(int)
     */
    static Profiler newProfiler(int expectedMethodCnt) {
        return new MethodInvocationProfiler(expectedMethodCnt);
    }

    /**
     * @see BTraceUtils.Profiling#recordEntry(com.sun.btrace.Profiler, java.lang.String)
     */
    static void recordEntry(Profiler profiler, String methodName) {
        profiler.recordEntry(methodName);
    }

    /**
     * @see BTraceUtils.Profiling#recordExit(com.sun.btrace.Profiler, java.lang.String, long)
     */
    static void recordExit(Profiler profiler, String methodName, long duration) {
        profiler.recordExit(methodName, duration);
    }

    /**
     * @see BTraceUtils.Profiling#snapshot(com.sun.btrace.Profiler)
     */
    static Profiler.Snapshot snapshot(Profiler profiler) {
        return profiler.snapshot();
    }

    /**
     * @see BTraceUtils.Profiling#snapshotAndReset(com.sun.btrace.Profiler)
     */
    static Profiler.Snapshot snapshotAndReset(Profiler profiler) {
        return profiler.snapshot(true);
    }

    static void resetProfiler(Profiler profiler) {
        profiler.reset();
    }

    // private methods below this point
    // raise DTrace USDT probe
    private static native int dtraceProbe0(String s1, String s2, int i1, int i2);

    private static final String HOTSPOT_BEAN_NAME =
         "com.sun.management:type=HotSpotDiagnostic";

    /**
     * Get the current thread BTraceRuntime instance
     * if there is one.
     */
    private static BTraceRuntime getCurrent() {
        BTraceRuntime current = rt.get().rt;
        assert current != null : "BTraceRuntime is null!";
        return current;
    }

    private void initThreadPool() {
        if (threadPool == null) {
            synchronized (this) {
                if (threadPool == null) {
                    threadPool = Executors.newFixedThreadPool(1,
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                Thread th = new Thread(r);
                                th.setDaemon(true);
                                return th;
                            }
                        });
    }
            }
        }
    }

    private static void initHotspotMBean() {
        if (hotspotMBean == null) {
            synchronized (BTraceRuntime.class) {
                if (hotspotMBean == null) {
                    hotspotMBean = getHotspotMBean();
    }
            }
        }
    }

    private static HotSpotDiagnosticMXBean getHotspotMBean() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<HotSpotDiagnosticMXBean>() {
                    @Override
                    public HotSpotDiagnosticMXBean run() throws Exception {
                        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                        Set<ObjectName> s = server.queryNames(new ObjectName(HOTSPOT_BEAN_NAME), null);
                        Iterator<ObjectName> itr = s.iterator();
                        if (itr.hasNext()) {
                            ObjectName name = itr.next();
                            HotSpotDiagnosticMXBean bean =
                                ManagementFactory.newPlatformMXBeanProxy(server,
                                    name.toString(), HotSpotDiagnosticMXBean.class);
                            return bean;
                        } else {
                            return null;
                        }
                   }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static void initMemoryMBean() {
        if (memoryMBean == null) {
            synchronized (BTraceRuntime.class) {
                if (memoryMBean == null) {
                    memoryMBean = getMemoryMBean();
    }
            }
        }
    }

    private void initMemoryListener() {
        initThreadPool();
        memoryListener = new NotificationListener() {
                @Override
                public void handleNotification(Notification notif, Object handback)  {
                    boolean entered = BTraceRuntime.enter();
                    try {
                        String notifType = notif.getType();
                        if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
                            CompositeData cd = (CompositeData) notif.getUserData();
                            final MemoryNotificationInfo info = MemoryNotificationInfo.from(cd);
                            String name = info.getPoolName();
                            final Method handler = lowMemHandlers.get(name);
                            if (handler != null) {
                                threadPool.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        boolean entered = BTraceRuntime.enter();
                                        try {
                                            if (handler.getParameterTypes().length == 1) {
                                                handler.invoke(null, info.getUsage());
                                            } else {
                                                handler.invoke(null, (Object[])null);
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
                }
            };
    }

    private static MemoryMXBean getMemoryMBean() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<MemoryMXBean>() {
                    @Override
                    public MemoryMXBean run() throws Exception {
                        return ManagementFactory.getMemoryMXBean();
                   }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static void initRuntimeMBean() {
        if (runtimeMBean == null) {
            synchronized (BTraceRuntime.class) {
                if (runtimeMBean == null) {
                    runtimeMBean = getRuntimeMBean();
                }
            }
        }
    }

    private static RuntimeMXBean getRuntimeMBean() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<RuntimeMXBean>() {
                    public RuntimeMXBean run() throws Exception {
                        return ManagementFactory.getRuntimeMXBean();
                   }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static void initThreadMBean() {
        if (threadMBean == null) {
            synchronized (BTraceRuntime.class) {
                if (threadMBean == null) {
                    threadMBean = getThreadMBean();
                }
            }
        }
    }

    private static ThreadMXBean getThreadMBean() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<ThreadMXBean>() {
                    @Override
                    public ThreadMXBean run() throws Exception {
                        return ManagementFactory.getThreadMXBean();
                    }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<List<MemoryPoolMXBean>>() {
                    @Override
                    public List<MemoryPoolMXBean> run() throws Exception {
                        return ManagementFactory.getMemoryPoolMXBeans();
                    }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static List<GarbageCollectorMXBean> getGarbageCollectionMBeans() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<List<GarbageCollectorMXBean>>() {
                    @Override
                    public List<GarbageCollectorMXBean> run() throws Exception {
                        return ManagementFactory.getGarbageCollectorMXBeans();
                   }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static void initGarbageCollectionBeans() {
        if (gcBeanList == null) {
            synchronized (BTraceRuntime.class) {
                if (gcBeanList == null) {
                	gcBeanList = getGarbageCollectionMBeans();
                }
            }
        }
    }

    private static void initMemoryPoolList() {
        if (memPoolList == null) {
            synchronized (BTraceRuntime.class) {
                if (memPoolList == null) {
                    memPoolList = getMemoryPoolMXBeans();
                }
            }
        }
    }

    private static OperatingSystemMXBean getOperatingSystemMXBean() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<OperatingSystemMXBean>() {
                    @Override
                    public OperatingSystemMXBean run() throws Exception {
                        return ManagementFactory.getOperatingSystemMXBean();
                    }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
        }
    }

    private static void initOperatingSystemBean() {
        if (operatingSystemMXBean == null) {
            synchronized (BTraceRuntime.class) {
                if (operatingSystemMXBean == null) {
                    operatingSystemMXBean = getOperatingSystemMXBean();
                }
            }
        }
    }

    private static PerfReader getPerfReader() {
        if (perfReader == null) {
            throw new UnsupportedOperationException();
        }
        return perfReader;
    }

    private static RunnableGenerator getRunnableGenerator() {
        return runnableGenerator;
    }

    private void send(String msg) {
        send(new MessageCommand(messageTimestamp? System.nanoTime() : 0L,
                               msg));
    }

    public void send(Command cmd) {
        boolean speculated = specQueueManager.send(cmd);
        if (! speculated) {
            enqueue(cmd);
        }
    }

    private void enqueue(Command cmd) {
        int backoffCntr = 0;
        while (!queue.relaxedOffer(cmd)) {
            try {
                if (backoffCntr < 3000) {
                    Thread.yield();
                } else if (backoffCntr < 3100) {
                    Thread.sleep(1);
                } else {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {}
            backoffCntr++;
        }
    }

    private void handleExceptionImpl(Throwable th) {
        if (currentException.get() != null) {
            return;
        }
        leave();
        currentException.set(th);
        try {
            if (th instanceof ExitException) {
                exitImpl(((ExitException)th).exitCode());
            } else {
                if (exceptionHandler != null) {
                    try {
                        exceptionHandler.invoke(null, th);
                    } catch (Throwable ignored) {
                    }
                } else {
                    // Do not call send(Command). Exception messages should not
                    // go to speculative buffers!
                    enqueue(new ErrorCommand(th));
                }
            }
        } finally {
            currentException.set(null);
        }
    }

    private void startImpl() {
        if (timerHandlers != null && timerHandlers.length != 0) {
            timer = new Timer(true);
            RunnableGenerator gen = getRunnableGenerator();
            Runnable[] runnables = new Runnable[timerHandlers.length];
            if (gen != null) {
                generateRunnables(gen, runnables);
            } else {
                wrapToRunnables(runnables);
            }
            for (int index = 0; index < timerHandlers.length; index++) {
                Method m = timerHandlers[index];
                OnTimer tp = m.getAnnotation(OnTimer.class);
                long period = tp.value();
                final Runnable r = runnables[index];
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() { r.run(); }
                }, period, period);
            }
        }

        if (! lowMemHandlers.isEmpty()) {
            initMemoryMBean();
            initMemoryListener();
            NotificationEmitter emitter = (NotificationEmitter) memoryMBean;
            emitter.addNotificationListener(memoryListener, null, null);
        }

        leave();
    }

    private void generateRunnables(RunnableGenerator gen, Runnable[] runnables) {
        final MemoryClassLoader loader = AccessController.doPrivileged(
            new PrivilegedAction<MemoryClassLoader>() {
                @Override
                public MemoryClassLoader run() {
                    return new MemoryClassLoader(clazz.getClassLoader());
                }
            });

        for (int index = 0; index < timerHandlers.length; index++) {
            Method m = timerHandlers[index];
            try {
                final String clzName = "com/sun/btrace/BTraceRunnable$" + index;
                final byte[] buf = gen.generate(m, clzName);
                Class cls = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Class>() {
                        @Override
                        public Class run() throws Exception {
                             return loader.loadClass(clzName.replace('/', '.'), buf);
                        }
                    });
                runnables[index] = (Runnable) cls.newInstance();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception exp) {
                throw new RuntimeException(exp);
            }
        }
    }

    private void wrapToRunnables(Runnable[] runnables) {
        for (int index = 0; index < timerHandlers.length; index++) {
            final Method m = timerHandlers[index];
            runnables[index] = new Runnable() {
                @Override
                public void run() {
                    try {
                        m.invoke(null, (Object[])null);
                    } catch (Throwable th) {
                    }
                }
            };
        }
    }

    private synchronized void exitImpl(int exitCode) {
        if (exitHandler != null) {
            try {
                exitHandler.invoke(null, exitCode);
            } catch (Throwable ignored) {
            }
        }
        disabled = true;
        if (timer != null) {
            timer.cancel();
        }

        if (memoryListener != null && memoryMBean != null) {
            NotificationEmitter emitter = (NotificationEmitter) memoryMBean;
            try {
                emitter.removeNotificationListener(memoryListener);
            } catch (ListenerNotFoundException lnfe) {}
        }

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        send(new ExitCommand(exitCode));
    }

    private static Perf getPerf() {
        if (perf == null) {
            synchronized(BTraceRuntime.class) {
                if (perf == null) {
                    perf = (Perf) AccessController.doPrivileged(new Perf.GetPerfAction());
    }
            }
        }
        return perf;
    }

    private static byte[] getStringBytes(String value) {
        byte[] v = null;
        try {
            v = value.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        byte[] v1 = new byte[v.length+1];
        System.arraycopy(v, 0, v1, 0, v.length);
        v1[v.length] = '\0';
        return v1;
    }

    private Class defineClassImpl(byte[] code, boolean mustBeBootstrap) {
        ClassLoader loader = null;
        if (! mustBeBootstrap) {
            loader = new ClassLoader(null) {};
        }
        Class cl = unsafe.defineClass(className, code, 0, code.length, loader, null);
        unsafe.ensureClassInitialized(cl);
        return cl;
    }

    private void init(Class cl) {
        if (this.clazz != null) {
            return;
        }

        this.clazz = cl;
        List<Method> timersList = new ArrayList<>();
        this.eventHandlers = new HashMap<>();
        this.lowMemHandlers = new HashMap<>();

        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            int modifiers = m.getModifiers();
            if (! Modifier.isStatic(modifiers)) {
                continue;
            }

            OnEvent oev = m.getAnnotation(OnEvent.class);
            if (oev != null && m.getParameterTypes().length == 0) {
                eventHandlers.put(oev.value(), m);
            }

            OnError oer = m.getAnnotation(OnError.class);
            if (oer != null) {
                Class[] argTypes = m.getParameterTypes();
                if (argTypes.length == 1 && argTypes[0] == Throwable.class) {
                    this.exceptionHandler = m;
                }
            }

            OnExit oex = m.getAnnotation(OnExit.class);
            if (oex != null) {
                Class[] argTypes = m.getParameterTypes();
                if (argTypes.length == 1 && argTypes[0] == int.class) {
                    this.exitHandler = m;
                }
            }

            OnTimer ot = m.getAnnotation(OnTimer.class);
            if (ot != null && m.getParameterTypes().length == 0) {
                timersList.add(m);
            }

            OnLowMemory olm = m.getAnnotation(OnLowMemory.class);
            if (olm != null) {
                Class[] argTypes = m.getParameterTypes();
                if ((argTypes.length == 0) ||
                    (argTypes.length == 1 && argTypes[0] == MemoryUsage.class)) {
                    lowMemHandlers.put(olm.pool(), m);
                }
            }
        }

        initMemoryPoolList();
        for (MemoryPoolMXBean mpoolBean : memPoolList) {
            String name = mpoolBean.getName();
            if (lowMemHandlers.containsKey(name)) {
                Method m = lowMemHandlers.get(name);
                OnLowMemory olm = m.getAnnotation(OnLowMemory.class);
                if (mpoolBean.isUsageThresholdSupported()) {
                    mpoolBean.setUsageThreshold(olm.threshold());
                }
            }
        }

        timerHandlers = new Method[timersList.size()];
        timersList.toArray(timerHandlers);

        try {
            level = cl.getDeclaredField("$btrace$$level");
            level.setAccessible(true);
        } catch (Throwable e) {
            debugPrint("Instrumentation level setting not available");
        }

        BTraceMBean.registerMBean(clazz);
    }

    private static String resolveFileName(String name) {
        if (name.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("directories are not allowed");
        }
        StringBuilder buf = new StringBuilder();
        buf.append('.');
        buf.append(File.separatorChar);
        BTraceRuntime runtime = getCurrent();
        buf.append("btrace");
        if (runtime.args != null && runtime.args.length > 0) {
            buf.append(runtime.args[0]);
        }
        buf.append(File.separatorChar);
        buf.append(runtime.className);
        new File(buf.toString()).mkdirs();
        buf.append(File.separatorChar);
        buf.append(name);
        return buf.toString();
    }

    private static void loadLibrary(final ClassLoader cl) {
        AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
                loadBTraceLibrary(cl);
                return null;
            }
        });
    }

    private static void loadBTraceLibrary(final ClassLoader loader) {
        boolean isSolaris = System.getProperty("os.name").equals("SunOS");
        if (isSolaris) {
            try {
                System.loadLibrary("btrace");
                dtraceEnabled = true;
            } catch (LinkageError le) {
                if (loader == null ||
                    loader.getResource("com/sun/btrace") == null) {
                    warning("cannot load libbtrace.so, will miss DTrace probes from BTrace");
                    return;
                }
                String path = loader.getResource("com/sun/btrace").toString();
                int archSeparator = path.indexOf("!");
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
                    warning("cannot load libbtrace.so, will miss DTrace probes from BTrace");
                }
            }
        }
    }

    private static void setupCmdQueueParams() {
        String maxQLen = System.getProperty(CMD_QUEUE_LIMIT_KEY, null);
        if (maxQLen == null) {
            CMD_QUEUE_LIMIT = CMD_QUEUE_LIMIT_DEFAULT;
//            debugPrint("\"" + CMD_QUEUE_LIMIT_KEY + "\" not provided. " +
//                    "Using the default cmd queue limit of " + CMD_QUEUE_LIMIT_DEFAULT);
        } else {
            try {
                CMD_QUEUE_LIMIT = Integer.parseInt(maxQLen);
//                debugPrint("The cmd queue limit set to " + CMD_QUEUE_LIMIT);
            } catch (NumberFormatException e) {
                warning("\"" + maxQLen + "\" is not a valid int number. " +
                        "Using the default cmd queue limit of " + CMD_QUEUE_LIMIT_DEFAULT);
                CMD_QUEUE_LIMIT = CMD_QUEUE_LIMIT_DEFAULT;
            }
        }
    }

    private void debugPrint(String msg) {
        debug.debug(msg);
    }

    private void debugPrint(Throwable t) {
        debug.debug(t);
    }

    private static void warning(String msg) {
        DebugSupport.warning(msg);
    }

    private static void warning(Throwable t) {
        DebugSupport.warning(t);
    }
}
