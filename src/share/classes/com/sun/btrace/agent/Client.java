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

import com.sun.btrace.DebugSupport;
import com.sun.btrace.SharedSettings;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.List;
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
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.comm.RetransformClassNotification;
import com.sun.btrace.comm.RetransformationStartNotification;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import com.sun.btrace.runtime.BTraceClassNode;
import com.sun.btrace.runtime.BTraceMethodNode;
import com.sun.btrace.runtime.ClassFilter;
import com.sun.btrace.runtime.CycleDetector;
import com.sun.btrace.runtime.Instrumentor;
import com.sun.btrace.runtime.InstrumentUtils;
import com.sun.btrace.runtime.Location;
import com.sun.btrace.runtime.NullPerfReaderImpl;
import com.sun.btrace.runtime.Verifier;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.runtime.OnProbe;
import com.sun.btrace.runtime.Preprocessor;
import com.sun.btrace.runtime.ProbeDescriptor;
import com.sun.btrace.runtime.RunnableGeneratorImpl;
import com.sun.btrace.util.templates.impl.MethodTrackingExpander;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;


/**
 * Abstract class that represents a BTrace client
 * at the BTrace agent.
 *
 * @author A. Sundararajan
 */
abstract class Client implements ClassFileTransformer, CommandListener {
    protected final Instrumentation inst;
    private volatile BTraceRuntime runtime;
    private volatile boolean isClassRenamed = false;
    private volatile String className;
    private volatile Class btraceClazz;
    private volatile byte[] btraceCode;
    private volatile List<OnMethod> onMethods;
    private volatile List<OnProbe> onProbes;
    private volatile ClassFilter filter;
    private volatile boolean skipRetransforms;
    private volatile boolean hasSubclassChecks;
    private BTraceClassNode btraceClass;

    protected final SharedSettings settings = new SharedSettings();
    private final DebugSupport debug = new DebugSupport(settings);

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

