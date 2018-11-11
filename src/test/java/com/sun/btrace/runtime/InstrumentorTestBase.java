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
import com.sun.btrace.util.MethodID;
import java.io.*;
import org.junit.After;
import org.junit.Before;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.BeforeClass;

/**
 *
 * @author Jaroslav Bachorik
 */
public abstract class InstrumentorTestBase {
    private static final boolean DEBUG = false;

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
    protected byte[] traceCode;

    private ClassLoader cl;
    private final static SharedSettings settings = SharedSettings.GLOBAL;
    private final static BTraceProbeFactory factory = new BTraceProbeFactory(settings);

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
        settings.setTrusted(true);
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
            traceCode = null;
            resetClassLoader();

            Field lastFld = MethodID.class.getDeclaredField("lastMehodId");
            Field mapFld = MethodID.class.getDeclaredField("methodIds");

            lastFld.setAccessible(true);
            mapFld.setAccessible(true);

            AtomicInteger last = (AtomicInteger)lastFld.get(null);
            Map<String, Integer> map = (Map<String, Integer>)mapFld.get(null);

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
        traceCode = null;
    }

    protected void load(String traceName, String clzName) throws Exception {
        loadTraceCode(traceName);
        loadClass(clzName);
    }

    @SuppressWarnings("ClassNewInstance")
    protected void loadClass(String origName) throws Exception {
        if (transformedBC != null) {
            String clzName = new ClassReader(transformedBC).getClassName().replace('.', '/');
            Class<?> clz = unsafe.defineClass(clzName, transformedBC, 0, transformedBC.length, cl, null);
            try {
                clz.newInstance();
            } catch (NoSuchFieldError | NoClassDefFoundError e) {
                // expected; ignore
            }
        } else {
            System.err.println("Unable to instrument class " + origName);
            transformedBC = originalBC;
        }
    }

    @SuppressWarnings("ClassNewInstance")
    protected void loadTraceCode(String origName) throws Exception {
        if (traceCode == null) {
            String traceName = new ClassReader(traceCode).getClassName().replace('.', '/');
            Class<?> clz  = unsafe.defineClass(traceName, traceCode, 0, traceCode.length, cl, null);
            clz.newInstance();
        } else {
            System.err.println("Unable to process trace " + origName);
        }
    }

    protected void checkTransformation(String expected) throws IOException {
        checkTransformation(expected, true);
    }

    protected void checkTransformation(String expected, boolean verify) throws IOException {
        if (transformedBC == null) {
            if (expected != null && !expected.isEmpty()) {
                fail();
            } else {
                return;
            }
        }
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(transformedBC);

        if (verify) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            CheckClassAdapter.verify(cr, true, pw);
            if (sw.toString().contains("AnalyzerException")) {
                System.err.println(sw.toString());
                fail();
            }
        }

        String diff = diff();
        if (DEBUG) {
            System.err.println("+++ DEBUG +++");
            System.err.println("--- original ---");
            System.err.println(asmify(originalBC));
            System.err.println("--- original ---");
            System.err.println("--- transformed ---");
            System.err.println(asmify(transformedBC));
            System.err.println("--- transformed ---");
            System.err.println("--- diff ---");
            System.err.println(diff);
            System.err.println("--- diff ---");
            System.err.println("+++ DEBUG +++");
        }
        assertEquals(expected, diff.substring(0, diff.length() > expected.length() ? expected.length() : diff.length()));
    }

    protected void checkTrace(String expected) throws IOException {
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(traceCode);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        CheckClassAdapter.verify(cr, false, pw);
        if (sw.toString().contains("AnalyzerException")) {
            System.err.println(sw.toString());
            fail();
        }
    }

    protected void transform(String traceName) throws Exception {
        transform(traceName, false);
    }

    protected void transform(String traceName, boolean unsafe) throws Exception {
        settings.setTrusted(unsafe);
        BTraceClassReader cr = InstrumentUtils.newClassReader(cl, originalBC);
        BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
        BTraceProbe btrace = loadTrace(traceName, unsafe);

        cw.addInstrumentor(btrace);

        transformedBC = cw.instrument();

        if (transformedBC != null) {
            try (OutputStream os = new FileOutputStream("/tmp/dummy.class")) {
                os.write(transformedBC);
            }
        }

//        load(traceName, cr.getJavaClassName());

        System.err.println("==== " + traceName);
    }

    protected String asmify(byte[] bytecode) {
        if (bytecode == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(bytecode).accept(acv, 0);
        return sw.toString();
    }

    protected String asmify(ClassNode cn) {
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return asmify(cw.toByteArray());
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
        byte[] traceData = loadFile("traces/" + name + ".class");

        BTraceProbe bcn = factory.createProbe(traceData);
        this.traceCode = bcn.getFullBytecode();

        if (DEBUG) {
            System.err.println("=== Loaded Trace: " + bcn + "\n");
            System.err.println(asmify(this.traceCode));
            Files.write(FileSystems.getDefault().getPath("/tmp/jingle.class"), traceCode);
        }

        bcn.checkVerified();

        return bcn;
    }

    protected byte[] loadTargetClass(String name) throws IOException {
        originalBC = loadResource("resources/" + name + ".class");
        return originalBC;
    }

    private byte[] loadResource(String path) throws IOException {
        InputStream is = ClassLoader.getSystemResourceAsStream(path);
        try {
            return loadFile(is);
        } finally {
            if (is == null) {
                System.err.println("Unable to load resource: " + path);
            } else {
                is.close();
            }
        }
    }

    private byte[] loadFile(String path) throws IOException {
        File f = new File("./build/classes/" + path);
        try (InputStream is = new FileInputStream(f)) {
            byte[] data = loadFile(is);
            return data;
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
}
