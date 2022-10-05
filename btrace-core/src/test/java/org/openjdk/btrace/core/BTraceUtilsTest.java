package org.openjdk.btrace.core;

import org.junit.jupiter.api.Test;


class BTraceUtilsTest {

    static class SuperClass {
        int a;
    }

    static class Subclass extends SuperClass {

    }
    @Test
    public void getIntTest() {
        Subclass a = new Subclass();
        assert BTraceUtils.Reflective.getInt("a", a) == 0;
    }
}