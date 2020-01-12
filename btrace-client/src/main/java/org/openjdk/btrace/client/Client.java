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

import com.sun.tools.attach.VirtualMachine;
import org.openjdk.btrace.compiler.Compiler;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.annotations.DTrace;
import org.openjdk.btrace.core.annotations.DTraceRef;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.EventCommand;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.openjdk.btrace.core.comm.SetSettingsCommand;
import org.openjdk.btrace.core.comm.WireIO;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class represents a BTrace client. This can be
 * used to create command line as well as a GUI based
 * BTrace clients. The BTrace compilation, traced JVM attach,
 * submission of compiled program and and I/O to the traced
 * JVM are handled by this class.
 *
 * @author A. Sundararajan
 */
public class Client {

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
            submitFile = dtraceClass.getMethod("submit",
                    File.class, String[].class,
                    CommandListener.class);
            submitString = dtraceClass.getMethod("submit",
                    String.class, String[].class,
                    CommandListener.class);
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

    // connection state to the traced JVM
    private volatile Socket sock;
    private volatile ObjectInputStream ois;
    private volatile ObjectOutputStream oos;

    public Client(int port) {
        this(port, null, ".", false, false, false, false, null, null);
    }

    public Client(int port, String probeDescPath) {
        this(port, null, probeDescPath, false, false, false, false, null, null);
    }

