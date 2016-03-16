/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
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

import com.sun.btrace.runtime.BTraceProbeFactory;
import com.sun.btrace.runtime.ClassCache;
import com.sun.btrace.runtime.ClassInfo;
import com.sun.btrace.runtime.BTraceTransformer;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.SharedSettings;
import java.io.IOException;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.CommandListener;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import com.sun.btrace.comm.OkayCommand;
import com.sun.btrace.comm.RenameCommand;
import com.sun.btrace.PerfReader;
import com.sun.btrace.comm.RetransformationStartNotification;
import com.sun.btrace.runtime.BTraceProbe;
import com.sun.btrace.runtime.ClassFilter;
import com.sun.btrace.runtime.Instrumentor;
import com.sun.btrace.runtime.InstrumentUtils;
import com.sun.btrace.util.templates.impl.MethodTrackingExpander;
import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;


/**
 * Abstract class that represents a BTrace client
 * at the BTrace agent.
 *
 * @author A. Sundararajan
 * @author J. Bachorik (j.bachorik@btrace.io)
 */
abstract class Client implements CommandListener {
    private static final Map<String, PrintWriter> writerMap = new HashMap<>();

    protected final Instrumentation inst;
    private volatile BTraceRuntime runtime;
    private volatile String outputName;
    private volatile byte[] btraceCode;
    private BTraceProbe probe;

    private Timer flusher;
    protected volatile PrintWriter out;

    protected final SharedSettings settings;
    protected final DebugSupport debug;
    private final BTraceTransformer transformer;

    static {
        ClassFilter.class.getClass();
        InstrumentUtils.class.getClass();
        Instrumentor.class.getClass();
        ClassReader.class.getClass();
        ClassWriter.class.getClass();
        AnnotationParser.class.getClass();
        AnnotationType.class.getClass();
        Annotation.class.getClass();
        MethodTrackingExpander.class.getClass();
        ClassCache.class.getClass();
        ClassInfo.class.getClass();

        BTraceRuntime.init(createPerfReaderImpl(), new RunnableGeneratorImpl());
    }

    Client(ClientContext ctx) {
        this(ctx.getInstr(), ctx.getSettings(), ctx.getTransformer());
    }

    private Client(Instrumentation inst, SharedSettings s, BTraceTransformer t) {
        this.inst  = inst;
        this.settings = s != null ? s : SharedSettings.GLOBAL;
        this.transformer = t;
        this.debug = new DebugSupport(settings);
        setupWriter();
    }

    protected final void setupWriter() {
        String outputFile = settings.getOutputFile();
        if (outputFile == null) return;

        if (!outputFile.equals("::stdout")) {
            String outputDir = settings.getOutputDir();
            String output = (outputDir != null ? outputDir + File.separator : "") + outputFile;
            outputFile = templateOutputFileName(output);
            infoPrint("Redirecting output to " + outputFile);
        }
        out = writerMap.get(outputFile);
        if (out == null) {
            if (outputFile.equals("::stdout")) {
                out = new PrintWriter(System.out);
            } else {
                if (SharedSettings.GLOBAL.getFileRollMilliseconds() > 0) {
                    out = new PrintWriter(new BufferedWriter(
                        TraceOutputWriter.rollingFileWriter(new File(outputFile), settings)
                    ));
                } else {
                    out = new PrintWriter(new BufferedWriter(TraceOutputWriter.fileWriter(new File(outputFile), settings)));
                }
            }
            writerMap.put(outputFile, out);
            out.append("### BTrace Log: " + DateFormat.getInstance().format(new Date()) + "\n\n");
            startFlusher();
        }
        outputName = outputFile;
    }

