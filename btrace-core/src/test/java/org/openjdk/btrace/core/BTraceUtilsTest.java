package org.openjdk.btrace.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BTraceUtilsTest {

    @Test
    public void getIntTest() {
        D a = new D();
        RuntimeException exception = assertThrows(RuntimeException.class, () -> BTraceUtils.Reflective.getInt("notExist", a));
        assertTrue(exception.getMessage().contains("notExist"));
    }

    static class A {
        int a;
    }

    static class B extends A {

    }

    static class C extends A {
    }

    static class D extends C {
    }
}