/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.runtime;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.objectweb.asm.util.TraceClassVisitor;

/**
 *
 * @author Jaroslav Bachorik
 */
public class InstrumentorTest {
    private static class Trace {
        final byte[] content;
        final List<OnMethod> onMethods;
        final String className;

        public Trace(byte[] content, List<OnMethod> onMethods, String className) {
            this.content = content;
            this.onMethods = onMethods;
            this.className = className;
        }
    }
    public InstrumentorTest() {
    }

    private byte[] originalBC;
    private byte[] transformedBC;

    @Before
    public void setUp() {

    }

    @After
    public void tearDown() {
        originalBC = null;
        transformedBC = null;
    }

    @Test
    public void methodEntryArgsNoSelf() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/ArgsNoSelf");

        checkTransformation("ALOAD 1\nILOAD 2\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$ArgsNoSelf$argsNoSelf (Ljava/lang/String;I[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryNoArgs() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/NoArgs");

        checkTransformation("ALOAD 0\nINVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$NoArgs$argsEmpty (Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryArgs() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/Args");
        checkTransformation("ALOAD 0\nALOAD 1\nILOAD 2\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$Args$args (Ljava/lang/Object;Ljava/lang/String;I[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryAnytypeArgs() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/AnytypeArgs");
        checkTransformation("ALOAD 0\nICONST_4\nANEWARRAY java/lang/Object\nDUP\n" +
                     "ICONST_0\nALOAD 1\nAASTORE\nDUP\n" +
                     "ICONST_1\nILOAD 2\nINVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\nAASTORE\nDUP\n" +
                     "ICONST_2\nALOAD 3\nAASTORE\nDUP\n" +
                     "ICONST_3\nALOAD 4\nAASTORE\nINVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryAnytypeArgsNoSelf() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/AnytypeArgsNoSelf");
        checkTransformation("ICONST_4\nANEWARRAY java/lang/Object\nDUP\nICONST_0\nALOAD 1\nAASTORE\n" +
                     "DUP\nICONST_1\nILOAD 2\nINVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;\nAASTORE\n" +
                     "DUP\nICONST_2\nALOAD 3\nAASTORE\nDUP\nICONST_3\nALOAD 4\nAASTORE\n" +
                     "INVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryStaticArgs() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/StaticArgs");

        checkTransformation("ALOAD 0\nILOAD 1\nALOAD 2\nALOAD 3\nINVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$StaticArgs$args (Ljava/lang/String;I[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryStaticArgsSelf() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/StaticArgsSelf");

        assertTrue(diff().length() == 0);
    }

    @Test
    public void methodEntryStaticNoArgs() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/StaticNoArgs");

        checkTransformation("INVOKESTATIC resources/MethodEntryTest.$btrace$traces$methodentry$StaticNoArgs$argsEmpty ()V");
    }

    @Test
    public void methodEntryStaticNoArgsSelf() throws Exception {
        originalBC = loadTargetClass("MethodEntryTest");
        transform("methodentry/StaticNoArgsSelf");

        assertTrue(diff().length() == 0);
    }
    
    private void checkTransformation(String expected) throws IOException {
        String diff = diff();
        System.err.println(diff);
        assertEquals(expected, diff.substring(0, expected.length()));
    }

    private void transform(String traceName) throws IOException {
        Trace btrace = loadTrace(traceName);
        ClassReader reader = new ClassReader(originalBC);
        ClassWriter writer = InstrumentUtils.newClassWriter();

        InstrumentUtils.accept(reader, new Instrumentor(null,
                    btrace.className, btrace.content,
                    btrace.onMethods, writer));

        transformedBC = writer.toByteArray();
        System.err.println("==== " + traceName);
    }

    private String diff() throws IOException {
        StringWriter sw = new StringWriter();
        TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(originalBC).accept(acv, ClassReader.SKIP_FRAMES);
        String origCode = sw.toString();

        sw = new StringWriter();
        acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(transformedBC).accept(acv, ClassReader.SKIP_FRAMES);
        String transCode = sw.toString();

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
        return sb.toString();
    }

    private Trace loadTrace(String name) throws IOException {
        byte[] btrace = loadFile("traces/" + name + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();
        Verifier verifier = new Verifier(new Preprocessor(writer));
        InstrumentUtils.accept(new ClassReader(btrace), verifier);
        return new Trace(writer.toByteArray(), verifier.getOnMethods(), verifier.getClassName());
    }

    private byte[] loadTargetClass(String name) throws IOException {
        return loadFile("resources/" + name + ".class");
    }

    private byte[] loadFile(String path) throws IOException {
        InputStream is = ClassLoader.getSystemResourceAsStream(path);
        try {
            return loadFile(is);
        } finally {
            is.close();
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