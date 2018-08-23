package com.sun.btrace.api.impl;

import com.sun.btrace.api.BTraceTask;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class ExtractedBTraceEngineImpl {
    private final Set<WeakReference<StateListener>> listeners = new HashSet<WeakReference<StateListener>>();

    ExtractedBTraceEngineImpl() {
    }

    void addListener(BTraceEngineImpl.StateListener listener) {
        synchronized (listeners) {
            listeners.add(new WeakReference<StateListener>(listener));
        }
    }

    void removeListener(BTraceEngineImpl.StateListener listener) {
        synchronized (listeners) {
            for (Iterator<WeakReference<StateListener>> iter = listeners.iterator(); iter.hasNext(); ) {
                WeakReference<StateListener> ref = iter.next();
                BTraceEngineImpl.StateListener l = ref.get();
                if (l == null || l.equals(listener)) {
                    iter.remove();
                }
            }
        }
    }

    void fireOnTaskStart(BTraceTask task) {
        synchronized (listeners) {
            for (WeakReference<StateListener> ref : listeners) {
                BTraceEngineImpl.StateListener l = ref.get();
                if (l != null) l.onTaskStart(task);
            }
        }
    }

    void fireOnTaskStop(BTraceTask task) {
        synchronized (listeners) {
            for (WeakReference<StateListener> ref : listeners) {
                BTraceEngineImpl.StateListener l = ref.get();
                if (l != null) l.onTaskStop(task);
            }
        }
    }
}