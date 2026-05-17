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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.btrace.core.Messages;
import io.btrace.runtime.BTraceRuntimeAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiler for a BTrace program. Note that a BTrace program is a Java program that is specially
 * annotated and can *not* use many Java constructs (essentially java--). We use JSR 199 API to
 * compile BTrace program but validate the program (for BTrace safety rules) using JSR 269 and
 * javac's Tree API.
 *
 * @author A. Sundararajan
 */
public class Compiler {
  private static final Logger log = LoggerFactory.getLogger(Compiler.class);

  private final CompilerHelper compilerHelper;
  // null means no preprocessing isf done.
  public List<String> includeDirs;
  private final StandardJavaFileManager stdManager;
  private final String packExtension = "class";

  public Compiler(String includePath, boolean generatePack) {
    if (includePath != null) {
      includeDirs = new ArrayList<>();
      String[] paths = includePath.split(File.pathSeparator);
      includeDirs.addAll(Arrays.asList(paths));
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    stdManager = compiler.getStandardFileManager(null, null, null);
    compilerHelper = new CompilerHelper(compiler, generatePack);
  }

  public Compiler(String includePath) {
    this(includePath, false);
  }

  public Compiler(boolean generatePack) {
    this(null, generatePack);
  }

  public Compiler() {
    this(null);
  }

  private static void usage(String msg) {
    System.err.println(msg);
    System.exit(1);
  }

  private static void usage() {
    usage(Messages.get("btracec.usage"));
  }

  // simple test main
  @SuppressWarnings({"DefaultCharset", "RedundantThrows"})
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      usage();
    }

    // turn off the unique client name generation as it is undesired during compilation
    try {
      Field f = BTraceRuntimeAccess.class.getDeclaredField("uniqueClientClassNames");
      f.setAccessible(true);
      f.set(null, false);
    } catch (Exception ignored) {
    }

    String classPath = ".";
    String outputDir = ".";
    String includePath = null;
    boolean trusted = false;
    boolean generatePack = true;
    String packExtension = null;
    int count = 0;
    boolean classPathDefined = false;
    boolean outputDirDefined = false;
    boolean includePathDefined = false;
    boolean trustedDefined = false;

    for (; ; ) {
      if (args[count].charAt(0) == '-') {
        if (args.length <= count + 1) {
          usage();
        }
        if ((args[count].equals("-cp") || args[count].equals("-classpath")) && !classPathDefined) {
          classPath = args[++count];
          classPathDefined = true;
        } else if (args[count].equals("-d") && !outputDirDefined) {
          outputDir = args[++count];
          outputDirDefined = true;
        } else if (args[count].equals("-I") && !includePathDefined) {
          includePath = args[++count];
          includePathDefined = true;
        } else if ((args[count].equals("-unsafe") || args[count].equals("-trusted"))
            && !trustedDefined) {
          trusted = true;
          trustedDefined = true;
        } else if (args[count].equals("-nopack")) {
          generatePack = false;
        } else if (args[count].equals("-packext")) {
          packExtension = args[++count];
        } else {
          usage();
        }
        count++;
        if (count >= args.length) {
          break;
        }
      } else {
        break;
      }
    }

    if (args.length <= count) {
      usage();
    }

    if (!generatePack && packExtension != null) {
      usage("Can not specify pack extension if not using packs (-nopack)");
    }

    File[] files = new File[args.length - count];
    for (int i = 0; i < files.length; i++) {
      files[i] = new File(args[i + count]);
      if (!files[i].exists()) {
        usage("File not found: " + files[i]);
      }
    }

