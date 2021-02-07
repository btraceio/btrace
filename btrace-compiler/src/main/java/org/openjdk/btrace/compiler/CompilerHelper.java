package org.openjdk.btrace.compiler;

import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
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
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.core.extensions.ExtensionEntry;
import org.openjdk.btrace.core.extensions.ExtensionRepository;

class CompilerHelper {
  private final boolean generatePack;

  // JSR 199 compiler
  private final JavaCompiler compiler;

  CompilerHelper(JavaCompiler compiler, boolean generatePack) {
    this.compiler = compiler;
    this.generatePack = generatePack;
  }

  Map<String, byte[]> compile(
      MemoryJavaFileManager manager,
      Iterable<? extends JavaFileObject> compUnits,
      Writer err,
      String sourcePath,
      String classPath) {
    // to collect errors, warnings etc.
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    // javac options
    List<String> options = new ArrayList<>();
    options.add("-Xlint:all");
    options.add("-g:lines");
    options.add("-deprecation");
    options.add("-source");
    options.add("1.7");
    options.add("-target");
    options.add("1.7");
    if (sourcePath != null) {
      options.add("-sourcepath");
      options.add(sourcePath);
    }

    StringBuilder cpBuilder = new StringBuilder();
    if (classPath != null) {
      cpBuilder.append(classPath);
    }
    for (ExtensionEntry extension : ExtensionRepository.getInstance().getExtensions()) {
      cpBuilder.append(File.pathSeparatorChar).append(extension.getJarPath().toString());
    }
    if (cpBuilder.length() > 0) {
      options.add("-classpath");
      options.add(cpBuilder.toString());
    }



    // create a compilation task
    JavacTask task =
        (JavacTask) compiler.getTask(err, manager, diagnostics, options, null, compUnits);
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
          ClassWriter cw = new CompilerClassWriter(classPath, perr);
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
      e.printStackTrace(perr);
    } finally {
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
      File f = new File("/tmp/" + name);
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
