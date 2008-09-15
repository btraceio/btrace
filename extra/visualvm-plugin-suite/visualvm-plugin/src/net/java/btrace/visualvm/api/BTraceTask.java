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

package net.java.btrace.visualvm.api;

import com.sun.btrace.comm.Command;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.core.datasource.DataSource;
import java.io.PrintWriter;
import java.util.EventListener;
import java.util.Set;
import org.openide.util.WeakListeners;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public abstract class BTraceTask extends DataSource {
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
    public static interface CommandListener extends EventListener {
        void onCommand(Command command);
    }

    /**
     * This enum represents the allowed states of the tracing task
     */
    public static enum State {

        NEW, STARTING, RUNNING, FINISHED
    }

    public BTraceTask(Application master) {
        super(master);
        setVisible(false);
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

    /**
     * Listener management (can use {@linkplain WeakListeners} to create a new listener)
     * @param listener {@linkplain BTraceTask.CommandListener} instance to add
     */
    abstract public void addCommandListener(CommandListener listener);
    /**
     * Listener management
     * @param listener {@linkplain BTraceTask.CommandListener} instance to remove
     */
    abstract public void removeCommandListener(CommandListener listener);

    /**
     * Starts the injected code using the attached {@linkplain BTraceEngine}
     */
    abstract public void start();

    /**
     * Stops the injected code running on the attached {@linkplain BTraceEngine}
     */
    abstract public void stop();

    /**
     * Property getter
     * @return Returns the script source code
     */
    abstract public String getScript();

    /**
     * Property setter
     * @param newValue The script source code to be used
     */
    abstract public void setScript(String newValue);

    /**
     * Property getter
     * @return Returns the bound {@linkplain BTraceEngine} instance
     */
    abstract public BTraceEngine getEngine();
    /**
     * Property getter
     * @return Returns the bound {@linkplain Application} instance
     */
    abstract public Application getApplication();

    /**
     * Property getter
     * @return Returns the {@linkplain PrintWriter} used in the task to post the script output to
     */
    abstract public PrintWriter getWriter();
    /**
     * Property setter
     * @param writer {@linkplain PrintWriter} instance to be used to post the script ouptut to
     */
    abstract public void setWriter(PrintWriter writer);

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
     * Property getter
     * @return Returns the list of all named events defined in the script
     */
    abstract public Set<String> getNamedEvents();

    /**
     * Flag property getter
     * @return Returns true if the script has defined at least one @OnEvent probe
     */
    abstract public boolean hasEvents();
}