    private void startFlusher() {
        int flushInterval;
        String flushIntervalStr = System.getProperty("com.sun.btrace.FileClient.flush", "5");
        try {
            flushInterval = Integer.parseInt(flushIntervalStr);
        } catch (NumberFormatException e) {
            flushInterval = 5; // default
        }

        final int flushSec  = flushInterval;
        if (flushSec > -1) {
            flusher = new Timer("BTrace FileClient Flusher", true);
            flusher.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (out != null) {
                        out.flush();
                    }
                }
            }, flushSec, flushSec);
        } else {
            flusher = null;
        }
    }

    private String templateOutputFileName(String fName) {
        if (fName != null) {
            boolean dflt = fName.contains("[default]");
            String agentName = System.getProperty("btrace.agent", "default");
            String clientName = settings.getClientName();
            fName = fName
                        .replace("${client}", clientName != null ? clientName : "")
                        .replace("${ts}", String.valueOf(System.currentTimeMillis()))
                        .replace("${agent}", agentName != null ? "." + agentName : "")
                        .replace("[default]", "");

            if (dflt && settings.isDebug()) {
                debugPrint("scriptOutputFile not specified. defaulting to " + fName);
            }
        }
        return fName;
    }

    protected synchronized void onExit(int exitCode) {
        cleanupTransformers();
        try {
            debugPrint("onExit: closing all");
            Thread.sleep(300);
            closeAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ioexp) {
            debugPrint(ioexp);
        }
    }

    protected final Class loadClass(InstrumentCommand instr) throws IOException {
        String[] args = instr.getArguments();
        this.btraceCode = instr.getCode();
        try {
            probe = verifyAndLoad(btraceCode);
        } catch (Throwable th) {
            debugPrint(th);
            errorExit(th);
            return null;
        }

        if (probe.isClassRenamed()) {
            if (isDebug()) {
                debugPrint("class renamed to " + probe.getClassName());
            }
            onCommand(new RenameCommand(probe.getClassName()));
        }
        if (isDebug()) {
            debugPrint("creating BTraceRuntime instance for " + probe.getClassName());
        }
        this.runtime = new BTraceRuntime(probe.getClassName(), args, this, debug, inst);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
	        boolean entered = BTraceRuntime.enter(runtime);
		try {
		    if (runtime != null) runtime.handleExit(0);
		} finally {
                    if (entered) {
                        BTraceRuntime.leave();
		    }
		}
            }
        }));
        if (isDebug()) {
            debugPrint("created BTraceRuntime instance for " + probe.getClassName());
            debugPrint("sending Okay command");
        }

        onCommand(new OkayCommand());

        try {
            return probe.register(runtime, transformer);
        } catch (Throwable th) {
            debugPrint(th);
            errorExit(th);
            return null;
        }
    }

    protected void closeAll() throws IOException {
        if (flusher != null) {
            flusher.cancel();
        }
        if (out != null) {
            out.close();
        }
        writerMap.remove(outputName);
    }

    protected final void errorExit(Throwable th) throws IOException {
        debugPrint("sending error command");
        onCommand(new ErrorCommand(th));
        debugPrint("sending exit command");
        onCommand(new ExitCommand(1));
        closeAll();
    }

    protected final void cleanupTransformers() {
        if (probe != null) {
            probe.unregister();
        }
    }

    // package privates below this point
    final void infoPrint(String msg) {
        DebugSupport.info(msg);
    }

    final boolean isDebug() {
        return settings.isDebug();
    }

    final void debugPrint(String msg) {
        debug.debug(msg);
    }

    final void debugPrint(Throwable th) {
        debug.debug(th);
    }

    final BTraceRuntime getRuntime() {
        return runtime;
    }

    protected final String getClassName() {
        return probe.getClassName();
    }

    final boolean isCandidate(Class c) {
        String cname = c.getName().replace('.', '/');
        if (c.isInterface() || c.isPrimitive() || c.isArray()) {
            return false;
        }
        if (isBTraceClass(cname)) {
            return false;
        } else {
            return probe.willInstrument(c);
        }
    }

    final void startRetransformClasses(int numClasses) {
        try {
            onCommand(new RetransformationStartNotification(numClasses));
            if (isDebug()) {
                debugPrint("calling retransformClasses (" + numClasses + " classes to be retransformed)");
            }
        } catch (IOException e) {
            debugPrint(e);
        }
    }

    final void endRetransformClasses() {
        try {
            onCommand(new OkayCommand());
            if (isDebug()) debugPrint("finished retransformClasses");
        } catch (IOException e) {
            debugPrint(e);
        }
    }

    // Internals only below this point
    private static boolean isBTraceClass(String name) {
        return name.startsWith("com/sun/btrace/");
    }

    private BTraceProbe verifyAndLoad(byte[] buf) {
        BTraceProbeFactory f = new BTraceProbeFactory(settings);
        debugPrint("loading BTrace class");
        BTraceProbe cn = f.createProbe(buf);

        if (isDebug()) {
            debugPrint("loaded '" + cn.getClassName() + "' successfully");
        }
        return cn;
    }

    private static PerfReader createPerfReaderImpl() {
        // see if we can access any jvmstat class
        try {
            Class.forName("sun.jvmstat.monitor.MonitoredHost");
            return (PerfReader) Class.forName("com.sun.btrace.agent.PerfReaderImpl").newInstance();
        } catch (Exception exp) {
            // no luck, create null implementation
            return new NullPerfReaderImpl();
        }
    }

    void retransformLoaded() throws UnmodifiableClassException {
        if (probe.isTransforming() && settings.isRetransformStartup()) {
            ArrayList<Class> list = new ArrayList<>();
            debugPrint("retransforming loaded classes");
            debugPrint("filtering loaded classes");
            ClassCache cc = ClassCache.getInstance();
            for (Class c : inst.getAllLoadedClasses()) {
                if (c != null) {
                    cc.get(c);
                    if (inst.isModifiableClass(c) &&  isCandidate(c)) {
                        debugPrint("candidate " + c + " added");
                        list.add(c);
                    }
                }
            }
            list.trimToSize();
            int size = list.size();
            if (size > 0) {
                Class[] classes = new Class[size];
                list.toArray(classes);
                startRetransformClasses(size);
                if (isDebug()) {
                    for(Class c : classes) {
                        try {
                            inst.retransformClasses(c);
                        } catch (VerifyError e) {
                            debugPrint("verification error: " + c.getName());
                        }
                    }
                } else {
                    inst.retransformClasses(classes);
                }
            }
        }
        runtime.send(new OkayCommand());
    }
}
