package org.openjdk.btrace.instr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassInfoTest {
    @BeforeAll
    static void setupAll() throws Throwable {
        Class<?> accessImpl = Class.forName("org.openjdk.btrace.runtime.BTraceRuntimeAccessImpl");
        Method m = accessImpl.getDeclaredMethod("install");
        m.setAccessible(true);
        m.invoke(null);
    }

    @Test
    void isBootstrap() {
        assertTrue(ClassInfo.isBootstrap(String.class.getName()));
        assertFalse(ClassInfo.isBootstrap(ClassInfoTest.class.getName()));
    }
}
