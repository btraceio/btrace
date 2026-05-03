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
package io.btrace.compiler;

import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import io.btrace.boot.MaskedClassLoader;
import io.btrace.boot.MaskedJarUtils;
import io.btrace.core.SharedSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CompilerHelper {
  private static final Logger log = LoggerFactory.getLogger(CompilerHelper.class);
  private final boolean generatePack;

  // JSR 199 compiler
  private final JavaCompiler compiler;

  CompilerHelper(JavaCompiler compiler, boolean generatePack) {
    this.compiler = compiler;
    this.generatePack = generatePack;
  }

  /**
   * Extends the classpath with JARs from extension directories. Scans in order:
   * BTRACE_HOME/libs/ext/, ~/.btrace/ext/, $BTRACE_EXT_PATH
   *
   * @param classPath original classpath (may be null)
   * @return extended classpath including extension JARs
   */
  private String extendClassPathWithExtensions(String classPath) {
    List<String> extensionJars = new ArrayList<>();

    // 1. Built-in extensions: BTRACE_HOME/libs/ext/
    String btraceHome = getBTraceHome();
    if (btraceHome != null) {
      Path builtinExtPath = Paths.get(btraceHome, "libs", "ext");
      addExtensionJars(builtinExtPath, extensionJars);
    }

    // 2. User extensions: ~/.btrace/ext/
    String userHome = System.getProperty("user.home");
    if (userHome != null) {
      Path userExtPath = Paths.get(userHome, ".btrace", "ext");
      addExtensionJars(userExtPath, extensionJars);
    }

    // 3. Environment variable: BTRACE_EXT_PATH
    String extPath = System.getenv("BTRACE_EXT_PATH");
    if (extPath != null && !extPath.isEmpty()) {
      String[] paths = extPath.split(File.pathSeparator);
      for (String path : paths) {
        addExtensionJars(Paths.get(path), extensionJars);
      }
    }

    // Combine with original classpath
    if (extensionJars.isEmpty()) {
      return classPath;
    }

    StringBuilder sb = new StringBuilder();
    if (classPath != null && !classPath.isEmpty()) {
      sb.append(classPath);
    }

    for (String jar : extensionJars) {
      if (sb.length() > 0) {
        sb.append(File.pathSeparator);
      }
      sb.append(jar);
    }

    return sb.toString();
  }

  /**
   * Attempts to determine BTRACE_HOME from environment or compiler classpath.
   *
   * @return BTRACE_HOME path, or null if not found
   */
  private String getBTraceHome() {
    // Try environment variable first
    String envHome = System.getenv("BTRACE_HOME");
    if (envHome != null && Files.isDirectory(Paths.get(envHome))) {
      return envHome;
    }

    // Try to find from compiler classpath (look for btrace-compiler.jar or btrace-boot.jar)
    String classPath = System.getProperty("java.class.path");
    if (classPath != null) {
      for (String entry : classPath.split(File.pathSeparator)) {
        if (entry.contains("btrace-compiler") || entry.contains("btrace-boot")) {
          File jarFile = new File(entry);
          if (jarFile.exists()) {
            File libsDir = jarFile.getParentFile();
            if (libsDir != null && libsDir.getName().equals("libs")) {
              File home = libsDir.getParentFile();
              if (home != null && home.isDirectory()) {
                return home.getAbsolutePath();
              }
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * Adds all JAR files from the given directory to the list.
   *
   * @param directory directory to scan
   * @param jars list to add JAR paths to
   */
  private void addExtensionJars(Path directory, List<String> jars) {
    if (!Files.isDirectory(directory)) {
      return;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
      for (Path jar : stream) {
        jars.add(jar.toAbsolutePath().toString());
      }
    } catch (IOException ignored) {
      // Directory not accessible, skip silently
    }
  }

  Map<String, byte[]> compile(
      MemoryJavaFileManager manager,
      Iterable<? extends JavaFileObject> compUnits,
      Writer err,
      String sourcePath,
      String classPath) {
    // Extend classpath with extension JARs
    classPath = extendClassPathWithExtensions(classPath);

    // to collect errors, warnings etc.
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    // javac options
    List<String> options = new ArrayList<>();
    options.add("-Xlint:all");
    options.add("-g:lines");
    options.add("-deprecation");
    options.add("-source");
    options.add("8");
    options.add("-target");
    options.add("8");
    if (sourcePath != null) {
      options.add("-sourcepath");
      options.add(sourcePath);
    }

    if (classPath != null) {
      options.add("-classpath");
      options.add(classPath);
    }

    // Wrap the file manager with MaskedJavaFileManager if a masked JAR is on the classpath
    javax.tools.JavaFileManager effectiveManager = manager;
    File maskedJar = MaskedJarUtils.findMaskedJarInClasspath(classPath);
    ClassLoader maskedClassLoader = null;
    if (maskedJar != null) {
      try {
        effectiveManager = new MaskedJavaFileManager(manager, maskedJar);
        log.debug("Using MaskedJavaFileManager for: {}", maskedJar.getAbsolutePath());
        // Create a MaskedClassLoader for CompilerClassWriter to use
        maskedClassLoader = new MaskedClassLoader(maskedJar, "client", getClass().getClassLoader());
      } catch (IOException e) {
        log.warn("Failed to create MaskedJavaFileManager, falling back to standard manager", e);
      }
    }

    // create a compilation task
    JavacTask task =
        (JavacTask) compiler.getTask(err, effectiveManager, diagnostics, options, null, compUnits);
    Verifier btraceVerifier = new Verifier();
    task.setTaskListener(btraceVerifier);

    // we add BTrace Verifier as a (JSR 269) Processor
    List<Processor> processors = new ArrayList<>(1);
    processors.add(btraceVerifier);
    task.setProcessors(processors);

    PrintWriter perr = (err instanceof PrintWriter) ? (PrintWriter) err : new PrintWriter(err);

    // print dignostics messages in case of failures.
    if (!task.call() || containsErrors(diagnostics)) {
      for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
        printDiagnostic(diagnostic, perr);
      }
      perr.flush();
      return null;
    }

    // collect .class bytes of all compiled classes
    Map<String, byte[]> result = new HashMap<>();
    try {
      Map<String, byte[]> classBytes = manager.getClassBytes();
      List<String> classNames = btraceVerifier.getClassNames();
      for (String name : classNames) {
        if (classBytes.containsKey(name)) {
          dump(name + "_before", classBytes.get(name));
          ClassReader cr = new ClassReader(classBytes.get(name));
          ClassWriter cw = new CompilerClassWriter(classPath, perr, maskedClassLoader);
          cr.accept(new Postprocessor(cw), ClassReader.EXPAND_FRAMES + ClassReader.SKIP_DEBUG);
          byte[] classData = cw.toByteArray();
          dump(name + "_after", classData);
          if (generatePack) {
            // temp hack; need turn off verifier
            SharedSettings.GLOBAL.setTrusted(true);

            String[] pathElements = classPath.split(File.pathSeparator);
            List<URL> urlElements = new ArrayList<>(pathElements.length);

            for (String pathElement : pathElements) {
              File f = new File(pathElement);
              urlElements.add(f.toURI().toURL());
            }
            URLClassLoader generatorCL =
                new URLClassLoader(
                    urlElements.toArray(new URL[0]), Compiler.class.getClassLoader());
            ServiceLoader<PackGenerator> generators =
                ServiceLoader.load(PackGenerator.class, generatorCL);
            Iterator<PackGenerator> iter = generators.iterator();
            if (iter.hasNext()) {
              PackGenerator generator = iter.next();
              SharedSettings.GLOBAL.setBootClassPath(classPath);
              classData = generator.generateProbePack(classData);
            }
          }
          result.put(name, classData);
        }
      }
    } catch (IOException e) {
      log.error("Compilation failed", e);
      perr.append("ERROR: Compilation failed: ").append(e.getMessage()).append("\n");
    } finally {
      // Close masked resources first (they wrap/depend on the base manager)
      if (maskedClassLoader instanceof AutoCloseable) {
        try {
          ((AutoCloseable) maskedClassLoader).close();
        } catch (Exception ignored) {
        }
      }
      if (effectiveManager != manager) {
        try {
          effectiveManager.close();
        } catch (IOException ignored) {
        }
      }
      try {
        manager.close();
      } catch (IOException ignored) {
      }
    }
    return result;
  }

  private void dump(String name, byte[] code) {
    OutputStream os = null;
    try {
      name = name.replace(".", "_") + ".class";
      File f = new File(System.getProperty("java.io.tmpdir") + File.separator + name);
      if (!f.exists()) {
        f.getParentFile().createNewFile();
      }
      os = new FileOutputStream(f);
      os.write(code);
    } catch (IOException ignored) {

    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  private void printDiagnostic(Diagnostic diagnostic, PrintWriter perr) {
    perr.println(diagnostic);
  }

  /**
   * Checks if the list of diagnostic messages contains at least one error. Certain {@link
   * JavacTask} implementations may return success error code even though errors were reported.
   */
  private boolean containsErrors(DiagnosticCollector<?> diagnostics) {
    for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
      if (diagnostic.getKind() == Kind.ERROR) {
        return true;
      }
    }
    return false;
  }
}
