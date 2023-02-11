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
package tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;

/** @author Jaroslav Bachorik */
@SuppressWarnings("ConstantConditions")
public abstract class RuntimeTest {
  private static String cp = null;
  protected static String javaHome = null;
  private static String clientClassPath = null;
  private static String eventsClassPath = null;
  private static Path projectRoot = null;
  private static boolean forceDebug = false;
  /** Try starting JFR recording if available */
  private boolean startJfr = false;
  /** Display the otput from the test application */
  protected boolean debugTestApp = false;
  /** Run BTrace in debug mode */
  protected boolean debugBTrace = false;
  /** Run BTrace in unsafe mode */
  protected boolean isUnsafe = false;
  /** Timeout in ms to wait for the expected BTrace output */
  protected long timeout = 10000L;
  /** Track retransforming progress */
  protected boolean trackRetransforms = false;
  /** Provide extra JVM args */
  protected List<String> extraJvmArgs = Collections.emptyList();

  protected boolean attachDebugger = false;

  public static void setup() {
    String forceDebugVal = System.getProperty("btrace.test.debug");
    if (forceDebugVal == null) {
      forceDebugVal = System.getenv("BTRACE_TEST_DEBUG");
    }
    forceDebug = Boolean.parseBoolean(forceDebugVal);
    Path libsPath = Paths.get(System.getProperty("btrace.libs"));
    projectRoot = Paths.get(System.getProperty("project.dir"));
    Path clientJarPath = libsPath.resolve("btrace-client.jar");
    Path eventsJarPath = projectRoot.resolve("build/libs/events.jar");
    clientClassPath = clientJarPath.toString();
    eventsClassPath = eventsJarPath.toString();
    // client jar needs to take precedence in order for the agent.jar inferring code to work
    cp =
        clientJarPath
            + File.pathSeparator
            + projectRoot.resolve("build/classes/java/test")
            + File.pathSeparator
            + eventsClassPath;

    Assertions.assertNotNull(projectRoot);
    Assertions.assertNotNull(clientClassPath);

    String toolsjar = null;

    javaHome = System.getProperty("java.home").replace("/jre", "");

    Path toolsJarPath = Paths.get(javaHome, "lib", "tools.jar");
    if (Files.exists(toolsJarPath)) {
      toolsjar = toolsJarPath.toString();
    }
    cp = toolsjar != null ? cp + File.pathSeparator + toolsjar : cp;
    System.out.println("=== Using Java: " + javaHome + ", cp: " + cp);
  }

  protected void reset() {
    debugTestApp = false;
    debugBTrace = false;
    isUnsafe = false;
    timeout = 10000L;
  }

