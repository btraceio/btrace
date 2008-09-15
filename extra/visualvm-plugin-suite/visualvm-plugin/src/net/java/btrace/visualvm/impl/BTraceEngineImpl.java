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
package net.java.btrace.visualvm.impl;

import com.sun.btrace.CommandListener;
import com.sun.btrace.client.Client;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.GridDataCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.NumberMapDataCommand;
import com.sun.btrace.comm.StringMapDataCommand;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.application.jvm.JvmFactory;
import com.sun.tools.visualvm.core.datasource.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import net.java.btrace.visualvm.api.BTraceEngine;
import net.java.btrace.visualvm.api.BTraceTask;
import net.java.btrace.visualvm.compiler.BCompiler;
import net.java.btrace.visualvm.options.BTraceSettings;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceEngineImpl extends BTraceEngine {
    private String clientPath;
    private String agentPath;
    private Map<BTraceTask, Client> clientMap = new HashMap<BTraceTask, Client>();

    final private Set<StateListener> listeners = new HashSet<StateListener>();

    public BTraceEngineImpl() {
        File locatedFile = InstalledFileLocator.getDefault().locate("modules/ext/btrace-agent.jar", "com.sun.btrace", false); // NOI18N
        agentPath = locatedFile.getAbsolutePath();

        locatedFile = InstalledFileLocator.getDefault().locate("modules/ext/btrace-client.jar", "com.sun.btrace", false); // NOI18N
        clientPath = locatedFile.getAbsolutePath();
    }

    @Override
    public BTraceTask createTask(Application app) {
        return new BTraceTaskImpl(app, this);
    }

    @Override
    public void addListener(StateListener listener) {
        synchronized(listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(StateListener listener) {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public boolean start(final BTraceTask task) {
        boolean result = doStart(task);
        if (result) {
            fireOnTaskStart(task);
        }
        return result;
    }

    @Override
    public boolean stop(final BTraceTask task) {
        boolean result = doStop(task);
        if (result) {
            fireOnTaskStop(task);
        }
        return result;
    }

    private boolean doStart(BTraceTask task) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final BTraceTaskImpl btrace = (BTraceTaskImpl) task;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final Application app = task.getApplication();
            final String toolsJarCp = findToolsJarPath(app);
            BCompiler compiler = new BCompiler(clientPath, toolsJarCp);
            final byte[] bytecode = compiler.compile(btrace.getScript(), "", btrace.getWriter());
            RequestProcessor.getDefault().post(new Runnable() {

                public void run() {
                    String portStr = JvmFactory.getJVMFor(app).getSystemProperties().getProperty("btrace.port"); // I

                    final PrintWriter pw = new PrintWriter(btrace.getWriter());
                    Client existingClient = clientMap.get(btrace);
                    final Client client = existingClient != null ? existingClient : new Client(portStr != null ? Integer.parseInt(portStr) : findFreePort(), ".", BTraceSettings.sharedInstance().isDebugMode(), BTraceSettings.sharedInstance().isDumpClasses(), BTraceSettings.sharedInstance().getDumpClassPath());
                    
                    try {
                        client.attach(String.valueOf(app.getPid()), agentPath, toolsJarCp, null);
                        client.submit(bytecode, new String[]{}, new CommandListener() {

                            public void onCommand(Command cmd) throws IOException {
                                switch (cmd.getType()) {
                                    case Command.SUCCESS: {
                                        pw.println("BTrace code successfuly deployed");
                                        pw.println("===========================================================");
                                        clientMap.put(btrace, client);
                                        result.set(true);
                                        latch.countDown();
                                        break;
                                    }
                                    case Command.MESSAGE: {
                                        ((MessageCommand) cmd).print(pw);
                                        break;
                                    }
                                    case Command.GRID_DATA: {
                                        ((GridDataCommand)cmd).print(pw);
                                        break;
                                    }
                                    case Command.NUMBER_MAP: {
                                        ((NumberMapDataCommand)cmd).print(pw);
                                        break;
                                    }
                                    case Command.STRING_MAP: {
                                        ((StringMapDataCommand)cmd).print(pw);
                                        break;
                                    }
                                    case Command.ERROR: {
                                        pw.println("*** Error in BTrace probe");
                                        pw.println("===========================================================");
                                        ((ErrorCommand) cmd).getCause().printStackTrace(pw);
                                        break;
                                    }
                                    case Command.EXIT: {
                                        latch.countDown();
                                        pw.println("===========================================================");
                                        pw.println("Application exited: " + ((ExitCommand) cmd).getExitCode());
                                        stop(btrace);
//                                        if (((ExitCommand) cmd).getExitCode() < 0) {
//                                            btrace.stop();
//                                        }
                                        break;
                                    }
                                }
                                btrace.dispatchCommand(cmd);
                            }
                        });
                    } catch (IOException iOException) {
                        latch.countDown();
                    } catch (UnsupportedOperationException unsupportedOperationException) {
                        latch.countDown();
                    }
                }
            });
            latch.await();

        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        } catch (InstantiationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return result.get();
    }

    private boolean doStop(BTraceTask task) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.close();
                return true;
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return false;
    }

    public boolean supports(DataSource ds) {
        return ds instanceof Application;
    }

    @Override
    public void sendEvent(BTraceTaskImpl task) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendEvent();
            } catch (IOException ex) {
                // TODO
                Exceptions.printStackTrace(ex);
            }
        }
    }

    @Override
    public void sendEvent(BTraceTaskImpl task, String eventName) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendEvent(eventName);
            } catch (IOException ex) {
                // TODO
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public static int findFreePort() {
        ServerSocket server = null;
        int port = 0;
        try {
            server = new ServerSocket(0);
            port = server.getLocalPort();
        } catch (IOException iOException) {
            port = 3456;
        } finally {
            try {
                server.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return port;
    }

    private static String findToolsJarPath(Application app) {
        String toolsJarPath = null;
        Properties props = JvmFactory.getJVMFor(app).getSystemProperties();
        if (props != null && props.containsKey("java.home")) {
            String java_home = props.getProperty("java.home");
            java_home = java_home.replace(File.separator + "jre", "");
            toolsJarPath = java_home + "/lib/tools.jar";
        }
        if (!new File(toolsJarPath).exists()) {
            // may be the target app is running on a JRE. Let us hope
            // VisualVM is running on a JDK!
            String java_home = System.getProperty("java.home");
            java_home = java_home.replace(File.separator + "jre", "");
            toolsJarPath = java_home + "/lib/tools.jar";
        }
        return toolsJarPath;
    }

    private void fireOnTaskStart(BTraceTask task) {
        synchronized(listeners) {
            for(StateListener listener : listeners) {
                listener.onTaskStart(task);
            }
        }
    }

    private void fireOnTaskStop(BTraceTask task) {
        synchronized(listeners) {
            for(StateListener listener : listeners) {
                listener.onTaskStop(task);
            }
        }
    }
}
