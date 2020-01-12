/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package traces.verifier;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@BTrace
public class VerifierScript implements Runnable {
    private static Properties p = new Properties();
    private static int[] x = new int[100];
    private int i = 10;

    @OnMethod(clazz = "/.*/")
    public static void invalidMethodCall(@Self List l) {
        if (l instanceof ArrayList) {
            System.out.println(l.size());
        }
    }

    @OnMethod(clazz = "/.*/")
    public static void invalidLoops(List<String> l) {
        for (int i = 0; i < 10; i++) {
            BTraceUtils.println(BTraceUtils.str(i));
        }
        for (String s : l) {
            BTraceUtils.println(s);
        }
        while (true) {
            BTraceUtils.print("x");
        }
    }

    @OnMethod(clazz = "/.*/")
    public static int invalidReturn() {
        return 1;
    }

    @OnMethod(clazz = "/.*/")
    public static synchronized void syncHandler() {
        synchronized (VerifierScript.class) {
            BTraceUtils.println("ok");
        }
    }

    @OnMethod(clazz = "/.*/")
    public static void validInstanceHandler() {
    }

    @OnMethod(clazz = "/.*/")
    public void invalidInstanceHandler() {
        try {
            System.out.println("x");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        // do nothing
    }
}
