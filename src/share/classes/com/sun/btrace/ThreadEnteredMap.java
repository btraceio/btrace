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
import java.util.IdentityHashMap;
import java.util.Map;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public class ThreadEnteredMap<T> {
    final private Object writeLock = new Object();
    long[] ids;
    private Map<Thread, T> entered;
    private T nullValue;

    public ThreadEnteredMap(T nullValue) {
        entered = new IdentityHashMap();
        this.nullValue = nullValue;
    }

    public static void main(String[] args) throws IOException {
        ThreadEnteredMap<String> instance = new ThreadEnteredMap("null");
        long cnt = 0;
        long start = System.nanoTime();
        for(int i=0;i<400000000;i++) {
            cnt += i;
        }
        long dur = System.nanoTime() - start;
        System.err.println("#" + cnt + " in " + dur + "ns");
        System.err.println(dur / 400000000);

        for(int i=0;i<400000;i++) {
            instance.enter("nasrat");
            instance.exit();
        }

        cnt = 0;
        System.err.println("Ready?");
        System.in.read();
        start = System.nanoTime();
        for(int i=0;i<400000000;i++) {
            instance.enter("nasrat");

            cnt += i;
//            Thread.currentThread().getId();
            instance.exit();
        }
        dur = System.nanoTime() - start;
        System.err.println("#" + cnt + " in " + dur + "ns");
        System.err.println(dur / 400000000);
    }

    public T get() {
        T val = entered.get(Thread.currentThread());
        return val != nullValue ? val : null;
    }

    public boolean enter(T rt) {
        Thread t = Thread.currentThread();
        T oldRt = entered.get(t);
        if (oldRt != null) {
            if (oldRt != nullValue) {
                return false;
            }
        }
        synchronized(writeLock) {
            entered.put(t, rt);
            return true;
        }
    }

    public void exit() {
        entered.put(Thread.currentThread(), nullValue);
    }
}
