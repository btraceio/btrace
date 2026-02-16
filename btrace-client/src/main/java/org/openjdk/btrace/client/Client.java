/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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
package org.openjdk.btrace.client;

import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.openjdk.btrace.boot.MaskedClassLoader;
import org.openjdk.btrace.boot.MaskedJarUtils;
import org.openjdk.btrace.compiler.Compiler;
import org.openjdk.btrace.core.Args;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.annotations.DTrace;
import org.openjdk.btrace.core.annotations.DTraceRef;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.DisconnectCommand;
import org.openjdk.btrace.core.comm.EventCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.BinaryWireProtocol;
import org.openjdk.btrace.core.comm.JavaSerializationProtocol;
import org.openjdk.btrace.core.comm.ListFailedExtensionsCommand;
import org.openjdk.btrace.core.comm.ListProbesCommand;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.openjdk.btrace.core.comm.ReconnectCommand;
import org.openjdk.btrace.core.comm.SetSettingsCommand;
import org.openjdk.btrace.core.comm.StatusCommand;
import org.openjdk.btrace.core.comm.ProtocolConfig;
import org.openjdk.btrace.core.comm.ProtocolNegotiator;
import org.openjdk.btrace.core.comm.ProtocolVersion;
import org.openjdk.btrace.core.comm.WireProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a BTrace client. This can be used to create command line as well as a GUI
 * based BTrace clients. The BTrace compilation, traced JVM attach, submission of compiled program
 * and and I/O to the traced JVM are handled by this class.
 *
 * @author A. Sundararajan
 */
public class Client {
  private static final Logger log = LoggerFactory.getLogger(Client.class);

  private static final String DTRACE_DESC;
  private static final String DTRACE_REF_DESC;
  private static boolean dtraceEnabled;
  private static Method submitFile;
  private static Method submitString;

  static {
    try {
      /*
       * Check for DTrace Consumer class -- if we don't have that
       * either /usr/lib/share/java/dtrace.jar is not in CLASSPATH
       * or we are not running on Solaris 11+.
       */
      Class<?> dtraceConsumerClass = Class.forName("org.opensolaris.os.dtrace.Consumer");
      /*
       * Check for BTrace's DTrace support class -- if that is available
       * may be the user didn't build BTrace on Solaris 11. S/he built
       * it on Solaris 10 or below or some other OS.
       */
      Class<?> dtraceClass = Class.forName("com.sun.btrace.dtrace.DTrace");
      dtraceEnabled = true;
      submitFile =
          dtraceClass.getMethod("submit", File.class, String[].class, CommandListener.class);
      submitString =
          dtraceClass.getMethod("submit", String.class, String[].class, CommandListener.class);
    } catch (Exception exp) {
      dtraceEnabled = false;
    }
    DTRACE_DESC = Type.getDescriptor(DTrace.class);
    DTRACE_REF_DESC = Type.getDescriptor(DTraceRef.class);
  }

  // port on which BTrace agent listens
  private final int port;
  // the output file or null
  private final String outputFile;
  // are we running debug mode?
  private final boolean debug;
  // do we need to track retransforming single classes? (will impose additional overhead)
  private final boolean trackRetransforms;
  // are we running in trusted mode?
  private final boolean trusted;
  // are we dumping .class files of
  // the instrumented classes?
  private final boolean dumpClasses;
  // which directory we dump the .class files?
  private final String dumpDir;
  private final String probeDescPath;
  private final String statsdDef;
  // override paths for agent and boot JARs (for jbang support)
  private final String agentJarOverride;
  private final String bootJarOverride;

  // connection state to the traced JVM
  private volatile Socket sock;
  private volatile WireProtocol protocol;

  private boolean disconnected = false;

  public Client(int port) {
    this(port, null, ".", false, false, false, false, null, null, null, null);
  }

  public Client(int port, String probeDescPath) {
    this(port, null, probeDescPath, false, false, false, false, null, null, null, null);
  }

  public Client(
      int port,
      String outputFile,
      String probeDescPath,
      boolean debug,
      boolean trackRetransforms,
      boolean trusted,
      boolean dumpClasses,
      String dumpDir,
      String statsdDef) {
    this(
        port,
        outputFile,
        probeDescPath,
        debug,
        trackRetransforms,
        trusted,
        dumpClasses,
        dumpDir,
        statsdDef,
        null,
        null);
  }

  public Client(
      int port,
      String outputFile,
      String probeDescPath,
      boolean debug,
      boolean trackRetransforms,
      boolean trusted,
      boolean dumpClasses,
      String dumpDir,
      String statsdDef,
      String agentJarOverride,
      String bootJarOverride) {
    this.port = port;
    this.outputFile = outputFile;
    this.probeDescPath = probeDescPath;
    this.debug = debug;
    this.trusted = trusted;
    this.dumpClasses = dumpClasses;
    this.dumpDir = dumpDir;
    this.trackRetransforms = trackRetransforms;
    this.statsdDef = statsdDef;
    this.agentJarOverride = agentJarOverride;
    this.bootJarOverride = bootJarOverride;
  }

