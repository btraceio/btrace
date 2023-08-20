package org.openjdk.btrace.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.jar.JarFile;

public final class Agent {
    public static void premain(String args, Instrumentation inst) {
        main(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        main(args, inst);
    }

    private static void main(String args, Instrumentation inst) {
        try {
            Class<?> mainClass = AgentClassLoader.getInstance().loadClass("org.openjdk.btrace.agent.Main");
            URL pd = Agent.class.getProtectionDomain().getCodeSource().getLocation();
            if (pd.toString().endsWith(".jar")) {
                inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(pd.toURI())));
            }
            mainClass.getMethod("main", String.class, Instrumentation.class).invoke(null, args, inst);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
