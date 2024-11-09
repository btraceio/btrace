package org.openjdk.btrace.instr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ClassInfoTest {
    @BeforeAll
    static void setupAll() throws Throwable {
        Method m = BTraceRuntimeAccess.class.getDeclaredMethod("registerRuntimeAccessor");
        m.setAccessible(true);
        m.invoke(null);
    }

    @Test
    void isBootstrap() {
        assertTrue(ClassInfo.isBootstrap(String.class.getName()));
        assertFalse(ClassInfo.isBootstrap(ClassInfoTest.class.getName()));
    }
}