        BTraceRuntime.init(createPerfReaderImpl(), new RunnableGeneratorImpl());
    }

    private static PerfReader createPerfReaderImpl() {
        // see if we can access any jvmstat class
        try {
            Class.forName("sun.jvmstat.monitor.MonitoredHost");
            return (PerfReader) Class.forName("com.sun.btrace.runtime.PerfReaderImpl").newInstance();
        } catch (Exception exp) {
            // no luck, create null implementation
            return new NullPerfReaderImpl();
        }
    }

    Client(Instrumentation inst) {
        this.inst = inst;
    }

    @Override
    public byte[] transform(
                ClassLoader loader,
                String cname,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer)
        throws IllegalClassFormatException {
        boolean entered = BTraceRuntime.enter();
        try {
            if (cname == null) {
                cname = readClassName(classfileBuffer);
                if (cname == null) {
                    debugPrint("skipping transform for unknown class"); // NOI18N
                    return null;
                }
            }
            if (isBTraceClass(cname) || isSensitiveClass(cname)) {
                if (isDebug()) {
                    debugPrint("skipping transform for BTrace class " + cname); // NOI18N
                }
                return null;
            }

            if (classBeingRedefined != null) {
                // class already defined; retransforming
                if (!skipRetransforms && filter.isCandidate(classBeingRedefined)) {
                    return doTransform(loader, classBeingRedefined, cname, classfileBuffer);
                } else {
                    if (isDebug()) {
                        debugPrint("client " + className + "[" + skipRetransforms + "]: skipping transform for " + cname); // NOi18N
                    }
                }
            } else {
                // class not yet defined
                if (filter.isCandidate(loader, cname, classfileBuffer, hasSubclassChecks)) {
                    return doTransform(loader, classBeingRedefined, cname, classfileBuffer);
                } else {
                    if (isDebug()) {
                        debugPrint("client " + className + "[" + skipRetransforms + "]: skipping transform for " + cname); // NOI18N
                    }
                }
            }

            return null; // ignore
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof IllegalClassFormatException) {
                throw (IllegalClassFormatException)e;
            }
            return null;
        } finally {
            if (entered) {
                BTraceRuntime.leave();
            }
        }
    }

    protected final void setSettings(SharedSettings other) {
        settings.from(other);
    }

    protected final void setSettings(Map<String, Object> params) {
        settings.from(params);
    }

    void registerTransformer() {
        inst.addTransformer(this, true);
    }

    void unregisterTransformer() {
        inst.removeTransformer(this);
    }

    private byte[] doTransform(ClassLoader loader, Class<?> classBeingRedefined, String cname, byte[] classfileBuffer) {
        if (isDebug()) {
            debugPrint("client " + className + ": instrumenting " + cname);
        }
        if (settings.isTrackRetransforms()) {
            try {
                onCommand(new RetransformClassNotification(cname));
            } catch (IOException e) {
                debugPrint(e);
            }
        }
        return instrument(loader, classBeingRedefined, cname, classfileBuffer);
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

    protected Class loadClass(InstrumentCommand instr) throws IOException {
        String[] args = instr.getArguments();
        this.btraceCode = instr.getCode();
        try {
            btraceClass = verifyAndLoad(btraceCode);
        } catch (Throwable th) {
            debugPrint(th);
            errorExit(th);
            return null;
        } finally {
            if (settings.isDumpClasses()) {
                debug.dumpClass(className + "_orig", className + "_orig", btraceCode);
            }
        }

        this.filter = new ClassFilter(onMethods);
        debugPrint("created class filter");

        if (isClassRenamed) {
            if (isDebug()) {
                debugPrint("class renamed to " + className);
            }
            onCommand(new RenameCommand(className));
        }
        try {
            if (isDebug()) {
                debugPrint("preprocessing BTrace class " + className);
            }
            btraceCode = btraceClass.getBytecode();
            if (isDebug()) {
                debugPrint("preprocessed BTrace class " + className);
            }
        } catch (Throwable th) {
            debugPrint(th);
            errorExit(th);
            return null;
        }
        if (settings.isDumpClasses()) {
            debug.dumpClass(className, className, btraceCode);
        }
        if (isDebug()) {
            debugPrint("creating BTraceRuntime instance for " + className);
        }
        this.runtime = new BTraceRuntime(className, args, this, debug, inst);
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
            debugPrint("created BTraceRuntime instance for " + className);
            debugPrint("removing @OnMethod, @OnProbe and shared methods");
        }
        byte[] codeBuf = btraceClass.getBytecode(true);

        debugPrint("removed @OnMethod, @OnProbe and shared methods");
        debugPrint("sending Okay command");
        onCommand(new OkayCommand());
        // This extra BTraceRuntime.enter is needed to
        // check whether we have already entered before.
        boolean enteredHere = BTraceRuntime.enter();
        try {
            // The trace class static initializer needs to be run
            // without BTraceRuntime.enter(). Please look at the
            // static initializer code of trace class.
            BTraceRuntime.leave();
            if (isDebug()) {
                debugPrint("about to defineClass " + className);
            }
            if (shouldAddTransformer()) {
                this.btraceClazz = runtime.defineClass(codeBuf);
            } else {
                this.btraceClazz = runtime.defineClass(codeBuf, false);
            }
            if (isDebug()) {
                debugPrint("defineClass succeeded for " + className);
            }
        } catch (Throwable th) {
            debugPrint(th);
            errorExit(th);
            return null;
        } finally {
            // leave BTraceRuntime enter state as it was before
            // we started executing this method.
            if (! enteredHere) BTraceRuntime.enter();
        }
        return this.btraceClazz;
    }

    protected abstract void closeAll() throws IOException;

    protected void errorExit(Throwable th) throws IOException {
        debugPrint("sending error command");
        onCommand(new ErrorCommand(th));
        debugPrint("sending exit command");
        onCommand(new ExitCommand(1));
        closeAll();
    }

    protected final void cleanupTransformers() {
        if (shouldAddTransformer()) {
            if (isDebug()) {
                debugPrint("onExit: removing transformer for " + className);
            }
            unregisterTransformer();
        }
    }

    // package privates below this point
    final boolean isDebug() {
        return settings.isDebug();
    }

    final void debugPrint(String msg) {
        debug.print(msg);
    }

    final void debugPrint(Throwable th) {
        debug.print(th);
    }

    final BTraceRuntime getRuntime() {
        return runtime;
    }

    final String getClassName() {
        return className;
    }

    final Class getBTraceClass() {
        return btraceClazz;
    }

    final boolean isCandidate(Class c) {
        String cname = c.getName().replace('.', '/');
        if (c.isInterface() || c.isPrimitive() || c.isArray()) {
            return false;
        }
        if (isBTraceClass(cname)) {
            return false;
        } else {
            return filter.isCandidate(c);
        }
    }

    final boolean shouldAddTransformer() {
        return onMethods != null && onMethods.size() > 0;
    }

    final void skipRetransforms() {
        skipRetransforms = true;
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

    /*
     * Certain classes like java.lang.ThreadLocal and it's
     * inner classes, java.lang.Object cannot be safely
     * instrumented with BTrace. This is because BTrace uses
     * ThreadLocal class to check recursive entries due to
     * BTrace's own functions. But this leads to infinite recursions
     * if BTrace instruments java.lang.ThreadLocal for example.
     * For now, we avoid such classes till we find a solution.
     */
    private static boolean isSensitiveClass(String name) {
        return name.equals("java/lang/Object") || // NOI18N
               name.startsWith("java/lang/ThreadLocal") || // NOI18N
               name.startsWith("sun/reflect") || // NOI18N
               name.equals("sun/misc/Unsafe")  || // NOI18N
               name.startsWith("sun/security/") || // NOI18N
               name.equals("java/lang/VerifyError"); // NOI18N
    }

    private byte[] instrument(ClassLoader loader, Class clazz, String cname, byte[] target) {
        byte[] instrumentedCode;
        try {
            ClassWriter writer = InstrumentUtils.newClassWriter(loader, target);
            ClassReader reader = new ClassReader(target);
            Instrumentor i = new Instrumentor(loader, clazz, btraceClass, writer);
            InstrumentUtils.accept(reader, i);
            if (isDebug() && !i.hasMatch()) {
                debugPrint("*WARNING* No method was matched for class " + cname); // NOI18N
            }
            instrumentedCode = writer.toByteArray();
        } catch (Throwable th) {
            debugPrint(th);
            return null;
        }
        if (settings.isDumpClasses()) {
            debug.dumpClass(className, cname, instrumentedCode);
        }
        return instrumentedCode;
    }

    private BTraceClassNode verifyAndLoad(byte[] buf) {
        debugPrint("loading and verifying BTrace class");
        BTraceClassNode cn = BTraceClassNode.from(buf, settings.isUnsafe());

        className = cn.name.replace('/', '.');
        isClassRenamed = cn.isClassRenamed();
        if (isDebug()) {
            debugPrint("verified '" + className + "' successfully");
        }
        onMethods = cn.getOnMethods();
        onProbes = cn.getOnProbes();
        if (onProbes != null && !onProbes.isEmpty()) {
            // map @OnProbe's to @OnMethod's and store
            onMethods.addAll(mapOnProbes(onProbes));
        }
        for(OnMethod om : onMethods) {
            verifySpecialParameters(om);
            if (om.getClazz().startsWith("+")) {
                hasSubclassChecks = true;
            }
        }
        return cn;
    }

    private void verifySpecialParameters(OnMethod om) {
        Location loc = om.getLocation();
        if (om.getReturnParameter() != -1) {
            if (!(loc.getValue() == Kind.RETURN ||
                (loc.getValue() == Kind.CALL && loc.getWhere() == Where.AFTER) ||
                (loc.getValue() == Kind.ARRAY_GET && loc.getWhere() == Where.AFTER) ||
                (loc.getValue() == Kind.FIELD_GET && loc.getWhere() == Where.AFTER) ||
                (loc.getValue() == Kind.NEW && loc.getWhere() == Where.AFTER) ||
                (loc.getValue() == Kind.NEWARRAY && loc.getWhere() == Where.AFTER))) {
                Verifier.reportError("return.desc.invalid", om.getTargetName() + om.getTargetDescriptor() + "(" + om.getReturnParameter() + ")");;
            }
        }
        if (om.getTargetMethodOrFieldParameter() != -1) {
            if (!(loc.getValue() == Kind.CALL ||
                loc.getValue() == Kind.FIELD_GET ||
                loc.getValue() == Kind.FIELD_SET)) {
                Verifier.reportError("target-method.desc.invalid", om.getTargetName() + om.getTargetDescriptor() + "(" + om.getTargetMethodOrFieldParameter() + ")");
            }
        }
        if (om.getTargetInstanceParameter() != -1) {
            if (!(loc.getValue() == Kind.CALL ||
                loc.getValue() == Kind.FIELD_GET ||
                loc.getValue() == Kind.FIELD_SET)) {
                Verifier.reportError("target-instance.desc.invalid", om.getTargetName() + om.getTargetDescriptor() + "(" + om.getTargetInstanceParameter() + ")");
            }
        }
        if (om.getDurationParameter() != -1) {
            if (!((loc.getValue() == Kind.RETURN ||
                loc.getValue() == Kind.ERROR) ||
                (loc.getValue() == Kind.CALL &&
                loc.getWhere() == Where.AFTER))) {
                Verifier.reportError("duration.desc.invalid", om.getTargetName() + om.getTargetDescriptor() + "(" + om.getDurationParameter() + ")");
            }
        }
    }

    private static String readClassName(byte[] classfileBuffer) {
        if (classfileBuffer == null || classfileBuffer.length == 0) {
            return null;
        }
        ClassReader cr = new ClassReader(classfileBuffer);
        return cr.getClassName();
    }

    /**
     * Maps a list of @OnProbe's to a list @OnMethod's using
     * probe descriptor XML files.
     */
    private List<OnMethod> mapOnProbes(List<OnProbe> onProbes) {
        ProbeDescriptorLoader pdl = new ProbeDescriptorLoader(settings.getProbeDescPath(), debug);
        List<OnMethod> res = new ArrayList<>();
        for (OnProbe op : onProbes) {
            String ns = op.getNamespace();
            if (isDebug()) debugPrint("about to load probe descriptor for " + ns);
            // load probe descriptor for this namespace
            ProbeDescriptor probeDesc = pdl.load(ns);
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
                 OnMethod omn = new OnMethod(op.getMethodNode());
                 omn.copyFrom(om);
                 omn.setTargetName(op.getTargetName());
                 omn.setTargetDescriptor(op.getTargetDescriptor());
                 omn.setClassNameParameter(op.getClassNameParameter());
                 omn.setMethodParameter(op.getMethodParameter());
                 omn.setDurationParameter(op.getDurationParameter());
                 omn.setMethodFqn(op.isMethodFqn());
                 omn.setReturnParameter(op.getReturnParameter());
                 omn.setSelfParameter(op.getSelfParameter());
                 omn.setTargetInstanceParameter(op.getTargetInstanceParameter());
                 omn.setTargetMethodOrFieldFqn(op.isTargetMethodOrFieldFqn());
                 omn.setTargetMethodOrFieldParameter(op.getTargetMethodOrFieldParameter());
                 res.add(omn);
            }
        }
        return res;
    }
}
