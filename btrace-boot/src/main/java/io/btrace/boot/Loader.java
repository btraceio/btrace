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
package io.btrace.boot;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Entry point and classloader for the masked JAR structure.
 *
 * <p>This class serves as the single entry point for btrace.jar in all modes:
 *
 * <ul>
 *   <li>Agent mode: {@code -javaagent:btrace.jar} calls {@link #premain(String, Instrumentation)}
 *   <li>Dynamic attach: calls {@link #agentmain(String, Instrumentation)}
 *   <li>Client mode: {@code java -jar btrace.jar} calls {@link #main(String[])}
 * </ul>
 *
 * <p>The JAR structure uses .classdata extension for non-bootstrap classes to hide them from the
 * bootstrap classloader while keeping everything in a single JAR:
 *
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
  private static final String CLIENT_MAIN_PROP = "btrace.client.main";
  private static final String DEFAULT_AGENT_MAIN = "io.btrace.agent.Main";
  private static final String DEFAULT_CLIENT_MAIN = "io.btrace.client.Main";
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
      debug("startAgent called with args: " + args);

      // Get JAR file location (tries CodeSource, then getResource() fallback)
      File jarFile = getJarFile();
      if (jarFile == null) {
        throw new RuntimeException("Cannot locate btrace.jar");
      }
      debug("Loading agent from: " + jarFile.getAbsolutePath());

      // Add btrace.jar to bootstrap classloader search path
      // This is required so that bootstrap-visible classes (BTraceRuntimeAccess, Auxiliary, etc.)
      // are visible to probe classes which are defined via bootstrap classloader
      try {
        inst.appendToBootstrapClassLoaderSearch(new JarFile(jarFile));
        debug("Added btrace.jar to bootstrap classpath");
      } catch (IOException e) {
        debug("WARNING: Failed to add btrace.jar to bootstrap classpath: " + e.getMessage());
      }

      // Read main class from manifest
      String mainClass = getManifestAttribute(jarFile, AGENT_MAIN_ATTR, DEFAULT_AGENT_MAIN);
      debug("Agent main class: " + mainClass);

      // Create classloader for agent section
      // Use null (bootstrap) as parent to ensure bootstrap classes like BTraceRuntimeAccess
      // are loaded from bootstrap (via appendToBootstrapClassLoaderSearch), not from
      // Loader's classloader. This ensures probe classes (loaded by bootstrap) see the
      // same BTraceRuntimeAccess class that the agent uses.
      MaskedClassLoader agentLoader = new MaskedClassLoader(jarFile, AGENT_SECTION, null);

      // Load and invoke agent main class
      Class<?> agentMain = agentLoader.loadClass(mainClass);
      debug("Loaded agent main class: " + agentMain.getName());

      Method method = agentMain.getMethod(methodName, String.class, Instrumentation.class);
      method.invoke(null, args, inst);
      debug("Successfully invoked agent main");

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

      // Set system property with btrace.jar location so client can use it as agent JAR
      System.setProperty("btrace.jar.path", jarFile.getAbsolutePath());
      debug("Set btrace.jar.path property to: " + jarFile.getAbsolutePath());

      // Allow dedicated launchers such as btracex to override the client entry point.
      String mainClass = getClientMainClass(jarFile);
      debug("Client main class: " + mainClass);

      // Create classloader for client section
      MaskedClassLoader clientLoader =
          new MaskedClassLoader(jarFile, CLIENT_SECTION, Loader.class.getClassLoader());

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

  private static String getClientMainClass(File jarFile) {
    String override = System.getProperty(CLIENT_MAIN_PROP);
    if (override != null && !override.isEmpty()) {
      return override;
    }
    return getManifestAttribute(jarFile, CLIENT_MAIN_ATTR, DEFAULT_CLIENT_MAIN);
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
      // Try CodeSource first
      ProtectionDomain pd = Loader.class.getProtectionDomain();
      CodeSource cs = pd.getCodeSource();
      if (cs != null) {
        URL location = cs.getLocation();
        if (location != null) {
          debug("CodeSource location: " + location);
          if ("file".equals(location.getProtocol())) {
            return new File(location.toURI());
          }
          debug("Unsupported protocol: " + location.getProtocol());
        }
      }

      // Fallback: use getResource() to find Loader.class in the JAR
      debug("CodeSource unavailable, trying getResource()");
      URL loaderResource = Loader.class.getResource("Loader.class");
      if (loaderResource != null) {
        debug("Loader resource: " + loaderResource);
        String path = loaderResource.toString();
        if (path.startsWith("jar:file:")) {
          // Extract JAR path from
          // jar:file:/path/to/btrace.jar!/org/openjdk/btrace/boot/Loader.class
          path = path.substring("jar:file:".length());
          int idx = path.indexOf("!");
          if (idx > -1) {
            path = path.substring(0, idx);
            return new File(path);
          }
        }
      }

      debug("Could not locate JAR file");
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
}
