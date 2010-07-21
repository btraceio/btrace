/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.profiling;

import com.sun.btrace.Profiler.Record;
import com.sun.btrace.Profiler.Snapshot;
import com.sun.btrace.profiling.MethodInvocationProfiler;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jb198685
 */
public class MethodInvocationProfilerTest {

    public MethodInvocationProfilerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of addSample method, of class MethodInvocationProfiler.
     */
    @Test
    public void testAddFirstSample() {
        System.out.println("testAddFirstSample()");
        MethodInvocationProfiler sc = new MethodInvocationProfiler(1);
        sc.recordEntry("paintComponent");
        sc.recordExit("paintComponent", 1000);

        dump(sc.snapshot());
    }

    @Test
    public void testAddTwoLevelsSample() {
        System.out.println("testAddTwoLevelsSample()");
        MethodInvocationProfiler sc = new MethodInvocationProfiler(3);
        sc.recordEntry("paintComponent");
        sc.recordEntry("paintChildren");
        sc.recordEntry("paintComponent");
        sc.recordExit("paintComponent", 300);
        sc.recordEntry("paintComponent");
        sc.recordExit("paintComponent", 300);
        sc.recordExit("paintChildren", 650);
        sc.recordExit("paintComponent", 1000);

        dump(sc.snapshot());
    }

    @Test
    public void testManyInvocations() {
        System.err.println("testManyInvocations()");
        MethodInvocationProfiler sc = new MethodInvocationProfiler(5);
        for(int i=0;i<1000;i++) {
            sc.recordEntry("call");
            sc.recordEntry("call3");
            sc.recordEntry("call2");
            sc.recordEntry("call1");
            sc.recordExit("call1", 150);
            sc.recordExit("call2", 160);
            sc.recordExit("call3", 170);
            sc.recordExit("call", 180);
        }
        dump(sc.snapshot());
    }

    @Test
    public void testMethodsOnStack() {
        System.err.println("testMethodsOnStack()");
        MethodInvocationProfiler sc = new MethodInvocationProfiler(2);
        sc.recordEntry("call");
        sc.recordEntry("call3");
        sc.recordEntry("call2");
        sc.recordEntry("call1");
        sc.recordExit("call1", 150);
        sc.recordExit("call2", 160);

        dump(sc.snapshot(true));
        sc.recordExit("call3", 170);
        sc.recordEntry("call1");
        sc.recordExit("call1", 150);
        sc.recordExit("call", 420);
        dump(sc.snapshot(true));
    }


    private void dump(Snapshot snp) {
        System.err.println("=== Totals");
        for(Record r : snp.total) {
            System.err.println(r.blockName + ":");
            System.err.println("invocations = " + r.invocations);
            System.err.println("selfTime = " + r.selfTime);
            System.err.println("wallTime = " + r.wallTime);
        }
    }
}