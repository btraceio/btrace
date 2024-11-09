package org.openjdk.btrace.compiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

public class TypeErasureTest {
    @Test
    void testTypeErasure() throws Exception {
        URL input = TypeErasureTest.class.getResource("/HistoProbe.java");
        File inputFile = new File(input.toURI());
        Map<String, byte[]> data =
                new Compiler(true)
                        .compile(
                                inputFile,
                                new PrintWriter(System.err),
                                null,
                                System.getProperty("java.class.path"));
        BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);
        for (byte[] bytes : data.values()) {
            BTraceProbe probe = factory.createProbe(bytes);
            verifyCode(probe.getFullBytecode());
            verifyCode(probe.getDataHolderBytecode());
        }
    }

    private void verifyCode(byte[] code) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        CheckClassAdapter.verify(new ClassReader(code), true, pw);
        if (sw.toString().contains("AnalyzerException")) {
            System.err.println(sw);
            fail();
        }
    }
}
