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

package support;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.runtime.InstrumentUtils;
import com.sun.btrace.runtime.Instrumentor;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.runtime.Preprocessor;
import com.sun.btrace.runtime.Verifier;
import com.sun.btrace.util.MethodID;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.objectweb.asm.util.TraceClassVisitor;
import static org.junit.Assert.*;
import org.junit.Before;
import org.objectweb.asm.util.CheckClassAdapter;
import sun.misc.Unsafe;

/**
 *
 * @author Jaroslav Bachorik
 */
abstract public class InstrumentorTestBase {
    protected static class Trace {
        final byte[] content;
        final List<OnMethod> onMethods;
        public final String className;

        public Trace(byte[] content, List<OnMethod> onMethods, String className) {
            this.content = content;
            this.onMethods = onMethods;
            this.className = className;
        }
    }

    private static final boolean DEBUG = false;

    private static Unsafe unsafe;

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

    @After
    public void tearDown() {
        cleanup();
    }

    @Before
    public void startup() {
        try {
            cl = new ClassLoader(InstrumentorTestBase.class.getClassLoader()) {};

            Field lastFld = MethodID.class.getDeclaredField("lastMehodId");
            Field mapFld = MethodID.class.getDeclaredField("methodIds");

            lastFld.setAccessible(true);
            mapFld.setAccessible(true);

            AtomicInteger last = (AtomicInteger)lastFld.get(null);
            Map map = (Map)mapFld.get(null);

            last.set(1);
            map.clear();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void cleanup() {
        originalBC = null;
        transformedBC = null;
        originalTrace = null;
        transformedTrace = null;
    }

    protected void load() {
        String clzName = new ClassReader(transformedBC).getClassName().replace('.', '/');
        unsafe.defineClass(clzName, transformedBC, 0, transformedBC.length, cl, null);
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
        Trace btrace = loadTrace(traceName, unsafe);
        ClassReader reader = new ClassReader(originalBC);
        ClassWriter writer = InstrumentUtils.newClassWriter();

        try {
            InstrumentUtils.accept(reader, new Instrumentor(null,
                        btrace.className, btrace.content,
                        btrace.onMethods, writer));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        transformedBC = writer.toByteArray();
        load();
        System.err.println("==== " + traceName);
    }

    protected String asmify(byte[] bytecode) {
        StringWriter sw = new StringWriter();
        TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(bytecode).accept(acv, ClassReader.SKIP_FRAMES);
        return sw.toString();
    }

    private String diff() throws IOException {
        String origCode = asmify(originalBC);
        String transCode = asmify(transformedBC);
        return diff(transCode, origCode);
    }

    private String diffTrace() throws IOException {
        String origCode = asmify(originalTrace);
        String transCode = asmify(transformedTrace);
        return diff(transCode, origCode);
    }

    private String diff(String modified, String orig) throws IOException {
        StringBuilder sb = new StringBuilder();

        String[] modArr = modified.split("\\n");
        String[] orgArr = orig.split("\\n");

        // number of lines of each file
        int M = modArr.length;
        int N = orgArr.length;

        // opt[i][j] = length of LCS of x[i..M] and y[j..N]
        int[][] opt = new int[M+1][N+1];

        // compute length of LCS and all subproblems via dynamic programming
        for (int i = M-1; i >= 0; i--) {
            for (int j = N-1; j >= 0; j--) {
                if (modArr[i].equals(orgArr[j]))
                    opt[i][j] = opt[i+1][j+1] + 1;
                else
                    opt[i][j] = Math.max(opt[i+1][j], opt[i][j+1]);
            }
        }

        // recover LCS itself and print out non-matching lines to standard output
        int i = 0, j = 0;
        while(i < M && j < N) {
            if (modArr[i].equals(orgArr[j])) {
                i++;
                j++;
            }
            else if (opt[i+1][j] >= opt[i][j+1]) sb.append(modArr[i++].trim()).append('\n');
            else j++; //                                sb.append("[").append(j).append("] ").append("> " + orgArr[j++]).append('\n');
        }

        // dump out one remainder of one string if the other is exhausted
        while(i < M || j < N) {
            if      (i == M) j++; //sb.append("[").append(j).append("] ").append("> " + modArr[j++]).append('\n');
            else if (j == N) sb.append(orgArr[i++].trim()).append('\n');
        }
        return sb.toString().trim();
    }

    protected Trace loadTrace(String name) throws IOException {
        return loadTrace(name, false);
    }

    protected Trace loadTrace(String name, boolean unsafe) throws IOException {
        originalTrace = loadFile("traces/" + name + ".class");
        if (DEBUG) {
            System.err.println("=== Loaded Trace: " + name + "\n");
            System.err.println(asmify(originalTrace));
        }
        ClassWriter writer = InstrumentUtils.newClassWriter();
        Verifier verifier = new Verifier(new Preprocessor(writer), unsafe);
        InstrumentUtils.accept(new ClassReader(originalTrace), verifier);
        Trace t =  new Trace(writer.toByteArray(), verifier.getOnMethods(), verifier.getClassName());
        transformedTrace = t.content;
        return t;
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
        while((read = is.read(buffer)) > 0) {
            byte[] newresult = new byte[result.length + read];
            System.arraycopy(result, 0, newresult, 0, result.length);
            System.arraycopy(buffer, 0, newresult, result.length, read);
            result = newresult;
        }
        return result;
    }
}
