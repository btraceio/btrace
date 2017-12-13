/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace.agent;

import com.sun.btrace.runtime.BTraceTransformer;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.SharedSettings;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.runtime.Constants;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.util.Messages;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

/**
 * This is the main class for BTrace java.lang.instrument agent.
 *
 * @author A. Sundararajan
 * @author Joachim Skeie (rolling output)
 */
public final class Main {
    private static long ts = System.nanoTime();

    private static volatile Map<String, String> argMap;
    private static volatile Instrumentation inst;
    private static volatile Long fileRollMilliseconds;

    private static final Pattern KV_PATTERN = Pattern.compile(",");

    private static final SharedSettings settings = SharedSettings.GLOBAL;
    private static final DebugSupport debug = new DebugSupport(settings);
    private static final BTraceTransformer transformer = new BTraceTransformer(debug);

    // #BTRACE-42: Non-daemon thread prevents traced application from exiting
    private static final ThreadFactory qProcessorThreadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread result = new Thread(r, "BTrace Command Queue Processor");
            result.setDaemon(true);
            return result;
        }
    };

    private static final ExecutorService serializedExecutor = Executors.newSingleThreadExecutor(qProcessorThreadFactory);

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

        try {
            loadArgs(args);
            // set the debug level based on cmdline config
            settings.setDebug(Boolean.parseBoolean(argMap.get("debug")));
            if (isDebug()) {
                debugPrint("parsed command line arguments");
            }
            parseArgs();

            int startedScripts = startScripts();

            String tmp = argMap.get("noServer");
            // noServer is defaulting to true if startup scripts are defined
            boolean noServer = tmp != null ? Boolean.parseBoolean(tmp) : hasScripts();
            if (noServer) {
                if (isDebug()) {
                    debugPrint("noServer is true, server not started");
                }
                return;
            }
            Thread agentThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    BTraceRuntime.enter();
                    try {
                        startServer();
                    } finally {
                        BTraceRuntime.leave();
                    }
                }
            });
            BTraceRuntime.initUnsafe();
            BTraceRuntime.enter();
            try {
                agentThread.setDaemon(true);
                if (isDebug()) {
                    debugPrint("starting agent thread");
                }
                agentThread.start();
            } finally {
                BTraceRuntime.leave();
            }
        } finally {
            inst.addTransformer(transformer, true);
            Main.debugPrint("Agent init took: " + (System.nanoTime() - ts) + "ns");
        }
    }

    private static boolean hasScripts() {
        return argMap.containsKey("script") || argMap.containsKey("scriptDir");
    }

    private static void loadDefaultArguments(String config) {
        try {
            String propTarget = Constants.EMBEDDED_BTRACE_SECTION_HEADER + "agent.properties";
            InputStream is = ClassLoader.getSystemResourceAsStream(propTarget);
            if (is != null) {
                Properties ps = new Properties();
                ps.load(is);
                StringBuilder log = new StringBuilder();
                for (Map.Entry<Object, Object> entry : ps.entrySet()) {
                    String keyConfig = "";
                    String argKey = (String) entry.getKey();
                    int configPos = argKey.lastIndexOf('#');
                    if (configPos > -1) {
                        keyConfig = argKey.substring(0, configPos);
                        argKey = argKey.substring(configPos + 1);
                    }
                    if (config == null || keyConfig.isEmpty() || config.equals(keyConfig)) {
                        String argVal = (String) entry.getValue();
                        switch (argKey) {
                            case "script": {
                                // special treatment for the 'script' parameter
                                boolean replace = false;
                                String scriptVal = argVal;
                                if (scriptVal.startsWith("!")) {
                                    scriptVal = scriptVal.substring(1);
                                    replace = true;
                                } else {
                                    String oldVal = argMap.get(argKey);
                                    if (oldVal != null && !oldVal.isEmpty()) {
                                        scriptVal = oldVal + ":" + scriptVal;
                                    } else {
                                        replace = true;
                                    }
                                }
                                if (replace) {
                                    log.append("setting default agent argument '").append(argKey)
                                            .append("' to '").append(scriptVal).append("'\n");
                                } else {
                                    log.append("augmenting default agent argument '").append(argKey)
                                            .append("':'").append(argMap.get(argKey)).append("' with '").append(argVal)
                                            .append("'\n");
                                }

                                argMap.put(argKey, scriptVal);
                                break;
                            }
                            case "systemClassPath": // fall through
                            case "bootClassPath": // fall through
                            case "config": {
                                log.append("argument '").append(argKey).append("' is not overridable\n");
                                break;
                            }
                            default: {
                                if (!argMap.containsKey(argKey)) {
                                    log.append("applying default agent argument '").append(argKey)
                                            .append("'='").append(argVal).append("'\n");
                                    argMap.put(argKey, argVal);
                                }
                            }
                        }
                    }
                }
                if (argMap.containsKey("debug") && argMap.get("debug").equalsIgnoreCase("true")) {
                    DebugSupport.info(log.toString());
                }
            }
        } catch (IOException e) {
            debug.debug(e);
        }
    }

    private static int startScripts() {
        int scriptCount = 0;

        String p = argMap.get("stdout");
        boolean traceToStdOut = p != null && !"false".equals(p);
        if (isDebug()) {
            debugPrint("stdout is " + traceToStdOut);
        }

        String script = argMap.get("script");
        String scriptDir = argMap.get("scriptdir");

        if (script != null) {
            StringTokenizer tokenizer = new StringTokenizer(script, ":");
            if (isDebug()) {
                debugPrint(((tokenizer.countTokens() == 1) ? "initial script is " : "initial scripts are ") + script);
            }
            while (tokenizer.hasMoreTokens()) {
                if (loadBTraceScript(tokenizer.nextToken(), traceToStdOut)) {
                    scriptCount++;
                }
            }
        }
        if (scriptDir != null) {
            File dir = new File(scriptDir);
            if (dir.isDirectory()) {
                if (isDebug()) {
                    debugPrint("found scriptdir: " + dir.getAbsolutePath());
                }
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (loadBTraceScript(file.getAbsolutePath(), traceToStdOut)) {
                            scriptCount++;
                        }
                    }
                }
            }
        }
        return scriptCount;
    }

    private static void usage() {
        System.out.println(Messages.get("btrace.agent.usage"));
        System.exit(0);
    }

    private static void loadArgs(String args) {
        if (args == null) {
            args = "";
        }
        String[] pairs = KV_PATTERN.split(args);
        argMap = new HashMap<>();
        for (String s : pairs) {
            int i = s.indexOf('=');
            String key, value = "";
            if (i != -1) {
                key = s.substring(0, i).trim();
                if (i + 1 < s.length()) {
                    value = s.substring(i + 1).trim();
                }
            } else {
                key = s;
            }
            argMap.put(key, value);
        }
    }

    private static void parseArgs() {
        String p = argMap.get("help");
        if (p != null) {
            usage();
        }

        String libs = argMap.get("libs");
        String config = argMap.get("config");
        processClasspaths(libs);
        loadDefaultArguments(config);


        p = argMap.get("debug");
        settings.setDebug(p != null && !"false".equals(p));
        if (isDebug()) {
            debugPrint("debugMode is " + settings.isDebug());
        }

        for (Map.Entry<String, String> e : argMap.entrySet()) {
            String key = e.getKey();
            p = e.getValue();
            switch (key) {
                case "startupRetransform": {
                    if (!p.isEmpty()) {
                        settings.setRetransformStartup(p.isEmpty() ? false : Boolean.parseBoolean(p));
                        if (isDebug()) {
                            debugPrint("startupRetransform is " + settings.isRetransformStartup());
                        }
                    }
                    break;
                }
                case "dumpDir": {
                    String dumpClassesVal = argMap.get("dumpClasses");
                    if (dumpClassesVal != null) {
                        boolean dumpClasses = Boolean.parseBoolean(dumpClassesVal);
                        if (isDebug()) {
                            debugPrint("dumpClasses is " + dumpClasses);
                        }
                        if (dumpClasses) {
                            String dumpDir = argMap.get("dumpDir");
                            settings.setDumpDir(dumpDir != null ? dumpDir : ".");
                            if (isDebug()) {
                                debugPrint("dumpDir is " + dumpDir);
                            }
                        }
                    }
                    break;
                }
                case "cmdQueueLimit": {
                    if (!p.isEmpty()) {
                        System.setProperty(BTraceRuntime.CMD_QUEUE_LIMIT_KEY, p);
                        if (isDebug()) {
                           debugPrint("cmdQueueLimit provided: " + p);
                        }
                    }

                    break;
                }
                case "trackRetransforms": {
                    if (!p.isEmpty()) {
                        settings.setTrackRetransforms(Boolean.parseBoolean(p));
                        if (settings.isTrackRetransforms()) {
                            debugPrint("trackRetransforms is " + settings.isTrackRetransforms());
                        }
                    }
                    break;
                }
                case "scriptOutputFile": {
                    if (!p.isEmpty()) {
                        settings.setOutputFile(p);
                        if (isDebug()) {
                            debugPrint("scriptOutputFile is " + p);
                        }
                    }
                    break;
                }
                case "scriptOutputDir": {
                    if (!p.isEmpty()) {
                        settings.setOutputDir(p);
                        if (isDebug()) {
                            debugPrint("scriptOutputDir is " + p);
                        }
                    }
                    break;
                }
                case "fileRollMilliseconds": {
                    if (!p.isEmpty()) {
                        Long msParsed = null;
                        try {
                            msParsed = Long.parseLong(p);
                            fileRollMilliseconds = msParsed;
                        } catch (NumberFormatException nfe) {
                            fileRollMilliseconds = null;
                        }
                        if (fileRollMilliseconds != null) {
                            settings.setFileRollMilliseconds(fileRollMilliseconds.intValue());
                            if (isDebug()) {
                                debugPrint("fileRollMilliseconds is " + fileRollMilliseconds);
                            }
                        }
                    }
                    break;
                }
                case "fileRollMaxRolls": {
                    if (!p.isEmpty()) {
                        Integer rolls = null;
                        try {
                            rolls = Integer.parseInt(p);
                        } catch (NumberFormatException nfe) {
                            rolls = null;
                        }

                        if (rolls != null) {
                            settings.setFileRollMaxRolls(rolls);
                        }
                    }
                    break;
                }
                case "unsafe": // fallthrough
                case "trusted": {
                    if (!p.isEmpty()) {
                        settings.setTrusted(Boolean.parseBoolean(p));
                        if (isDebug()) {
                            debugPrint("trustedMode is " + settings.isTrusted());
                        }
                    }
                    break;
                }
                case "statsd": {
                    if (!p.isEmpty()) {
                        String[] parts = p.split(":");
                        if (parts.length == 2) {
                            settings.setStatsdHost(parts[0].trim());
                            try {
                                settings.setStatsdPort(Integer.parseInt(parts[1].trim()));
                            } catch (NumberFormatException ex) {
                                DebugSupport.warning("Invalid statsd port number: " + parts[1]);
                                // leave the port unconfigured
                            }
                        } else if (parts.length == 1) {
                            settings.setStatsdHost(parts[0].trim());
                        }
                    }
                    break;
                }
                case "probeDescPath": {
                    if (!p.isEmpty()) {
                        settings.setProbeDescPath(!p.isEmpty() ? p : ".");
                        if (isDebug()) {
                            debugPrint("probe descriptor path is " + settings.getProbeDescPath());
                        }
                    }
                    break;
                }

                default: {
                    if (key.startsWith("$")) {
                        String pKey = key.substring(1);
                        System.setProperty(pKey, p);
                        if (isDebug()) {
                            debugPrint("Setting system property: " + pKey + "=" + p);
                        }
                    }
                }
            }
        }
    }

    private static void processClasspaths(String libs) {
        String bootClassPath = argMap.get("bootClassPath");
        if (bootClassPath != null) {
            if (isDebug()) {
                debugPrint("Bootstrap ClassPath: " + bootClassPath);
            }
            StringTokenizer tokenizer = new StringTokenizer(bootClassPath, File.pathSeparator);
            try {
                while (tokenizer.hasMoreTokens()) {
                    String path = tokenizer.nextToken();
                    File f = new File(path);
                    if (!f.exists()) {
                        debug.warning("BTrace bootstrap classpath resource [ " + path + "] does not exist");
                    } else {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                            JarFile jf = new JarFile(f);
                            inst.appendToBootstrapClassLoaderSearch(jf);
                        } else {
                            debugPrint("ignoring boot classpath element '" + path
                                    + "' - only jar files allowed");
                        }
                    }
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
                    File f = new File(path);
                    if (!f.exists()) {
                        debug.warning("BTrace system classpath resource [" + path + "] does not exist.");
                    } else {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                            JarFile jf = new JarFile(f);
                            inst.appendToSystemClassLoaderSearch(jf);
                        } else {
                            debugPrint("ignoring system classpath element '" + path
                                    + "' - only jar files allowed");
                        }
                    }
                }
            } catch (IOException ex) {
                debugPrint("adding to boot classpath failed!");
                debugPrint(ex);
                return;
            }
        }

        addPreconfLibs(libs);
    }

    private static void addPreconfLibs(String libs) {
        URL u = Main.class.getClassLoader().getResource(Main.class.getName().replace('.', '/') + ".class");
        if (u != null) {
            String path = u.toString();
            int delimiterPos = path.lastIndexOf('!');
            if (delimiterPos > -1) {
                String jar = path.substring(9, delimiterPos);
                File jarFile = new File(jar);
                String libPath = new File(jarFile.getParent() + File.separator + "btrace-libs").getAbsolutePath();
                appendToBootClassPath(libPath);
                appendToSysClassPath(libPath);
                appendToBootClassPath(libPath, libs);
                appendToSysClassPath(libPath, libs);
            }
        }
    }

    private static void appendToBootClassPath(String libPath) {
        appendToBootClassPath(libPath, null);
    }

    private static void appendToBootClassPath(String libPath, String libs) {
        File libFolder = new File(libPath + (libs != null ? File.separator + libs : "") + File.separator + "boot");
        if (libFolder.exists()) {
            for (File f : libFolder.listFiles()) {
                if (f.getName().toLowerCase().endsWith(".jar")) {
                    try {
                        if (isDebug()) {
                            debugPrint("Adding " + f.getAbsolutePath() + " to bootstrap classpath");
                        }
                        inst.appendToBootstrapClassLoaderSearch(new JarFile(f));
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    private static void appendToSysClassPath(String libPath) {
        appendToSysClassPath(libPath, null);
    }

    private static void appendToSysClassPath(String libPath, String libs) {
        File libFolder = new File(libPath + (libs != null ? File.separator + libs : "") + File.separator + "system");
        if (libFolder.exists()) {
            for (File f : libFolder.listFiles()) {
                if (f.getName().toLowerCase().endsWith(".jar")) {
                    try {
                        if (isDebug()) {
                            debugPrint("Adding " + f.getAbsolutePath() + " to system classpath");
                        }
                        inst.appendToSystemClassLoaderSearch(new JarFile(f));
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    private static boolean loadBTraceScript(String filePath, boolean traceToStdOut) {
        try {
            String scriptName = "";
            String scriptParent = "";
            File traceScript = new File(filePath);
            scriptName = traceScript.getName();
            scriptParent = traceScript.getParent();

            if (!traceScript.exists()) {
                traceScript = new File(Constants.EMBEDDED_BTRACE_SECTION_HEADER + filePath);
            }

            if (!scriptName.endsWith(".class")) {
                if (isDebug()) {
                    debugPrint("refusing " + filePath + " - script should be a pre-compiled .class file");
                }
                return false;
            }

            SharedSettings clientSettings = new SharedSettings();
            clientSettings.from(settings);
            clientSettings.setClientName(scriptName);
            if (traceToStdOut) {
                clientSettings.setOutputFile("::stdout");
            } else {
                String traceOutput = clientSettings.getOutputFile();
                String outDir = clientSettings.getOutputDir();
                if (traceOutput == null || traceOutput.length() == 0) {
                    clientSettings.setOutputFile("${client}-${agent}.${ts}.btrace[default]");
                    if (outDir == null || outDir.length() == 0) {
                        clientSettings.setOutputDir(scriptParent);
                    }
                }
            }
            ClientContext ctx = new ClientContext(inst, transformer, clientSettings);
            Client client = new FileClient(ctx, traceScript);
            if (client.isInitialized()) {
                handleNewClient(client).get();
                return true;
            }
        } catch (NullPointerException e) {
            if (isDebug()) {
                debugPrint("script " + filePath + " does not exist!");
            }
        } catch (RuntimeException | IOException | ExecutionException re) {
            if (isDebug()) {
                debugPrint(re);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
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
            if (isDebug()) {
                debugPrint("starting server at " + port);
            }
            System.setProperty("btrace.port", String.valueOf(port));
            String scriptOutputFile = settings.getOutputFile();
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
                if (isDebug()) {
                    debugPrint("waiting for clients");
                }
                Socket sock = ss.accept();
                if (isDebug()) {
                    debugPrint("client accepted " + sock);
                }
                ClientContext ctx = new ClientContext(inst, transformer, settings);
                Client client = new RemoteClient(ctx, sock);
                handleNewClient(client).get();
            } catch (RuntimeException | IOException | ExecutionException re) {
                if (isDebug()) {
                    debugPrint(re);
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static Future<?> handleNewClient(final Client client) {
        return serializedExecutor.submit(new Runnable() {

            @Override
            public void run() {
                boolean entered = BTraceRuntime.enter();
                try {
                    client.debugPrint("new Client created " + client);
                    client.retransformLoaded();
                } catch (UnmodifiableClassException uce) {
                    if (isDebug()) {
                        debugPrint(uce);
                    }
                    client.getRuntime().send(new ErrorCommand(uce));
                } finally {
                    if (entered) {
                        BTraceRuntime.leave();
                    }
                }
            }
        });

    }

    private static void error(String msg) {
        System.err.println("btrace ERROR: " + msg);
    }

    private static boolean isDebug() {
        return settings.isDebug();
    }

    private static void debugPrint(String msg) {
        debug.debug(msg);
    }

    private static void debugPrint(Throwable th) {
        debug.debug(th);
    }
}