    Compiler compiler = new Compiler(includePath, generatePack);
    classPath += File.pathSeparator + System.getProperty("java.class.path");
    try {
      Map<String, byte[]> classes =
          compiler.compile(files, new PrintWriter(System.err), ".", classPath);
      if (classes != null) {
        // write .class files.
        for (Map.Entry<String, byte[]> c : classes.entrySet()) {
          String name = c.getKey().replace(".", File.separator);
          int index = name.lastIndexOf(File.separatorChar);
          String dir = outputDir + File.separator;
          if (index != -1) {
            dir += name.substring(0, index);
          }
          new File(dir).mkdirs();
          String file;
          if (index != -1) {
            file = name.substring(index + 1);
          } else {
            file = name;
          }
          file += "." + (packExtension != null ? packExtension : "class");
          File out = new File(dir, file);
          try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(c.getValue());
          }
        }
      } else {
        // fail
        System.exit(1);
      }
    } catch (Throwable t) {
      log.error("Compiler invocation failed", t);
      System.err.println("ERROR: Compiler failed: " + t.getMessage());
      // fail
      System.exit(1);
    }
  }

  public Map<String, byte[]> compile(
      String fileName, String source, Writer err, String sourcePath, String classPath) {
    // create a new memory JavaFileManager
    MemoryJavaFileManager manager = new MemoryJavaFileManager(stdManager, includeDirs);

    // prepare the compilation unit
    List<JavaFileObject> compUnits = new ArrayList<>(1);
    compUnits.add(MemoryJavaFileManager.makeStringSource(fileName, injectDslImport(source), includeDirs));
    return compile(manager, compUnits, err, sourcePath, classPath);
  }

  private static final String DSL_IMPORT = "import static io.btrace.BTrace.*;\n";
  private static final String ANNOTATIONS_IMPORT = "import io.btrace.core.annotations.*;\n";

  private static String injectDslImport(String source) {
    boolean hasDsl =
        source.contains("import static io.btrace.BTrace")
            || source.contains("import static io.btrace.core.BTraceUtils");
    boolean hasAnnotations = source.contains("import io.btrace.core.annotations");
    if (hasDsl && hasAnnotations) return source;

    String[] lines = source.split("\n", -1);
    StringBuilder sb = new StringBuilder();
    boolean injected = false;
    for (String line : lines) {
      sb.append(line).append("\n");
      if (!injected && line.trim().startsWith("package ") && line.contains(";")) {
        if (!hasDsl) sb.append(DSL_IMPORT);
        if (!hasAnnotations) sb.append(ANNOTATIONS_IMPORT);
        injected = true;
      }
    }
    if (!injected) {
      String prefix = (!hasDsl ? DSL_IMPORT : "") + (!hasAnnotations ? ANNOTATIONS_IMPORT : "");
      return prefix + source;
    }
    return sb.toString();
  }

  public Map<String, byte[]> compile(File file, Writer err, String sourcePath, String classPath) {
    File[] files = new File[1];
    files[0] = file;
    return compile(files, err, sourcePath, classPath);
  }

  public Map<String, byte[]> compile(
      File[] files, Writer err, String sourcePath, String classPath) {
    List<JavaFileObject> preprocessedCompUnits = new ArrayList<>();
    try {
      for (File file : files) {
        String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        preprocessedCompUnits.add(
            MemoryJavaFileManager.makeStringSource(file.getName(), injectDslImport(source), includeDirs));
      }
    } catch (IOException ioExp) {
      throw new RuntimeException(ioExp);
    }
    return compile(preprocessedCompUnits, err, sourcePath, classPath);
  }

  public Map<String, byte[]> compile(
      Iterable<? extends JavaFileObject> compUnits,
      Writer err,
      String sourcePath,
      String classPath) {
    // create a new memory JavaFileManager
    MemoryJavaFileManager manager = new MemoryJavaFileManager(stdManager, includeDirs);
    return compilerHelper.compile(manager, compUnits, err, sourcePath, classPath);
  }

  private Map<String, byte[]> compile(
      MemoryJavaFileManager manager,
      Iterable<? extends JavaFileObject> compUnits,
      Writer err,
      String sourcePath,
      String classPath) {
    // to collect errors, warnings etc.

    // javac options

    // create a compilation task

    // we add BTrace Verifier as a (JSR 269) Processor

    // print dignostics messages in case of failures.

    // collect .class bytes of all compiled classes
    return compilerHelper.compile(manager, compUnits, err, sourcePath, classPath);
  }
}
