/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests;

import io.btrace.client.Client;
import io.btrace.core.comm.BinaryWireProtocol;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.JavaSerializationProtocol;
import io.btrace.core.comm.ListProbesCommand;
import io.btrace.core.comm.ProtocolConfig;
import io.btrace.core.comm.ProtocolNegotiator;
import io.btrace.core.comm.ProtocolVersion;
import io.btrace.core.comm.WireProtocol;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
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
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;

/**
 * @author Jaroslav Bachorik
 */
@SuppressWarnings("ConstantConditions")
public abstract class RuntimeTest {
  private static String cp = null;
  private static String targetAppCp = null;
  protected static String javaHome = null;
  private static String clientClassPath = null;
  private static String eventsClassPath = null;
  private static Path projectRoot = null;
  private static boolean forceDebug = false;
  private static String permissionsFile = null;
  private static long defaultTimeoutMs = 10000L;

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

  /** Disconnect after status OK (client -x) */
  protected boolean unattended = false;

  /** Delay before client attach (ms) */
  protected long attachDelayMs = 0;

  /** Dump generated oneliner source */
  protected boolean dumpOneliner = false;

  /** Dump verifier errors in target JVM */
  protected boolean dumpVerifierErrors = false;

  /** Override the BTrace agent/client port (0 = use default 2020) */
  protected int btracePort = 0;

  /** Provide extra JVM args */
  private static final List<String> extraJvmArgs = new ArrayList<>();

  protected boolean attachDebugger = false;

  public static void classSetup() {
    if (System.getProperty("btrace.comm.protocol") == null) {
      System.setProperty("btrace.comm.protocol", "2");
    }
    if (System.getProperty("btrace.comm.autoNegotiate") == null) {
      System.setProperty("btrace.comm.autoNegotiate", "false");
    }
    if (System.getProperty("btrace.comm.forceVersion") == null) {
      System.setProperty("btrace.comm.forceVersion", "true");
    }
    String forceDebugVal = System.getProperty("btrace.test.debug");
    if (forceDebugVal == null) {
      forceDebugVal = System.getenv("BTRACE_TEST_DEBUG");
    }
    forceDebug = Boolean.parseBoolean(forceDebugVal);
    Path libsPath = Paths.get(System.getProperty("btrace.libs"));
    projectRoot = Paths.get(System.getProperty("project.dir"));
    Path btraceJarPath = libsPath.resolve("btrace.jar");

    Assertions.assertTrue(
        Files.isRegularFile(btraceJarPath), "btrace.jar missing in libs directory");
    Path eventsJarPath = projectRoot.resolve("build/libs/events.jar");
    clientClassPath = btraceJarPath.toString();
    eventsClassPath = eventsJarPath.toString();

    cp =
        btraceJarPath
            + File.pathSeparator
            + projectRoot.resolve("build/classes/java/test")
            + File.pathSeparator
            + projectRoot.resolve("build/resources/test")
            + File.pathSeparator
            + eventsClassPath;

    // Target app classpath without btrace.jar - btrace is attached as agent
    targetAppCp =
        projectRoot.resolve("build/classes/java/test")
            + File.pathSeparator
            + projectRoot.resolve("build/resources/test")
            + File.pathSeparator
            + eventsClassPath;

    Assertions.assertNotNull(projectRoot);
    Assertions.assertNotNull(clientClassPath);

    String toolsjar = null;
    // Accept both TEST_JAVA_HOME (preferred) and JAVA_TEST_HOME as aliases
    // TEST_JAVA_HOME has the highest precedence
    javaHome = System.getenv("TEST_JAVA_HOME");
    if (javaHome == null) {
      javaHome = System.getenv("JAVA_TEST_HOME");
    }
    if (javaHome == null) {
      javaHome = System.getenv("JAVA_HOME");
    }
    if (javaHome == null) {
      javaHome = System.getProperty("java.home");
    }
    if (javaHome == null) {
      throw new IllegalStateException("Missing TEST_JAVA_HOME or JAVA_HOME env variables");
    }
    javaHome = javaHome.replace("/jre", "");

    Path toolsJarPath = Paths.get(javaHome, "lib", "tools.jar");
    if (Files.exists(toolsJarPath)) {
      toolsjar = toolsJarPath.toString();
    }
    cp = toolsjar != null ? cp + File.pathSeparator + toolsjar : cp;
    System.out.println("=== Using Java: " + javaHome + ", cp: " + cp);

    // Forward any btrace.* system properties to the traced app via JVM args
    // so the agent/client can pick them up (e.g., btrace.verify.transformed, debug flags).
    try {
      System.getProperties().stringPropertyNames().stream()
          .filter(n -> n.startsWith("btrace."))
          .forEach(n -> extraJvmArgs.add("-D" + n + "=" + System.getProperty(n)));
    } catch (Throwable ignore) {
      // best effort; if this fails, tests still run with defaults
    }

    // Tune default timeout for newer JDKs which may exhibit slower attach/instrument timing
    try {
      Properties release = new Properties();
      release.load(Files.newInputStream(Paths.get(javaHome, "release")));
      String ver = release.getProperty("JAVA_VERSION", "\"0\"").replace("\"", "");
      if (isVersionAtLeast(ver, 25)) {
        defaultTimeoutMs = 20000L;
      }
    } catch (Exception ignore) {
      // keep default
    }

    // Prepare permissions policy to allow privileged extensions for tests
    try {
      Path permsDir = projectRoot.resolve("build");
      Files.createDirectories(permsDir);
      Path perms = permsDir.resolve("permissions.properties");
      String content =
          "allowPrivileged=true\n"
              + "allowExtensions=btrace-metrics,btrace-utils,btrace-statsd,btrace-ext-test\n";
      Files.write(perms, content.getBytes(StandardCharsets.UTF_8));
      permissionsFile = perms.toAbsolutePath().toString();
    } catch (IOException ioe) {
      // best effort; leave permissionsFile null if we can't create it
      permissionsFile = null;
    }
  }

