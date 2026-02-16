/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.btrace.boot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Entry point and classloader for the masked JAR structure.
 * <p>
 * This class serves as the single entry point for btrace.jar in all modes:
 * <ul>
 *   <li>Agent mode: {@code -javaagent:btrace.jar} calls {@link #premain(String, Instrumentation)}</li>
 *   <li>Dynamic attach: calls {@link #agentmain(String, Instrumentation)}</li>
 *   <li>Client mode: {@code java -jar btrace.jar} calls {@link #main(String[])}</li>
 * </ul>
 * <p>
 * The JAR structure uses .classdata extension for non-bootstrap classes to hide them
 * from the bootstrap classloader while keeping everything in a single JAR:
 * <pre>
 * btrace.jar
 * ├── org/openjdk/btrace/boot/Loader.class      (bootstrap: this class)
 * ├── org/openjdk/btrace/core/*.class           (bootstrap: core API)
 * ├── org/openjdk/btrace/runtime/*.class        (bootstrap: runtime)
 * ├── META-INF/btrace/agent/*.classdata         (masked: agent classes)
 * └── META-INF/btrace/client/*.classdata        (masked: client classes)
 * </pre>
 *
 * @author Jaroslav Bachorik
 */
public final class Loader {

    private static final String AGENT_SECTION = "agent";
    private static final String CLIENT_SECTION = "client";
    private static final String AGENT_MAIN_ATTR = "BTrace-Agent-Main";
    private static final String CLIENT_MAIN_ATTR = "BTrace-Client-Main";
    private static final String DEFAULT_AGENT_MAIN = "org.openjdk.btrace.agent.Main";
    private static final String DEFAULT_CLIENT_MAIN = "org.openjdk.btrace.client.Main";
    private static final boolean DEBUG = Boolean.getBoolean("btrace.boot.debug");

    private Loader() {
        // Utility class
    }

    /**
     * Agent entry point for load-time instrumentation.
     *
     * @param args command line arguments
     * @param inst instrumentation instance
     */
    public static void premain(String args, Instrumentation inst) {
        debug("premain called with args: " + args);
        startAgent(args, inst, "premain");
    }

    /**
     * Agent entry point for dynamic attach.
     *
     * @param args command line arguments
     * @param inst instrumentation instance
     */
    public static void agentmain(String args, Instrumentation inst) {
        debug("agentmain called with args: " + args);
        startAgent(args, inst, "agentmain");
    }

    /**
     * Client entry point for command-line usage.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        debug("main called");
        startClient(args);
    }

    private static void startAgent(String args, Instrumentation inst, String methodName) {
        try {
            File jarFile = getJarFile();
            if (jarFile == null) {
                throw new RuntimeException("Cannot locate btrace.jar");
            }
            debug("Loading agent from: " + jarFile);

            // Read main class from manifest
            String mainClass = getManifestAttribute(jarFile, AGENT_MAIN_ATTR, DEFAULT_AGENT_MAIN);
            debug("Agent main class: " + mainClass);

            // Create classloader for agent section
            MaskedClassLoader agentLoader = new MaskedClassLoader(
                    jarFile, AGENT_SECTION, Loader.class.getClassLoader());

            // Load and invoke agent main class
            Class<?> agentMain = agentLoader.loadClass(mainClass);
            Method method = agentMain.getMethod(methodName, String.class, Instrumentation.class);
            method.invoke(null, args, inst);

        } catch (Exception e) {
            System.err.println("BTrace agent initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("BTrace agent initialization failed", e);
        }
    }

    private static void startClient(String[] args) {
        try {
            File jarFile = getJarFile();
            if (jarFile == null) {
                throw new RuntimeException("Cannot locate btrace.jar");
            }
            debug("Loading client from: " + jarFile);

            // Read main class from manifest
            String mainClass = getManifestAttribute(jarFile, CLIENT_MAIN_ATTR, DEFAULT_CLIENT_MAIN);
            debug("Client main class: " + mainClass);

            // Create classloader for client section
            MaskedClassLoader clientLoader = new MaskedClassLoader(
                    jarFile, CLIENT_SECTION, Loader.class.getClassLoader());

            // Load and invoke client main class
            Class<?> clientMain = clientLoader.loadClass(mainClass);
            Method method = clientMain.getMethod("main", String[].class);
            method.invoke(null, (Object) args);

        } catch (Exception e) {
            System.err.println("BTrace client initialization failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getManifestAttribute(File jarFile, String name, String defaultValue) {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                Attributes attrs = manifest.getMainAttributes();
                String value = attrs.getValue(name);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        } catch (IOException e) {
            debug("Failed to read manifest: " + e.getMessage());
        }
        return defaultValue;
    }

    private static File getJarFile() {
        try {
            ProtectionDomain pd = Loader.class.getProtectionDomain();
            CodeSource cs = pd.getCodeSource();
            if (cs == null) {
                debug("No CodeSource available");
                return null;
            }
            URL location = cs.getLocation();
            if (location == null) {
                debug("No location in CodeSource");
                return null;
            }
            debug("CodeSource location: " + location);

            // Handle file:// URLs
            if ("file".equals(location.getProtocol())) {
                return new File(location.toURI());
            }

            debug("Unsupported protocol: " + location.getProtocol());
            return null;
        } catch (Exception e) {
            debug("Error getting JAR file: " + e.getMessage());
            return null;
        }
    }

    private static void debug(String msg) {
        if (DEBUG) {
            System.err.println("[BTrace Boot] " + msg);
        }
    }

    /**
     * ClassLoader that loads classes from .classdata files in a masked section of a JAR.
     * <p>
     * This classloader reads class bytecode from files stored as .classdata instead of .class,
     * allowing them to be hidden from the bootstrap classloader while remaining in the same JAR.
     */
    static final class MaskedClassLoader extends URLClassLoader {
        private final JarFile jarFile;
        private final File jarPath;
        private final String sectionPrefix;

        /**
         * Creates a new MaskedClassLoader.
         *
         * @param jarPath path to the JAR file
         * @param section section name ("agent" or "client")
         * @param parent  parent classloader
         */
        MaskedClassLoader(File jarPath, String section, ClassLoader parent) throws IOException {
            super(new URL[]{jarPath.toURI().toURL()}, parent);
            this.jarPath = jarPath;
            this.jarFile = new JarFile(jarPath);
            this.sectionPrefix = "META-INF/btrace/" + section + "/";
            debug("Created MaskedClassLoader for section: " + section);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            debug("findClass: " + name);

            // Convert class name to path: org.foo.Bar -> META-INF/btrace/agent/org/foo/Bar.classdata
            String path = sectionPrefix + name.replace('.', '/') + ".classdata";

            JarEntry entry = jarFile.getJarEntry(path);
            if (entry == null) {
                debug("Class not found in masked section: " + name + " (path: " + path + ")");
                throw new ClassNotFoundException(name);
            }

            try {
                byte[] bytes = readEntry(entry);
                debug("Loaded " + bytes.length + " bytes for class: " + name);

                // Define the class with a CodeSource pointing to the JAR
                URL jarUrl = jarPath.toURI().toURL();
                CodeSource codeSource = new CodeSource(jarUrl, (java.security.cert.Certificate[]) null);
                ProtectionDomain pd = new ProtectionDomain(codeSource, null, this, null);

                return defineClass(name, bytes, 0, bytes.length, pd);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override
        public URL findResource(String name) {
            debug("findResource: " + name);

            // First check for the resource in the masked section
            String maskedPath = sectionPrefix + name;
            JarEntry entry = jarFile.getJarEntry(maskedPath);
            if (entry != null) {
                try {
                    return createJarEntryURL(maskedPath);
                } catch (MalformedURLException e) {
                    debug("Failed to create URL for: " + maskedPath);
                }
            }

            // Fall back to regular resource lookup
            entry = jarFile.getJarEntry(name);
            if (entry != null) {
                try {
                    return createJarEntryURL(name);
                } catch (MalformedURLException e) {
                    debug("Failed to create URL for: " + name);
                }
            }

            return null;
        }

        @Override
        public Enumeration<URL> findResources(String name) throws IOException {
            debug("findResources: " + name);

            final URL resource = findResource(name);
            return new Enumeration<URL>() {
                private boolean hasMore = (resource != null);

                @Override
                public boolean hasMoreElements() {
                    return hasMore;
                }

                @Override
                public URL nextElement() {
                    if (!hasMore) {
                        throw new NoSuchElementException();
                    }
                    hasMore = false;
                    return resource;
                }
            };
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            debug("getResourceAsStream: " + name);

            // First check in the masked section
            String maskedPath = sectionPrefix + name;
            JarEntry entry = jarFile.getJarEntry(maskedPath);
            if (entry != null) {
                try {
                    return jarFile.getInputStream(entry);
                } catch (IOException e) {
                    debug("Failed to get stream for: " + maskedPath);
                }
            }

            // Fall back to regular resource lookup
            entry = jarFile.getJarEntry(name);
            if (entry != null) {
                try {
                    return jarFile.getInputStream(entry);
                } catch (IOException e) {
                    debug("Failed to get stream for: " + name);
                }
            }

            return null;
        }

        private byte[] readEntry(JarEntry entry) throws IOException {
            try (InputStream is = jarFile.getInputStream(entry)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
            }
        }

        private URL createJarEntryURL(String entryPath) throws MalformedURLException {
            // Create a jar: URL for the entry
            return new URL("jar:" + jarPath.toURI().toURL() + "!/" + entryPath);
        }

        @Override
        public void close() throws IOException {
            super.close();
            jarFile.close();
        }

        private void debug(String msg) {
            Loader.debug("[MaskedCL:" + sectionPrefix + "] " + msg);
        }
    }
}
