package com.sun.btrace.runtime;

import org.junit.Test;

public class BTRACE333Test extends InstrumentorTestBase {
    @Test
    public void stackmapErrorTest() throws Exception {
        originalBC = loadTargetClass("classdata/BackpackExtensionTest");
        transform("issues/BTRACE_333");
        checkTransformation("");

    }
}
