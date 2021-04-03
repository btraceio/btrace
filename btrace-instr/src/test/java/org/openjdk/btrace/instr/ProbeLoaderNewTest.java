package org.openjdk.btrace.instr;

import java.io.InputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.SharedSettings;

public class ProbeLoaderNewTest {
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
        ProbeLoaderNewTest.class.getResourceAsStream("/resources/classdata/OnMethodTest.btrc");
  }

  @Test
  public void testPersistedProbeLoad() throws Exception {
    BTraceProbe bp;
    long t1 = System.nanoTime();
    try {
      bp = BPF.createProbe(classStream);
    } finally {
      System.err.println("# Creating probe took: " + (System.nanoTime() - t1) + "ns");
    }
    Assertions.assertNotNull(bp);
    Assertions.assertNotNull(bp.getClassName());
  }
}
