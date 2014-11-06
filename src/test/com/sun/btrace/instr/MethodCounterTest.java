/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.instr;

import com.sun.btrace.instr.MethodTracker;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbachorik
 */
public class MethodCounterTest {

    public MethodCounterTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of hit method, of class MethodCounter.
     */
    @Test
    public void testHit() {
        System.out.println("hit");
        final int iterations = 3000000;
        final int mean = 20;

        int methodId = 0;

        MethodTracker.registerCounter(methodId, mean);

        int hits = 0;

        for(int i=0;i<iterations;i++) {
            hits += MethodTracker.hit(methodId) ? 1 : 0;
        }
        long b = System.nanoTime();

        System.err.println("hits = " + hits);

        assertTrue(Math.abs(mean - (iterations / hits)) < (mean / 10));
    }

}
