/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.btrace.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.runtime.OnProbe;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.runtime.ProbeDescriptor;
import com.sun.btrace.util.Messages;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This is the main class for BTrace java.lang.instrument agent.
 *
 * @author A. Sundararajan
 */
public final class Main {
    private static volatile Map<String, String> argMap;
    private static volatile Instrumentation inst;
    private static volatile boolean debugMode;
    private static volatile boolean unsafeMode;
    private static volatile boolean dumpClasses;
    private static volatile String dumpDir;
    private static volatile String probeDescPath;
    private static volatile String scriptOutputFile;

    private static final ExecutorService serializedExecutor = Executors.newSingleThreadExecutor();

    public static void premain(String args, Instrumentation inst) {
        main(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        main(args, inst);
    }

    private static synchronized void main(final String args, final Instrumentation inst) {
        if (Main.inst != null) {
            return;
        } else {
            Main.inst = inst;
        }


        if (isDebug()) debugPrint("parsing command line arguments");
        parseArgs(args);
        if (isDebug()) debugPrint("parsed command line arguments");

        String bootClassPath = argMap.get("bootClassPath");
        if (bootClassPath != null) {
            if (isDebug()) {
                 debugPrint("Bootstrap ClassPath: " + bootClassPath);
            }
            StringTokenizer tokenizer = new StringTokenizer(bootClassPath, File.pathSeparator);
            try {
                while (tokenizer.hasMoreTokens()) {
                    String path = tokenizer.nextToken();
                    inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(path)));
                }
            } catch (IOException ex) {
                debugPrint("adding to boot classpath failed!");
                debugPrint(ex);
                return;
            }
        }

        String systemClassPath = argMap.get("systemClassPath");
        if (systemClassPath != null) {
            if (isDebug()) {
                 debugPrint("System ClassPath: " + systemClassPath);
            }
            StringTokenizer tokenizer = new StringTokenizer(systemClassPath, File.pathSeparator);
            try {
                while (tokenizer.hasMoreTokens()) {
                    String path = tokenizer.nextToken();
                    inst.appendToSystemClassLoaderSearch(new JarFile(new File(path)));
                }
            } catch (IOException ex) {
                debugPrint("adding to boot classpath failed!");
                debugPrint(ex);
                return;
            }
        }