  private static boolean isPortAvailable(int port) {
    Socket clSocket = null;
    try {
      clSocket = new Socket("127.0.0.1", port);
    } catch (IOException ignored) {
      // ignore
    }
    if (clSocket != null) {
      try {
        clSocket.close();
      } catch (IOException ignored) {
        // ignore
      }
      return false;
    }
    return true;
  }

  private boolean isAgentAvailableAfterLoadFailure(VirtualMachine vm) {
    try {
      Properties serverVmProps = vm.getSystemProperties();
      String serverPort = serverVmProps.getProperty("btrace.port");
      if (serverPort != null) {
        return Integer.parseInt(serverPort) == port;
      }
    } catch (Exception ignore) {
      // fall through to port probe
    }
    return !isPortAvailable(port);
  }

  @SuppressWarnings("DefaultCharset")
  public byte[] compile(String fileName, String classPath) {
    return compile(fileName, classPath, new PrintWriter(System.err), null);
  }

  /** Compiles given BTrace program using given classpath. */
  @SuppressWarnings("DefaultCharset")
  public byte[] compile(String fileName, String classPath, String includePath) {
    return compile(fileName, classPath, new PrintWriter(System.err), includePath);
  }

  public byte[] compile(String fileName, String classPath, PrintWriter err) {
    return compile(fileName, classPath, err, null);
  }

