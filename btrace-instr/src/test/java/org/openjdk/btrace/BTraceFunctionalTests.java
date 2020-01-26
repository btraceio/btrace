/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A set of end-to-end functional tests.
 * <p>
 * The test simulates a user submitting a BTrace script to the target application
 * and asserts that no exceptions are thrown, JVM keeps on running and
 * BTrace generates the anticipated output.
 *
 * @author Jaroslav Bachorik
 */
public class BTraceFunctionalTests extends RuntimeTest {
    @BeforeClass
    public static void classSetup() throws Exception {
        setup();
    }

    @Before
    @Override
    public void reset() {
        super.reset();
    }

    @Test
    public void testOSMBean() throws Exception {
        isUnsafe = true;
        test(
            "resources.Main",
            "btrace/OSMBeanTest.java",
            2,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                }
            }
        );
    }

    @Test
    public void testOnProbe() throws Exception {
        if (Files.exists(Paths.get(java, "jre"))) {
            test(
                    "resources.Main",
                    "btrace/OnProbeTest.java",
                    5,
                    new ResultValidator() {
                        @Override
                        public void validate(String stdout, String stderr, int retcode) {
                            Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                            Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                            Assert.assertTrue(stdout.contains("[this, noargs]"));
                            Assert.assertTrue(stdout.contains("[this, args]"));
                        }
                    }
            );
        } else {
            System.err.println("XML libraries not available. Skipping @OnProbe tests");
        }
    }

    @Test
    public void testOnTimer() throws Exception {
        test(
            "resources.Main",
            "btrace/OnTimerTest.java",
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("vm version"));
                    Assert.assertTrue(stdout.contains("vm starttime"));
                    Assert.assertTrue(stdout.contains("timer"));
                }
            }
        );
    }

    @Test
    public void testOnTimerArg() throws Exception {
        test(
            "resources.Main",
            "btrace/OnTimerArgTest.java",
            new String[]{"timer=500"},
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("vm version"));
                    Assert.assertTrue(stdout.contains("vm starttime"));
                    Assert.assertTrue(stdout.contains("timer"));
                }
            }
        );
    }

    @Test
    public void testOnExit() throws Exception {
        test(
            "resources.Main",
            "btrace/OnExitTest.java",
            1,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("onexit"));
                }
            }
        );
    }

    @Test
    public void testOnMethod() throws Exception {
        test(
            "resources.Main",
            "btrace/OnMethodTest.java",
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("[this, noargs]"));
                    Assert.assertTrue(stdout.contains("[this, args]"));
                    Assert.assertTrue(stdout.contains("{xxx}"));
                }
            }
        );
    }

    @Test
    public void testOnMethodLevel() throws Exception {
        test(
            "resources.Main",
            "btrace/OnMethodLevelTest.java",
            new String[] {"level=200"},
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("[this, noargs]"));
                    Assert.assertTrue(stdout.contains("[this, args]"));
                    Assert.assertTrue(stdout.contains("{xxx}"));
                }
            }
        );
    }

    @Test
    public void testOnMethodTrackRetransform() throws Exception {
        trackRetransforms = true;
        test(
            "resources.Main",
            "btrace/OnMethodTest.java",
            2,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("Going to retransform class"));
                }
            }
        );
    }

    @Test
    public void testOnMethodReturn() throws Exception {
        test(
            "resources.Main",
            "btrace/OnMethodReturnTest.java",
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("[this, anytype(void)]"));
                    Assert.assertTrue(stdout.contains("[this, void]"));
                    Assert.assertTrue(stdout.contains("[this, 2]"));
                }
            }
        );
    }

    @Test
    public void testOnMethodSubclass() throws Exception {
        debugTestApp = true;
        debugBTrace = true;
        test(
            "resources.Main",
            "btrace/OnMethodSubclassTest.java",
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("print:class resources.Main"));
                }
            }
        );
    }

    @Test
    public void testProbeArgs() throws Exception {
        isUnsafe = true;
        test(
            "resources.Main",
            "btrace/ProbeArgsTest.java",
            new String[] {"arg1", "arg2=val2"},
            5,
            new ResultValidator() {
                @Override
                public void validate(String stdout, String stderr, int retcode) {
                    Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                    Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                    Assert.assertTrue(stdout.contains("arg#=2"));
                    Assert.assertTrue(stdout.contains("arg1="));
                    Assert.assertTrue(stdout.contains("arg2=val2"));
                    Assert.assertFalse(stdout.contains("matching probe"));
                }
            }
        );
    }

    @Test
    public void testPerfCounter() throws Exception {
        test(
                "resources.Main",
                "btrace/PerfCounterTest.java",
                5,
                new ResultValidator() {
                    @Override
                    public void validate(String stdout, String stderr, int retcode) {
                        Assert.assertFalse("Script should not have failed", stdout.contains("FAILED"));
                        Assert.assertTrue("Non-empty stderr", stderr.isEmpty());
                        Assert.assertTrue(stdout.contains("matching probe"));
                    }
                }
        );
    }
}