    public Client(int port, String outputFile, String probeDescPath,
                  boolean debug, boolean trackRetransforms,
                  boolean trusted, boolean dumpClasses,
                  String dumpDir, String statsdDef) {
        this.port = port;
        this.outputFile = outputFile;
        this.probeDescPath = probeDescPath;
        this.debug = debug;
        this.trusted = trusted;
        this.dumpClasses = dumpClasses;
        this.dumpDir = dumpDir;
        this.trackRetransforms = trackRetransforms;
        this.statsdDef = statsdDef;
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

    @SuppressWarnings("DefaultCharset")
    public byte[] compile(String fileName, String classPath) {
        return compile(fileName, classPath, new PrintWriter(System.err), null);
    }

    /**
     * Compiles given BTrace program using given classpath.
     */
    @SuppressWarnings("DefaultCharset")
    public byte[] compile(String fileName, String classPath, String includePath) {
        return compile(fileName, classPath, new PrintWriter(System.err), includePath);
    }

    public byte[] compile(String fileName, String classPath,
                          PrintWriter err) {
        return compile(fileName, classPath, err, null);
    }

    /**
     * Compiles given BTrace program using given classpath.
     * Errors and warning are written to given PrintWriter.
     */
    public byte[] compile(String fileName, String classPath,
                          PrintWriter err, String includePath) {
        byte[] code = null;
        File file = new File(fileName);
        if (fileName.endsWith(".java")) {
            Compiler compiler = new Compiler(includePath);
            classPath += File.pathSeparator + System.getProperty("java.class.path");
            if (debug) {
                debugPrint("compiling " + fileName);
            }
            Map<String, byte[]> classes = compiler.compile(file,
                    err, ".", classPath);
            if (classes == null) {
                err.println("btrace compilation failed!");
                return null;
            }

            int size = classes.size();
            if (size != 1) {
                err.println("no classes or more than one class");
                return null;
            }
            String name = classes.keySet().iterator().next();
            code = classes.get(name);
            if (debug) {
                debugPrint("compiled " + fileName);
            }
        } else if (fileName.endsWith(".class")) {
            int codeLen = (int) file.length();
            code = new byte[codeLen];
            try {
                if (debug) {
                    debugPrint("reading " + fileName);
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
                if (debug) {
                    debugPrint("read " + fileName);
                }
            } catch (IOException exp) {
                err.println(exp.getMessage());
                return null;
            }
        } else {
            err.println("BTrace script has to be a .java or a .class");
            return null;
        }

        return code;
    }

    /**
     * Attach the BTrace client to the given Java process.
     * Loads BTrace agent on the target process if not loaded
     * already.
     */
    public void attach(String pid, String sysCp, String bootCp) throws IOException {
        try {
            String agentPath = "/btrace-agent.jar";
            URL btracePkg = Client.class.getClassLoader().getResource("org/openjdk/btrace");
            if (btracePkg != null) {
                String tmp = btracePkg.toString();
                tmp = tmp.substring(0, tmp.indexOf('!'));
                tmp = tmp.substring("jar:".length(), tmp.lastIndexOf('/'));
                agentPath = tmp + agentPath;
                agentPath = new File(new URI(agentPath)).getAbsolutePath();
                attach(pid, agentPath, sysCp, bootCp);
            } else {
                warn("Unable to prepare BTrace agent");
            }
        } catch (RuntimeException | IOException e) {
            throw e;
        } catch (Exception exp) {
            throw new IOException(exp.getMessage());
        }
    }

    /**
     * Attach the BTrace client to the given Java process.
     * Loads BTrace agent on the target process if not loaded
     * already. Accepts the full path of the btrace agent jar.
     * Also, accepts system classpath and boot classpath optionally.
     */
    public void attach(String pid, String agentPath, String sysCp, String bootCp) throws IOException {
        try {
            VirtualMachine vm = null;
            if (debug) {
                debugPrint("attaching to " + pid);
            }
            vm = VirtualMachine.attach(pid);
            if (debug) {
                debugPrint("checking port availability: " + port);
            }
            Properties serverVmProps = vm.getSystemProperties();
            int serverPort = Integer.parseInt(serverVmProps.getProperty("btrace.port", "-1"));
            if (serverPort != -1) {
                if (serverPort != port) {
                    throw new IOException("Can not attach to PID " + pid + " on port " + port + ". There is already a BTrace server active on port " + serverPort + "!");
                }
            } else {
                if (!isPortAvailable(port)) {
                    throw new IOException("Port " + port + " unavailable.");
                }
            }

            if (debug) {
                debugPrint("attached to " + pid);
            }

            if (debug) {
                debugPrint("loading " + agentPath);
            }
            String agentArgs = "port=" + port;
            if (statsdDef != null) {
                agentArgs += ",statsd=" + statsdDef;
            }
            if (debug) {
                agentArgs += ",debug=true";
            }
            if (trusted) {
                agentArgs += ",trusted=true";
            }
            if (dumpClasses) {
                agentArgs += ",dumpClasses=true";
                agentArgs += ",dumpDir=" + dumpDir;
            }
            if (trackRetransforms) {
                agentArgs += ",trackRetransforms=true";
            }
            if (bootCp != null) {
                agentArgs += ",bootClassPath=" + bootCp;
            }
            String toolsPath = getToolsJarPath(
                    serverVmProps.getProperty("java.class.path"),
                    serverVmProps.getProperty("java.home")
            );
            if (sysCp == null) {
                sysCp = toolsPath;
            } else {
                sysCp = sysCp + File.pathSeparator + toolsPath;
            }
            agentArgs += ",systemClassPath=" + sysCp;
            String cmdQueueLimit = System.getProperty(BTraceRuntime.CMD_QUEUE_LIMIT_KEY, null);
            if (cmdQueueLimit != null) {
                agentArgs += ",cmdQueueLimit=" + cmdQueueLimit;
            }
            agentArgs += ",probeDescPath=" + probeDescPath;
            if (debug) {
                debugPrint("agent args: " + agentArgs);
            }
            vm.loadAgent(agentPath, agentArgs);
            if (debug) {
                debugPrint("loaded " + agentPath);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (IOException ioexp) {
            throw ioexp;
        } catch (Exception exp) {
            throw new IOException(exp.getMessage());
        }
    }

    /**
     * Submits the compiled BTrace .class to the VM
     * attached and passes given command line arguments.
     * Receives commands from the traced JVM and sends those
     * to the command listener provided.
     */
    public void submit(String fileName, byte[] code, String[] args,
                       CommandListener listener) throws IOException {
        submit("localhost", fileName, code, args, listener);
    }

    /**
     * Submits the compiled BTrace .class to the VM
     * attached and passes given command line arguments.
     * Receives commands from the traced JVM and sends those
     * to the command listener provided.
     */
    public void submit(String host, String fileName, byte[] code, String[] args,
                       CommandListener listener) throws IOException {
        if (sock != null) {
            throw new IllegalStateException();
        }
        submitDTrace(fileName, code, args, listener);
        try {
            if (debug) {
                debugPrint("opening socket to " + port);
            }
            long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
            while (sock == null && System.nanoTime()<= timeout) {
                try {
                    sock = new Socket(host, port);
                } catch (ConnectException e) {
                    if (debug) {
                        debugPrint("server not yet available; retrying ...");
                    }
                    Thread.sleep(20);
                }
            }

            if (sock == null) {
                debugPrint("server not available. exitting.");
                System.exit(1);
            }
            oos = new ObjectOutputStream(sock.getOutputStream());
            if (debug) {
                debugPrint("setting up client settings");
            }
            Map<String, Object> settings = new HashMap<>();
            settings.put(SharedSettings.DEBUG_KEY, debug);
            settings.put(SharedSettings.DUMP_DIR_KEY, dumpClasses ? dumpDir : "");
            settings.put(SharedSettings.TRACK_RETRANSFORMS_KEY, trackRetransforms);
            settings.put(SharedSettings.TRUSTED_KEY, trusted);
            settings.put(SharedSettings.PROBE_DESC_PATH_KEY, probeDescPath);
            settings.put(SharedSettings.OUTPUT_FILE_KEY, outputFile);

            WireIO.write(oos, new SetSettingsCommand(settings));

            if (debug) {
                debugPrint("sending instrument command: " + Arrays.deepToString(args));
            }
            SharedSettings sSettings = new SharedSettings();
            sSettings.from(settings);
            WireIO.write(oos, new InstrumentCommand(code, args, new DebugSupport(sSettings)));
            ois = new ObjectInputStream(sock.getInputStream());
            if (debug) {
                debugPrint("entering into command loop");
            }
            commandLoop(listener);
        } catch (UnknownHostException uhe) {
            throw new IOException(uhe);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Submits the compiled BTrace .class to the VM
     * attached and passes given command line arguments.
     * Receives commands from the traced JVM and sends those
     * to the command listener provided.
     */
    public void submit(byte[] code, String[] args,
                       CommandListener listener) throws IOException {
        submit(null, code, args, listener);
    }

    /**
     * Sends ExitCommand to the traced JVM.
     */
    public void sendExit() throws IOException {
        sendExit(0);
    }

    /**
     * Sends ExitCommand to the traced JVM.
     */
    public void sendExit(int code) throws IOException {
        send(new ExitCommand(code));
    }

    /**
     * Sends an EventCommand to the traced JVM.
     */
    public void sendEvent() throws IOException {
        sendEvent("");
    }

    /**
     * Sends an EventCommand to the traced JVM.
     */
    public void sendEvent(String name) throws IOException {
        send(new EventCommand(name));
    }

    /**
     * Closes all connection state to the traced JVM.
     */
    public synchronized void close() throws IOException {
        if (ois != null) {
            ois.close();
        }
        if (oos != null) {
            oos.close();
        }
        if (sock != null) {
            sock.close();
        }
        reset();
    }

    /**
     * reset the internal status of the client
     */
    private void reset() {
        sock = null;
        ois = null;
        oos = null;
    }

    //-- Internals only below this point
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
        if (oos == null) {
            throw new IllegalStateException();
        }
        oos.reset();
        WireIO.write(oos, cmd);
    }

    private void commandLoop(CommandListener listener)
            throws IOException {
        assert ois != null : "null input stream?";
        AtomicBoolean exited = new AtomicBoolean(false);
        while (true) {
            try {
                Command cmd = WireIO.read(ois);
                if (debug) {
                    debugPrint("received " + cmd);
                }
                listener.onCommand(cmd);
                if (cmd.getType() == Command.EXIT) {
                    debugPrint("received EXIT cmd");
                    return;
                }
            } catch (IOException e) {
                if (exited.compareAndSet(false, true)) listener.onCommand(new ExitCommand(-1));
                throw e;
            } catch (NullPointerException e) {
                e.printStackTrace();
                if (exited.compareAndSet(false, true)) listener.onCommand(new ExitCommand(-1));
            }
        }
    }

    public void debugPrint(String msg) {
        System.out.println("DEBUG: " + msg);
    }

    private void warn(String msg) {
        System.out.println("WARNING: " + msg);
    }

    private void warn(CommandListener listener, String msg) {
        try {
            msg = "WARNING: " + msg + "\n";
            listener.onCommand(new MessageCommand(msg));
        } catch (IOException exp) {
            if (debug) {
                exp.printStackTrace();
            }
        }
    }

    private void submitDTrace(String fileName, byte[] code,
                              String[] args, CommandListener listener) {
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

    private Object getDTraceSource(final String fileName, byte[] code) {
        if (isPersistedProbe(code)) {
            return null;
        }

        ClassReader reader = new ClassReader(code);
        final Object[] result = new Object[1];
        reader.accept(new ClassVisitor(Opcodes.ASM5) {

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean vis) {
                if (desc.equals(DTRACE_DESC)) {
                    return new AnnotationVisitor(Opcodes.ASM5) {

                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals("value")) {
                                result[0] = value;
                            }
                        }
                    };
                } else if (desc.equals(DTRACE_REF_DESC)) {
                    return new AnnotationVisitor(Opcodes.ASM5) {

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
        }, ClassReader.SKIP_CODE);
        return result[0];
    }

    private static final int MAGIC = 0xbacecaca;
    private static boolean isPersistedProbe(byte[] code) {
        int num = 0;
        for (int i = 0; i < 4; i++) {
            num += (256 * num) + code[i];
        }
        return MAGIC == num;
    }
}
