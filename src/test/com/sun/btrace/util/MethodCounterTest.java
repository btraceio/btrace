/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.util;

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
     * Test of registerCounter method, of class MethodCounter.
     */
    @Test
    public void testRegisterCounter() {
        System.out.println("registerCounter");
//        for(int i=0;i<100;i++) {
//            MethodCounter.registerCounter(i);
//        }
    }

    /**
     * Test of addSampler method, of class MethodCounter.
     */
    @Test
    public void testAddSampler() {
        System.out.println("addSampler");
//        MethodCounter.registerCounter(0);
//        int sampler = -1;
//        sampler = MethodCounter.addSampler(0, 10);
//        assertEquals(0, sampler);
//        sampler = MethodCounter.addSampler(0, 20);
//        assertEquals(1, sampler);
//        sampler = MethodCounter.addSampler(0, 30);
//        assertEquals(2, sampler);
//        sampler = MethodCounter.addSampler(0, 10);
//        assertEquals(0, sampler);
    }

    /**
     * Test of hit method, of class MethodCounter.
     */
    @Test
    public void testHit() {
        System.out.println("hit");
        final int iterations = 3000000;
        final int rate = 20;

        int methodId = 0;
        int sampler = 0;

        int hits = 0;

//        MethodCounter.registerCounter(methodId);
//        sampler = MethodCounter.addSampler(methodId, rate);
//
//        for(int i=0;i<iterations;i++) {
//            hits += MethodCounter.hit(methodId, sampler) ? 1 : 0;
//        }
//
//        hits = 0;
//
//        long a = System.nanoTime();
//        for(int i=0;i<iterations;i++) {
//            hits += MethodCounter.hit(methodId, sampler) ? 1 : 0;
//        }
//        long b = System.nanoTime();
//
//        System.err.println("hits = " + hits);
//        System.err.println("avg time = " + ((b - a) / iterations));

        assertEquals(rate, iterations / hits);
    }

}