        String tmp = argMap.get("noServer");
        boolean noServer = tmp != null && !"false".equals(tmp);
        if (noServer) {
            if (isDebug()) debugPrint("noServer is true, server not started");
            return;
        }
        Thread agentThread = new Thread(new Runnable() {
            public void run() {
                BTraceRuntime.enter();
                try {
                    startServer();
                } finally {
                    BTraceRuntime.leave();
                }
            }
        });
        BTraceRuntime.enter();
        try {
            agentThread.setDaemon(true);
            if (isDebug()) debugPrint("starting agent thread");
            agentThread.start();
        } finally {
            BTraceRuntime.leave();
        }
    }

    private static void usage() {
        System.out.println(Messages.get("btrace.agent.usage"));
        System.exit(0);
    }


    private static void parseArgs(String args) {
        if (args == null) {
            args = "";
        }
        String[] pairs = args.split(",");
        argMap = new HashMap<String, String>();
        for (String s : pairs) {
            int i = s.indexOf('=');
            String key, value = "";
            if (i != -1) {
                key = s.substring(0,i).trim();
                if (i+1 < s.length()) {
                    value = s.substring(i+1).trim();
                }
            } else {
                key = s;
            }
            argMap.put(key, value);
        }

        String p = argMap.get("help");
        if (p != null) {
            usage();
        }
        p = argMap.get("debug");
        debugMode = p != null && !"false".equals(p);
        if (isDebug()) debugPrint("debugMode is " + debugMode);
        scriptOutputFile = argMap.get("scriptOutputFile");
        if (scriptOutputFile != null && scriptOutputFile.length() > 0) {
            if (isDebug()) debugPrint("scriptOutputFile is " + scriptOutputFile);
        }
	p = argMap.get("unsafe");
        unsafeMode = "true".equals(p);
        if (isDebug()) debugPrint("unsafeMode is " + unsafeMode);
        p = argMap.get("dumpClasses");
        dumpClasses = p != null && !"false".equals(p);
        if (isDebug()) debugPrint("dumpClasses is " + dumpClasses);
        if (dumpClasses) {
            dumpDir = argMap.get("dumpDir");
            if (dumpDir == null) {
                dumpDir = ".";
            }
            if (isDebug()) debugPrint("dumpDir is " + dumpDir);
        }

        p = argMap.get("stdout");
        boolean traceToStdOut = p != null && !"false".equals(p);
        if (isDebug()) debugPrint("stdout is " + traceToStdOut);

        probeDescPath = argMap.get("probeDescPath");
        if (probeDescPath == null) {
            probeDescPath = ".";
        }
        if (isDebug()) debugPrint("probe descriptor path is " + probeDescPath);
        ProbeDescriptorLoader.init(probeDescPath);
        p = argMap.get("script");
        if (p != null) {
            StringTokenizer tokenizer = new StringTokenizer(p, ",");

	    if (isDebug()) {
                debugPrint(((tokenizer.countTokens() == 1) ? "initial script is " : "initial scripts are " ) + p);
            }
            while (tokenizer.hasMoreTokens()) {
                loadBTraceScript(tokenizer.nextToken(), traceToStdOut);
            }
        }
        p = argMap.get("scriptdir");
        if (p != null) {
            File scriptdir = new File(p);
            if (scriptdir != null && scriptdir.isDirectory()) {
                if (isDebug()) debugPrint("found scriptdir: " + scriptdir.getAbsolutePath());
                File[] files = scriptdir.listFiles();
                if (files != null) {
                    for (File file : files) {
                       loadBTraceScript(file.getAbsolutePath(), traceToStdOut);
                    }
                }
            }
        }
    }

    // This is really a *private* interface to Glassfish monitoring.
    // For now, please avoid using this in any other scenario. 
    public static void handleFlashLightClient(byte[] code, PrintWriter traceWriter) {
        handleNewClient(code, traceWriter);
    }

    // This is really a *private* interface to Glassfish monitoring.
    // For now, please avoid using this in any other scenario. 
    public static void handleFlashLightClient(byte[] code) {
        try {
            String twn = new String("flashlighttrace" + (new Date()).getTime());
            PrintWriter traceWriter = null;
            traceWriter = new PrintWriter(new BufferedWriter(new FileWriter(new File(twn + ".btrace"))));
            handleFlashLightClient(code, traceWriter);
        } catch (IOException ioexp) {
            if (isDebug()) {
                debugPrint(ioexp);
            }
        }
    }

    private static void loadBTraceScript(String filename, boolean traceToStdOut) {
        try {
            if (! filename.endsWith(".class")) {
                if (isDebug()) { 
                    debugPrint("refusing " + filename + ". script should be a pre-compiled .class file");
                }
                return;
            }
            File traceScript = new File(filename);
            if (! traceScript.exists()) {
                if (isDebug()) debugPrint("script " + traceScript + " does not exist!");
                return;
            }

            PrintWriter traceWriter = null;
            if (traceToStdOut) {
                traceWriter = new PrintWriter(System.out);
            } else {
                if (scriptOutputFile == null || scriptOutputFile.length() == 0) {
                    scriptOutputFile = filename + ".btrace";
                    if (isDebug()) debugPrint("scriptOutputFile not specified. defaulting to " + scriptOutputFile);
                }
                traceWriter = new PrintWriter(new BufferedWriter(new FileWriter(new File(scriptOutputFile))));
            }

            Client client = new FileClient(inst, traceScript, traceWriter);

            handleNewClient(client);
        } catch (RuntimeException re) {
            if (isDebug()) debugPrint(re);
        } catch (IOException ioexp) {
            if (isDebug()) debugPrint(ioexp);
        }
    }

    public static final int BTRACE_DEFAULT_PORT = 2020;

    //-- Internals only below this point
    private static void startServer() {
        int port = BTRACE_DEFAULT_PORT;
        String p = argMap.get("port");
        if (p != null) {
            try {
                port = Integer.parseInt(p);
            } catch (NumberFormatException exp) {
                error("invalid port assuming default..");
            }
        }
        ServerSocket ss;
        try {
            if (isDebug()) debugPrint("starting server at " + port);
            System.setProperty("btrace.port", String.valueOf(port));
            if (scriptOutputFile != null && scriptOutputFile.length() > 0) {
                System.setProperty("btrace.output", scriptOutputFile);
            }
            ss = new ServerSocket(port);
        } catch (IOException ioexp) {
            ioexp.printStackTrace();
            return;
        }

        while (true) {
            try {
                if (isDebug()) debugPrint("waiting for clients");
                Socket sock = ss.accept();
                if (isDebug()) debugPrint("client accepted " + sock);
                Client client = new RemoteClient(inst, sock);
                handleNewClient(client);
            } catch (RuntimeException re) {
                if (isDebug()) debugPrint(re);
            } catch (IOException ioexp) {
                if (isDebug()) debugPrint(ioexp);
            }
        }
    }

    private static void handleNewClient(byte[] code, PrintWriter traceWriter) {
        try {
            handleNewClient(new FileClient(inst, code, traceWriter));
        } catch (RuntimeException re) {
            if (isDebug()) {
                debugPrint(re);
            }
        } catch (IOException ioexp) {
            if (isDebug()) {
                debugPrint(ioexp);
            }
        }
    }

    private static void handleNewClient(final Client client) {
        serializedExecutor.submit(new Runnable() {

            public void run() {
                try {
                    if (isDebug()) debugPrint("new Client created " + client);
                    if (client.shouldAddTransformer()) {
                        inst.addTransformer(client, true);
                        Class[] classes = inst.getAllLoadedClasses();
                        ArrayList<Class> list = new ArrayList<Class>();
                        if (isDebug()) debugPrint("filtering loaded classes");
                        for (Class c : classes) {
                            if (inst.isModifiableClass(c) &&
                                client.isCandidate(c)) {
                                if (isDebug()) debugPrint("candidate " + c + " added");
                                list.add(c);
                            }
                        }
                        list.trimToSize();
                        int size = list.size();
                        if (isDebug()) debugPrint("added as ClassFileTransformer");
                        if (size > 0) {
                            classes = new Class[size];
                            list.toArray(classes);
                            if (isDebug()) debugPrint("calling retransformClasses");
                            inst.retransformClasses(classes);
                            client.skipRetransforms();
                            if (isDebug()) debugPrint("finished retransformClasses");
                        }
                    }
                } catch (UnmodifiableClassException uce) {
                    if (isDebug()) {
                        debugPrint(uce);
                    }
                }
            }
        });
        
    }

    private static void error(String msg) {
        System.err.println(msg);
    }

    static void dumpClass(String btraceClassName, String targetClassName, byte[] code) {
        if (dumpClasses) {
            try {
                targetClassName = targetClassName.replace(".", File.separator).replace("/", File.separator);
                int index = targetClassName.lastIndexOf(File.separatorChar);
                StringBuilder buf = new StringBuilder();
                if (!dumpDir.equals(".")) {
                    buf.append(dumpDir);
                    buf.append(File.separatorChar);
                }
                String dir = buf.toString();
                if (index != -1) {
                    dir += targetClassName.substring(0, index);
                }
                new File(dir).mkdirs();
                String file;
                if (index != -1) {
                    file = targetClassName.substring(index+1);
                } else {
                    file = targetClassName;
                }
                file += ".class";
                File out = new File(dir, file);
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    fos.write(code);
                } finally {
                    fos.close();
                }
            } catch (Exception exp) {
                exp.printStackTrace();
            }
        }
    }

    /**
     * Maps a list of @OnProbe's to a list @OnMethod's using
     * probe descriptor XML files.
     */
    static List<OnMethod> mapOnProbes(List<OnProbe> onProbes) {
        List<OnMethod> res = new ArrayList<OnMethod>();
        for (OnProbe op : onProbes) {
            String ns = op.getNamespace();
            if (isDebug()) debugPrint("about to load probe descriptor for " + ns);
            // load probe descriptor for this namespace
            ProbeDescriptor probeDesc = ProbeDescriptorLoader.load(ns);
            if (probeDesc == null) {
                if (isDebug()) debugPrint("failed to find probe descriptor for " + ns);
                continue;
            }
            // find particular probe mappings using "local" name
            OnProbe foundProbe = probeDesc.findProbe(op.getName());
            if (foundProbe == null) {
                if (isDebug()) debugPrint("no probe mappings for " + op.getName());
                continue;
            }
            if (isDebug()) debugPrint("found probe mappings for " + op.getName());
            Collection<OnMethod> omColl = foundProbe.getOnMethods();
            for (OnMethod om : omColl) {
                 // copy the info in a new OnMethod so that
                 // we can set target method name and descriptor
                 // Note that the probe descriptor cache is used
                 // across BTrace sessions. So, we should not update
                 // cached OnProbes (and their OnMethods).
                 OnMethod omn = new OnMethod();
                 omn.copyFrom(om);
                 omn.setTargetName(op.getTargetName());
                 omn.setTargetDescriptor(op.getTargetDescriptor());
                 res.add(omn);
            }
        }
        return res;
    }

    static boolean isDebug() {
        return debugMode;
    }

    static boolean isUnsafe() {
        return unsafeMode;
    }

    static void debugPrint(String msg) {
        System.out.println("btrace DEBUG: " + msg);
    }

    static void debugPrint(Throwable th) {
        System.out.println("btrace DEBUG: " + th);
        th.printStackTrace();
    }
}
