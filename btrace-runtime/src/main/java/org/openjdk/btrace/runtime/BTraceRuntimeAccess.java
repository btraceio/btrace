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

import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.handlers.ErrorHandler;
import org.openjdk.btrace.core.handlers.EventHandler;
import org.openjdk.btrace.core.handlers.ExitHandler;
import org.openjdk.btrace.core.handlers.LowMemoryHandler;
import org.openjdk.btrace.core.handlers.TimerHandler;
import org.openjdk.btrace.runtime.aux.Auxilliary;
import org.openjdk.btrace.services.api.RuntimeContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Base class form multiple Java version specific implementation.
 *
 * Helper class used by BTrace built-in functions and
 * also acts runtime "manager" for a specific BTrace client
 * and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
public abstract class BTraceRuntimeAccess implements RuntimeContext {
    static final class RTWrapper {
        private BTraceRuntime.Impl rt = null;

        boolean set(BTraceRuntime.Impl other) {
            if (rt != null && other != null) {
                return false;
            }
            rt = other;
            return true;
        }

        <T> T escape(Callable<T> c) {
            BTraceRuntime.Impl oldRuntime = rt;
            rt = null;
            try {
                return c.call();
            } catch (Exception ignored) {
            } finally {
                if (oldRuntime != null) {
                    rt = oldRuntime;
                }
            }
            return null;
        }
    }

    static final class Accessor implements BTraceRuntime.BTraceRuntimeAccessor {
        @Override
        public BTraceRuntime.Impl getRt() {
            BTraceRuntime.Impl current = getCurrent();
            return current != null ? current : dummy;
        }

    }

    // to be registered by BTraceRuntimeImpl implementation class
    // should be treated as virtually immutable
    private static volatile BTraceRuntime.Impl dummy = null;

    protected static final ThreadLocal<RTWrapper> rt;

    static {
        rt = new ThreadLocal<RTWrapper>() {
            @Override
            protected RTWrapper initialValue() {
                return new RTWrapper();
            }
        };
        registerRuntimeAccessor();
        // ignore
    }

    // for testing purposes
    private static volatile boolean uniqueClientClassNames = true;

    // BTraceRuntime against BTrace class name
    protected static final Map<String, BTraceRuntimeImplBase> runtimes = new ConcurrentHashMap<>();

    // a set of all the client names connected so far
    private static final Set<String> clients = new HashSet<>();

    // BTrace Class object corresponding to this client; accessed from instrumented code
    private Class clazz;

    // instrumentation level field for each runtime; accessed from instrumented code
    private Field level;

    private final AtomicBoolean exitting = new AtomicBoolean(false);

    static void addRuntime(String className, BTraceRuntimeImplBase rt) {
        runtimes.put(className, rt);
    }

    /**
     * Enter method is called by every probed method just
     * before the probe actions start.
     */
    public static boolean enter(BTraceRuntime.Impl currentRt) {
        BTraceRuntimeImplBase current = (BTraceRuntimeImplBase)currentRt;
        if (current.isDisabled()) return false;
        return rt.get().set(current);
    }

    public static void leave() {
        rt.get().set(null);
    }

    public static String getClientName(String forClassName) {
        int idx = forClassName.lastIndexOf('/');
        if (idx > -1) {
            forClassName = Auxilliary.class.getPackage().getName().replace('.', '/') + "/" + forClassName.substring(idx + 1);
        } else {
            forClassName = Auxilliary.class.getPackage().getName().replace('.', '/') + "/" + forClassName;
        }

        if (!uniqueClientClassNames) {
            return forClassName;
        }

        String name = forClassName;
        int suffix = 1;
        while (clients.contains(name)) {
            name = forClassName + "$" + (suffix++);
        }
        clients.add(name);
        return name;
    }

    public void shutdownCmdLine() {
        exitting.set(true);
    }

    /**
     * One instance of BTraceRuntime is created per-client.
     * This forClass method creates it. Class passed is the
     * preprocessed BTrace program of the client.
     */
    public static BTraceRuntimeImplBase forClass(Class cl, TimerHandler[] tHandlers, EventHandler[] evHandlers, ErrorHandler[] errHandlers,
                                              ExitHandler[] eHandlers, LowMemoryHandler[] lmHandlers) {
        BTraceRuntimeImplBase runtime = runtimes.get(cl.getName());
        runtime.init(cl, tHandlers, evHandlers, errHandlers, eHandlers, lmHandlers);
        return runtime;
    }

    /**
     * Utility to create a new ThreadLocal object. Called
     * by preprocessed BTrace class to create ThreadLocal
     * for each @TLS variable.
     * Called from instrumented code.
     * @param initValue Initial value.
     *                  This value must be either a boxed primitive or {@linkplain Cloneable}.
     *                  In case a {@linkplain Cloneable} value is provided the value is never used directly
     *                  - instead, a new clone of the value is created per thread.
     */
    public static ThreadLocal newThreadLocal(final Object initValue) {
        return new ThreadLocal() {
            @Override
            protected Object initialValue() {
                if (initValue == null) return initValue;

                if (initValue instanceof Cloneable) {
                    try {
                        Class<?> clz = initValue.getClass();
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

    /**
     * Get the current thread BTraceRuntime instance
     * if there is one.
     */
    static BTraceRuntimeImplBase getCurrent() {
        RTWrapper rtw = rt.get();
        BTraceRuntime.Impl current = rtw != null ? rtw.rt : null;
        current = current != null ? current : dummy;
        return (BTraceRuntimeImplBase)current;
    }

    static <T> T doWithCurrent(Callable<T> callable) {
        RTWrapper rtw = rt.get();
        assert rtw != null : "BTraceRuntime access not set up";
        return rtw.escape(callable);
    }

    @Override
    public void send(String msg) {
        BTraceRuntimeImplBase rt = getCurrent();
        if (rt != null) {
            rt.send(msg);
        }
    }

    @Override
    public void send(Command cmd) {
        BTraceRuntimeImplBase rt = getCurrent();
        if (rt != null) {
            rt.send(cmd);
        }
    }

    static void registerRuntimeAccessor() {
        try {
            dummy = BTraceRuntimes.getDefault();
            Field fld = BTraceRuntime.class.getDeclaredField("rtAccessor");
            fld.setAccessible(true);
            fld.set(null, new Accessor());
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            DebugSupport.warning(e);
        }
    }

    static void debugPrint0(String msg) {
        BTraceRuntimeImplBase rt = getCurrent();
        if (rt != null) {
            rt.debugPrint(msg);
        } else {
            DebugSupport.info(msg);
        }
    }

    private static void warning(String msg) {
        DebugSupport.warning(msg);
    }

    private static void warning(Throwable t) {
        DebugSupport.warning(t);
    }
}
