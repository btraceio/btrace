/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.btrace.api.impl;

import com.sun.btrace.CommandListener;
import com.sun.btrace.api.BTraceCompiler;
import com.sun.btrace.client.Client;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.RetransformationStartNotification;
import java.io.IOException;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.btrace.api.BTraceEngine;
import com.sun.btrace.api.BTraceSettings;
import com.sun.btrace.api.BTraceTask;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.spi.BTraceCompilerFactory;
import com.sun.btrace.spi.BTraceSettingsProvider;
import com.sun.btrace.spi.ClasspathProvider;
import com.sun.btrace.spi.PortLocator;
import com.sun.btrace.spi.impl.BTraceCompilerFactoryImpl;
import com.sun.btrace.spi.impl.BTraceSettingsProviderImpl;
import com.sun.btrace.spi.OutputProvider;
import com.sun.btrace.spi.impl.PortLocatorImpl;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceEngineImpl extends BTraceEngine {
    final private static Logger LOGGER = Logger.getLogger(BTraceEngineImpl.class.getName());

    /**
     * Basic state listener<br>
     * Makes it possible to intercept start/stop of a certain {@linkplain BTraceTask}
     */
    interface StateListener extends EventListener {
        /**
         * Called on task startup
         * @param task The task that has started up
         */
        void onTaskStart(BTraceTask task);
        /**
         * Called on task shutdown
         * @param task The task that has stopped
         */
        void onTaskStop(BTraceTask task);
    }

    private BTraceSettingsProvider settingsProvider;
    private BTraceCompilerFactory compilerFactory;
    private ClasspathProvider cpProvider;
    private PortLocator portLocator;
    private OutputProvider outputProvider;

    private Map<BTraceTask, Client> clientMap = new HashMap<BTraceTask, Client>();

    final private Set<WeakReference<StateListener>> listeners = new HashSet<WeakReference<StateListener>>();
    final private ExecutorService commQueue = Executors.newCachedThreadPool();

    public BTraceEngineImpl() {

        this.settingsProvider = initSettingsProvider();
        this.compilerFactory = initCompilerFactory();
        this.cpProvider = initClasspathProvider();
        this.portLocator = initPortLocator();
        this.outputProvider = initOutputProvider();
    }

    private static BTraceCompilerFactory initCompilerFactory() {
        ServiceLoader<BTraceCompilerFactory> loader = ServiceLoader.load(BTraceCompilerFactory.class);
        if (loader != null) {
            Iterator<BTraceCompilerFactory> iter = loader.iterator();
            if (iter.hasNext()) {
                return iter.next();
            }
        }
        return new BTraceCompilerFactoryImpl();
    }

    private static ClasspathProvider initClasspathProvider() {
        ServiceLoader<ClasspathProvider> loader = ServiceLoader.load(ClasspathProvider.class);
        if (loader != null) {
            Iterator<ClasspathProvider> iter = loader.iterator();
            if (iter.hasNext()) {
                return iter.next();
            }
        }
        return ClasspathProvider.EMPTY;
    }

    private static BTraceSettingsProvider initSettingsProvider() {
        ServiceLoader<BTraceSettingsProvider> loader = ServiceLoader.load(BTraceSettingsProvider.class);
        if (loader != null) {
            Iterator<BTraceSettingsProvider> iter = loader.iterator();
            if (iter.hasNext()) {
                return iter.next();
            }
        }
        return new BTraceSettingsProviderImpl();
    }

    private static PortLocator initPortLocator() {
        ServiceLoader<PortLocator> loader = ServiceLoader.load(PortLocator.class);
        if (loader != null) {
            Iterator<PortLocator> iter = loader.iterator();
            if (iter.hasNext()) {
                return iter.next();
            }
        }
        return new PortLocatorImpl();
    }

    private static OutputProvider initOutputProvider() {
        ServiceLoader<OutputProvider> loader = ServiceLoader.load(OutputProvider.class);
        if (loader != null) {
            Iterator<OutputProvider> iter = loader.iterator();
            if (iter.hasNext()) {
                return iter.next();
            }
        }
        return OutputProvider.DEFAULT;
    }

    @Override
    public BTraceTask createTask(int pid) {
        return new BTraceTaskImpl(pid, this);
    }

    void addListener(StateListener listener) {
        synchronized(listeners) {
            listeners.add(new WeakReference<StateListener>(listener));
        }
    }

    void removeListener(StateListener listener) {
        synchronized(listeners) {
            for(Iterator<WeakReference<StateListener>> iter=listeners.iterator();iter.hasNext();) {
                WeakReference<StateListener> ref = iter.next();
                StateListener l = ref.get();
                if (l == null || l.equals(listener)) {
                    iter.remove();
                }
            }
        }
    }

    boolean start(final BTraceTask task) {
        LOGGER.finest("Starting BTrace task");

        boolean result = doStart(task);
        LOGGER.log(Level.FINEST, "BTrace task {0}", result ? "started successfuly" : "failed");
        if (result) {
            fireOnTaskStart(task);
        }
        return result;
    }

    final private AtomicBoolean stopping = new AtomicBoolean(false);

    boolean stop(final BTraceTask task) {
        LOGGER.finest("Attempting to stop BTrace task");
        try {
            if (stopping.compareAndSet(false, true)) {
                LOGGER.finest("Stopping BTrace task");
                boolean result = doStop(task);
                LOGGER.log(Level.FINEST, "BTrace task {0}", result ? "stopped successfuly" : "not stopped");
                if (result) {
                    fireOnTaskStop(task);
                }
                return result;
            }
            return true;
        } finally {
            stopping.set(false);
        }
    }

    private boolean doStart(BTraceTask task) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final BTraceTaskImpl btrace = (BTraceTaskImpl) task;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final BTraceCompiler compiler = compilerFactory.newCompiler(btrace);
            btrace.setState(BTraceTask.State.COMPILING);
            final byte[] bytecode = compiler.compile(btrace.getScript(), task.getClassPath(), outputProvider.getStdErr(task));
            if (bytecode.length == 0) {
                btrace.setState(BTraceTask.State.FAILED);
                return false;
            }
            btrace.setState(BTraceTask.State.COMPILED);
            
            LOGGER.log(Level.FINEST, "Compiled the trace: {0} bytes", bytecode.length);
            commQueue.submit(new Runnable() {

                public void run() {
                    int  port = portLocator.getTaskPort(btrace);
                    LOGGER.log(Level.FINEST, "BTrace agent listening on port {0}", port);
                    BTraceSettings settings = settingsProvider.getSettings();
                    final Client client = new Client(port, ".", settings.isDebugMode(), true, btrace.isUnsafe(), settings.isDumpClasses(), settings.getDumpClassPath());
                    
                    try {
                        client.attach(String.valueOf(btrace.getPid()), compiler.getAgentJarPath(), compiler.getToolsJarPath(), null);
                        Thread.sleep(200); // give the server side time to initialize and open the port
                        client.submit(bytecode, new String[]{}, new CommandListener() {
                            public void onCommand(Command cmd) throws IOException {
                                LOGGER.log(Level.FINEST, "Received command: {0}", cmd.toString());
                                switch (cmd.getType()) {
                                    case Command.SUCCESS: {
                                        if (btrace.getState() == BTraceTask.State.COMPILED) {
                                            btrace.setState(BTraceTask.State.ACCEPTED);
                                        } else if (EnumSet.of(BTraceTask.State.INSTRUMENTING, BTraceTask.State.ACCEPTED).contains(btrace.getState())) {
                                            btrace.setState(BTraceTask.State.RUNNING);
                                            result.set(true);
                                            clientMap.put(btrace, client);
                                            latch.countDown();
                                        }
                                        break;
                                    }
                                    case Command.EXIT: {
                                        btrace.setState(BTraceTask.State.FINISHED);
                                        latch.countDown();
                                        stop(btrace);
                                        break;
                                    }
                                    case Command.RETRANSFORMATION_START: {
                                        int numClasses = ((RetransformationStartNotification)cmd).getNumClasses();
                                        btrace.setInstrClasses(numClasses);
                                        btrace.setState(BTraceTask.State.INSTRUMENTING);
                                        break;
                                    }
                                    case Command.ERROR: {
                                        ((ErrorCommand)cmd).getCause().printStackTrace(outputProvider.getStdErr(btrace));
                                        btrace.setState(BTraceTask.State.FAILED);
                                        latch.countDown();
                                        stop(btrace);
                                        break;
                                    }
                                }
                                btrace.dispatchCommand(cmd);
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, e.getLocalizedMessage(), e);
                        result.set(false);
                        latch.countDown();
                    }
                }
            });
            latch.await();

        } catch (InterruptedException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        }
        return result.get();
    }

    private boolean doStop(BTraceTask task) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendExit(0);
                Thread.sleep(300);
                client.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                // ignore all IO related exception during the stop sequence
            }
        }
        return true;
    }

    void sendEvent(BTraceTaskImpl task) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendEvent();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    void sendEvent(BTraceTaskImpl task, String eventName) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendEvent(eventName);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    ClasspathProvider getClasspathProvider() {
        return cpProvider;
    }

    private void fireOnTaskStart(BTraceTask task) {
        synchronized(listeners) {
            for(WeakReference<StateListener> ref : listeners) {
                StateListener l = ref.get();
                if (l != null) l.onTaskStart(task);
            }
        }
    }

    private void fireOnTaskStop(BTraceTask task) {
        synchronized(listeners) {
            for(WeakReference<StateListener> ref : listeners) {
                StateListener l = ref.get();
                if (l != null) l.onTaskStop(task);
            }
        }
    }
}
