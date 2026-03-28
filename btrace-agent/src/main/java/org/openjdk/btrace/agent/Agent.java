package org.openjdk.btrace.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.jar.JarFile;

public final class Agent {
    public static void premain(String args, Instrumentation inst) {
        main(args, inst, true);
    }

    public static void agentmain(String args, Instrumentation inst) {
        main(args, inst, false);
    }

    private static void main(String args, Instrumentation inst, boolean isPremain) {
        try {
            // Append to bootstrap classpath before loading Main to ensure classes
            // required by Main's static initializers are available
            URL pd = Agent.class.getProtectionDomain().getCodeSource().getLocation();
            if (pd.toString().endsWith(".jar")) {
                inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(pd.toURI())));
            }
            Class<?> mainClass = AgentClassLoader.getInstance().loadClass("org.openjdk.btrace.agent.Main");
            Method mainMethod = mainClass.getMethod("main", String.class, Instrumentation.class);
            mainMethod.invoke(null, args, inst);
        } catch (Exception e) {
            String msg = "BTrace agent failed to initialize: " + e.getMessage();
            System.err.println(msg);
            e.printStackTrace();
            if (isPremain) {
                throw new RuntimeException(msg, e);
            }
        }
    }
}
