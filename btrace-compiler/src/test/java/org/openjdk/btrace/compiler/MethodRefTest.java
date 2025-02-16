package org.openjdk.btrace.compiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.VerifierException;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class MethodRefTest {
    @Test
    void testMethodRefUsage() throws Exception {
        URL input = MethodRefTest.class.getResource("/MethodRefProbe.java");
        File inputFile = new File(input.toURI());
        Map<String, byte[]> data =
                new Compiler(true)
                        .compile(
                                inputFile,
                                new PrintWriter(System.err),
                                null,
                                System.getProperty("java.class.path"));
        BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);
        assertNotNull(data);
        for (byte[] bytes : data.values()) {
            BTraceProbe probe = factory.createProbe(bytes);
            verifyCode(probe.getFullBytecode());
            verifyCode(probe.getDataHolderBytecode());
        }
    }

    @Test
    void testInvalidMethodRef() throws Exception {
        URL input = MethodRefTest.class.getResource("/MethodRefInvalidProbe.java");
        File inputFile = new File(input.toURI());
        assertThrows(VerifierException.class, () -> {
            Map<String, byte[]> data =
                    new Compiler(true)
                            .compile(
                                    inputFile,
                                    new PrintWriter(System.err),
                                    null,
                                    System.getProperty("java.class.path"));
        });
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

    public static String asmify(byte[] bytecode) {
        StringWriter sw = new StringWriter();
        TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(bytecode).accept(acv, 0);
        return sw.toString();
    }
}
