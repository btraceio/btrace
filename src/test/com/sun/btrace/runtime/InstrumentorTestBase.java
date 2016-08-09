/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime;

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.SharedSettings;
import static org.junit.Assert.*;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.runtime.BTraceProbe;
import com.sun.btrace.runtime.BTraceProbeFactory;
import com.sun.btrace.runtime.InstrumentUtils;
import com.sun.btrace.util.MethodID;
import org.junit.After;
import org.junit.Before;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.BeforeClass;

/**
 *
 * @author Jaroslav Bachorik
 */
public abstract class InstrumentorTestBase {
    private static final boolean DEBUG = true;

    private static Unsafe unsafe;

    private static Field uccn = null;

    static {
        try {
            Field unsafeFld = AtomicInteger.class.getDeclaredField("unsafe");
            unsafeFld.setAccessible(true);
            unsafe = (Unsafe)unsafeFld.get(null);
        } catch (Exception e) {
        }
    }

    protected byte[] originalBC;
    protected byte[] transformedBC;
    protected byte[] originalTrace;
    protected byte[] transformedTrace;

    private ClassLoader cl;
    private final SharedSettings settings = SharedSettings.GLOBAL;
    private final BTraceProbeFactory factory = new BTraceProbeFactory(settings);

    protected final void enableUniqueClientClassNameCheck() throws Exception {
        uccn.set(null, true);
    }

    protected final void disableUniqueClientClassNameCheck() throws Exception {
        uccn.set(null, false);
    }

    @BeforeClass
    public static void classStartup() throws Exception {
        uccn = BTraceRuntime.class.getDeclaredField("uniqueClientClassNames");
        uccn.setAccessible(true);
        uccn.set(null, true);
    }

    @After
    public void tearDown() {
        cleanup();
    }

