package com.sun.btrace.aggregation;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuantizeTest {

    public QuantizeTest() {
    }

    @Before
    public void setUp() {
    }

    @Test
    public void testLogBase2() {
        System.out.println("logBase2");

        long val = 1;

        for (int i = 0; i < 63; i++) {
            assertEquals(i, Quantize.logBase2(val));
            val <<= 1;
        }
    }
}
