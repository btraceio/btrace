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

package com.sun.btrace.api;

import com.sun.btrace.annotations.BTrace;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class represents a single BTrace script
 * <p>
 * An instance of {@linkplain BTraceTask} bound to a certain process can be obtained by calling
 * <pre>
 * BTraceEngine.sharedInstance().createTask(PID)
 * </pre>
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public abstract class BTraceTask {
    /**
     * Simple state listener interface
     */
    public static interface StateListener extends EventListener {
        /**
         * Called upon state change
         * @param newState New state
         */
        void stateChanged(State newState);
    }

    /**
     * CommandListener interface exposes the BTrace commands received over
     * the wire to anyone interested
     */
    public static abstract class MessageDispatcher implements EventListener {
        public void onPrintMessage(String message) {};
        public void onNumberMessage(String name, Number value) {};
        public void onGrid(String name, List<Object[]> data) {};
        public void onNumberMap(String name, Map<String, ? extends Number> data) {};
        public void onStringMap(String name, Map<String, String> data) {};
        public void onClassInstrumented(String name) {}
        public void onError(Throwable cause) {}
    }

    /**
     * This enum represents the allowed states of the tracing task
     */
    public static enum State {
        /**
         * This is the state of a newly created task
         */
        NEW,
        /**
         * The task becomes STARTING immediately after calling its <b>start()</b>method
         */
        STARTING,
        /**
         * Indicates that the BTrace compiler has started compiling
         */
        COMPILING,
        /**
         * The task script has just been compiled
         */
        COMPILED,
        /**
         * The BTrace agent has accepted the script
         */
        ACCEPTED,
        /**
         * The BTrace agent is going to instrument appropriate classes
         */
        INSTRUMENTING,
        /**
         * All instrumentation and initialization is done; task is running
         */
        RUNNING,
        /**
         * Indicates an inactive task
         */
        FINISHED,
        /*
         * Indicates a failed task either due to a compilation error or a runtime error
         */
        FAILED
    }

    /**
     * Listener management (can use {@linkplain WeakListeners} to create a new listener)
     * @param listener {@linkplain BTraceTask.StateListener} instance to add
     */
    abstract public void addStateListener(StateListener listener);

    /**
     * Listener management
     * @param listener {@linkplain StateListener} instance to remove
     */
    abstract public void removeStateListener(StateListener listener);

    abstract public void addMessageDispatcher(MessageDispatcher dispatcher);

    abstract public void removeMessageDispatcher(MessageDispatcher dispatcher);

    /**
     * Starts the injected code
     */
    abstract public void start();

    /**
     * Stops the injected code
     */
    abstract public void stop();

    abstract public int getPid();

    /**
     * Property getter
     * @return Returns the script source code
     */
    abstract public String getScript();

    /**
     * Returns the name specified in {@linkplain BTrace} annotation
     * @return Returns the name specified in {@linkplain BTrace} annotation or NULL if not specified
     */
    abstract public String getName();

    /**
     * Property setter
     * @param newValue The script source code to be used
     */
    abstract public void setScript(String newValue);

    /**
     * 
     * @return Returns the complete classpath for the task
     */
    abstract public String getClassPath();

    /**
     * Checks whether the task requires to be run in BTrace <b>Unsafe</b> mode
     * @return Returns true if the BTrace task requires to be run in BTrace <b>Unsafe</b> mode
     */
    abstract public boolean isUnsafe();

    /**
     * Sends a named event to the script server side
     * @param event The event to send
     */
    abstract public void sendEvent(String event);

    /**
     * Sends an anonymous event to the script server side
     */
    abstract public void sendEvent();

    /**
     * A list of named events
     * @return Returns the list of all named events defined in the script
     */
    abstract public Set<String> getNamedEvents();

    /**
     * A flag indicating that the task has some anonymous events assigned
     * @return Returns TRUE if there are any anonymous events defined
     */
    abstract public boolean hasAnonymousEvents();

    /**
     * Flag property getter
     * @return Returns true if the script has defined at least one @OnEvent probe
     */
    abstract public boolean hasEvents();

    /**
     * The number of classes the task needs to instruments
     * @return Returns the number of classes the task needs to instrument
     */
    abstract public int getInstrClasses();
}