  public void testWithJfr(String testApp, String testScript, int checkLines, ResultValidator v)
      throws Exception {
    startJfr = true;
    test(testApp, testScript, checkLines, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void testWithJfr(
      String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)
      throws Exception {
    startJfr = true;
    test(testApp, testScript, cmdArgs, checkLines, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void test(String testApp, String testScript, int checkLines, ResultValidator v)
      throws Exception {
    test(testApp, testScript, null, checkLines, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void test(
      String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)
      throws Exception {
    if (forceDebug) {
      // force debug flags
      debugBTrace = true;
      debugTestApp = true;
    }
    String jfrFile = null;
    List<String> args = new ArrayList<>(Arrays.asList(javaHome + "/bin/java", "-cp", cp));
    if (attachDebugger) {
      args.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
    }
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    // uncomment the following line to get extra JFR logs
    //    args.add("-Xlog:jfr*=trace");
    args.addAll(extraJvmArgs);
    args.addAll(
        Arrays.asList(
            "-XX:+AllowRedefinitionToAddDeleteMethods", "-XX:+IgnoreUnrecognizedVMOptions"));
    if (startJfr) {
      jfrFile = Files.createTempFile("btrace-", ".jfr").toString();
      args.add("-XX:StartFlightRecording=settings=default,dumponexit=true,filename=" + jfrFile);
    }
    args.add("-Dbtrace.test=test");
    args.add(testApp);

    ProcessBuilder pb = new ProcessBuilder(args);
    pb.environment().remove("JAVA_TOOL_OPTIONS");

    Process p = pb.start();
    PrintWriter pw = new PrintWriter(p.getOutputStream());

    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();
    AtomicInteger ret = new AtomicInteger(-1);

    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(p.getInputStream()));

    CountDownLatch testAppLatch = new CountDownLatch(1);
    AtomicReference<String> pidStringRef = new AtomicReference<>();

    Thread outT =
        new Thread(
            () -> {
              try {
                String l;
                while ((l = stdoutReader.readLine()) != null) {
                  if (l.startsWith("ready:")) {
                    pidStringRef.set(l.split(":")[1]);
                    testAppLatch.countDown();
                  }
                  if (debugTestApp) {
                    System.out.println("[traced app] " + l);
                  }
                }

              } catch (Exception e) {
                e.printStackTrace(System.err);
              }
            },
            "STDOUT Reader");
    outT.setDaemon(true);

    BufferedReader stderrReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

    Thread errT =
        new Thread(
            () -> {
              try {
                String l = null;
                while ((l = stderrReader.readLine()) != null) {
                  if (l.contains("Server VM warning")
                      || l.contains("XML libraries not available")) {
                    continue;
                  }
                  testAppLatch.countDown();
                  if (debugTestApp) {
                    System.err.println("[traced app] " + l);
                  }
                }
              } catch (Exception e) {
                e.printStackTrace(System.err);
              }
            },
            "STDERR Reader");
    errT.setDaemon(true);

    outT.start();
    errT.start();

    testAppLatch.await();
    String pid = pidStringRef.get();
    if (pid != null) {
      System.out.println("Target process ready: " + pid);

      Process client = attach(pid, testScript, cmdArgs, checkLines, stdout, stderr);

      System.out.println("Detached.");

      int retries = 10;
      boolean exitted = false;
      while (!exitted && retries-- > 0) {
        pw.println("done");
        pw.flush();
        exitted = client.waitFor(1, TimeUnit.SECONDS);
        if (!exitted) {
          System.out.println("... retrying ...");
        }
      }

      if (!exitted) {
        client.destroyForcibly();
      }

      ret.set(exitted ? client.exitValue() : -1);

      outT.join();
      errT.join();
    }

    v.validate(stdout.toString(), stderr.toString(), ret.get(), jfrFile);
  }

  public static final class TestApp {
    private int pid;
    private final CountDownLatch testAppLatch = new CountDownLatch(1);
    private final Process process;

    public TestApp(Process process, boolean debug) {
      this.process = process;

      BufferedReader stdoutReader =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      Thread outT =
          new Thread(
              () -> {
                try {
                  String l;
                  while ((l = stdoutReader.readLine()) != null) {
                    if (l.startsWith("ready:")) {
                      pid = Integer.parseInt(l.split(":")[1]);
                      testAppLatch.countDown();
                    }
                    if (debug) {
                      System.out.println("[traced app] " + l);
                    }
                  }

                } catch (Exception e) {
                  e.printStackTrace(System.err);
                }
              },
              "STDOUT Reader");
      outT.setDaemon(true);

      BufferedReader stderrReader =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));

      Thread errT =
          new Thread(
              () -> {
                try {
                  String l = null;
                  while ((l = stderrReader.readLine()) != null) {
                    if (l.contains("Server VM warning")
                        || l.contains("XML libraries not available")) {
                      continue;
                    }
                    testAppLatch.countDown();
                    if (debug) {
                      System.err.println("[traced app] " + l);
                    }
                  }
                } catch (Exception e) {
                  e.printStackTrace(System.err);
                }
              },
              "STDERR Reader");
      errT.setDaemon(true);

      outT.start();
      errT.start();
    }

    public void stop() throws InterruptedException {
      if (process.isAlive()) {
        PrintWriter pw = new PrintWriter(process.getOutputStream());
        pw.println("done");
        pw.flush();
        process.waitFor();
      }
    }

    public int getPid() throws InterruptedException {
      testAppLatch.await();
      return pid;
    }
  }

  public TestApp launchTestApp(String testApp, String... cmdArgs) throws Exception {
    if (forceDebug) {
      // force debug flags
      debugBTrace = true;
      debugTestApp = true;
    }
    String jfrFile = null;
    List<String> args = new ArrayList<>(Arrays.asList(javaHome + "/bin/java", "-cp", cp));
    if (attachDebugger) {
      args.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
    }
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    // uncomment the following line to get extra JFR logs
    //    args.add("-Xlog:jfr*=trace");
    args.addAll(extraJvmArgs);
    args.addAll(
        Arrays.asList(
            "-XX:+AllowRedefinitionToAddDeleteMethods", "-XX:+IgnoreUnrecognizedVMOptions"));
    if (startJfr) {
      jfrFile = Files.createTempFile("btrace-", ".jfr").toString();
      args.add("-XX:StartFlightRecording=settings=default,dumponexit=true,filename=" + jfrFile);
    }
    args.add(testApp);

    ProcessBuilder pb = new ProcessBuilder(args);
    pb.environment().remove("JAVA_TOOL_OPTIONS");

    return new TestApp(pb.start(), debugTestApp);
  }

  public interface ProcessOutputProcessor {
    boolean onStdout(int lineno, String line);

    boolean onStderr(int lineno, String line);
  }

  public void runBTrace(String[] args, ProcessOutputProcessor outputProcessor) throws Exception {
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-cp",
                cp,
                "org.openjdk.btrace.client.Main",
                debugBTrace ? "-v" : "",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString()));
    argVals.addAll(Arrays.asList(args));
    String testJavaHome = System.getenv("TEST_JAVA_HOME");
    testJavaHome = testJavaHome != null ? testJavaHome : System.getenv("JAVA_HOME");
    if (testJavaHome == null) {
      throw new IllegalStateException("Missing TEST_JAVA_HOME or JAVA_HOME env variables");
    }
    if (Files.exists(Paths.get(testJavaHome, "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    Thread stderrThread =
        new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));

                int lineno = 0;
                String line = null;
                while ((line = br.readLine()) != null) {
                  System.out.println("[btrace err] " + line);
                  if (line.contains("Server VM warning")
                      || line.contains("XML libraries not available")
                      || line.contains("Connection reset")) {
                    // skip JVM generated warnings
                    continue;
                  }
                  if (line.startsWith("[traced app]") || line.startsWith("[btrace out]")) {
                    // skip test debug lines
                    continue;
                  }
                  if (!outputProcessor.onStderr(++lineno, line)) {
                    return;
                  }
                  ;
                }
              } catch (Exception e) {
                throw new Error(e);
              }
            },
            "Stderr Reader");

