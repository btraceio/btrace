package org.openjdk.btrace.agent;

import java.io.IOException;
import java.io.InputStream;

final class AgentClassLoader extends ClassLoader {
    private static final class Singleton {
        private static final AgentClassLoader INSTANCE = new AgentClassLoader();
    }

    private AgentClassLoader() {
        super(Agent.class.getClassLoader() == null
                ? ClassLoader.getSystemClassLoader()
                : Agent.class.getClassLoader());
    }

    public static AgentClassLoader getInstance() {
        return Singleton.INSTANCE;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            // Try loading from .classdata resources first, then delegate to parent
            try (InputStream is = getParent().getResourceAsStream(name.replace('.', '/') + ".classdata")) {
                if (is != null) {
                    byte[] data = is.readAllBytes();
                    c = defineClass(name, data, 0, data.length);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            } catch (IOException e) {
                throw new ClassNotFoundException("Failed to read classdata for " + name, e);
            }
            return super.loadClass(name, resolve);
        }
    }
}
