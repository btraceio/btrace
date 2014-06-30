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

package com.sun.btrace;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class TestBTraceUtils {
    private String cp = null;
    private String java = null;

    @Before
    public void setup() {
        cp = System.getProperty("java.class.path");
        java =  System.getProperty("java.home");
    }
    @Test
    public void testOSMBean() throws Exception {
        String cp = System.getProperty("java.class.path");
        String java = System.getProperty("java.home");

        ProcessBuilder pb = new ProcessBuilder(
            java + "/bin/java",
            "-cp",
            cp,
            "resources.Main"
        );

        Process p = pb.start();
        PrintWriter pw = new PrintWriter(p.getOutputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

        String l = br.readLine();
        if (l.startsWith("ready:")) {
            String pid = l.split("\\:")[1];
            System.out.println("Target process ready: " + pid);
//            attach(pid, "traces/OSMBeanTest.btrace");
            attach(pid, "traces/onmethod/Args.class");
            Thread.sleep(500);

            pw.println("done");
        }
    }

    private void attach(String pid, String trace) throws Exception {
        trace = trace.replace(".btrace", ".java");
        URL u = ClassLoader.getSystemResource(trace);
        System.out.println(trace);
        System.out.println(u);
        trace = new File(u.toURI()).getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(
            java + "/bin/java",
            "-cp",
            cp,
            "com.sun.btrace.client.Main",
            pid,
            trace
        );
        pb.start();
    }
}
