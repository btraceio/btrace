/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.List;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.CommandListener;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import com.sun.btrace.comm.OkayCommand;
import com.sun.btrace.comm.RenameCommand;
import com.sun.btrace.PerfReader;
import com.sun.btrace.runtime.ClassFilter;
import com.sun.btrace.runtime.ClassRenamer;
import com.sun.btrace.runtime.Instrumentor;
import com.sun.btrace.runtime.InstrumentUtils;
import com.sun.btrace.runtime.MethodRemover;
import com.sun.btrace.runtime.NullPerfReaderImpl;
import com.sun.btrace.runtime.Preprocessor;
import com.sun.btrace.runtime.Verifier;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.runtime.OnProbe;
import com.sun.btrace.runtime.RunnableGeneratorImpl;
import com.sun.btrace.util.EmptyMethodsEvaluator;
import com.sun.btrace.util.NullVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;

/**
 * Abstract class that represents a BTrace client
 * at the BTrace agent.
 *
 * @author A. Sundararajan
 */
abstract class Client implements ClassFileTransformer, CommandListener {
    protected final Instrumentation inst;
    private volatile BTraceRuntime runtime;
    private volatile String className;
    private volatile Class btraceClazz;
    private volatile byte[] btraceCode;
    private volatile List<OnMethod> onMethods;
    private volatile List<OnProbe> onProbes;
    private volatile ClassFilter filter;
    private volatile boolean skipRetransforms;
    protected final boolean debug = Main.isDebug();

    static {
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

    public byte[] transform(
                ClassLoader loader,
                String cname,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer)
        throws IllegalClassFormatException {
        boolean entered = BTraceRuntime.enter();
        try {
            if (isBTraceClass(cname) || isSensitiveClass(cname)) {
                if (debug) Main.debugPrint("skipping transform for BTrace class " + cname);
                return null;
            }
            if (classBeingRedefined != null &&
                skipRetransforms == false &&
                filter.isCandidate(classBeingRedefined)) {
                if (debug) Main.debugPrint("client " + className + ": instrumenting " + cname);
                return instrument(classBeingRedefined, cname, classfileBuffer);
            } else if (filter.isCandidate(classfileBuffer)) {
                if (debug) Main.debugPrint("client " + className + ": instrumenting " + cname);
                return instrument(classBeingRedefined, cname, classfileBuffer); 
            } else {
                if (debug) Main.debugPrint("client " + className + ": skipping transform for " + cname);
                return null;
            }
        } finally {
            if (entered) {
                BTraceRuntime.leave();
            }
        }
    }

    protected synchronized void onExit(int exitCode) {
        if (shouldAddTransformer()) {
            if (debug) Main.debugPrint("onExit: removing transformer for " + className);
            inst.removeTransformer(this);
        }
        try {
            if (debug) Main.debugPrint("onExit: closing all");
            Thread.sleep(300);
            closeAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ioexp) {
            if (debug) Main.debugPrint(ioexp);
        }
    }

    protected Class loadClass(InstrumentCommand instr) throws IOException {
        String[] args = instr.getArguments();
        this.btraceCode = instr.getCode();
        try {
            verify(btraceCode);
        } catch (Throwable th) {
            if (debug) Main.debugPrint(th);
            errorExit(th);
            return null;
        }

        this.filter = new ClassFilter(onMethods);
        if (debug) Main.debugPrint("created class filter");
        
        ClassWriter writer = InstrumentUtils.newClassWriter(btraceCode);
        ClassReader reader = new ClassReader(btraceCode);
        ClassVisitor visitor = new Preprocessor(writer);
        if (BTraceRuntime.classNameExists(className)) {
            className += "$" + getCount();
            if (debug) Main.debugPrint("class renamed to " + className);
            onCommand(new RenameCommand(className));
            visitor = new ClassRenamer(className, visitor);
        }
        try {
            if (debug) Main.debugPrint("preprocessing BTrace class " + className);
            InstrumentUtils.accept(reader, visitor);
            if (debug) Main.debugPrint("preprocessed BTrace class " + className);
            btraceCode = writer.toByteArray();
        } catch (Throwable th) {
            if (debug) Main.debugPrint(th);
            errorExit(th);
            return null;
        }
        Main.dumpClass(className, className, btraceCode);
        if (debug) Main.debugPrint("creating BTraceRuntime instance for " + className);
        this.runtime = new BTraceRuntime(className, args, this, inst);
        if (debug) Main.debugPrint("created BTraceRuntime instance for " + className);
        if (debug) Main.debugPrint("removing @OnMethod, @OnProbe methods");
        byte[] codeBuf = removeMethods(btraceCode);
        if (debug) Main.debugPrint("removed @OnMethod, @OnProbe methods");
        if (debug) Main.debugPrint("sending Okay command");
        onCommand(new OkayCommand());
        try {
            BTraceRuntime.leave();
            if (debug) Main.debugPrint("about to defineClass " + className);
            if (shouldAddTransformer()) {
                this.btraceClazz = runtime.defineClass(codeBuf);
            } else {
                this.btraceClazz = runtime.defineClass(codeBuf, false);
            }
            if (debug) Main.debugPrint("defineClass succeeded for " + className);
        } catch (Throwable th) {
            if (debug) Main.debugPrint(th);
            errorExit(th);
            return null;
        } finally {
            BTraceRuntime.enter();
        }
        return this.btraceClazz;
    }

    protected abstract void closeAll() throws IOException;

    protected void errorExit(Throwable th) throws IOException {
        if (debug) Main.debugPrint("sending error command");
        onCommand(new ErrorCommand(th));
        if (debug) Main.debugPrint("sending exit command");
        onCommand(new ExitCommand(1));
        closeAll();
    }

    // package privates below this point
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
        return name.equals("java/lang/Object") ||
               name.startsWith("java/lang/ThreadLocal") ||
               name.startsWith("sun/reflect");
    }

