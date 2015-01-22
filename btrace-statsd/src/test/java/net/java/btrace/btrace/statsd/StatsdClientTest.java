/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.java.btrace.btrace.statsd;

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
public class StatsdClientTest {

    public StatsdClientTest() {
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
     * Test of gauge method, of class StatsdClient.
     */
    @org.junit.Test
    public void testGauge() {
        System.out.println("gauge");
        String metric = "btrace.test";
        int value = 0;
        StatsdClient instance = new StatsdClient();
        instance.gauge(metric, value);
        // TODO review the generated test code and remove the default call to fail.
    }

}
