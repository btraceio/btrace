/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.btrace.profiling;

import com.sun.btrace.Profiler;
import com.sun.btrace.Profiler.Record;
import com.sun.btrace.Profiler.Snapshot;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private Profiler p;

    @Before
    public void setUp() {
        p = new MethodInvocationProfiler(5);
    }

    @After
    public void tearDown() {
        p = null;
    }

    @Test
    public void testEmptySnapshot() {
        System.out.println("testEmptySnapshot()");
        Snapshot s = p.snapshot();
        assertNotNull(s);
        assertTrue(s.timeStamp > -1);
        assertNotNull(s.total);
        assertTrue(s.total.length == 0);
    }

    @Test
    public void testOneRecord() {
        System.out.println("testOneRecord()");
        Record[] expected = new Record[] {new Record("r")};
        expected[0].invocations = 1;
        expected[0].selfTime = 1000;
        expected[0].wallTime = 1000;
        p.recordEntry("r");
        p.recordExit("r", 1000);

        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testOneLevelRecordsNoCompacting() {
        System.out.println("testOneLevelRecordsNoCompacting()");
        Record[] expected = new Record[]{new Record("r1"), new Record("r2")};

        expected[0].invocations = 20;
        expected[0].selfTime = 200;
        expected[0].wallTime = 200;
        expected[1].invocations = 20;
        expected[1].selfTime = 200;
        expected[1].wallTime = 200;

        for(int i=0;i<20;i++) {
            p.recordEntry("r1");
            p.recordExit("r1", 10);
            p.recordEntry("r2");
            p.recordExit("r2", 10);
        }
        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testOneLevelRecordsNoCompactingMultiThread() throws Exception {
        System.out.println("testOneLevelRecordsNoCompactingMultiThread()");
        Record r1 = new Record("r1");
        Record r2 = new Record("r2");

        r1.invocations = 20;
        r1.selfTime = 200;
        r1.wallTime = 200;
        r2.invocations = 20;
        r2.selfTime = 200;
        r2.wallTime = 200;

        Set<Record> expected = new HashSet<Record>();
        expected.add(r1);
        expected.add(r2);

        final Phaser ph = new Phaser(2);
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                ph.arriveAndAwaitAdvance();
                for(int i=0;i<20;i++) {
                    p.recordEntry("r1");
                    p.recordExit("r1", 10);
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            public void run() {
                ph.arriveAndAwaitAdvance();
                for(int i=0;i<20;i++) {
                    p.recordEntry("r2");
                    p.recordExit("r2", 10);
                }
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        Snapshot s = p.snapshot();
        for(Record r : s.total) {
            if (!expected.remove(r)) {
                fail("Unexpected record: " + r);
            }
        }
        if (!expected.isEmpty()) {
            System.err.println("The following records were not matched:");
            for(Record r : expected) {
                System.err.println(r);
            }
            fail();
        }
    }

    @Test
    public void testOneLevelRecordsCompacting() {
        System.out.println("testOneLevelRecordsCompacting()");
        Record[] expected = new Record[]{new Record("r1"), new Record("r2")};

        expected[0].invocations = 200;
        expected[0].selfTime = 2000;
        expected[0].wallTime = 2000;
        expected[1].invocations = 200;
        expected[1].selfTime = 2000;
        expected[1].wallTime = 2000;

        for(int i=0;i<200;i++) {
            p.recordEntry("r1");
            p.recordExit("r1", 10);
            p.recordEntry("r2");
            p.recordExit("r2", 10);
        }
        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testOneLevelRecordsCompactingMultiThread() throws Exception {
        System.out.println("testOneLevelRecordsCompactingMultiThread()");
        Record r1 = new Record("r1");
        Record r2 = new Record("r2");

        r1.invocations = 200;
        r1.selfTime = 2000;
        r1.wallTime = 2000;
        r2.invocations = 200;
        r2.selfTime = 2000;
        r2.wallTime = 2000;

        Set<Record> expected = new HashSet<Record>();
        expected.add(r1);
        expected.add(r2);


        final Phaser ph = new Phaser(2);
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                ph.arriveAndAwaitAdvance();
                for(int i=0;i<200;i++) {
                    p.recordEntry("r1");
                    p.recordExit("r1", 10);
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            public void run() {
                ph.arriveAndAwaitAdvance();
                for(int i=0;i<200;i++) {
                    p.recordEntry("r2");
                    p.recordExit("r2", 10);
                }
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        Snapshot s = p.snapshot();

        for(Record r : s.total) {
            if (!expected.remove(r)) {
                fail("Unexpected record: " + r);
            }
        }
        if (!expected.isEmpty()) {
            System.err.println("The following records were not matched:");
            for(Record r : expected) {
                System.err.println(r);
            }
            fail();
        }
    }

    @Test
    public void testTwoLevelsRecordsNoCompacting() {
        System.out.println("testTwoLevelsRecordsNoCompacting()");
        Record[] expected = new Record[]{new Record("r1"), new Record("r2")};

        expected[0].invocations = 20;
        expected[0].selfTime = 200;
        expected[0].wallTime = 400;
        expected[1].invocations = 20;
        expected[1].selfTime = 200;
        expected[1].wallTime = 200;

        for(int i=0;i<20;i++) {
            p.recordEntry("r1");
            p.recordEntry("r2");
            p.recordExit("r2", 10);
            p.recordExit("r1", 20);
        }
        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testTwoLevelsRecordsCompacting() {
        System.out.println("testTwoLevelsRecordsCompacting()");
        Record[] expected = new Record[]{new Record("r1"), new Record("r2")};

        expected[0].invocations = 200;
        expected[0].selfTime = 2000;
        expected[0].wallTime = 4000;
        expected[1].invocations = 200;
        expected[1].selfTime = 2000;
        expected[1].wallTime = 2000;

        for(int i=0;i<200;i++) {
            p.recordEntry("r1");
            p.recordEntry("r2");
            p.recordExit("r2", 10);
            p.recordExit("r1", 20);
        }
        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testSnapshotWithMethodsOnStackNoCompacting() {
        System.out.println("testSnapshotWithMethodsOnStackNoCompacting()");
        Record[] expected = new Record[] {new Record("r3")};

        expected[0].invocations = 1;
        expected[0].selfTime = 10;
        expected[0].wallTime = 10;

        p.recordEntry("r1");
        p.recordEntry("r2");
        p.recordEntry("r3");
        p.recordExit("r3", 10);

        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testSnapshotWithMethodsOnStackCompacting() {
        System.out.println("testSnapshotWithMethodsOnStackCompacting()");
        Record[] expected = new Record[]{new Record("r1"), new Record("r2")};
        expected[0].invocations = 12;
        expected[0].selfTime = 120;
        expected[0].wallTime = 240;
        expected[1].invocations = 13;
        expected[1].selfTime = 130;
        expected[1].wallTime = 130;

        for(int i=0;i<12;i++) {
            p.recordEntry("r1");
            p.recordEntry("r2");
            p.recordExit("r2", 10);
            p.recordExit("r1", 20);
        }
        p.recordEntry("r1");
        p.recordEntry("r2");
        p.recordExit("r2", 10);

        Snapshot s = p.snapshot();

        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testSnapshotRecursiveWallTime() {
        System.out.println("testSnapshotRecursiveWallTime()");

        Record[] expected = new Record[]{new Record("r1"), new Record("r2")};

        expected[0].invocations = 3;
        expected[0].wallTime = 40;
        expected[0].selfTime = 30;

        expected[1].invocations = 1;
        expected[1].wallTime = 30;
        expected[1].selfTime = 10;

        p.recordEntry("r1");
        p.recordEntry("r2");
        p.recordEntry("r1");
        p.recordExit("r1", 10);
        p.recordEntry("r1");
        p.recordExit("r1", 10);
        p.recordExit("r2", 30);
        p.recordExit("r1", 40);

        Snapshot s = p.snapshot();

        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testSnapshotReset() {
        System.out.println("testSnapshotReset()");

        Record[] expected = new Record[]{new Record("r1")};
        expected[0].invocations = 1;
        expected[0].selfTime = 10;
        expected[0].wallTime = 10;

        p.recordEntry("r1");
        p.recordExit("r1", 10);

        p.reset();

        p.recordEntry("r1");
        p.recordExit("r1", 10);

        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testSnapshotResetMultiThread() throws Exception {
        System.out.println("testSnapshotResetMultiThread()");

        // this is just a sanity test to make sure that invoking "reset()"
        // will not throw any exceptions

        Record[] expected = new Record[]{new Record("r1")};
        expected[0].invocations = 1;
        expected[0].selfTime = 10;
        expected[0].wallTime = 10;

        final AtomicBoolean finished = new AtomicBoolean(false);

        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    while(!finished.get()) {
                        p.recordEntry("r1");
                        p.recordExit("r1", 10);
                        Thread.sleep(7);
                    }
                } catch (InterruptedException e) {}
            }
        });
        t.start();

        for(int i=0;i<50;i++) {
            p.reset();
            Thread.sleep(13);
        }

        p.recordEntry("r1");
        p.recordExit("r1", 10);

        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }

    @Test
    public void testSnapshotResetWithMethodsOnStack() {
        System.out.println("testSnapshotResetWithMethodsOnStack()");

        Record[] expected = new Record[]{new Record("r1")};
        expected[0].invocations = 1;
        expected[0].selfTime = 10;
        expected[0].wallTime = 20;

        p.recordEntry("r1");
        p.recordEntry("r2");
        p.recordExit("r2", 10);

        p.reset();

        p.recordExit("r1", 20);

        Snapshot s = p.snapshot();
        assertArrayEquals(expected, s.total);
    }
}