    Thread stdoutThread =
        new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                int lineno = 1;
                String line = null;
                while ((line = br.readLine()) != null) {
                  if (!outputProcessor.onStdout(lineno, line)) {
                    return;
                  }
                  if (!(debugBTrace && line.contains("DEBUG"))) {
                    lineno++;
                  }
                }
              } catch (Exception e) {
                throw new Error(e);
              }
            },
            "Stdout Reader");

    stderrThread.setDaemon(true);
    stdoutThread.setDaemon(true);

    stderrThread.start();
    stdoutThread.start();

    stderrThread.join();
    stdoutThread.join();
  }

  public Process runBTrace(
      String[] args, int checkLines, StringBuilder stdout, StringBuilder stderr) throws Exception {
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-cp",
                cp,
                "org.openjdk.btrace.client.Main",
                debugBTrace ? "-v" : "",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString()));
    argVals.addAll(Arrays.asList(args));
    if (Files.exists(Paths.get(System.getenv("TEST_JAVA_HOME"), "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    CountDownLatch l = new CountDownLatch(checkLines);

    new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));

                String line = null;
                while ((line = br.readLine()) != null) {
                  System.out.println("[btrace err] " + line);
                  if (line.contains("Server VM warning")
                      || line.contains("XML libraries not available")
                      || line.contains("Connection reset")) {
                    // skip JVM generated warnings
                    continue;
                  }
                  if (line.startsWith("[traced app]") || line.startsWith("[btrace out]")) {
                    // skip test debug lines
                    continue;
                  }
                  stderr.append(line).append('\n');
                  if (line.contains("Exception") || line.contains("Error")) {
                    for (int i = 0; i < checkLines; i++) {
                      l.countDown();
                    }
                  }
                }
              } catch (Exception e) {
                for (int i = 0; i < checkLines; i++) {
                  l.countDown();
                }
                throw new Error(e);
              }
            },
            "Stderr Reader")
        .start();

    new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String line = null;
                while ((line = br.readLine()) != null) {
                  stdout.append(line).append('\n');
                  System.out.println("[btrace out] " + line);
                  if (!(debugBTrace && line.contains("DEBUG:"))) {
                    l.countDown();
                  }
                }
              } catch (Exception e) {
                for (int i = 0; i < checkLines; i++) {
                  l.countDown();
                }
                throw new Error(e);
              }
            },
            "Stdout Reader")
        .start();

    l.await(timeout, TimeUnit.MILLISECONDS);

    // Thread.sleep(100_000_000L);

    return p;
  }

  public File locateTrace(String trace) {
    Path start = projectRoot.resolve("src");
    AtomicReference<Path> tracePath = new AtomicReference<>();
    try {
      Files.walkFileTree(
          start,
          new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (file.toString().endsWith(trace)) {
                tracePath.set(file);
                return FileVisitResult.TERMINATE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
              return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      e.printStackTrace();
    }
    return tracePath.get() != null ? tracePath.get().toFile() : null;
  }

  private Process attach(
      String pid,
      String trace,
      String[] cmdArgs,
      int checkLines,
      StringBuilder stdout,
      StringBuilder stderr)
      throws Exception {
    File traceFile = locateTrace(trace);
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-Dcom.sun.btrace.unsafe=" + isUnsafe,
                "-Dcom.sun.btrace.debug=" + debugBTrace,
                "-Dcom.sun.btrace.trackRetransforms=" + trackRetransforms,
                "-cp",
                cp,
                "org.openjdk.btrace.client.Main",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString(),
                "-pd",
                traceFile.getParentFile().getAbsolutePath()));
    if (debugBTrace) {
      argVals.add("-v");
    }
    argVals.addAll(Arrays.asList(pid, traceFile.getAbsolutePath()));
    if (cmdArgs != null) {
      argVals.addAll(Arrays.asList(cmdArgs));
    }
    String testJavaHome = System.getenv("TEST_JAVA_HOME");
    if (testJavaHome == null) {
      testJavaHome = System.getenv("JAVA_HOME");
    }
    if (Files.exists(Paths.get(testJavaHome, "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList("--add-exports", "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"));
    }
    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    CountDownLatch l = new CountDownLatch(checkLines);

    new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));

                String line = null;
                while ((line = br.readLine()) != null) {
                  System.out.println("[btrace err] " + line);
                  if (line.contains("Server VM warning")
                      || line.contains("XML libraries not available")
                      || line.contains("Connection reset")) {
                    // skip JVM generated warnings
                    continue;
                  }
                  if (line.startsWith("[traced app]") || line.startsWith("[btrace out]")) {
                    // skip test debug lines
                    continue;
                  }
                  stderr.append(line).append('\n');
                  if (line.contains("Exception") || line.contains("Error")) {
                    for (int i = 0; i < checkLines; i++) {
                      l.countDown();
                    }
                  }
                }
              } catch (Exception e) {
                for (int i = 0; i < checkLines; i++) {
                  l.countDown();
                }
                throw new Error(e);
              }
            },
            "Stderr Reader")
        .start();

    new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String line = null;
                while ((line = br.readLine()) != null) {
                  stdout.append(line).append('\n');
                  System.out.println("[btrace out] " + line);
                  if (!(debugBTrace && line.contains("DEBUG"))) {
                    l.countDown();
                  }
                }
              } catch (Exception e) {
                for (int i = 0; i < checkLines; i++) {
                  l.countDown();
                }
                throw new Error(e);
              }
            },
            "Stdout Reader")
        .start();

    l.await(timeout, TimeUnit.MILLISECONDS);

    // Thread.sleep(100_000_000L);

    return p;
  }

  protected interface ResultValidator {
    void validate(String stdout, String stderr, int retcode, String jfrFile);
  }
}