  /**
   * Scans the extensions directory and returns a classpath string with all API JARs.
   *
   * Looks in the following locations (first match wins):
   * - System property 'btrace.libs' (assumed to point to BTrace libs directory); scans sibling 'extensions/'
   * - Derived from java.class.path entry containing 'btrace-client' (for dist layouts); scans sibling 'extensions/'
   */
  private String getExtensionApiClasspath() {
    try {
      // 1) Use -Dbtrace.libs if provided
      String libsProp = System.getProperty("btrace.libs");
      if (libsProp != null && !libsProp.isEmpty()) {
        File libsDir = new File(libsProp);
        File btraceHome = libsDir.getParentFile();
        String cp = scanExtensionsDir(new File(btraceHome, "extensions"));
        if (!cp.isEmpty()) {
          return cp;
        }
      }

      // 2) Fall back to locating btrace-client in the classpath
      String classPath = System.getProperty("java.class.path");
      for (String entry : classPath.split(File.pathSeparator)) {
        if (entry.contains("btrace-client")) {
          File clientPath = new File(entry);
          File libsDir = clientPath.getParentFile();
          if (libsDir != null) {
            File btraceHome = libsDir.getParentFile();
            String cp = scanExtensionsDir(new File(btraceHome, "extensions"));
            if (!cp.isEmpty()) {
              return cp;
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to scan extensions directory", e);
    }
    return "";
  }

  /**
   * Builds a classpath string of all *-api.jar files in immediate subdirectories of the given
   * extensions directory.
   */
  private static String scanExtensionsDir(File extensionsDir) {
    if (extensionsDir == null || !extensionsDir.exists() || !extensionsDir.isDirectory()) {
      return "";
    }
    StringBuilder cp = new StringBuilder();
    File[] extensionDirs = extensionsDir.listFiles(File::isDirectory);
    if (extensionDirs != null) {
      for (File extDir : extensionDirs) {
        File[] apiJars = extDir.listFiles((dir, name) -> name.endsWith("-api.jar"));
        if (apiJars != null) {
          for (File jar : apiJars) {
            if (cp.length() > 0) {
              cp.append(File.pathSeparator);
            }
            cp.append(jar.getAbsolutePath());
          }
        }
      }
    }
    return cp.toString();
  }

  private WireProtocol createProtocol(Socket socket, String host, boolean allowFallback)
      throws IOException {
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();
    ProtocolVersion preferred = config.getVersion();

    if (config.isAutoNegotiate() && preferred == ProtocolVersion.V2) {
      try {
        return createV2Protocol(socket);
      } catch (IOException e) {
        if (!allowFallback) {
          throw e;
        }
        closeSocketQuietly(socket);
        Socket fallback = new Socket(host, port);
        this.sock = fallback;
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
    }
  }

  /**
   * Compiles given BTrace program using given classpath. Errors and warning are written to given
   * PrintWriter.
   */
  public byte[] compile(String fileName, String classPath, PrintWriter err, String includePath) {
    File file = new File(fileName);
    if (fileName.endsWith(".java")) {
      Compiler compiler = new Compiler(includePath);
      classPath += File.pathSeparator + System.getProperty("java.class.path");
      // Add extension API JARs to classpath
      String extApiCp = getExtensionApiClasspath();
      if (!extApiCp.isEmpty()) {
        classPath += File.pathSeparator + extApiCp;
      }
      if (log.isDebugEnabled()) {
        log.debug("compiling {}", fileName);
        if (!extApiCp.isEmpty()) {
          log.debug("extension API classpath: {}", extApiCp);
        }
        log.debug("compiler classpath: {}", classPath);
      }

      // Ensure the verifier's classloader can see the same classpath (TCCL lookup)
      ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
      String prevSysCp = System.getProperty("java.class.path", "");
      Map<String, byte[]> classes;
      try {
        String[] cpEntries = classPath.split(File.pathSeparator);

        // Check if any entry is a masked JAR (contains META-INF/btrace/shared/)
        File maskedJar = MaskedJarUtils.findMaskedJarInClasspath(cpEntries);
        ClassLoader compileCl;

        if (maskedJar != null) {
          // Use MaskedClassLoader to load classes from .classdata files
          log.debug("Using MaskedClassLoader for compilation: {}", maskedJar.getAbsolutePath());
          compileCl = new MaskedClassLoader(maskedJar, "client", prevCl);
        } else {
          // Fall back to URLClassLoader for standard JARs
          java.net.URL[] urls = new java.net.URL[cpEntries.length];
          for (int i = 0; i < cpEntries.length; i++) {
            urls[i] = new File(cpEntries[i]).toURI().toURL();
          }
          compileCl = new java.net.URLClassLoader(urls, prevCl);
        }

        Thread.currentThread().setContextClassLoader(compileCl);
        // Ensure VerifierVisitor fallback scan (java.class.path) can see extension API jars
        if (!extApiCp.isEmpty()) {
          System.setProperty(
              "java.class.path",
              prevSysCp.isEmpty() ? extApiCp : (prevSysCp + File.pathSeparator + extApiCp));
        }
        classes = compiler.compile(file, err, ".", classPath);
      } catch (Exception e) {
        log.error("Failed to set up compiler classloader", e);
        classes = compiler.compile(file, err, ".", classPath);
      } finally {
        Thread.currentThread().setContextClassLoader(prevCl);
        System.setProperty("java.class.path", prevSysCp);
      }
      if (classes == null) {
        log.error("btrace compilation for script {} failed!", fileName);
        return null;
      }

      int size = classes.size();
      if (size != 1) {
        log.error("no classes or more than one class in script {}", fileName);
        return null;
      }
      String name = classes.keySet().iterator().next();
      code = classes.get(name);
      if (log.isDebugEnabled()) {
        log.debug("compiled {}", fileName);
      }
    } else if (fileName.endsWith(".class")) {
      int codeLen = (int) file.length();
      byte[] code = new byte[codeLen];
      try {
        if (log.isDebugEnabled()) {
          log.debug("reading {}", fileName);
        }
        try (FileInputStream fis = new FileInputStream(file)) {
          int off = 0;
          int len = 0;
          do {
            len = fis.read(code, off, codeLen - off);
            if (len > -1) {
              off += len;
            }
          } while (off < codeLen && len != -1);
        }
        if (log.isDebugEnabled()) {
          log.debug("read {}", fileName);
        }
        return code;
      } catch (IOException exp) {
        err.println(exp.getMessage());
        return null;
      }
    } else {
      err.println("BTrace script has to be a .java or a .class");
      return null;
    }
  }

  public byte[] compileSource(String fileName, String source, String classPath) {
    return compileSource(fileName, source, classPath, new PrintWriter(System.err), null);
  }

  public byte[] compileSource(
      String fileName, String source, String classPath, PrintWriter err, String includePath) {
    return compileInternal(
        fileName,
        classPath,
        err,
        includePath,
        (compiler, resolvedCp) -> compiler.compile(fileName, source, err, ".", resolvedCp));
  }

  @FunctionalInterface
  private interface CompilerInvoker {
    Map<String, byte[]> compile(Compiler compiler, String classPath) throws Exception;
  }

  private byte[] compileInternal(
      String scriptName,
      String classPath,
      PrintWriter err,
      String includePath,
      CompilerInvoker invoker) {
    Compiler compiler = new Compiler(includePath);
    classPath += File.pathSeparator + System.getProperty("java.class.path");
    // Add extension API JARs to classpath
    String extApiCp = getExtensionApiClasspath();
    if (!extApiCp.isEmpty()) {
      classPath += File.pathSeparator + extApiCp;
    }
    if (log.isDebugEnabled()) {
      log.debug("compiling {}", scriptName);
      if (!extApiCp.isEmpty()) {
        log.debug("extension API classpath: {}", extApiCp);
      }
      log.debug("compiler classpath: {}", classPath);
    }

    // Ensure the verifier's classloader can see the same classpath (TCCL lookup)
    ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
    String prevSysCp = System.getProperty("java.class.path", "");
    Map<String, byte[]> classes;
    try {
      String[] cpEntries = classPath.split(File.pathSeparator);
      URL[] urls = new URL[cpEntries.length];
      for (int i = 0; i < cpEntries.length; i++) {
        urls[i] = new File(cpEntries[i]).toURI().toURL();
      }
      URLClassLoader compileCl = new URLClassLoader(urls, prevCl);
      Thread.currentThread().setContextClassLoader(compileCl);
      // Ensure VerifierVisitor fallback scan (java.class.path) can see extension API jars
      if (!extApiCp.isEmpty()) {
        System.setProperty(
            "java.class.path",
            prevSysCp.isEmpty() ? extApiCp : (prevSysCp + File.pathSeparator + extApiCp));
      }
      classes = invoker.compile(compiler, classPath);
    } catch (Exception e) {
      log.error("Failed to set up compiler classloader", e);
      try {
        classes = invoker.compile(compiler, classPath);
      } catch (Exception ex) {
        log.error("btrace compilation for script {} failed!", scriptName, ex);
        return null;
      }
    } finally {
      Thread.currentThread().setContextClassLoader(prevCl);
      System.setProperty("java.class.path", prevSysCp);
    }
    if (classes == null) {
      log.error("btrace compilation for script {} failed!", scriptName);
      return null;
    }

    int size = classes.size();
    if (size != 1) {
      log.error("no classes or more than one class in script {}", scriptName);
      return null;
    }
    String name = classes.keySet().iterator().next();
    byte[] code = classes.get(name);
    if (log.isDebugEnabled()) {
      log.debug("compiled {}", scriptName);
    }
    return code;
  }

  /**
   * Attach the BTrace client to the given Java process. Loads BTrace agent on the target process if
   * not loaded already.
   */
  public void attach(String pid, String sysCp, String bootCp) throws IOException {
    try {
      String agentPath;
      boolean isMaskedJar = false;

      // If --agent-jar was specified, use it
      if (agentJarOverride != null && !agentJarOverride.isEmpty()) {
        File agentFile = new File(agentJarOverride);
        if (!agentFile.exists()) {
          throw new IOException("Specified agent JAR does not exist: " + agentJarOverride);
        }
        agentPath = agentFile.getAbsolutePath();
        isMaskedJar = isMaskedJar(agentFile);
      } else {
        // Find masked btrace.jar in classpath
        agentPath = findMaskedAgentJar();
        if (agentPath == null) {
          throw new IOException("No masked btrace.jar found in classpath. " +
              "Please ensure btrace.jar (not btrace-client.jar) is in the classpath.");
        }
        // Verify it exists and is readable
        File agentFile = new File(agentPath);
        if (!agentFile.exists() || !agentFile.canRead()) {
          throw new IOException("Agent JAR not found or not readable: " + agentPath);
        }
        agentPath = agentFile.getAbsolutePath();  // Normalize to absolute path
        isMaskedJar = true;  // findMaskedAgentJar only returns masked JARs
      }

      // Handle boot classpath for masked JAR
      String effectiveBootCp = bootCp;

      // For masked JAR, use the agent JAR itself as boot classpath (it has bootstrap classes as .class files)
      if (isMaskedJar) {
        if (bootCp == null || bootCp.isEmpty() || bootCp.equals(".")) {
          effectiveBootCp = agentPath;
        } else {
          // If bootCp is set to something else, prepend the masked JAR to it
          effectiveBootCp = agentPath + File.pathSeparator + bootCp;
        }
      }

      attach(pid, agentPath, sysCp, effectiveBootCp);
    } catch (RuntimeException | IOException e) {
      throw e;
    } catch (Exception exp) {
      throw new IOException("Failed to attach to PID " + pid, exp);
    }
  }

  /**
   * Attach the BTrace client to the given Java process. Loads BTrace agent on the target process if
   * not loaded already. Accepts the full path of the btrace agent jar. Also, accepts system
   * classpath and boot classpath optionally.
   */
  public void attach(String pid, String agentPath, String sysCp, String bootCp) throws IOException {
    try {
      VirtualMachine vm = null;
      if (log.isDebugEnabled()) {
        log.debug("attaching to {}", pid);
      }
      vm = VirtualMachine.attach(pid);
      if (log.isDebugEnabled()) {
        log.debug("checking port availability: {}", port);
      }
      Properties serverVmProps = vm.getSystemProperties();
      int serverPort = Integer.parseInt(serverVmProps.getProperty("btrace.port", "-1"));
      if (serverPort != -1) {
        if (serverPort != port) {
          throw new IOException(
              "Can not attach to PID "
                  + pid
                  + " on port "
                  + port
                  + ". There is already a BTrace server active on port "
                  + serverPort
                  + "!");
        }
      } else {
        if (!isPortAvailable(port)) {
          throw new IOException("Port " + port + " unavailable.");
        }
      }

      if (log.isDebugEnabled()) {
        log.debug("attached to {}", pid);
        log.debug("loading {}", agentPath);
      }
      String agentArgs = Args.PORT + "=" + port;
      if (statsdDef != null) {
        agentArgs += "," + Args.STATSD + "=" + statsdDef;
      }
      if (debug) {
        agentArgs += "," + Args.DEBUG + "=true";
      }
      if (trusted) {
        agentArgs += "," + Args.TRUSTED + "=true";
      }
      if (dumpClasses) {
        agentArgs += "," + Args.DUMP_CLASSES + "=true";
        agentArgs += "," + Args.DUMP_DIR + "=" + dumpDir;
      }
      if (trackRetransforms) {
        agentArgs += "," + Args.TRACK_RETRANSFORMS + "=true";
      }
      if (bootCp != null) {
        agentArgs += "," + Args.BOOT_CLASS_PATH + "=" + bootCp;
      }
      String toolsPath =
          getToolsJarPath(
              serverVmProps.getProperty("java.class.path"), serverVmProps.getProperty("java.home"));
      if (sysCp == null) {
        sysCp = toolsPath;
      } else {
        sysCp = sysCp + File.pathSeparator + toolsPath;
      }
      agentArgs += "," + Args.SYSTEM_CLASS_PATH + "=" + sysCp;
      String cmdQueueLimit = System.getProperty(BTraceRuntime.CMD_QUEUE_LIMIT_KEY, null);
      if (cmdQueueLimit != null) {
        agentArgs += ",=" + Args.CMD_QUEUE_LIMIT + cmdQueueLimit;
      }
      agentArgs += "," + Args.PROBE_DESC_PATH + "=" + probeDescPath;
      if (log.isDebugEnabled()) {
        log.debug("agent args: {}", agentArgs);
      }
      boolean isJava8 = serverVmProps.getProperty("java.version", "").startsWith("1.8");
      // HotSpot on JDK 8 can report AgentLoadException("0") even when the agent loads successfully.
      // Use a small retry window and treat error code 0 as success only if the agent is reachable.
      int attempts = isJava8 ? 3 : 1;
      AgentLoadException lastAle = null;
      for (int i = 0; i < attempts; i++) {
        try {
          vm.loadAgent(agentPath, agentArgs);
          lastAle = null;
          break;
        } catch (AgentLoadException ale) {
          lastAle = ale;
          if ("0".equals(ale.getMessage()) && isJava8) {
            if (isAgentAvailableAfterLoadFailure(vm)) {
              if (log.isDebugEnabled()) {
                log.debug("Agent load reported error 0 but agent is available on port {}", port);
              }
              lastAle = null;
              break;
            }
            try {
              Thread.sleep(100);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              break;
            }
            continue;
          }
          throw ale;
        }
      }
      if (lastAle != null) {
        throw lastAle;
      }
      if (log.isDebugEnabled()) {
        log.debug("loaded {}", agentPath);
      }
    } catch (RuntimeException | IOException re) {
      System.err.println("[DEBUG] IOException/RuntimeException during attach:");
      re.printStackTrace();
      throw re;
    } catch (Exception exp) {
      System.err.println("[DEBUG] Exception during attach:");
      exp.printStackTrace();
      throw new IOException("Failed to attach to PID " + pid, exp);
    }
  }

  void connectAndListProbes(String host, CommandListener listener) throws IOException {
    if (sock != null) {
      throw new IllegalStateException();
    }
    try {
      if (log.isDebugEnabled()) {
        log.debug("opening socket to {}", port);
      }
      long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
      while (sock == null && System.nanoTime() <= timeout) {
        try {
          sock = new Socket(host, port);
        } catch (ConnectException e) {
          log.debug("server not yet available; retrying ...");
          Thread.sleep(20);
        }
      }

      if (sock == null) {
        log.debug("server not available. exiting.");
        System.exit(1);
      }
      protocol = createProtocol(sock, host, true);
      protocol.write(new ListProbesCommand());

      log.debug("entering into command loop");
      commandLoop(
          cmd -> {
            if (cmd.getType() == Command.LIST_PROBES) {
              listener.onCommand(cmd);
              System.exit(0);
            } else {
              listener.onCommand(cmd);
            }
          });
    } catch (UnknownHostException uhe) {
      throw new IOException(uhe);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  void connectAndListFailedExtensions(String host, CommandListener listener) throws IOException {
    if (sock != null) {
      throw new IllegalStateException();
    }
    try {
      if (log.isDebugEnabled()) {
        log.debug("opening socket to {}", port);
      }
      long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
      while (sock == null && System.nanoTime() <= timeout) {
        try {
          sock = new Socket(host, port);
        } catch (ConnectException e) {
          log.debug("server not yet available; retrying ...");
          Thread.sleep(20);
        }
      }

      if (sock == null) {
        log.debug("server not available. exiting.");
        System.exit(1);
      }
      protocol = createProtocol(sock, host, true);
      protocol.write(new ListFailedExtensionsCommand());

      log.debug("entering into command loop");
      commandLoop(
          cmd -> {
            if (cmd.getType() == Command.LIST_FAILED_EXTENSIONS) {
              listener.onCommand(cmd);
              System.exit(0);
            } else {
              listener.onCommand(cmd);
            }
          });
    } catch (UnknownHostException uhe) {
      throw new IOException(uhe);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  void reconnect(String host, String resumeProbe, CommandListener listener, String[] command)
      throws IOException {
    if (sock != null) {
      throw new IllegalStateException();
    }
    try {
      if (log.isDebugEnabled()) {
        log.debug("opening socket to {}", port);
      }
      long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
      while (sock == null && System.nanoTime() <= timeout) {
        try {
          sock = new Socket(host, port);
        } catch (ConnectException e) {
          log.debug("server not yet available; retrying ...");
          Thread.sleep(20);
        }
      }

      if (sock == null) {
        log.debug("server not available. exiting.");
        System.exit(1);
      }
      protocol = createProtocol(sock, host, true);

      log.debug("reconnecting client {}", resumeProbe);
      protocol.write(new ReconnectCommand(resumeProbe));

      log.debug("entering into command loop");
      commandLoop(
          new CommandListener() {
            boolean statusReported = false;

            @Override
            public void onCommand(Command cmd) throws IOException {
              if (statusReported || cmd.getType() != Command.STATUS) {
                listener.onCommand(cmd);
              } else {
                StatusCommand statusCommand = (StatusCommand) cmd;
                if (statusCommand.getFlag() == ReconnectCommand.STATUS_FLAG) {
                  if (statusCommand.isSuccess()) {
                    log.info("Reconnected to an active probe: {}", resumeProbe);
                    String probeCommand = command[0];
                    String probeCommandArg = command[1];
                    if (probeCommand != null) {
                      if (log.isDebugEnabled()) {
                        log.debug(
                            "Executing remote command '{}'{}",
                            probeCommand,
                            (probeCommandArg != null ? "(" + probeCommandArg + ")" : ""));
                      }
                      switch (probeCommand) {
                        case "exit":
                          {
                            sendExit();
                            break;
                          }
                        case "event":
                          {
                            if (probeCommandArg == null || probeCommandArg.equals("*")) {
                              sendEvent();
                            } else {
                              sendEvent(probeCommandArg);
                            }
                            sendDisconnect();
                            break;
                          }
                        default:
                          {
                            log.warn("Unrecognized BTrace command {}", probeCommand);
                          }
                      }
                    }
                  } else {
                    log.warn("Unable to reconnect to an active probe: {}", resumeProbe);
                    System.exit(1);
                  }
                } else {
                  listener.onCommand(cmd);
                }
                statusReported = true;
              }
            }
          });
    } catch (UnknownHostException uhe) {
      throw new IOException(uhe);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Submits the compiled BTrace .class to the VM attached and passes given command line arguments.
   * Receives commands from the traced JVM and sends those to the command listener provided.
   */
  public void submit(String fileName, byte[] code, String[] args, CommandListener listener)
      throws IOException {
    submit("localhost", fileName, code, args, listener);
  }

  /**
   * Submits the compiled BTrace .class to the VM attached and passes given command line arguments.
   * Receives commands from the traced JVM and sends those to the command listener provided.
   */
  public void submit(
      String host, String fileName, byte[] code, String[] args, CommandListener listener)
      throws IOException {
    if (sock != null) {
      throw new IllegalStateException();
    }
    submitDTrace(fileName, code, args, listener);
    try {
      if (log.isDebugEnabled()) {
        log.debug("opening socket to {}", port);
      }
      long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
      while (sock == null && System.nanoTime() <= timeout) {
        try {
          sock = new Socket(host, port);
        } catch (ConnectException e) {
          log.debug("server not yet available; retrying ...");
          Thread.sleep(20);
        }
      }

      if (sock == null) {
        log.debug("server not available. exiting.");
        System.exit(1);
      }
      protocol = createProtocol(sock, host, true);
      log.debug("setting up client settings");
      Map<String, Object> settings = new HashMap<>();
      settings.put(SharedSettings.DEBUG_KEY, debug);
      settings.put(SharedSettings.DUMP_DIR_KEY, dumpClasses ? dumpDir : "");
      settings.put(SharedSettings.TRACK_RETRANSFORMS_KEY, trackRetransforms);
      settings.put(SharedSettings.TRUSTED_KEY, trusted);
      settings.put(SharedSettings.PROBE_DESC_PATH_KEY, probeDescPath);
      settings.put(SharedSettings.OUTPUT_FILE_KEY, outputFile);

      protocol.write(new SetSettingsCommand(settings));

      if (log.isDebugEnabled()) {
        log.debug("sending instrument command: {}", Arrays.deepToString(args));
      }
      protocol.write(new InstrumentCommand(code, args));

      log.debug("entering into command loop");
      commandLoop(
          new CommandListener() {
            boolean statusReported = false;

            @Override
            public void onCommand(Command cmd) throws IOException {
              if (statusReported || cmd.getType() != Command.STATUS) {
                listener.onCommand(cmd);
              } else {
                StatusCommand statusCommand = (StatusCommand) cmd;
                if (statusCommand.getFlag() == StatusCommand.STATUS_FLAG) {
                  if (statusCommand.isSuccess()) {
                    log.info("Successfully started BTrace probe: {}", fileName);
                  } else {
                    log.warn("Failed to start BTrace probe: {}", fileName);
                    System.exit(1);
                  }
                  statusReported = true;
                  // Forward the first STATUS as well so higher-level listeners (e.g. unattended -x)
                  // can react immediately (e.g. initiate disconnect)
                  listener.onCommand(cmd);
                } else {
                  listener.onCommand(cmd);
                }
              }
            }
          });
    } catch (UnknownHostException uhe) {
      throw new IOException(uhe);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Submits the compiled BTrace .class to the VM attached and passes given command line arguments.
   * Receives commands from the traced JVM and sends those to the command listener provided.
   */
  public void submit(byte[] code, String[] args, CommandListener listener) throws IOException {
    submit(null, code, args, listener);
  }

  /** Sends ExitCommand to the traced JVM. */
  public void sendExit() throws IOException {
    sendExit(0);
  }

  /** Sends ExitCommand to the traced JVM. */
  public void sendExit(int code) throws IOException {
    send(new ExitCommand(code));
  }

  public void sendDisconnect() throws IOException {
    if (log.isDebugEnabled()) {
      log.debug("sending command {}", DisconnectCommand.class.getSimpleName());
    }
    send(new DisconnectCommand());
  }

  /** Sends an EventCommand to the traced JVM. */
  public void sendEvent() throws IOException {
    sendEvent("");
  }

  /** Sends an EventCommand to the traced JVM. */
  public void sendEvent(String name) throws IOException {
    send(new EventCommand(name));
  }

  /** Closes all connection state to the traced JVM. */
  public synchronized void close() throws IOException {
    // Mark as disconnected to prevent shutdown hook from attempting further sends
    disconnected = true;
    if (protocol != null) {
      protocol.close();
    }
    if (sock != null) {
      sock.close();
    }
    reset();
  }

  boolean isDisconnected() {
    return disconnected;
  }

  void disconnect() throws IOException {
    disconnected = true;
    if (log.isDebugEnabled()) {
      log.debug("sending DISCONNECT request to agent");
    }
    sendDisconnect();
  }

  void listProbes() throws IOException {
    send(new ListProbesCommand());
  }

  void listFailedExtensions() throws IOException {
    send(new ListFailedExtensionsCommand());
  }

  /** reset the internal status of the client */
  private void reset() {
    sock = null;
    protocol = null;
  }

  /**
   * Finds the masked btrace.jar in the classpath.
   *
   * @return the path to the masked JAR, or null if not found
   */
  private String findMaskedAgentJar() {
    try {
      // 1. Check system property (set by Loader.main())
      String maskedJarPath = System.getProperty("btrace.jar.path");
      if (maskedJarPath != null && !maskedJarPath.isEmpty()) {
        File maskedJar = new File(maskedJarPath);
        if (maskedJar.exists() && maskedJar.isFile() && isMaskedJar(maskedJar)) {
          if (log.isDebugEnabled()) {
            log.debug("Using masked JAR from property: {}", maskedJar.getAbsolutePath());
          }
          return maskedJar.getAbsolutePath();
        }
      }

      // 2. Search classpath for btrace.jar
      String classpath = System.getProperty("java.class.path", "");
      for (String entry : classpath.split(File.pathSeparator)) {
        if (entry.endsWith("btrace.jar")) {
          File jarFile = new File(entry);
          if (jarFile.exists() && isMaskedJar(jarFile)) {
            if (log.isDebugEnabled()) {
              log.debug("Found masked btrace.jar in classpath: {}", jarFile.getAbsolutePath());
            }
            return jarFile.getAbsolutePath();
          }
        }
      }

      // 3. No masked JAR found
      return null;
    } catch (Exception e) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to find masked agent JAR: {}", e.getMessage());
      }
      return null;
    }
  }

  /**
   * Check if a JAR file has the masked JAR structure (contains Loader and masked .classdata files)
   */
  private boolean isMaskedJar(File jarFile) {
    try (JarFile jar = new JarFile(jarFile)) {
      // Check for Loader class (unmasked entry point)
      if (jar.getJarEntry("org/openjdk/btrace/boot/Loader.class") == null) {
        return false;
      }
      // Check for masked agent classes
      if (jar.getJarEntry("META-INF/btrace/agent/") != null) {
        return true;
      }
      // Also check for at least one .classdata file
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".classdata")) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to check if JAR is masked: {}", e.getMessage());
      }
      return false;
    }
  }

  // -- Internals only below this point
  private String getToolsJarPath(String javaClassPath, String javaHome) {
    // try to get absolute path of tools.jar
    // first check this application's classpath
    String[] components = javaClassPath.split(File.pathSeparator);
    for (String c : components) {
      if (c.endsWith("tools.jar")) {
        return new File(c).getAbsolutePath();
      } else if (c.endsWith("classes.jar")) { // MacOS specific
        return new File(c).getAbsolutePath();
      }
    }
    // we didn't find -- make a guess! If this app is running on a JDK rather
    // than a JRE there will be a tools.jar in $JDK_HOME/lib directory.
    if (System.getProperty("os.name").startsWith("Mac")) {
      int homeIndex = javaHome.indexOf("/Home");
      if (homeIndex > -1) {
        String java_mac_home = javaHome.substring(0, javaHome.indexOf("/Home"));
        return java_mac_home + "/Home/lib/tools.jar";
      }
    }
    // tools.jar is not included in JRE
    return javaHome + (javaHome.contains("/jre") ? "/.." : "") + "/lib/tools.jar";
  }

  private void send(Command cmd) throws IOException {
    if (protocol == null) {
      throw new IllegalStateException();
    }
    protocol.write(cmd);
  }

  private void commandLoop(CommandListener listener) throws IOException {
    assert protocol != null : "null protocol?";
    AtomicBoolean exited = new AtomicBoolean(false);
    while (true) {
      try {
        Command cmd = protocol.read();
        if (log.isDebugEnabled()) {
          log.debug("received command {}", cmd);
        }
        listener.onCommand(cmd);
        if (cmd.getType() == Command.EXIT) {
          log.debug("received EXIT cmd");
          return;
        }
      } catch (IOException e) {
        if (exited.compareAndSet(false, true)) listener.onCommand(new ExitCommand(-1));
        throw e;
      } catch (ClassNotFoundException e) {
        if (exited.compareAndSet(false, true)) listener.onCommand(new ExitCommand(-1));
        throw new IOException(e);
      } catch (NullPointerException e) {
        log.error("Unexpected null pointer during command processing", e);
        if (exited.compareAndSet(false, true)) listener.onCommand(new ExitCommand(-1));
      }
    }
  }

  private void warn(CommandListener listener, String msg) {
    try {
      msg = "WARNING: " + msg + "\n";
      listener.onCommand(new MessageCommand(msg));
    } catch (IOException exp) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to send warning messge", exp);
      }
    }
  }

  private void submitDTrace(String fileName, byte[] code, String[] args, CommandListener listener) {
    if (fileName == null || code == null) {
      return;
    }

    Object dtraceSrc = getDTraceSource(fileName, code);
    try {
      if (dtraceSrc instanceof String) {
        if (dtraceEnabled) {
          submitString.invoke(null, dtraceSrc, args, listener);
        } else {
          warn(listener, "@DTrace is supported only on Solaris 11+");
        }
      } else if (dtraceSrc instanceof File) {
        if (dtraceEnabled) {
          submitFile.invoke(null, dtraceSrc, args, listener);
        } else {
          warn(listener, "@DTraceRef is supported only on Solaris 11+");
        }
      }
    } catch (IllegalAccessException | IllegalArgumentException iace) {
      iace.printStackTrace();
    } catch (InvocationTargetException ite) {
      throw new RuntimeException(ite.getTargetException());
    }
  }

  private Object getDTraceSource(String fileName, byte[] code) {
    if (isPersistedProbe(code)) {
      return null;
    }

    ClassReader reader = new ClassReader(code);
    Object[] result = new Object[1];
    reader.accept(
        new ClassVisitor(Opcodes.ASM9) {

          @Override
          public AnnotationVisitor visitAnnotation(String desc, boolean vis) {
            if (desc.equals(DTRACE_DESC)) {
              return new AnnotationVisitor(Opcodes.ASM9) {

                @Override
                public void visit(String name, Object value) {
                  if (name.equals("value")) {
                    result[0] = value;
                  }
                }
              };
            } else if (desc.equals(DTRACE_REF_DESC)) {
              return new AnnotationVisitor(Opcodes.ASM9) {

                @Override
                public void visit(String name, Object value) {
                  if (name.equals("value")) {
                    String tmp = value.toString();
                    File file = new File(tmp);
                    if (file.isAbsolute()) {
                      result[0] = file;
                    } else {
                      int index = fileName.lastIndexOf(File.separatorChar);
                      String dir;
                      if (index == -1) {
                        dir = ".";
                      } else {
                        dir = fileName.substring(0, index);
                      }
                      result[0] = new File(dir, tmp);
                    }
                  }
                }
              };
            } else {
              return super.visitAnnotation(desc, vis);
            }
          }
        },
        ClassReader.SKIP_CODE);
    return result[0];
  }

  private static boolean isPersistedProbe(byte[] code) {
    return code[0] == (byte) (0xBA & 0xff)
        && code[1] == (byte) (0xCE & 0xff)
        && code[2] == (byte) (0XCA & 0xff)
        && code[3] == (byte) (0XCA & 0xff);
  }
}
