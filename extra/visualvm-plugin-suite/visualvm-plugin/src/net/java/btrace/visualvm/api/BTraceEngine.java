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

import com.sun.tools.visualvm.application.Application;
import java.util.EventListener;
import net.java.btrace.visualvm.impl.BTraceEngineImpl;
import net.java.btrace.visualvm.impl.BTraceTaskImpl;
import org.openide.util.WeakListeners;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public abstract class BTraceEngine {
    private static class Singleton {
        private final static BTraceEngine INSTANCE = new BTraceEngineImpl();
    }
    /**
     * Basic state listener
     */
    public interface StateListener extends EventListener {
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

    /**
     * {@linkplain BTraceEngine} shared instance locator
     * @return Returns a singleton instance of {@linkplain BTraceEngine}
     */
    final static public BTraceEngine sharedInstance() {
        return Singleton.INSTANCE;
    }

    /**
     * Abstract factory method for {@linkplain BTraceTask} instances
     * @param app The application to create the task form
     * @return Returns a {@linkplain BTraceTask} instance bound to the particular {@linkplain Application}
     */
    abstract public BTraceTask createTask(Application app);

    /**
     * Starts the given {@linkplain BTraceTask} instance
     * @param task The task to start
     * @return Returns TRUE if the task has been started
     */
    abstract public boolean start(final BTraceTask task);

    /**
     * Stops the given {@linkplain BTraceTask} instance
     * @param task The task to stop
     * @return Returns TRUE if the task has been stopped
     */
    abstract public boolean stop(final BTraceTask task);

    abstract public void sendEvent(BTraceTaskImpl task);
    abstract public void sendEvent(BTraceTaskImpl task, String eventName);

    /**
     * State listener management support (can use {@linkplain WeakListeners} to create a new listener)
     * @param listener The {@linkplain StateListener} to add
     */
    abstract public void addListener(StateListener listener);

    /**
     * State listener management support
     * @param listener the {@linkplain  StateListener} to remove
     */
    abstract public void removeListener(StateListener listener);
}
