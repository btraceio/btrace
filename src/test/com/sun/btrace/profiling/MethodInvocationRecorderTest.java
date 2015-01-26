/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;
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
public class MethodInvocationRecorderTest {

    public MethodInvocationRecorderTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    private MethodInvocationRecorder mir;

    @Before
    public void setUp() {
        mir = new MethodInvocationRecorder(1);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of recordEntry method, of class MethodInvocationRecorder.
     */
    @Test
    public void testRecordEntry() {
        System.out.println("recordEntry");
    }

    /**
     * Test of recordExit method, of class MethodInvocationRecorder.
     */
    @Test
    public void testRecordExit() {
        System.out.println("recordExit");
    }

    /**
     * Test of getRecords method, of class MethodInvocationRecorder.
     */
    @Test
    public void testGetRecords() {
        System.out.println("getRecords");

        Profiler.Record[] expected = new Profiler.Record[] {
            new Profiler.Record("r1"),
            new Profiler.Record("r2")
        };
        expected[0].invocations = 1;
        expected[0].selfTime = 10;
        expected[0].wallTime = 20;
        expected[1].invocations = 1;
        expected[1].selfTime = 10;
        expected[1].wallTime = 10;

        mir.recordEntry("r1");
        mir.recordEntry("r2");
        mir.recordExit("r2", 10);
        mir.recordExit("r1", 20);

        Profiler.Record[] result = mir.getRecords(false);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testGetRecordsConcurrent() {
        System.out.println("getRecords");

        Profiler.Record[] expected = new Profiler.Record[] {
            new Profiler.Record("r1"),
            new Profiler.Record("r2"),
            new Profiler.Record("r3")
        };
        expected[0].invocations = 1;
        expected[0].selfTime = 10;
        expected[0].wallTime = 30;
        expected[1].invocations = 1;
        expected[1].selfTime = 10;
        expected[1].wallTime = 10;
        expected[2].invocations = 1;
        expected[2].selfTime = 10;
        expected[2].wallTime = 10;

        final Phaser p = new Phaser(2);
        final AtomicReference<Profiler.Record[]> recRef = new AtomicReference<Profiler.Record[]>();

        Thread getter = new Thread(new Runnable() {
            public void run() {
                p.arriveAndAwaitAdvance();
                recRef.set(mir.getRecords(false));
                p.arriveAndAwaitAdvance();
            }
        });
        getter.start();


        mir.recordEntry("r1");
        mir.recordEntry("r2");
        mir.recordExit("r2", 10);
        p.arriveAndAwaitAdvance();
        mir.recordEntry("r3");
        mir.recordExit("r3", 10);
        mir.recordExit("r1", 30);
        p.arriveAndAwaitAdvance();

        Profiler.Record[] result = mir.getRecords(false);
        assertArrayEquals(expected, result);
    }

    /**
     * Test of reset method, of class MethodInvocationRecorder.
     */
    @Test
    public void testReset() {
        System.out.println("reset");
    }

}
