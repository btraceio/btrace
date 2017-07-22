package com.sun.btrace.compiler;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Assert;
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

        System.out.println(sb.toString());
        Map<String, byte[]> data = instance.compile("traces/OnMethodTest.java", sb.toString(), new PrintWriter(System.err), ".", classpath);

        Assert.assertNotNull(data);
    }
}