    private byte[] instrument(Class clazz, String cname, byte[] target) {
        byte[] instrumentedCode;
        try {
            ClassWriter writer = InstrumentUtils.newClassWriter(target);
            ClassReader reader = new ClassReader(target);
            EmptyMethodsEvaluator eme = new EmptyMethodsEvaluator();
            reader.accept(eme, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);
            InstrumentUtils.accept(reader,
                new Instrumentor(clazz, className,  btraceCode, onMethods, eme.getEmptyMethods(), writer));
            instrumentedCode = writer.toByteArray();
        } catch (Throwable th) {
            Main.debugPrint(th);
            return null;
        }
        Main.dumpClass(className, cname, instrumentedCode);
        return instrumentedCode;
    }

    private void verify(byte[] buf) {
        ClassReader reader = new ClassReader(buf);
        Verifier verifier = new Verifier(new NullVisitor(), Main.isUnsafe());
        if (debug) Main.debugPrint("verifying BTrace class");
        InstrumentUtils.accept(reader, verifier);
        className = verifier.getClassName().replace('/', '.');
        if (debug) Main.debugPrint("verified '" + className + "' successfully");
        onMethods = verifier.getOnMethods();
        onProbes = verifier.getOnProbes();
        if (onProbes != null && !onProbes.isEmpty()) {
            // map @OnProbe's to @OnMethod's and store
            onMethods.addAll(Main.mapOnProbes(onProbes));
        }
    }

    private static byte[] removeMethods(byte[] buf) {
        ClassWriter writer = InstrumentUtils.newClassWriter(buf);
        ClassReader reader = new ClassReader(buf);
        InstrumentUtils.accept(reader, new MethodRemover(writer));
        return writer.toByteArray();
    }

    private static long count = 0L;
    private static long getCount() {
        return count++;
    }
}
