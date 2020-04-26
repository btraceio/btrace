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

package org.openjdk.btrace.agent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.OkayCommand;
import org.openjdk.btrace.core.comm.RenameCommand;
import org.openjdk.btrace.core.comm.RetransformationStartNotification;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;
import org.openjdk.btrace.instr.BTraceProbePersisted;
import org.openjdk.btrace.instr.BTraceTransformer;
import org.openjdk.btrace.instr.ClassCache;
import org.openjdk.btrace.instr.ClassFilter;
import org.openjdk.btrace.instr.ClassInfo;
import org.openjdk.btrace.instr.HandlerRepository;
import org.openjdk.btrace.instr.InstrumentUtils;
import org.openjdk.btrace.instr.Instrumentor;
import org.openjdk.btrace.instr.templates.impl.MethodTrackingExpander;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;
import org.openjdk.btrace.runtime.BTraceRuntimes;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;

/**
 * Abstract class that represents a BTrace client at the BTrace agent.
 *
 * @author A. Sundararajan
 * @author J. Bachorik (j.bachorik@btrace.io)
 */
abstract class Client implements CommandListener {
  private static final Map<String, PrintWriter> WRITER_MAP = new HashMap<>();
  private static final Pattern SYSPROP_PTN = Pattern.compile("\\$\\{(.+?)\\}");

  static {
    ClassFilter.class.getClassLoader();
    InstrumentUtils.class.getClassLoader();
    Instrumentor.class.getClassLoader();
    ClassReader.class.getClassLoader();
    ClassWriter.class.getClassLoader();
    AnnotationParser.class.getClassLoader();
    AnnotationType.class.getClassLoader();
    Annotation.class.getClassLoader();
    MethodTrackingExpander.class.getClassLoader();
    ClassCache.class.getClassLoader();
    ClassInfo.class.getClassLoader();
  }

  protected final Instrumentation inst;
  protected final SharedSettings settings;
  protected final DebugSupport debug;
  protected final ArgsMap argsMap;
  private final BTraceTransformer transformer;
  protected volatile PrintWriter out;
  private volatile BTraceRuntime.Impl runtime;
  private volatile String outputName;
  private byte[] btraceCode;
  private BTraceProbe probe;
  private Timer flusher;
  private volatile boolean initialized = false;
  private volatile boolean shuttingDown = false;

  Client(ClientContext ctx) {
    this(ctx.getInstr(), ctx.getArguments(), ctx.getSettings(), ctx.getTransformer());
  }

  private Client(Instrumentation inst, ArgsMap argsMap, SharedSettings s, BTraceTransformer t) {
    this.inst = inst;
    this.argsMap = argsMap;
    settings = s != null ? s : SharedSettings.GLOBAL;
    transformer = t;
    debug = new DebugSupport(settings);

    setupWriter();
  }

  private static String pid() {
    String pName = ManagementFactory.getRuntimeMXBean().getName();
    if (pName != null && pName.length() > 0) {
      String[] parts = pName.split("@");
      if (parts.length == 2) {
        return parts[0];
      }
    }

    return "-1";
  }

  protected final void initialize() {
    initialized = true;
  }

  @SuppressWarnings("DefaultCharset")
  protected final void setupWriter() {
    String outputFile = settings.getOutputFile();
    if (outputFile == null || outputFile.equals("::null") || outputFile.equals("/dev/null")) return;

    if (!outputFile.equals("::stdout")) {
      String outputDir = settings.getOutputDir();
      String output = (outputDir != null ? outputDir + File.separator : "") + outputFile;
      outputFile = templateOutputFileName(output);
      infoPrint("Redirecting output to " + outputFile);
    }
    out = WRITER_MAP.get(outputFile);
    if (out == null) {
      if (outputFile.equals("::stdout")) {
        out = new PrintWriter(System.out);
      } else {
        if (settings.getFileRollMilliseconds() > 0) {
          out =
              new PrintWriter(
                  new BufferedWriter(
                      TraceOutputWriter.rollingFileWriter(new File(outputFile), settings)));
        } else {
          out =
              new PrintWriter(
                  new BufferedWriter(TraceOutputWriter.fileWriter(new File(outputFile), settings)));
        }
      }
      WRITER_MAP.put(outputFile, out);
      out.append("### BTrace Log: ")
          .append(DateFormat.getInstance().format(new Date()))
          .append("\n\n");
      startFlusher();
    }
    outputName = outputFile;
  }

  private void startFlusher() {
    int flushInterval;
    String flushIntervalStr = System.getProperty("org.openjdk.btrace.FileClient.flush");
    if (flushIntervalStr == null) {
      flushIntervalStr = System.getProperty("com.sun.btrace.FileClient.flush", "5");
    }
    try {
      flushInterval = Integer.parseInt(flushIntervalStr);
    } catch (NumberFormatException e) {
      flushInterval = 5; // default
    }

    int flushSec = flushInterval;
    if (flushSec > -1) {
      flusher = new Timer("BTrace FileClient Flusher", true);
      flusher.scheduleAtFixedRate(
          new TimerTask() {
            @Override
            public void run() {
              try {
                if (out != null) {
                  boolean entered = BTraceRuntime.enter();
                  try {
                    out.flush();
                  } finally {
                    if (entered) {
                      BTraceRuntime.leave();
                    }
                  }
                }
              } catch (Throwable t) {
                t.printStackTrace();
              }
            }
          },
          flushSec,
          flushSec);
    } else {
      flusher = null;
    }
  }

