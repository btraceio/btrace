/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace;

import java.lang.management.ManagementFactory;
import static java.lang.management.ManagementFactory.*;
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
import java.util.concurrent.LinkedBlockingQueue;
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
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
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
import java.util.Collections;
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
import sun.misc.Perf;
import sun.misc.Unsafe;
import sun.reflect.Reflection;
import sun.security.action.GetPropertyAction;


/**
 * Helper class used by BTrace built-in functions and
 * also acts runtime "manager" for a specific BTrace client
 * and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 */
public final class BTraceRuntime {
    // we need Unsafe to load BTrace class bytes as
    // bootstrap class
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    // a dummy BTraceRuntime instance
    private static BTraceRuntime dummy;
    private static BTraceRuntime NULL;

    // are we running with DTrace support enabled?
    private static boolean dtraceEnabled;

    private static final boolean messageTimestamp = false;
    private static final String LINE_SEPARATOR;

    static {
        dummy = new BTraceRuntime();
        NULL = new BTraceRuntime();
        LINE_SEPARATOR = System.getProperty("line.separator");
    }

    // at most one BTrace action method runs per thread.
    // This way platform classes (which may be called by BTrace)
    // may be instrumented. Also, this helps in inteferring with
    // other BTrace clients that may be running concurrently.
    private static ThreadLocal<BTraceRuntime> tls =
        new ThreadLocal<BTraceRuntime>();

    private static ThreadEnteredMap<BTraceRuntime> map = new ThreadEnteredMap<BTraceRuntime>(NULL);

    // BTraceRuntime against BTrace class name
    private static Map<String, BTraceRuntime> runtimes =
        Collections.synchronizedMap(new HashMap<String, BTraceRuntime>());

    // jvmstat related stuff
    // to read and write perf counters
    private static volatile Perf perf;
    // interface to read perf counters of this process
    private static volatile PerfReader perfReader;
    // performance counters created by this client
    private static Map<String, ByteBuffer> counters =
        new HashMap<String, ByteBuffer>();

    // Few MBeans used to implement certain built-in functions
    private static volatile HotSpotDiagnosticMXBean hotspotMBean;
    private static volatile MemoryMXBean memoryMBean;
    private static volatile RuntimeMXBean runtimeMBean;
    private static volatile ThreadMXBean threadMBean;

    // bytecode generator that generates Runnable implementations
    private static RunnableGenerator runnableGenerator;

    // Per-client state starts here.

    // current thread's exception
    private ThreadLocal<Throwable> currentException = new ThreadLocal<Throwable>();

    // "command line" args supplied by client
    private String[] args;

    // whether current runtime has been disabled?
    private volatile boolean disabled;

    // Class object of the BTrace class [of this client]
    private String className;

    // BTrace Class object corresponding to this client
    private Class clazz;

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
    private volatile LinkedBlockingQueue<Command> queue;

    private static class SpeculativeQueueManager {
        // maximum number of speculative buffers
        private static final int MAX_SPECULATIVE_BUFFERS = Short.MAX_VALUE;
        // per buffer message limit
        private static final int MAX_SPECULATIVE_MSG_LIMIT = Short.MAX_VALUE;
        // next speculative buffer id
        private int nextSpeculationId;
        // speculative buffers map
        private ConcurrentHashMap<Integer, LinkedBlockingQueue<Command>> speculativeQueues;
        // per thread current speculative buffer id
        private ThreadLocal<Integer> currentSpeculationId;

