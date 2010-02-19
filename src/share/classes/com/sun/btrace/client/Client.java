/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.btrace.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.URI;
import java.util.Map;
import com.sun.btrace.CommandListener;
import com.sun.btrace.compiler.Compiler;
import com.sun.btrace.annotations.DTrace;
import com.sun.btrace.annotations.DTraceRef;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.EventCommand;
import com.sun.btrace.comm.ExitCommand;
import com.sun.btrace.comm.InstrumentCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.WireIO;
import com.sun.btrace.util.NullVisitor;
import com.sun.tools.attach.VirtualMachine;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Type;
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

    private static boolean dtraceEnabled;
    private static Method submitFile;
    private static Method submitString;
    private static final String DTRACE_DESC;
    private static final String DTRACE_REF_DESC;

    static {
        try {
            /*
             * Check for DTrace Consumer class -- if we don't have that
             * either /usr/lib/share/java/dtrace.jar is not in CLASSPATH 
             * or we are not running on Solaris 11+.
             */
            Class dtraceConsumerClass = Class.forName("org.opensolaris.os.dtrace.Consumer");
            /*
             * Check for BTrace's DTrace support class -- if that is available
             * may be the user didn't build BTrace on Solaris 11. S/he built
             * it on Solaris 10 or below or some other OS.
             */
            Class dtraceClass = Class.forName("com.sun.btrace.dtrace.DTrace");
            dtraceEnabled = true;
            submitFile = dtraceClass.getMethod("submit",
                    new Class[]{File.class, String[].class,
                CommandListener.class
            });
            submitString = dtraceClass.getMethod("submit",
                    new Class[]{String.class, String[].class,
                CommandListener.class
            });
        } catch (Exception exp) {
            dtraceEnabled = false;
        }
        DTRACE_DESC = Type.getDescriptor(DTrace.class);
        DTRACE_REF_DESC = Type.getDescriptor(DTraceRef.class);
    }

    // port on which BTrace agent listens
    private final int port;
    // are we running debug mode?
    private final boolean debug;
    // do we need to track retransforming single classes? (will impose additional overhead)
    private final boolean trackRetransforms;
    // are we running in unsafe mode?
    private final boolean unsafe;
    // are we dumping .class files of
    // the instrumented classes?
    private final boolean dumpClasses;
    // which directory we dump the .class files?
    private final String dumpDir;
    private final String probeDescPath;

    // connection state to the traced JVM
    private volatile Socket sock;
    private volatile ObjectInputStream ois;
    private volatile ObjectOutputStream oos;

    public Client(int port) {
        this(port, ".", false, false, false, false, null);
    }

    public Client(int port, String probeDescPath) {
        this(port, probeDescPath, false, false, false, false, null);
    }

    public Client(int port, String probeDescPath,
            boolean debug, boolean trackRetransforms,
            boolean unsafe, boolean dumpClasses,
            String dumpDir) {
        this.port = port;
        this.probeDescPath = probeDescPath;
        this.debug = debug;
        this.unsafe = unsafe;
        this.dumpClasses = dumpClasses;
        this.dumpDir = dumpDir;
        this.trackRetransforms = trackRetransforms;
    }

    public byte[] compile(String fileName, String classPath) {
        return compile(fileName, classPath, new PrintWriter(System.err), null);
    }

    /** 
     * Compiles given BTrace program using given classpath.
     */
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
            Compiler compiler = new Compiler(includePath, unsafe);
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
            code = new byte[(int) file.length()];
            try {
                FileInputStream fis = new FileInputStream(file);
                if (debug) {
                    debugPrint("reading " + fileName);
                }
                try {
                    fis.read(code);
                } finally {
                    fis.close();
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
    public void attach(String pid) throws IOException {
        try {
            String agentPath = "/btrace-agent.jar";
            String tmp = Client.class.getClassLoader().getResource("com/sun/btrace").toString();
            tmp = tmp.substring(0, tmp.indexOf("!"));
            tmp = tmp.substring("jar:".length(), tmp.lastIndexOf("/"));
            agentPath = tmp + agentPath;
            agentPath = new File(new URI(agentPath)).getAbsolutePath();
            attach(pid, agentPath, null, null);
        } catch (RuntimeException re) {
            throw re;
        } catch (IOException ioexp) {
            throw ioexp;
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
            int serverPort = Integer.parseInt(vm.getSystemProperties().getProperty("btrace.port", "-1"));
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
            if (debug) {
                agentArgs += ",debug=true";
            }
            if (unsafe) {
                agentArgs += ",unsafe=true";
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
            if (sysCp == null) {
                sysCp = getToolsJarPath();
            }
            agentArgs += ",systemClassPath=" + sysCp;
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
        if (sock != null) {
            throw new IllegalStateException();
        }
        submitDTrace(fileName, code, args, listener);
        try {
            if (debug) {
                debugPrint("opening socket to " + port);
            }
            sock = new Socket("localhost", port);
            oos = new ObjectOutputStream(sock.getOutputStream());
            if (debug) {
                debugPrint("sending instrument command");
            }
            WireIO.write(oos, new InstrumentCommand(code, args));
            ois = new ObjectInputStream(sock.getInputStream());
            if (debug) {
                debugPrint("entering into command loop");
            }
            commandLoop(listener);
        } catch (UnknownHostException uhe) {
            throw new IOException(uhe);
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
    private String getToolsJarPath() {
        // try to get absolute path of tools.jar
        // first check this application's classpath
        String[] components = System.getProperty("java.class.path").split(File.pathSeparator);
        for (String c : components) {
            if (c.endsWith("tools.jar")) {
                return new File(c).getAbsolutePath();
            } else if (c.endsWith("classes.jar")) { // MacOS specific
                return new File(c).getAbsolutePath();
            }
        }
        // we didn't find -- make a guess! If this app is running on a JDK rather 
        // than a JRE there will be a tools.jar in $JDK_HOME/lib directory.
        if(System.getProperty("os.name").startsWith("Mac")) {
            String java_home = System.getProperty("java.home");
            String java_mac_home = java_home.substring(0,java_home.indexOf("/Home"));
            return java_mac_home + "/Classes/classes.jar";
        } else {
            return System.getProperty("java.home") + "../lib/tools.jar";
        }
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
        final AtomicBoolean exited = new AtomicBoolean(false);
        while (true) {
            try {
                Command cmd = WireIO.read(ois);
                if (debug) {
                    debugPrint("received " + cmd);
                }
                listener.onCommand(cmd);
                if (cmd.getType() == Command.EXIT) {
                    return;
                }
            } catch (IOException e) {
                if (exited.compareAndSet(false, true)) listener.onCommand(new ExitCommand(-1));
                throw e;
            } catch (NullPointerException e) {
                if (exited.compareAndSet(false, true))listener.onCommand(new ExitCommand(-1));
            }
        }
    }

    public void debugPrint(String msg) {
        System.out.println("DEBUG: " + msg);
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
        } catch (IllegalAccessException iace) {
            iace.printStackTrace();
        } catch (IllegalArgumentException iarge) {
            iarge.printStackTrace();
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite.getTargetException());
        }
    }

    private Object getDTraceSource(final String fileName, byte[] code) {
        ClassReader reader = new ClassReader(code);
        final Object[] result = new Object[1];
        reader.accept(new NullVisitor() {

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean vis) {
                if (desc.equals(DTRACE_DESC)) {
                    return new NullVisitor() {

                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals("value")) {
                                result[0] = value;
                            }
                        }
                    };
                } else if (desc.equals(DTRACE_REF_DESC)) {
                    return new NullVisitor() {

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

    private static boolean isPortAvailable(int port) {
        Socket clSocket = null;
        try {
            clSocket = new Socket("127.0.0.1", port);
        } catch (UnknownHostException ex) {
        } catch (IOException ex) {
            clSocket = null;
        }
        if (clSocket != null) {
            try {
                clSocket.close();
            } catch (IOException e) {
            }
            return false;
        }
        return true;
    }
}