  private String templateOutputFileName(String fName) {
    if (fName != null) {
      boolean dflt = fName.contains("[default]");
      String agentName = System.getProperty("btrace.agent", "default");
      String clientName = settings.getClientName();
      fName =
          fName
              .replace("${client}", clientName != null ? clientName : "")
              .replace("${ts}", String.valueOf(System.currentTimeMillis()))
              .replace("${pid}", pid())
              .replace("${agent}", agentName != null ? "." + agentName : "")
              .replace("[default]", "");

      fName = replaceSysProps(fName);
      if (dflt && settings.isDebug()) {
        debugPrint("scriptOutputFile not specified. defaulting to " + fName);
      }
    }
    return fName;
  }

  private String replaceSysProps(String str) {
    int replaced = 0;
    do {
      StringBuffer sb = new StringBuffer();
      replaced = replaceSysProps(str, sb);
      str = sb.toString();
    } while (replaced > 0);
    return str;
  }

  private int replaceSysProps(String str, StringBuffer sb) {
    int cnt = 0;
    Matcher m = SYSPROP_PTN.matcher(str);
    while (m.find()) {
      String key = m.group(1);
      String val = System.getProperty(key);
      if (val != null) {
        cnt++;
        m.appendReplacement(sb, val);
      } else {
        m.appendReplacement(sb, m.group(0));
      }
    }
    m.appendTail(sb);
    return cnt;
  }

  protected synchronized void onExit(int exitCode) {
    if (!shuttingDown) {
      shuttingDown = true;
      if (out != null) {
        out.flush();
      }

      BTraceRuntime.leave();
      try {
        debugPrint("onExit:");
        debugPrint("cleaning up transformers");
        cleanupTransformers();
        debugPrint("removing instrumentation");
        retransformLoaded();
        debugPrint("closing all I/O");
        Thread.sleep(300);
        try {
          closeAll();
        } catch (IOException e) {
          // ignore IOException when closing
        }
        debugPrint("done");
      } catch (Throwable th) {
        // ExitException is expected here
        if (!th.getClass().getName().equals("ExitException")) {
          debugPrint(th);
          BTraceRuntime.handleException(th);
        }
      } finally {
        runtime.shutdownCmdLine();
        HandlerRepository.unregisterProbe(probe);
      }
    }
  }

  protected final Class<?> loadClass(InstrumentCommand instr) throws IOException {
    return loadClass(instr, true);
  }

  protected final Class<?> loadClass(InstrumentCommand instr, boolean canLoadPack)
      throws IOException {
    ArgsMap args = instr.getArguments();
    btraceCode = instr.getCode();
    try {
      probe = load(btraceCode, ArgsMap.merge(argsMap, args), canLoadPack);
      if (probe == null) {
        debugPrint("Failed to load BTrace probe code");
        return null;
      }

      if (!settings.isTrusted()) {
        probe.checkVerified();
      }
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
    runtime = BTraceRuntimes.getRuntime(probe.getClassName(), args, this, debug, inst);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    if (runtime != null) {
                      runtime.handleExit(0);
                    }
                  }
                }));
    if (isDebug()) {
      debugPrint("created BTraceRuntime instance for " + probe.getClassName());
      debugPrint("sending Okay command");
    }

    onCommand(new OkayCommand());

    boolean entered = false;
    try {
      entered = BTraceRuntimeAccess.enter(runtime);
      return probe.register(runtime, transformer);
    } catch (Throwable th) {
      debugPrint(th);
      errorExit(th);
      return null;
    } finally {
      if (entered) {
        BTraceRuntime.leave();
      }
    }
  }

  protected void closeAll() throws IOException {
    if (flusher != null) {
      flusher.cancel();
    }
    if (out != null) {
      out.close();
    }
    WRITER_MAP.remove(outputName);
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
  final boolean isInitialized() {
    return initialized;
  }

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

  final BTraceRuntime.Impl getRuntime() {
    return runtime;
  }

  protected final String getClassName() {
    return probe != null ? probe.getClassName() : "<unknown>";
  }

  final boolean isCandidate(Class c) {
    String cname = c.getName().replace('.', '/');
    if (c.isInterface() || c.isPrimitive() || c.isArray()) {
      return false;
    }
    if (ClassFilter.isSensitiveClass(cname)) {
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
  private BTraceProbe load(byte[] buf, ArgsMap args, boolean canLoadPack) {
    BTraceProbeFactory f = new BTraceProbeFactory(settings, canLoadPack);
    debugPrint("loading BTrace class");
    BTraceProbe cn = f.createProbe(buf, args);

    if (cn != null) {
      if (isDebug()) {
        if (cn.isVerified()) {
          debugPrint("loaded '" + cn.getClassName() + "' successfully");
        } else {
          debugPrint(cn.getClassName() + " failed verification");
          return null;
        }
      }
    }
    return BTraceProbePersisted.from(cn);
  }

  void retransformLoaded() throws UnmodifiableClassException {
    if (runtime != null) {
      if (probe.isTransforming() && settings.isRetransformStartup()) {
        ArrayList<Class<?>> list = new ArrayList<>();
        debugPrint("retransforming loaded classes");
        debugPrint("filtering loaded classes");
        ClassCache cc = ClassCache.getInstance();
        for (Class<?> c : inst.getAllLoadedClasses()) {
          if (c != null) {
            if (inst.isModifiableClass(c) && isCandidate(c)) {
              debugPrint("candidate " + c + " added");
              list.add(c);
            }
          }
        }
        list.trimToSize();
        int size = list.size();
        if (size > 0) {
          Class<?>[] classes = new Class[size];
          list.toArray(classes);
          startRetransformClasses(size);
          if (isDebug()) {
            for (Class<?> c : classes) {
              try {
                debugPrint("Attempting to retransform class: " + c.getName());
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
}
