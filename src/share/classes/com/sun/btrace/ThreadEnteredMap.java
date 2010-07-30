/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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

import java.io.IOException;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public class ThreadEnteredMap {
    final private static int SECTIONS = 13;
    final private static int BUCKETS = 27;
    final private static int DEFAULT_BUCKET_SIZE = 4;

    final private Object[][][] map = new Object[SECTIONS][BUCKETS][];
    final private int[][] mapPtr = new int[SECTIONS][BUCKETS];

    private Object nullValue;

    public ThreadEnteredMap(Object nullValue) {
        this.nullValue = nullValue;
    }

    public static void main(String[] args) throws IOException {
        ThreadEnteredMap instance = new ThreadEnteredMap("null");
        long cnt = 0;
        long start = System.nanoTime();
        for(int i=0;i<4000000;i++) {
            cnt += i;
        }
        long dur = System.nanoTime() - start;
        System.err.println("#" + cnt + " in " + dur + "ns");
        System.err.println(dur / 4000000);

        for(int i=0;i<400000;i++) {
            instance.enter("nasrat");
            instance.exit();
        }

        final ThreadEnteredMap tem = new ThreadEnteredMap("null");
        cnt = 0;
        System.err.println("Ready?");
        System.in.read();
        Thread[] thrd = new Thread[200];
        for(int i=0;i<4;i++) {
            final int idx = i;
            thrd[i] = new Thread(new Runnable() {

                public void run() {
                    long cnt = 0;
                    long start = System.nanoTime();
                    for(int i=0;i<4000000;i++) {
                        tem.enter("nasrat");

                        cnt += i;
                        tem.exit();
                    }
                    long dur = System.nanoTime() - start;
                    System.out.println("Thread #" + idx);
                    System.err.println("#" + cnt + " in " + dur + "ns");
                    System.err.println(dur / 4000000);
                }
            }, "Thread#" + i);
        }
        for(int i=0;i<4;i++) {
            thrd[i].start();
        }
        
    }

    public Object get() {
        Thread thrd = Thread.currentThread();
        long thrdId = thrd.getId();
        int sectionId = (int)(((thrdId << 1) - (thrdId << 8)) & (SECTIONS - 1));
        Object[][] section = map[sectionId];
        int[] sectionPtr = mapPtr[sectionId];
        int bucketId = (int)(int)(((thrdId << 1) - (thrdId << 8)) & (BUCKETS - 1));
        synchronized(section) {
            Object[] bucket = section[bucketId];
            if (bucket != null && bucket.length > 0) {
                int ptr = sectionPtr[bucketId];
                for(int i=0;i<ptr;i+=2) {
                    if (bucket[i] == thrd) {
                        return bucket[i+1] == nullValue ? null : bucket[i+1];
                    }
                }
            }
            return null;
        }
    }

    public boolean enter(Object rt) {
        Thread thrd = Thread.currentThread();
        long thrdId = thrd.getId();
        int sectionId = (int)(((thrdId << 1) - (thrdId << 8)) & (SECTIONS - 1));
        Object[][] section = map[sectionId];
        int[] sectionPtr = mapPtr[sectionId];
        int bucketId = (int)(int)(((thrdId << 1) - (thrdId << 8)) & (BUCKETS - 1));
        synchronized(section) {
            Object[] bucket = section[bucketId];
            int ptr = sectionPtr[bucketId];
            if (bucket != null && bucket.length > 0) {
                for(int i=0;i<ptr;i+=2) {
                    if (bucket[i] == thrd) {
                        if (bucket[i+1] == nullValue) {
                            bucket[i+1] = rt;
                            return true;
                        }
                        return false;
                    }
                }
            }
            if (bucket == null || bucket.length == 0) {
                bucket = new Object[DEFAULT_BUCKET_SIZE * 2];
                section[bucketId] = bucket;
            } else {
                if (ptr >= bucket.length) {
                    Object[] newBucket = new Object[bucket.length * 2];
                    System.arraycopy(bucket, 0, newBucket, 0, bucket.length);
                    bucket = newBucket;
                    section[bucketId] = bucket;
                }
            }
            bucket[ptr++] = thrd;
            bucket[ptr++] = rt;
            mapPtr[sectionId][bucketId] = ptr;
            return true;
        }
    }

    public void exit() {
        Thread thrd = Thread.currentThread();
        long thrdId = thrd.getId();
        int sectionId = (int)(((thrdId << 1) - (thrdId << 8)) & (SECTIONS - 1));
        Object[][] section = map[sectionId];
        int[] sectionPtr = mapPtr[sectionId];
        int bucketId = (int)(int)(((thrdId << 1) - (thrdId << 8)) & (BUCKETS - 1));
        synchronized(section) {
            Object[] bucket = section[bucketId];
            if (bucket != null && bucket.length > 0) {
                int ptr = sectionPtr[bucketId];
                for(int i=0;i<ptr;i+=2) {
                    if (bucket[i] == thrd) {
                        bucket[i+1] = nullValue;
                    }
                }
            }
        }
    }
}
