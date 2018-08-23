package com.sun.btrace.api.impl;

import com.sun.btrace.api.BTraceTask;
import com.sun.btrace.comm.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ExtractedBTraceTaskImpl {
    private final static ExecutorService dispatcher = Executors.newSingleThreadExecutor();
    private final Set<MessageDispatcher> messageDispatchers = new HashSet<>();

    ExtractedBTraceTaskImpl() {
    }

    /**
     * Dispatcher management
     *
     * @param dispatcher {@linkplain BTraceTask.MessageDispatcher} instance to add
     */
    void addMessageDispatcher(BTraceTask.MessageDispatcher dispatcher) {
        synchronized (messageDispatchers) {
            messageDispatchers.add(dispatcher);
        }
    }

    /**
     * Dispatcher management
     *
     * @param dispatcher {@linkplain BTraceTask.MessageDispatcher} instance to remove
     */
    void removeMessageDispatcher(BTraceTask.MessageDispatcher dispatcher) {
        synchronized (messageDispatchers) {
            messageDispatchers.remove(dispatcher);
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    void dispatchCommand(final Command cmd) {
        final Set<MessageDispatcher> dispatchingSet = new HashSet<BTraceTask.MessageDispatcher>();
        synchronized (messageDispatchers) {
            dispatchingSet.addAll(messageDispatchers);
        }
        dispatcher.submit(new Runnable() {
            @Override
            public void run() {
                for (BTraceTask.MessageDispatcher listener : dispatchingSet) {
                    switch (cmd.getType()) {
                        case Command.MESSAGE: {
                            listener.onPrintMessage(((MessageCommand) cmd).getMessage());
                            break;
                        }
                        case Command.RETRANSFORM_CLASS: {
                            listener.onClassInstrumented(((RetransformClassNotification) cmd).getClassName());
                            break;
                        }
                        case Command.NUMBER: {
                            NumberDataCommand ndc = (NumberDataCommand) cmd;
                            listener.onNumberMessage(ndc.getName(), ndc.getValue());
                            break;
                        }
                        case Command.NUMBER_MAP: {
                            NumberMapDataCommand nmdc = (NumberMapDataCommand) cmd;
                            listener.onNumberMap(nmdc.getName(), nmdc.getData());
                            break;
                        }
                        case Command.STRING_MAP: {
                            StringMapDataCommand smdc = (StringMapDataCommand) cmd;
                            listener.onStringMap(smdc.getName(), smdc.getData());
                            break;
                        }
                        case Command.GRID_DATA: {
                            GridDataCommand gdc = (GridDataCommand) cmd;
                            listener.onGrid(gdc.getName(), gdc.getData());
                            break;
                        }
                        case Command.ERROR: {
                            ErrorCommand ec = (ErrorCommand) cmd;
                            listener.onError(ec.getCause());
                            break;
                        }
                    }
                }
            }
        });
    }
}