        SpeculativeQueueManager() {
            speculativeQueues = new ConcurrentHashMap<Integer, LinkedBlockingQueue<Command>>();
            currentSpeculationId = new ThreadLocal<Integer>();
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
                        new LinkedBlockingQueue<Command>(MAX_SPECULATIVE_MSG_LIMIT));
            }
            return nextId;
        }

        boolean send(Command cmd) {
            Integer curId = currentSpeculationId.get();
            if ((curId != null) && (cmd.getType() != Command.EXIT)) {
                LinkedBlockingQueue<Command> sb = speculativeQueues.get(curId);
                if (sb != null) {
                    try {
                        sb.add(cmd);
                    } catch (IllegalStateException ise) {
                        sb.clear();
                        sb.add(new MessageCommand("speculative buffer overflow: " + curId));
                    }
                    return true;
                }
            }
            return false;
        }

        void speculate(int id) {
            validateId(id);
            currentSpeculationId.set(id);
        }

        void commit(int id, LinkedBlockingQueue<Command> result) {
            validateId(id);
            currentSpeculationId.set(null);
            LinkedBlockingQueue<Command> sb = speculativeQueues.get(id);
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
    private volatile SpeculativeQueueManager specQueueManager;
    // background thread that sends Commands to the handler
    private volatile Thread cmdThread;
    // CommandListener that receives the Commands
    private volatile CommandListener cmdListener;
    private Instrumentation instrumentation;

    private BTraceRuntime() {
    }

    public BTraceRuntime(final String className, String[] args,
                         final CommandListener cmdListener,
                         Instrumentation inst) {
        this.args = args;
        this.queue = new LinkedBlockingQueue<Command>();
        this.specQueueManager = new SpeculativeQueueManager();
        this.cmdListener = cmdListener;
        this.className = className;
        this.instrumentation = inst;
        runtimes.put(className, this);
        this.cmdThread = new Thread(new Runnable() {
            public void run() {
                try {
                    BTraceRuntime.enter();
                    while (true) {
                        Command cmd = queue.take();
                        cmdListener.onCommand(cmd);
                        if (cmd.getType() == Command.EXIT) {
                            return;
                        }
                    }
                } catch (InterruptedException ignored) {
                } catch (IOException ignored) {
                } finally {
                    runtimes.put(className, null);
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

    public static boolean classNameExists(String name) {
        return runtimes.containsKey(name);
    }

    public static void init(PerfReader perfRead, RunnableGenerator runGen) {
        Class caller = Reflection.getCallerClass(2);
        if (! caller.getName().equals("com.sun.btrace.agent.Client")) {
            throw new SecurityException("unsafe init");
        }
        perfReader = perfRead;
        runnableGenerator = runGen;
        loadLibrary(perfRead.getClass().getClassLoader());
    }

    public Class defineClass(byte[] code) {
        Class caller = Reflection.getCallerClass(2);
        if (! caller.getName().equals("com.sun.btrace.agent.Client")) {
            throw new SecurityException("unsafe defineClass");
        }
        return defineClassImpl(code, true);
    }

    public Class defineClass(byte[] code, boolean mustBeBootstrap) {
        Class caller = Reflection.getCallerClass(2);
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
        return map.enter(current);
//        // check we have entered already or disabled
//        if (current.disabled || (tls.get() != null)) {
//            return false;
//        } else {
//            tls.set(current);
//            return true;
//        }
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
        map.exit();
//        tls.remove();
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

    public void handleEvent(EventCommand ecmd) {
        if (eventHandlers != null) {
            String event = ecmd.getEvent();
            Method eventHandler = eventHandlers.get(event);
            if (eventHandler != null) {
//                BTraceRuntime oldRuntime = tls.get();
                BTraceRuntime oldRuntime = map.get();
                leave();
                try {
                    eventHandler.invoke(null, (Object[])null);
                } catch (Throwable ignored) {
                } finally {
                    if (oldRuntime != null) {
                        enter(oldRuntime);
                    }
                }
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

    // package-private interface to BTraceUtil class.

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
        return new BTraceDeque<V>(new ArrayDeque<V>());
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

    static <K, V> V get(Map<K, V> map, Object key) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.get(key);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K, V> boolean containsKey(Map<K, V> map, Object key) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            return map.containsKey(key);
        } else {
            throw new IllegalArgumentException();
        }
    }

    static <K, V> boolean containsValue(Map<K, V> map, Object value) {
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

    static <K, V> V remove(Map<K, V> map, Object key) {
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

    static void printMap(Map map) {
        if (map instanceof BTraceMap ||
            map.getClass().getClassLoader() == null) {
            synchronized(map) {
                Map<String, String> m = new HashMap<String, String>();
                Set<Map.Entry<Object, Object>> entries = map.entrySet();
                for (Map.Entry<Object, Object> e : entries) {
                   m.put(BTraceUtils.str(e.getKey()), BTraceUtils.str(e.getValue()));
                }
                printStringMap(null, m);
            }
        } else {
            print(BTraceUtils.str(map));
        }
    }

    public static <V> void push(Deque<V> queue, V value) {
        if (queue instanceof BTraceDeque || queue.getClass().getClassLoader() == null) {
            queue.push(value);
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

    // stack trace functions
    static String stackTraceAllStr(int numFrames) {
        Set<Map.Entry<Thread, StackTraceElement[]>> traces =
                Thread.getAllStackTraces().entrySet();
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> t : traces) {
            buf.append(t.getKey().toString());
            buf.append(LINE_SEPARATOR);
            buf.append(LINE_SEPARATOR);
            StackTraceElement[] st = t.getValue();
            buf.append(stackTraceStr("\t", st, 0, numFrames));
            buf.append(LINE_SEPARATOR);
        }
        return buf.toString();
    }

    static void stackTraceAll(int numFrames) {
        getCurrent().send(stackTraceAllStr(numFrames));
    }

    static String stackTraceStr(StackTraceElement[] st,
                                 int start, int numFrames) {
        return stackTraceStr(null, st, start, numFrames);
    }

    static String stackTraceStr(String prefix, StackTraceElement[] st,
                                 int start, int numFrames) {
        start = start > 0 ? start : 0;
        numFrames = numFrames > 0 ? numFrames : st.length - start;

        int limit = start + numFrames;
        limit = limit <= st.length ? limit : st.length;

        if (prefix == null) { prefix = ""; }

        StringBuilder buf = new StringBuilder();
        for (int i = start; i < limit; i++) {
            buf.append(prefix);
            buf.append(st[i].toString());
            buf.append(LINE_SEPARATOR);
        }
        if (limit < st.length) {
            buf.append(prefix);
            buf.append(st.length - limit);
            buf.append(" more frame(s) ...");
            buf.append(LINE_SEPARATOR);
        }
        return buf.toString();
    }

    static void stackTrace(StackTraceElement[] st,
                           int start, int numFrames) {
        stackTrace(null, st, start, numFrames);
    }

    static void stackTrace(String prefix, StackTraceElement[] st,
                                 int start, int numFrames) {
        getCurrent().send(stackTraceStr(prefix, st, start, numFrames));
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
                public Properties run() {
                    return System.getProperties();
                }
            }
        );
    }

    static String getenv(final String name) {
        return AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                public String run() {
                    return System.getenv(name);
                }
            }
        );
    }

    static Map<String, String> getenv() {
        return AccessController.doPrivileged(
            new PrivilegedAction<Map<String, String>>() {
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

    static void serialize(Object obj, String fileName) {
        try {
            BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(resolveFileName(fileName)));
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.close();
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
            BufferedWriter bw = new BufferedWriter(
                new FileWriter(resolveFileName(fileName)));
            XMLSerializer.write(obj, bw);
            bw.close();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
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
                    sb.append("\"" + ti.getThreadName() + "\"" +
                              " Id=" + ti.getThreadId() +
                              " in " + ti.getThreadState());
                    if (ti.getLockName() != null) {
                        sb.append(" on lock=" + ti.getLockName());
                    }
                    if (ti.isSuspended()) {
                        sb.append(" (suspended)");
                    }
                    if (ti.isInNative()) {
                        sb.append(" (running in native)");
                    }
                    if (ti.getLockOwnerName() != null) {
                        sb.append(INDENT + " owned by " +
                                  ti.getLockOwnerName() +
                                  " Id=" + ti.getLockOwnerId());
                        sb.append(LINE_SEPARATOR);
                    }

                    if (stackTrace) {
                        // print stack trace with locks
                        StackTraceElement[] stacktrace = ti.getStackTrace();
                        MonitorInfo[] monitors = ti.getLockedMonitors();
                        for (int i = 0; i < stacktrace.length; i++) {
                            StackTraceElement ste = stacktrace[i];
                            sb.append(INDENT + "at " + ste.toString());
                            sb.append(LINE_SEPARATOR);
                            for (MonitorInfo mi : monitors) {
                                if (mi.getLockedStackDepth() == i) {
                                    sb.append(INDENT + "  - locked " + mi);
                                    sb.append(LINE_SEPARATOR);
                                }
                            }
                        }
                        sb.append(LINE_SEPARATOR);
                    }

                    LockInfo[] locks = ti.getLockedSynchronizers();
                    sb.append(INDENT + "Locked synchronizers: count = " + locks.length);
                    sb.append(LINE_SEPARATOR);
                    for (LockInfo li : locks) {
                        sb.append(INDENT + "  - " + li);
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
//        BTraceRuntime current = tls.get();
        BTraceRuntime current = map.get();
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
                public void handleNotification(Notification notif, Object handback)  {
                    String notifType = notif.getType();
                    if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
                        CompositeData cd = (CompositeData) notif.getUserData();
                        final MemoryNotificationInfo info = MemoryNotificationInfo.from(cd);
                        String name = info.getPoolName();
                        final Method handler = lowMemHandlers.get(name);
                        if (handler != null) {
                            threadPool.submit(new Runnable() {
                                public void run() {
                                    try {
                                        if (handler.getParameterTypes().length == 1) {
                                            handler.invoke(null, info.getUsage());
                                        } else {
                                            handler.invoke(null, (Object[])null);
                                        }
                                    } catch (Throwable th) { }
                                }
                            });
                        }
                    }
                }
            };
    }

    private static MemoryMXBean getMemoryMBean() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<MemoryMXBean>() {
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
                    public List<MemoryPoolMXBean> run() throws Exception {
                        return ManagementFactory.getMemoryPoolMXBeans();
                    }
                });
        } catch (Exception exp) {
            throw new UnsupportedOperationException(exp);
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

    private void send(Command cmd) {
        try {
            boolean speculated = specQueueManager.send(cmd);
            if (! speculated) {
                queue.put(cmd);
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
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
                    try {
                        // Do not call send(Command). Exception messages should not
                        // go to speculative buffers!
                        queue.put(new ErrorCommand(th));
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
        } finally {
            currentException.set(null);
        }
    }

    private void startImpl() {
        if (timerHandlers != null && timerHandlers.length != 0) {
            timer = new Timer();
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
                public MemoryClassLoader run() {
                    return new MemoryClassLoader(clazz.getClassLoader());
                }
            });

        for (int index = 0; index < timerHandlers.length; index++) {
            Method m = timerHandlers[index];
            try {
                final String className = "com/sun/btrace/BTraceRunnable$" + index;
                final byte[] buf = gen.generate(m, className);
                Class cls = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Class>() {
                        public Class run() throws Exception {
                             return loader.loadClass(className.replace('/', '.'), buf);
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
        List<Method> timersList = new ArrayList<Method>();
        this.eventHandlers = new HashMap<String, Method>();
        this.lowMemHandlers = new HashMap<String, Method>();

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

        List<MemoryPoolMXBean> mpools = getMemoryPoolMXBeans();
        for (MemoryPoolMXBean mpoolBean : mpools) {
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
        buf.append(runtime.args[0]);
        buf.append(File.separatorChar);
        buf.append(runtime.className);
        new File(buf.toString()).mkdirs();
        buf.append(File.separatorChar);
        buf.append(name);
        return buf.toString();
    }

    private static void loadLibrary(final ClassLoader cl) {
        AccessController.doPrivileged(new PrivilegedAction() {
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
                    System.err.println("cannot load libbtrace.so, will miss DTrace probes from BTrace");
                    return;
                }
                String path = loader.getResource("com/sun/btrace").toString();
                path = path.substring(0, path.indexOf("!"));
                path = path.substring("jar:".length(), path.lastIndexOf('/'));
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
                    System.err.println("cannot load libbtrace.so, will miss DTrace probes from BTrace");
                }
            }
        }
    }
}