  protected void reset() {
    debugTestApp = false;
    debugBTrace = false;
    isUnsafe = false;
    unattended = false;
    attachDelayMs = 0;
    dumpOneliner = false;
    dumpVerifierErrors = false;
    btracePort = 0;
    timeout = defaultTimeoutMs;
  }

  private static boolean isVersionAtLeast(String version, int majorThreshold) {
    try {
      // Accept forms like "25", "25.0.1", or legacy "1.8.0_262"
      String v = version;
      if (v.startsWith("1.")) {
        v = v.substring(2); // e.g., 8.0_262 -> 8.0_262
      }
      int dot = v.indexOf('.') == -1 ? v.length() : v.indexOf('.');
      String majorStr = v.substring(0, dot);
      int major = Integer.parseInt(majorStr.replaceAll("[^0-9]", ""));
      return major >= majorThreshold;
    } catch (Throwable t) {
      return false;
    }
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
    testDynamic(testApp, testScript, cmdArgs, checkLines, v);
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
    testDynamic(testApp, testScript, cmdArgs, checkLines, v);
    testStartup(testApp, testScript.replace(".java", ".class"), cmdArgs, checkLines, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void testDynamic(String testApp, String testScript, int checkLines, ResultValidator v)
      throws Exception {
    testDynamic(testApp, testScript, null, checkLines, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void testDynamicOneliner(
      String testApp, String oneliner, int checkLines, ResultValidator v) throws Exception {
    testDynamicOneliner(testApp, oneliner, null, checkLines, v);
  }

  @SuppressWarnings("DefaultCharset")
  public void testDynamicOneliner(
      String testApp, String oneliner, String[] cmdArgs, int checkLines, ResultValidator v)
      throws Exception {
    System.out.println("=== Dynamic attach (oneliner)");
    if (forceDebug) {
      // force debug flags
      debugBTrace = true;
      debugTestApp = true;
    }
    String testJavaHome = System.getenv("TEST_JAVA_HOME");
    if (testJavaHome == null) {
      testJavaHome = System.getenv("JAVA_TEST_HOME");
    }
    testJavaHome = testJavaHome != null ? testJavaHome : System.getenv("JAVA_HOME");
    if (testJavaHome == null) {
      throw new IllegalStateException("Missing TEST_JAVA_HOME or JAVA_HOME env variables");
    }
    System.out.println("===> test java: " + testJavaHome);
    String jfrFile = null;
    List<String> args = new ArrayList<>(Arrays.asList(testJavaHome + "/bin/java", "-cp", cp));
    if (permissionsFile != null) {
      args.add("-Dbtrace.permissions=" + permissionsFile);
    }
    if (attachDebugger) {
      args.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
    }
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    args.add("-XX:+EnableDynamicAgentLoading");
    args.add("-XX:+UnlockDiagnosticVMOptions");
    args.add("-XX:-OmitStackTraceInFastThrow");
    if (dumpVerifierErrors) {
      args.add("-Dbtrace.verifier.dump=true");
    }
    args.addAll(extraJvmArgs);
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
                      || l.contains("XML libraries not available")
                      || l.contains("terminally deprecated method in sun.misc.Unsafe")
                      || l.contains("sun.misc.Unsafe::objectFieldOffset")
                      || l.contains("org.jctools.util.UnsafeAccess")
                      || l.contains("ASM verification requested for ")
                      || l.contains("ASM verification OK for ")) {
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
      if (attachDelayMs > 0) {
        try {
          Thread.sleep(attachDelayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }

      Process client = attachOneliner(pid, oneliner, cmdArgs, checkLines, stdout, stderr);

      System.out.println("Detached.");

      // Signal the target app to shut down
      pw.println("done");
      pw.flush();

      // Wait for the target process to exit gracefully
      if (!p.waitFor(10, TimeUnit.SECONDS)) {
        System.out.println("Target process did not exit in time, destroying.");
        p.destroyForcibly();
      }

      // Now wait for the client process (should exit quickly once target is gone)
      if (!client.waitFor(10, TimeUnit.SECONDS)) {
        System.out.println("Client process did not exit in time, destroying.");
        client.destroyForcibly();
      }

      ret.set(client.isAlive() ? -1 : client.exitValue());

      outT.join(5000);
      errT.join(5000);
    }

    // Allow a brief grace period for any final output to flush before validation
    try {
      Thread.sleep(500L);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    // If JFR was enabled for dynamic attach, give it a moment and dump the recording
    if (startJfr && pidStringRef.get() != null) {
      try {
        Thread.sleep(1500L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      try {
        ProcessBuilder jcmdPb;
        String jcmdExe =
            testJavaHome != null ? Paths.get(testJavaHome, "bin", "jcmd").toString() : "jcmd";
        if (jfrFile != null) {
          jcmdPb =
              new ProcessBuilder(
                  jcmdExe, pidStringRef.get(), "JFR.dump", "name=1", "filename=" + jfrFile);
        } else {
          jcmdPb = new ProcessBuilder(jcmdExe, pidStringRef.get(), "JFR.dump", "name=1");
        }
        jcmdPb.start().waitFor();
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
    }

    v.validate(stdout.toString(), stderr.toString(), ret.get(), jfrFile);
  }

  @SuppressWarnings("DefaultCharset")
  public void testDynamic(
      String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)
      throws Exception {
    System.out.println("=== Dynamic attach");
    if (forceDebug) {
      // force debug flags
      debugBTrace = true;
      debugTestApp = true;
    }
    String testJavaHome = System.getenv("TEST_JAVA_HOME");
    if (testJavaHome == null) {
      testJavaHome = System.getenv("JAVA_TEST_HOME");
    }
    testJavaHome = testJavaHome != null ? testJavaHome : System.getenv("JAVA_HOME");
    if (testJavaHome == null) {
      throw new IllegalStateException("Missing TEST_JAVA_HOME or JAVA_HOME env variables");
    }
    System.out.println("===> test java: " + testJavaHome);
    String jfrFile = null;
    List<String> args =
        new ArrayList<>(Arrays.asList(testJavaHome + "/bin/java", "-cp", targetAppCp));
    if (permissionsFile != null) {
      args.add("-Dbtrace.permissions=" + permissionsFile);
    }
    if (attachDebugger) {
      args.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
    }
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    args.add("-XX:+EnableDynamicAgentLoading");
    args.add("-XX:+UnlockDiagnosticVMOptions");
    args.add("-XX:-OmitStackTraceInFastThrow");
    //    args.add("-Xlog");

    // uncomment the following line to get extra JFR logs
    //    args.add("-Xlog:jfr*=trace");
    args.addAll(extraJvmArgs);
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
                      || l.contains("XML libraries not available")
                      || l.contains("terminally deprecated method in sun.misc.Unsafe")
                      || l.contains("sun.misc.Unsafe::objectFieldOffset")
                      || l.contains("org.jctools.util.UnsafeAccess")
                      || l.contains("ASM verification requested for ")
                      || l.contains("ASM verification OK for ")) {
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
      if (attachDelayMs > 0) {
        try {
          Thread.sleep(attachDelayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }

      Process client = attach(pid, testScript, cmdArgs, checkLines, stdout, stderr);

      System.out.println("Detached.");

      // Signal the target app to shut down
      pw.println("done");
      pw.flush();

      // Wait for the target process to exit gracefully
      if (!p.waitFor(10, TimeUnit.SECONDS)) {
        System.out.println("Target process did not exit in time, destroying.");
        p.destroyForcibly();
      }

      // Now wait for the client process (should exit quickly once target is gone)
      if (!client.waitFor(10, TimeUnit.SECONDS)) {
        System.out.println("Client process did not exit in time, destroying.");
        client.destroyForcibly();
      }

      ret.set(client.isAlive() ? -1 : client.exitValue());

      outT.join(5000);
      errT.join(5000);
    }

    // Allow a brief grace period for any final output to flush before validation
    try {
      Thread.sleep(500L);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    // If JFR was enabled for dynamic attach, give it a moment and dump the recording
    if (startJfr && pidStringRef.get() != null) {
      try {
        Thread.sleep(1500L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      try {
        ProcessBuilder jcmdPb;
        String jcmdExe =
            testJavaHome != null ? Paths.get(testJavaHome, "bin", "jcmd").toString() : "jcmd";
        if (jfrFile != null) {
          jcmdPb =
              new ProcessBuilder(
                  jcmdExe, pidStringRef.get(), "JFR.dump", "name=1", "filename=" + jfrFile);
        } else {
          jcmdPb = new ProcessBuilder(jcmdExe, pidStringRef.get(), "JFR.dump", "name=1");
        }
        jcmdPb.start().waitFor();
      } catch (Exception ignore) {
        // best effort
      }
    }

    v.validate(stdout.toString(), stderr.toString(), ret.get(), jfrFile);
  }

  public void testStartup(
      String testApp, String testScript, String[] cmdArgs, int checkLines, ResultValidator v)
      throws Exception {
    System.out.println("=== On-Startup");
    Path agentPath = locateAgent();
    if (agentPath == null) {
      throw new RuntimeException("Missing btrace.jar or btrace-agent.jar");
    }
    if (forceDebug) {
      // force debug flags
      debugBTrace = true;
      debugTestApp = true;
    }
    String testJavaHome = System.getenv("TEST_JAVA_HOME");
    if (testJavaHome == null) {
      testJavaHome = System.getenv("JAVA_TEST_HOME");
    }
    testJavaHome = testJavaHome != null ? testJavaHome : System.getenv("JAVA_HOME");
    if (testJavaHome == null) {
      throw new IllegalStateException("Missing TEST_JAVA_HOME or JAVA_HOME env variables");
    }
    String jfrFile = null;
    List<String> args =
        new ArrayList<>(Arrays.asList(testJavaHome + "/bin/java", "-cp", targetAppCp));
    if (permissionsFile != null) {
      args.add("-Dbtrace.permissions=" + permissionsFile);
    }
    if (attachDebugger) {
      args.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
    }
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    args.add("-XX:+UnlockDiagnosticVMOptions");
    //    args.add("-Xlog:exceptions");
    // uncomment the following line to get extra JFR logs
    //    args.add("-Xlog:jfr*=trace");
    args.addAll(extraJvmArgs);
    if (startJfr) {
      jfrFile = Files.createTempFile("btrace-", ".jfr").toString();
      args.add("-XX:StartFlightRecording=settings=default,dumponexit=true,filename=" + jfrFile);
    }

    String agentSetup =
        "-javaagent:"
            + agentPath
            + "=script="
            + locateTrace(testScript)
            + ",scriptOutputFile=::stdout";
    if (debugBTrace) {
      agentSetup += ",debug=true,dumpClasses=true,dumpDir=/tmp/btrace";
    }
    args.add(agentSetup);
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
    CountDownLatch stdoutLatch = new CountDownLatch(checkLines);
    AtomicReference<String> pidStringRef = new AtomicReference<>();

    Thread outT =
        new Thread(
            () -> {
              try {
                String l;
                while ((l = stdoutReader.readLine()) != null) {
                  stdout.append(l).append(System.lineSeparator());
                  if (l.startsWith("ready:")) {
                    pidStringRef.set(l.split("\\:")[1]);
                    testAppLatch.countDown();
                  } else {
                    stdoutLatch.countDown();
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
                  if (l.contains("SLF4J")
                      || l.contains("Server VM warning")
                      || l.contains("XML")
                      || l.contains("Successfully started BTrace probe")
                      || l.contains("terminally deprecated method in sun.misc.Unsafe")
                      || l.contains("sun.misc.Unsafe::objectFieldOffset")
                      || l.contains("org.jctools.util.UnsafeAccess")
                      || l.contains("ASM verification requested for ")
                      || l.contains("ASM verification OK for ")
                      || l.contains("A restricted method")
                      || l.contains("has been called by")
                      || l.contains("enable-native-access")
                      || l.contains("Restricted methods will be blocked")) {
                    continue;
                  }
                  stderr.append(l).append(System.lineSeparator());
                  if (l.contains("Server VM warning")
                      || l.contains("XML libraries not available")) {
                    continue;
                  }
                  if (debugTestApp) {
                    System.err.println("[traced app] " + l);
                  }
                  testAppLatch.countDown();
                  for (int i = 0; i < checkLines; i++) {
                    stdoutLatch.countDown();
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
    stdoutLatch.await();
    // Allow some time for late BTrace output to flush in on-startup mode.
    try {
      Thread.sleep(1000L);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    if (startJfr && pidStringRef.get() != null) {
      // Give the periodic event at least one interval to fire before dumping
      try {
        Thread.sleep(1500L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      // Dump the current recording into the configured file to ensure events are flushed
      ProcessBuilder jcmdPb;
      String jcmdExe =
          testJavaHome != null ? Paths.get(testJavaHome, "bin", "jcmd").toString() : "jcmd";
      if (jfrFile != null) {
        jcmdPb =
            new ProcessBuilder(
                jcmdExe, pidStringRef.get(), "JFR.dump", "name=1", "filename=" + jfrFile);
      } else {
        jcmdPb = new ProcessBuilder(jcmdExe, pidStringRef.get(), "JFR.dump", "name=1");
      }
      jcmdPb.start().waitFor();
    }

    v.validate(stdout.toString(), stderr.toString(), ret.get(), jfrFile);

    // Clean up the target process
    pw.println("done");
    pw.flush();
    if (!p.waitFor(10, TimeUnit.SECONDS)) {
      p.destroyForcibly();
    }
  }

  protected Path locateAgent() {
    Path start = projectRoot.resolve("../btrace-dist/build/resources/main");
    // [0] = masked btrace.jar, [1] = old btrace-agent.jar
    Path[] tracePath = new Path[2];
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
              String fileName = file.getFileName().toString();
              if (fileName.equals("btrace.jar")) {
                // Prefer the new masked btrace.jar
                tracePath[0] = file;
                return FileVisitResult.TERMINATE;
              }
              if (fileName.equals("btrace-agent.jar") && tracePath[1] == null) {
                // Fall back to old btrace-agent.jar
                tracePath[1] = file;
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
    // Prefer masked btrace.jar, fall back to btrace-agent.jar
    return tracePath[0] != null ? tracePath[0] : tracePath[1];
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
    String testJavaHome = System.getenv("TEST_JAVA_HOME");
    testJavaHome = testJavaHome != null ? testJavaHome : System.getenv("JAVA_HOME");
    if (testJavaHome == null) {
      throw new IllegalStateException("Missing TEST_JAVA_HOME or JAVA_HOME env variables");
    }
    String jfrFile = null;
    List<String> args = new ArrayList<>(Arrays.asList(testJavaHome + "/bin/java", "-cp", cp));
    if (attachDebugger) {
      args.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
    }
    args.add("-XX:+AllowRedefinitionToAddDeleteMethods");
    args.add("-XX:+IgnoreUnrecognizedVMOptions");
    // uncomment the following line to get extra JFR logs
    //    args.add("-Xlog:jfr*=trace");
    args.addAll(extraJvmArgs);
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
                "io.btrace.boot.Loader",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString()));
    if (debugBTrace) {
      int mainClassIdx = argVals.indexOf("io.btrace.boot.Loader");
      argVals.add(mainClassIdx + 1, "-v");
    }
    argVals.addAll(Arrays.asList(args));
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
      argVals.addAll(
          1,
          Arrays.asList(
              "--add-exports",
              "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
              "--add-modules",
              "jdk.attach",
              "--add-exports",
              "jdk.attach/sun.tools.attach=ALL-UNNAMED"));
    }

    ProcessBuilder pb = new ProcessBuilder(argVals);

    pb.environment().remove("JAVA_TOOL_OPTIONS");
    Process p = pb.start();

    AtomicBoolean done = new AtomicBoolean(false);

    Thread stderrThread =
        new Thread(
            () -> {
              try {
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));

                int lineno = 0;
                String line = null;
                boolean callbackActive = true;
                while ((line = br.readLine()) != null) {
                  System.out.println("[btrace err] " + line);
                  if (line.contains("Server VM warning")
                      || line.contains("XML libraries not available")
                      || line.contains("Successfully started BTrace probe")
                      || line.contains("Connection reset")) {
                    // skip JVM generated warnings
                    continue;
                  }
                  if (line.startsWith("[traced app]") || line.startsWith("[btrace out]")) {
                    // skip test debug lines
                    continue;
                  }
                  if (callbackActive && !outputProcessor.onStderr(++lineno, line)) {
                    callbackActive = false;
                    // Continue draining output to prevent buffer fill-up and deadlock,
                    // but stop calling the callback
                    done.set(true);
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
                boolean callbackActive = true;
                while ((line = br.readLine()) != null) {
                  if (callbackActive && !outputProcessor.onStdout(lineno, line)) {
                    callbackActive = false;
                    // Continue draining output to prevent buffer fill-up and deadlock,
                    // but stop calling the callback
                    done.set(true);
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

    // Wait adaptively: if callbacks indicate completion (done=true), shorten wait and terminate
    long deadline = System.currentTimeMillis() + 30000; // 30s max
    while ((stderrThread.isAlive() || stdoutThread.isAlive())
        && System.currentTimeMillis() < deadline) {
      if (done.get()) {
        // Give the client a brief grace period to exit on its own
        try {
          Thread.sleep(200);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
        if (p.isAlive()) {
          p.destroy();
          try {
            Thread.sleep(200);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
          if (p.isAlive()) {
            p.destroyForcibly();
          }
        }
        // After short-circuiting, only wait up to 1s more for threads to drain
        deadline = System.currentTimeMillis() + 1000;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }

    // If threads are still alive, process likely hung - destroy it
    if (stderrThread.isAlive() || stdoutThread.isAlive()) {
      System.err.println(
          "WARNING: BTrace process output threads still alive after timeout, destroying process");
      p.destroyForcibly();
      // Give threads a moment to notice process died
      stderrThread.join(1000);
      stdoutThread.join(1000);
    }
  }

  public Process runBTrace(
      String[] args, int checkLines, StringBuilder stdout, StringBuilder stderr) throws Exception {
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-cp",
                cp,
                "io.btrace.boot.Loader",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString()));
    if (debugBTrace) {
      int mainClassIdx = argVals.indexOf("io.btrace.boot.Loader");
      argVals.add(mainClassIdx + 1, "-v");
    }
    argVals.addAll(Arrays.asList(args));
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
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
    //    Path start = projectRoot.resolve("src");
    List<Path> roots = new ArrayList<>();
    if (trace.toLowerCase().endsWith(".java")) {
      roots.add(projectRoot.resolve("src"));
    } else {
      roots.add(projectRoot.resolve("../btrace-instr/build/classes"));
      roots.add(projectRoot.resolve("build/classes"));
    }
    Path[] tracePath = new Path[1];
    for (Path start : roots) {
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
                  tracePath[0] = file;
                  return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc)
                  throws IOException {
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
      if (tracePath[0] != null) {
        return tracePath[0].toFile();
      }
    }
    return null;
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
                "-Dbtrace.comm.protocol=2",
                "-Dbtrace.comm.autoNegotiate=false",
                "-Dbtrace.comm.forceVersion=true",
                "-Dbtrace.port=" + getBTracePort(),
                "-Dbtrace.libs=" + System.getProperty("btrace.libs"),
                "-cp",
                cp,
                "io.btrace.boot.Loader",
                "-p",
                String.valueOf(getBTracePort()),
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString(),
                "-pd",
                traceFile.getParentFile().getAbsolutePath()));
    if (debugBTrace) {
      argVals.add("-v");
    }
    if (dumpOneliner) {
      int cpIdx = argVals.indexOf("-cp");
      if (cpIdx > -1) {
        argVals.add(cpIdx, "-Dbtrace.oneliner.dump=true");
      }
    }
    if (unattended) {
      argVals.add("-x");
    }
    argVals.addAll(Arrays.asList(pid, traceFile.getAbsolutePath()));
    if (cmdArgs != null) {
      argVals.addAll(Arrays.asList(cmdArgs));
    }
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
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

  private Process attachOneliner(
      String pid,
      String oneliner,
      String[] cmdArgs,
      int checkLines,
      StringBuilder stdout,
      StringBuilder stderr)
      throws Exception {
    List<String> argVals =
        new ArrayList<>(
            Arrays.asList(
                javaHome + "/bin/java",
                "-Dcom.sun.btrace.unsafe=" + isUnsafe,
                "-Dcom.sun.btrace.debug=" + debugBTrace,
                "-Dcom.sun.btrace.trackRetransforms=" + trackRetransforms,
                "-Dbtrace.comm.protocol=2",
                "-Dbtrace.comm.autoNegotiate=false",
                "-Dbtrace.comm.forceVersion=true",
                "-cp",
                cp,
                "io.btrace.boot.Loader",
                "-cp",
                eventsClassPath,
                "-d",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-test").toString(),
                "-n",
                oneliner,
                "-pd",
                Paths.get(System.getProperty("java.io.tmpdir"), "btrace-oneliner").toString()));
    if (debugBTrace) {
      argVals.add("-v");
    }
    if (unattended) {
      argVals.add("-x");
    }
    if (btracePort > 0) {
      argVals.add("-p");
      argVals.add(String.valueOf(btracePort));
    }
    argVals.addAll(Arrays.asList(pid));
    if (cmdArgs != null) {
      argVals.addAll(Arrays.asList(cmdArgs));
    }
    if (Files.exists(Paths.get(javaHome, "jmods"))) {
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
                      || line.contains("Successfully started BTrace probe")
                      || line.contains("Connection reset")
                      || line.contains("terminally deprecated method in sun.misc.Unsafe")
                      || line.contains("sun.misc.Unsafe::objectFieldOffset")
                      || line.contains("org.jctools.util.UnsafeAccess")
                      || line.contains("A restricted method")) {
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

    return p;
  }

  protected int getBTracePort() {
    return btracePort > 0 ? btracePort : Integer.getInteger("btrace.port", 2020);
  }

  protected String getEventsClassPath() {
    return eventsClassPath;
  }

  protected Client createClientForTests(String probeDescPath) {
    String libs = System.getProperty("btrace.libs");
    String agentJar = null;

    if (libs != null) {
      Path maskedJar = Paths.get(libs, "btrace.jar");
      if (Files.exists(maskedJar)) {
        agentJar = maskedJar.toString();
      }
    }

    return new Client(
        getBTracePort(),
        null,
        probeDescPath,
        debugBTrace,
        trackRetransforms,
        false,
        false,
        null,
        null,
        agentJar);
  }

  protected List<String> listProbesWithProtocol(String host) throws IOException {
    int port = getBTracePort();
    try (Socket socket = new Socket(host, port);
        WireProtocol protocol = createClientProtocol(socket, host)) {
      ProtocolConfig config = ProtocolConfig.fromSystemProperties();
      if (config.isForceVersion()
          && config.getVersion() == ProtocolVersion.V2
          && !(protocol instanceof BinaryWireProtocol)) {
        throw new IOException("Expected V2 protocol but got: " + protocol.getClass().getName());
      }
      protocol.write(new ListProbesCommand());
      Command cmd = protocol.read();
      if (cmd instanceof ListProbesCommand) {
        return ((ListProbesCommand) cmd).getProbes();
      }
      return Collections.emptyList();
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }

  private WireProtocol createClientProtocol(Socket socket, String host) throws IOException {
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();
    ProtocolVersion preferred = config.getVersion();

    if (config.isAutoNegotiate() && preferred == ProtocolVersion.V2) {
      try {
        return createV2Protocol(socket);
      } catch (IOException e) {
        closeSocketQuietly(socket);
        Socket fallback = new Socket(host, getBTracePort());
        return createV1Protocol(fallback);
      }
    }

    if (config.isForceVersion() && preferred == ProtocolVersion.V2) {
      return createV2Protocol(socket);
    }

    return createV1Protocol(socket);
  }

  private WireProtocol createV1Protocol(Socket socket) throws IOException {
    InputStream in = socket.getInputStream();
    OutputStream out = socket.getOutputStream();
    return new JavaSerializationProtocol(in, out);
  }

  private WireProtocol createV2Protocol(Socket socket) throws IOException {
    InputStream in = socket.getInputStream();
    OutputStream out = socket.getOutputStream();
    ProtocolNegotiator negotiator = new ProtocolNegotiator(ProtocolVersion.V2);
    int timeoutMs = ProtocolNegotiator.getNegotiationTimeoutMs();
    int previousTimeout = socket.getSoTimeout();
    try {
      socket.setSoTimeout(timeoutMs);
      ProtocolVersion negotiated = negotiator.negotiateClient(in, out, ProtocolVersion.V2);
      if (negotiated != ProtocolVersion.V2) {
        throw new IOException("Protocol negotiation failed: expected V2");
      }
      return new BinaryWireProtocol(in, out);
    } finally {
      socket.setSoTimeout(previousTimeout);
    }
  }

  private void closeSocketQuietly(Socket socket) {
    if (socket == null) {
      return;
    }
    try {
      socket.close();
    } catch (IOException ignore) {
      // best effort
    }
  }

  public interface ResultValidator {
    void validate(String stdout, String stderr, int retcode, String jfrFile);
  }
}
