package org.openjdk.btrace.instr;

import java.io.InputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openjdk.btrace.core.SharedSettings;

public class ProbeLoaderNewTest {
  private static BTraceProbeFactory BPF;
  private InputStream classStream;
  private byte[] defData;

  @BeforeClass
  public static void setupClass() throws Exception {
    BPF = new BTraceProbeFactory(SharedSettings.GLOBAL);
  }

  @Before
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
    Assert.assertNotNull(bp);
    Assert.assertNotNull(bp.getClassName());
  }
}
