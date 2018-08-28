package com.sun.btrace.api.impl;

import com.sun.btrace.api.BTraceTask;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class BTraceEngineListeners {
    private final Set<WeakReference<BTraceEngineImpl.StateListener>> listeners = new HashSet<WeakReference<BTraceEngineImpl.StateListener>>();

    BTraceEngineListeners() {
    }

    void addListener(BTraceEngineImpl.StateListener listener) {
        synchronized (listeners) {
            listeners.add(new WeakReference<BTraceEngineImpl.StateListener>(listener));
        }
    }

    void removeListener(BTraceEngineImpl.StateListener listener) {
        synchronized (listeners) {
            for (Iterator<WeakReference<BTraceEngineImpl.StateListener>> iter = listeners.iterator(); iter.hasNext(); ) {
                WeakReference<BTraceEngineImpl.StateListener> ref = iter.next();
                BTraceEngineImpl.StateListener l = ref.get();
                if (l == null || l.equals(listener)) {
                    iter.remove();
                }
            }
        }
    }

    void fireOnTaskStart(BTraceTask task) {
        synchronized (listeners) {
            for (WeakReference<BTraceEngineImpl.StateListener> ref : listeners) {
                BTraceEngineImpl.StateListener l = ref.get();
                if (l != null) l.onTaskStart(task);
            }
        }
    }

    void fireOnTaskStop(BTraceTask task) {
        synchronized (listeners) {
            for (WeakReference<BTraceEngineImpl.StateListener> ref : listeners) {
                BTraceEngineImpl.StateListener l = ref.get();
                if (l != null) l.onTaskStop(task);
            }
        }
    }
}