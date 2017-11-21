package com.sun.btrace.runtime;

import com.sun.btrace.SharedSettings;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

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
