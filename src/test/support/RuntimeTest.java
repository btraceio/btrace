/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package support;

import com.sun.btrace.BTraceFunctionalTests;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;

/**
 *
 * @author Jaroslav Bachorik
 */
abstract public class RuntimeTest {
    protected static interface ResultValidator {
        void validate(String stdout, String stderr, int retcode);
    }

    private static String cp = null;
    private static String java = null;
    private static String btraceExtPath = null;

    public static void setup() {
        URL url = BTraceFunctionalTests.class.getClassLoader().getResource("com/sun/btrace/client/Main.class");
        try {
            File f = new File(url.toURI());
            while (f != null) {
                if (f.getName().equals("build")) {
                    break;
                }
                f = f.getParentFile();
            }
            if (f != null) {
                btraceExtPath = f.getAbsolutePath() + "/btrace-client.jar";
            }
            Assert.assertNotNull(btraceExtPath);
        } catch (URISyntaxException e) {
            throw new Error(e);
        }
        String toolsjar = null;
        cp = System.getProperty("java.class.path");
        StringTokenizer st = new StringTokenizer(cp, File.pathSeparator);
        while (st.hasMoreTokens()) {
            String elem = st.nextToken();
            if (elem.contains("tools.jar")) {
                toolsjar = elem;
            }
        }
        if (toolsjar == null) {
            URL rturl = String.class.getResource("/java/lang/String.class");
            toolsjar = rturl.toString().replace("jar:file:", "").replace("jre/lib/rt.jar", "lib/tools.jar");
            toolsjar = toolsjar.substring(0, toolsjar.indexOf("!"));
            System.err.println(toolsjar);
        }
        btraceExtPath = btraceExtPath + File.pathSeparator + toolsjar;
        java = System.getProperty("java.home").replace("/jre", "");
    }

    /**
     * Display the otput from the test application
     */
    protected boolean debugTestApp = false;
    /**
     * Run BTrace in debug mode
     */
    protected boolean debugBTrace = false;
    /**
     * Run BTrace in unsafe mode
     */
    protected boolean isUnsafe = false;
    /**
     * Timeout in ms to wait for the expected BTrace output
     */
    protected long timeout = 10000L;

    protected void reset() {
        debugTestApp = false;
        debugBTrace = false;
        isUnsafe = false;
        timeout = 10000L;
    }

    public void test(String testApp, final String testScript, int checkLines, ResultValidator v) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            java + "/bin/java",
            "-cp",
            cp,
            testApp
        );
        pb.environment().remove("JAVA_TOOL_OPTIONS");

        Process p = pb.start();
        final PrintWriter pw = new PrintWriter(p.getOutputStream());

        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        final AtomicInteger ret = new AtomicInteger(-1);

        final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(p.getInputStream()));

        final CountDownLatch testAppLatch = new CountDownLatch(1);
        final AtomicReference<String> pidStringRef = new AtomicReference<>();

        Thread outT = new Thread(new Runnable() {
            public void run() {
                try {
                    String l;
                    while ((l = stdoutReader.readLine()) != null) {
                        if (l.startsWith("ready:")) {
                            pidStringRef.set(l.split("\\:")[1]);
                            testAppLatch.countDown();
                        }
                        if (debugTestApp) {
                            System.out.println("[traced app] " + l);
                        }
                    }


                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
        }, "STDOUT Reader");
        outT.setDaemon(true);

        final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

        Thread errT = new Thread(new Runnable() {

            public void run() {
                try {
                    String l = null;
                    while ((l = stderrReader.readLine()) != null) {
                        testAppLatch.countDown();
                        if (debugTestApp) {
                            System.err.println("[traced app] " + l);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
        }, "STDERR Reader");
        errT.setDaemon(true);

        outT.start();
        errT.start();

        testAppLatch.await();
        String pid = pidStringRef.get();
        if (pid != null) {
            System.out.println("Target process ready: " + pid);

            Process client = attach(pid, testScript, checkLines, stdout, stderr);

            System.out.println("Detached.");
            pw.println("done");
            pw.flush();

            ret.set(client.waitFor());

            outT.join();
            errT.join();
        }

        v.validate(stdout.toString(), stderr.toString(), ret.get());
    }

    private Process attach(String pid, String trace, final int checkLines, final StringBuilder stdout, final StringBuilder stderr) throws Exception {
        URL u = ClassLoader.getSystemResource(trace);
        System.out.println(trace);
        File traceFile = new File(u.toURI());
        trace = traceFile.getAbsolutePath();
        final ProcessBuilder pb = new ProcessBuilder(
            java + "/bin/java",
            "-Dcom.sun.btrace.unsafe=" + isUnsafe,
            "-Dcom.sun.btrace.debug=" + debugBTrace,
            "-cp",
            btraceExtPath,
            "com.sun.btrace.client.Main",
            "-d", "/tmp/btrace-test",
            "-pd", traceFile.getParentFile().getAbsolutePath(),
            pid,
            trace
        );

        pb.environment().remove("JAVA_TOOL_OPTIONS");
        final Process p = pb.start();

        final CountDownLatch l = new CountDownLatch(checkLines);

        new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                    String line = null;
                    while ((line = br.readLine()) != null) {
                        stderr.append(line).append('\n');
                        System.out.println("[btrace err] " + line);
                        if (line.contains("Exception") || line.contains("Error")) {
                            for(int i=0;i<checkLines;i++) {
                                l.countDown();
                            }
                        }
                    }
                } catch (Exception e) {
                    for(int i=0;i<checkLines;i++) {
                        l.countDown();
                    }
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
                        stdout.append(line).append('\n');
                        System.out.println("[btrace out] " + line);
                        if (!(debugBTrace && line.contains("DEBUG:"))) {
                            l.countDown();
                        }
                    }
                } catch (Exception e) {
                    for(int i=0;i<checkLines;i++) {
                        l.countDown();
                    }
                    throw new Error(e);
                }
            }
        }, "Stdout Reader").start();

        l.await(timeout, TimeUnit.MILLISECONDS);

        return p;
    }
}
