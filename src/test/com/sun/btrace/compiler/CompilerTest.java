package com.sun.btrace.compiler;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class CompilerTest {
    private Compiler instance;

    public CompilerTest() {
    }

    @Before
    public void setUp() {
        instance = new Compiler();
    }

    @Test
    @SuppressWarnings("DefaultCharset")
    public void testCompile() throws Exception {
        System.out.println("compile");
        URL script = CompilerTest.class.getResource("/traces/OnMethodTest.java");
        InputStream is = script.openStream();

        File f = new File(script.toURI());
        File base = f.getParentFile().getParentFile().getParentFile();

        String classpath = base.getAbsolutePath() + "/main" + ":" + base.getAbsolutePath() + "/test";

        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        Map<String, byte[]> data = instance.compile("traces/OnMethodTest.java", sb.toString(), new PrintWriter(System.err), ".", classpath);

        for (Map.Entry<String, byte[]> e : data.entrySet()) {
            System.err.println("== " + e.getKey());
            Postprocessor1 pp = new Postprocessor1();
            long t1 = System.nanoTime();
            for (int i = 0; i < 1; i++) {
                ClassReader cr = new ClassReader(e.getValue());
//                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            System.out.println("*** " + ((System.nanoTime() - t1) / 1) + "ns");
//            pp.process(cn);
//            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
//            cn.accept(cw);
//            dump(e.getKey(), cw.toByteArray());
        }

        System.out.println(sb.toString());
//
//
//        String fileName = "";
//        String source = "";
//        Writer err = null;
//        String sourcePath = "";
//        String classPath = "";
//        Compiler instance = new Compiler();
//        Map expResult = null;
//        Map result = instance.compile(fileName, source, err, sourcePath, classPath);
//        assertEquals(expResult, result);
//        fail("The test case is a prototype.");
    }

    private void dump(String name, byte[] code) {
        OutputStream os = null;
        try {
            name = name.replace(".", "/") + ".class";
            File f = new File("/tmp/" + name);
            if (!f.exists()) {
                f.getParentFile().createNewFile();
            }
            os = new FileOutputStream(f);
            os.write(code);
        } catch (IOException e) {

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {}
            }
        }
    }
}
