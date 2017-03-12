/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace.api.impl;

import com.sun.btrace.comm.Command;
import java.io.File;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.btrace.api.BTraceEngine;
import com.sun.btrace.api.BTraceTask;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.GridDataCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.NumberDataCommand;
import com.sun.btrace.comm.NumberMapDataCommand;
import com.sun.btrace.comm.RetransformClassNotification;
import com.sun.btrace.comm.StringMapDataCommand;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceTaskImpl extends BTraceTask implements BTraceEngineImpl.StateListener {
    final private static Pattern NAMED_EVENT_PATTERN = Pattern.compile("@OnEvent\\s*\\(\\s*\\\"(\\w.*)\\\"\\s*\\)", Pattern.MULTILINE);
    final private static Pattern ANONYMOUS_EVENT_PATTERN = Pattern.compile("@OnEvent(?!\\s*\\()");
    final private static Pattern TRUSTED_PATTERN = Pattern.compile("@BTrace\\s*\\(.*(unsafe|trusted)\\s*=\\s*(true|false).*\\)", Pattern.MULTILINE);
    final private static Pattern NAME_ANNOTATION_PATTERN = Pattern.compile("@BTrace\\s*\\(.*name\\s*=\\s*\"(.*)\".*\\)", Pattern.MULTILINE);
    final private static Pattern NAME_PATTERN = Pattern.compile("@BTrace.*?class\\s+(.*?)\\s+", Pattern.MULTILINE);

    final private AtomicReference<State> currentState = new AtomicReference<>(State.NEW);
    final private Set<StateListener> stateListeners = new HashSet<>();
    final private Set<MessageDispatcher> messageDispatchers = new HashSet<>();

    final private static ExecutorService dispatcher = Executors.newSingleThreadExecutor();

    private String script;
    private int numInstrClasses;
    private boolean trusted;

    final private BTraceEngineImpl engine;

    final private int pid;

    public BTraceTaskImpl(int pid, BTraceEngine engine) {
        this.pid = pid;
        this.engine = (BTraceEngineImpl)engine;
        this.engine.addListener(this);
    }

    @Override
    public String getScript() {
        return script != null ? script : "";
    }

    @Override
    public String getName() {
        Matcher m = NAME_ANNOTATION_PATTERN.matcher(getScript());
        if (m.find()) {
            return m.group(1);
        } else {
            m = NAME_PATTERN.matcher(getScript());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    @Override
    public void setScript(String newValue) {
        script = newValue;
        Matcher m = TRUSTED_PATTERN.matcher(getScript());
        if (m.find()) {
            trusted = Boolean.parseBoolean(m.group(1));
        }
    }

    @Override
    public void sendEvent(String event) {
        engine.sendEvent(this, event);
    }

    @Override
    public void sendEvent() {
        engine.sendEvent(this);
    }

    @Override
    public Set<String> getNamedEvents() {
        Set<String> events = new HashSet<String>();
        Matcher matcher = NAMED_EVENT_PATTERN.matcher(getScript());
        while (matcher.find()) {
            events.add(matcher.group(1));
        }
        return events;
    }

    @Override
    public boolean hasAnonymousEvents() {
        Matcher matcher = ANONYMOUS_EVENT_PATTERN.matcher(getScript());
        return matcher.find();
    }

    @Override
    public boolean hasEvents() {
        return getScript() != null && getScript().contains("@OnEvent");
    }

    @Override
    public int getPid() {
        return pid;
    }

    /**
     * Property getter
     * @return Returns the current state
     */
    protected State getState() {
        return currentState.get();
    }

    @Override
    public int getInstrClasses() {
        return EnumSet.of(State.INSTRUMENTING, State.RUNNING).contains(getState()) ? numInstrClasses : -1;
    }

    /**
     * Listener management (can use {@linkplain WeakListeners} to create a new listener)
     * @param listener {@linkplain StateListener} instance to add
     */
    @Override
    public void addStateListener(StateListener listener) {
        synchronized (stateListeners) {
            stateListeners.add(listener);
        }
    }

    /**
     * Listener management
     * @param listener {@linkplain StateListener} instance to remove
     */
    @Override
    public void removeStateListener(StateListener listener) {
        synchronized (stateListeners) {
            stateListeners.remove(listener);
        }
    }

    /**
     * Dispatcher management (can use {@linkplain WeakListeners} to create a new listener)
     * @param dispatcher {@linkplain StateListener} instance to add
     */
    @Override
    public void addMessageDispatcher(MessageDispatcher dispatcher) {
        synchronized (messageDispatchers) {
            messageDispatchers.add(dispatcher);
        }
    }

    /**
     * Dispatcher management
     * @param dispatcher {@linkplain StateListener} instance to remove
     */
    @Override
    public void removeMessageDispatcher(MessageDispatcher dispatcher) {
        synchronized (messageDispatchers) {
            messageDispatchers.remove(dispatcher);
        }
    }

    /**
     * Starts the injected code using the attached {@linkplain BTraceEngine}
     */
    @Override
    public void start() {
        setState(State.STARTING);
        if (!engine.start(this)) {
            setState(State.FAILED);
        }
    }

    /**
     * Stops the injected code running on the attached {@linkplain BTraceEngine}
     */
    @Override
    public void stop() {
        if (getState() == State.RUNNING) {
            if (!engine.stop(this)) {
                setState(State.RUNNING);
            }
        }
    }

    /**
     * @see BTraceEngine.StateListener#onTaskStart(net.java.visualvm.btrace.api.BTraceTask)
     */
    @Override
    public void onTaskStart(BTraceTask task) {
        if (task.equals(this)) {
            setState(State.RUNNING);
        }
    }

    /**
     * @see BTraceEngine.StateListener#onTaskStop(net.java.visualvm.btrace.api.BTraceTask)
     */
    @Override
    public void onTaskStop(BTraceTask task) {
        if (task.equals(this)) {
            setState(State.FINISHED);
        }
    }

    @Override
    public String getClassPath() {
        StringBuilder sb = new StringBuilder();
        for(String cpEntry : engine.getClasspathProvider().getClasspath(this)) {
            if (sb.length() > 0) {
                sb.append(File.pathSeparator);
            }
            sb.append(cpEntry);
        }
        return sb.toString();
    }

    @Override
    public boolean isTrusted() {
        return trusted;
    }

    void setState(State newValue) {
        currentState.set(newValue);
        fireStateChange();
    }

    void setInstrClasses(int value) {
        numInstrClasses = value;
    }

    private void fireStateChange() {
        Set<StateListener> toProcess = null;

        synchronized (stateListeners) {
             toProcess = new HashSet<StateListener>(stateListeners);
        }
        for (StateListener listener : toProcess) {
            listener.stateChanged(getState());
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    void dispatchCommand(final Command cmd) {
        final Set<MessageDispatcher> dispatchingSet = new HashSet<BTraceTask.MessageDispatcher>();
        synchronized(messageDispatchers) {
            dispatchingSet.addAll(messageDispatchers);
        }
        dispatcher.submit(new Runnable() {
            @Override
            public void run() {
                for(MessageDispatcher listener : dispatchingSet) {
                    switch (cmd.getType()) {
                        case Command.MESSAGE: {
                            listener.onPrintMessage(((MessageCommand)cmd).getMessage());
                            break;
                        }
                        case Command.RETRANSFORM_CLASS: {
                            listener.onClassInstrumented(((RetransformClassNotification)cmd).getClassName());
                            break;
                        }
                        case Command.NUMBER: {
                            NumberDataCommand ndc = (NumberDataCommand)cmd;
                            listener.onNumberMessage(ndc.getName(), ndc.getValue());
                            break;
                        }
                        case Command.NUMBER_MAP: {
                            NumberMapDataCommand nmdc = (NumberMapDataCommand)cmd;
                            listener.onNumberMap(nmdc.getName(), nmdc.getData());
                            break;
                        }
                        case Command.STRING_MAP: {
                            StringMapDataCommand smdc = (StringMapDataCommand)cmd;
                            listener.onStringMap(smdc.getName(), smdc.getData());
                            break;
                        }
                        case Command.GRID_DATA: {
                            GridDataCommand gdc = (GridDataCommand)cmd;
                            listener.onGrid(gdc.getName(), gdc.getData());
                            break;
                        }
                        case Command.ERROR: {
                            ErrorCommand ec = (ErrorCommand)cmd;
                            listener.onError(ec.getCause());
                            break;
                        }
                    }
                }
            }
        });
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BTraceTaskImpl other = (BTraceTaskImpl) obj;
        if (this.pid != other.pid) {
            return false;
        }
        return !(this.getName() != other.getName() && (this.getName() == null || !this.getName().equals(other.getName())));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + pid;
        hash = 19 * hash + (this.getName() != null ? this.getName().hashCode() : 0);
        return hash;
    }
}
