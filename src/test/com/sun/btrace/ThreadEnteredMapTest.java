/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common
 *  Development and Distribution License("CDDL") (collectively, the
 *  "License"). You may not use this file except in compliance with the
 *  License. You can obtain a copy of the License at
 *  http://www.netbeans.org/cddl-gplv2.html
 *  or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 *  specific language governing permissions and limitations under the
 *  License.  When distributing the software, include this License Header
 *  Notice in each file and include the License file at
 *  nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Sun in the GPL Version 2 section of the License file that
 *  accompanied this code. If applicable, add the following below the
 *  License Header, with the fields enclosed by brackets [] replaced by
 *  your own identifying information:
 *  "Portions Copyrighted [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *
 *  The Original Software is NetBeans. The Initial Developer of the Original
 *  Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 *  Microsystems, Inc. All Rights Reserved.
 *
 *  If you wish your version of this file to be governed by only the CDDL
 *  or only the GPL Version 2, indicate your decision by adding
 *  "[Contributor] elects to include this software in this distribution
 *  under the [CDDL or GPL Version 2] license." If you do not indicate a
 *  single choice of license, a recipient has the option to distribute
 *  your version of this file under either the CDDL, the GPL Version 2 or
 *  to extend the choice of license to its licensees as provided above.
 *  However, if you add GPL Version 2 code and therefore, elected the GPL
 *  Version 2 license, then the option applies only if the new code is
 *  made subject to such option by the copyright holder.
 */

package com.sun.btrace;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ThreadEnteredMapTest {

    public ThreadEnteredMapTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    private ThreadEnteredMap map;

    @Before
    public void setUp() {
        map = new ThreadEnteredMap("null");
    }

    @After
    public void tearDown() {
        map = null;
    }

    @Test
    public void testGetEmpty() {
        System.out.println("getEmpty");
        Object expResult = null;
        Object result = map.get();
        assertEquals(expResult, result);
    }

    @Test
    public void testEnteredCurThrd() {
        System.out.println("enteredCurThrd");
        Object myval = new Object();
        assertTrue(map.enter(myval));
        assertEquals(myval, map.get());
    }

    @Test
    public void testExitedCurThrd() {
        System.out.println("exitedCurThrd");
        Object myval = new Object();
        map.enter(myval);
        map.exit();
        assertNull(map.get());
    }

    @Test
    public void testEnteredManyThrds() throws InterruptedException {
        System.out.println("enteredManyThrds");

        final CountDownLatch latch = new CountDownLatch(4096);
        final AtomicBoolean rslt = new AtomicBoolean(true);

        final Object lock = new Object();
        Thread[] thrds = new Thread[4096];
        final Random r = new Random(System.nanoTime());
        for(int i=0;i<4096;i++) {
            thrds[i] = new Thread(new Runnable() {
                Object myval = new Object();
                public void run() {
                    try {
                        Thread.sleep(r.nextInt(500));
                    } catch (InterruptedException ex) {
                    }
                    synchronized(lock) {
                        boolean outcome = map.enter(myval);
                        outcome = outcome && myval.equals(map.get());
                        rslt.compareAndSet(true, outcome);
                        latch.countDown();
                    }
                    
                }
            }, "Thrd#" + i);
        }
        for(int i=4095;i>=0;i--) {
            thrds[i].start();
        }
        latch.await();
        assertTrue(rslt.get());
    }
}