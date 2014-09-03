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

import com.sun.btrace.client.Main;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class TestBTraceUtils {
    private static String cp = null;
    private static String java = null;
    private static String btraceExtPath = null;

    @BeforeClass
    public static void setup() {
        URL url = TestBTraceUtils.class.getClassLoader().getResource("com/sun/btrace/client/Main.class");
        try {
            File f = new File(url.toURI());
            while (f != null) {
                if (f.getName().equals("build")) {
                    break;
                }
                f = f.getParentFile();
            }
            if (f != null) {
                btraceExtPath = f.getAbsolutePath() + "/btrace-client.jar" +
                                File.pathSeparator +
                                f.getAbsolutePath() + "/btrace-agent.jar";
            }
            Assert.assertNotNull(btraceExtPath);
        } catch (URISyntaxException e) {
            throw new Error(e);
        }
        cp = System.getProperty("java.class.path");
        StringTokenizer st = new StringTokenizer(cp, File.pathSeparator);
        while (st.hasMoreTokens()) {
            String elem = st.nextToken();
            if (elem.contains("tools.jar")) {
                btraceExtPath = btraceExtPath + File.pathSeparator + elem;
            }
        }
        java = System.getProperty("java.home");
    }

    @Test
    public void testOSMBean() throws Exception {
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
            Process client = attach(pid, "traces/OSMBeanTest.java");

            pw.println("done");
            pw.flush();

            while ((l = br.readLine()) != null) {
                System.out.println("[traced app] " + l);
            }

            int ret = client.waitFor();
        }
    }

    private Process attach(String pid, String trace) throws Exception {
        URL u = ClassLoader.getSystemResource(trace);
        System.out.println(trace);
        trace = new File(u.toURI()).getAbsolutePath();
        final ProcessBuilder pb = new ProcessBuilder(
            java + "/bin/java",
            "-Dcom.sun.btrace.unsafe=true",
            "-cp",
            btraceExtPath,
            "com.sun.btrace.client.Main",
            pid,
            trace
        );

        final Process p = pb.start();

        final Semaphore s = new Semaphore(0);

        new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                    String line = null;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[btrace err] " + line);
                        if (line.contains("Exception") || line.contains("Error")) {
                            s.release(10);
                        }
                    }
                } catch (Exception e) {
                    s.release(10);
                    throw new Error(e);
                }
            }
        }, "Stderr Reader").start();

        new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[btrace out] " + line);
                        s.release();
                    }
                } catch (Exception e) {
                    s.release(10);
                    throw new Error(e);
                }
            }
        }, "Stdout Reader").start();

        s.acquire(10);

        return p;
    }
}
