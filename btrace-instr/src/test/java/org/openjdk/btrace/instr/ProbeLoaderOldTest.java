package org.openjdk.btrace.instr;


import org.openjdk.btrace.core.SharedSettings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;

public class ProbeLoaderOldTest {
    private static BTraceProbeFactory BPF;
    private InputStream classStream;
    private byte[] defData;

    @BeforeClass
    public static void setupClass() throws Exception {
        BPF = new BTraceProbeFactory(SharedSettings.GLOBAL);
    }

    @Before
    public void setup() throws Exception {
        classStream = ProbeLoaderOldTest.class.getResourceAsStream("/resources/classdata/TraceScript.clazz");
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
