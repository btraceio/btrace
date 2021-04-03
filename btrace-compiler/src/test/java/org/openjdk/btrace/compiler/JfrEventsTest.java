package org.openjdk.btrace.compiler;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;

public class JfrEventsTest {
  @Test
  public void testCompile() throws Exception {
    URL input = JfrEventsTest.class.getResource("/JfrEventsProbe.java");
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
      byte[] code = probe.getDataHolderBytecode();
      CheckClassAdapter.verify(new ClassReader(code), true, new PrintWriter(System.err));
    }
    System.out.println(data);
  }
}
