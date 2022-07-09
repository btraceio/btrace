package org.openjdk.btrace.instr;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.runtime.BTraceRuntimes;

public class ProbeLoaderUpgradeTest {
  private static BTraceProbeFactory BPF;
  private InputStream classStream;
  private byte[] defData;

  @BeforeAll
  public static void setupClass() throws Exception {
    BPF = new BTraceProbeFactory(SharedSettings.GLOBAL);
  }

  @BeforeEach
  public void setup() throws Exception {
    classStream =
        ProbeLoaderUpgradeTest.class.getResourceAsStream("/resources/classdata/PackVersion1.btrc");
  }

  @Test
  public void testPersistedProbeLoad() throws Exception {
    System.err.println("=== " + ManagementFactory.getRuntimeMXBean().getInputArguments());
    BTraceRuntime.Impl rt =
        BTraceRuntimes.getRuntime(
            "PackVersion1",
            new ArgsMap(),
            new CommandListener() {
              @Override
              public void onCommand(Command cmd) throws IOException {}
            },
            null);

    BTraceProbe bp;
    long t1 = System.nanoTime();
    try {
      bp = BPF.createProbe(classStream);
    } finally {
      System.err.println("# Creating probe took: " + (System.nanoTime() - t1) + "ns");
    }
    Assertions.assertNotNull(bp);
    Assertions.assertNotNull(bp.getClassName());

    String fullAsm = InstrumentorTestBase.asmify(bp.getFullBytecode());
    String bootstrapAsm = InstrumentorTestBase.asmify(bp.getDataHolderBytecode());

    Assertions.assertFalse(fullAsm.contains("com/sun/btrace"));
    Assertions.assertFalse(bootstrapAsm.contains("com/sun/btrace"));

    InstrumentorTestBase.loadCode("PackVersion1", bp.getFullBytecode());
  }
}