    @Before
    public void startup() {
        try {
            originalBC = null;
            transformedBC = null;
            originalTrace = null;
            transformedTrace = null;
            resetClassLoader();

            Field lastFld = MethodID.class.getDeclaredField("lastMehodId");
            Field mapFld = MethodID.class.getDeclaredField("methodIds");

            lastFld.setAccessible(true);
            mapFld.setAccessible(true);

            AtomicInteger last = (AtomicInteger)lastFld.get(null);
            Map map = (Map)mapFld.get(null);

            last.set(1);
            map.clear();
            disableUniqueClientClassNameCheck();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected final void resetClassLoader() {
        cl = new ClassLoader(InstrumentorTestBase.class.getClassLoader()) {};
    }

    protected void cleanup() {
        originalBC = null;
        transformedBC = null;
        originalTrace = null;
        transformedTrace = null;
    }

    protected void load() {
        String clzName = new ClassReader(transformedBC).getClassName().replace('.', '/');
        String traceName = new ClassReader(transformedTrace).getClassName().replace('.', '/');
        unsafe.defineClass(clzName, transformedBC, 0, transformedBC.length, cl, null);
        unsafe.defineClass(traceName, transformedTrace, 0, transformedTrace.length, cl, null);
    }

    protected void checkTransformation(String expected) throws IOException {
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(transformedBC);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        CheckClassAdapter.verify(cr, false, pw);
        if (sw.toString().contains("AnalyzerException")) {
            System.err.println(sw.toString());
            fail();
        }

        String diff = diff();
        if (DEBUG) {
            System.err.println(diff);
        }
        assertEquals(expected, diff.substring(0, diff.length() > expected.length() ? expected.length() : diff.length()));
    }

    protected void checkTrace(String expected) throws IOException {
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(transformedTrace);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        CheckClassAdapter.verify(cr, false, pw);
        if (sw.toString().contains("AnalyzerException")) {
            System.err.println(sw.toString());
            fail();
        }

        System.err.println("=== HERE");
        System.err.println(asmify(transformedTrace));
        System.err.println("========");

        String diff = diffTrace();
        if (DEBUG) {
            System.err.println(diff);
        }
        assertEquals(expected, diff.substring(0, diff.length() > expected.length() ? expected.length() : diff.length()));
    }

    protected void transform(String traceName) throws IOException {
        transform(traceName, false);
    }

    protected void transform(String traceName, boolean unsafe) throws IOException {
        settings.setUnsafe(unsafe);
        BTraceClassReader cr = InstrumentUtils.newClassReader(cl, originalBC);
        BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
        BTraceProbe btrace = loadTrace(traceName, unsafe);

        cw.addInstrumentor(btrace);

        transformedBC = cw.instrument();

        if (transformedBC != null) {
            load();
        } else {
            System.err.println("Unable to instrument class " + cr.getJavaClassName());
            transformedBC = originalBC;
        }
        System.err.println("==== " + traceName);
    }

    protected String asmify(byte[] bytecode) {
        StringWriter sw = new StringWriter();
        TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(bytecode).accept(acv, ClassReader.SKIP_FRAMES);
        return sw.toString();
    }

    protected String asmify(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return asmify(cw.toByteArray());
    }

    private String diffTrace() throws IOException {
        String origCode = asmify(originalTrace);
        String transCode = asmify(transformedTrace);
        return diff(transCode, origCode);
    }

    private String diff() throws IOException {
        String origCode = asmify(originalBC);
        String transCode = asmify(transformedBC);
        return diff(transCode, origCode);
    }

    private String diff(String modified, String orig) throws IOException {
        StringBuilder sb = new StringBuilder();

        String[] modArr = modified.split("\\n");
        String[] orgArr = orig.split("\\n");

        // number of lines of each file
        int modLen = modArr.length;
        int origLen = orgArr.length;

        // opt[i][j] = length of LCS of x[i..M] and y[j..N]
        int[][] opt = new int[modLen + 1][origLen + 1];

        // compute length of LCS and all subproblems via dynamic programming
        for (int i = modLen - 1; i >= 0; i--) {
            for (int j = origLen - 1; j >= 0; j--) {
                if (modArr[i].equals(orgArr[j])) {
                    opt[i][j] = opt[i + 1][j + 1] + 1;
                } else {
                    opt[i][j] = Math.max(opt[i + 1][j], opt[i][j + 1]);
                }
            }
        }

        // recover LCS itself and print out non-matching lines to standard output
        int modIndex = 0;
        int origIndex = 0;
        while (modIndex < modLen && origIndex < origLen) {
            if (modArr[modIndex].equals(orgArr[origIndex])) {
                modIndex++;
                origIndex++;
            } else if (opt[modIndex + 1][origIndex] >= opt[modIndex][origIndex + 1]) {
                sb.append(modArr[modIndex++].trim()).append('\n');
            } else {
                origIndex++;
            }
        }

        // dump out one remainder of one string if the other is exhausted
        while (modIndex < modLen || origIndex < origLen) {
            if (modIndex == modLen) {
                origIndex++;
            } else if (origIndex == origLen) {
                sb.append(orgArr[modIndex++].trim()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    protected BTraceProbe loadTrace(String name) throws IOException {
        return loadTrace(name, false);
    }

    protected BTraceProbe loadTrace(String name, boolean unsafe) throws IOException {
        originalTrace = loadFile("traces/" + name + ".class");
        if (DEBUG) {
            System.err.println("=== Loaded Trace: " + name + "\n");
            System.err.println(asmify(originalTrace));
        }
        BTraceProbe bcn = factory.createProbe(originalTrace);
//        Trace t =  new Trace(bcn.getBytecode(), bcn.getOnMethods(), verifier.getReachableCalls(), bcn.name);
        transformedTrace = extractBytecode(bcn);
        if (DEBUG) {
//            writer = InstrumentUtils.newClassWriter();
//            InstrumentUtils.accept(new ClassReader(originalTrace), new Preprocessor1(writer));
            System.err.println("=== Preprocessed Trace ===");
//            System.err.println(asmify(writer.toByteArray()));
            System.err.println(asmify(transformedTrace));
        }
        return bcn;
    }

    protected byte[] loadTargetClass(String name) throws IOException {
        originalBC = loadFile("resources/" + name + ".class");
        return originalBC;
    }

    private byte[] loadFile(String path) throws IOException {
        InputStream is = ClassLoader.getSystemResourceAsStream(path);
        try {
            return loadFile(is);
        } finally {
            if (is == null) {
                System.err.println("Unable to load file: " + path);
            } else {
                is.close();
            }
        }
    }

    private byte[] loadFile(InputStream is) throws IOException {
        byte[] result = new byte[0];

        byte[] buffer = new byte[1024];

        int read = -1;
        while ((read = is.read(buffer)) > 0) {
            byte[] newresult = new byte[result.length + read];
            System.arraycopy(result, 0, newresult, 0, result.length);
            System.arraycopy(buffer, 0, newresult, result.length, read);
            result = newresult;
        }
        return result;
    }

    private byte[] extractBytecode(BTraceProbe bp) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        bp.accept(cw);
        return cw.toByteArray();
    }
}
