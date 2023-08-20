package org.openjdk.btrace.agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

final class AgentClassLoader extends ClassLoader {
    private static final class Singleton {
        private static final AgentClassLoader INSTANCE = new AgentClassLoader();
    }

    private final ClassLoader parent = Agent.class.getClassLoader() == null ? ClassLoader.getSystemClassLoader() : Agent.class.getClassLoader();

    public static AgentClassLoader getInstance() {
        return Singleton.INSTANCE;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try (InputStream is = parent.getResourceAsStream(name.replace('.', '/') + ".classdata")) {
            if (is == null) {
                return parent.loadClass(name);
            }
            byte[] buffer = new byte[4096];
            byte[] data = new byte[65536];
            int len = 0;
            int read = 0;
            while ((read = is.read(buffer)) > 0) {
                int newLen = read + len;
                if (newLen >= data.length) {
                    data = Arrays.copyOf(data, data.length * 2);
                }
                System.arraycopy(buffer, 0, data, len, read);
                len = newLen;
            }
            return defineClass(name, data, 0, len);
        } catch (IOException e) {
            throw new ClassNotFoundException("Missing class " + name);
        }
    }
}
