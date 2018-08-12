package com.sun.btrace.compiler;

import com.sun.btrace.SharedSettings;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.runtime.BTraceProbeFactory;
import com.sun.btrace.runtime.BTraceProbeNode;
import com.sun.btrace.runtime.BTraceProbePersisted;
import com.sun.btrace.util.Messages;
import com.sun.source.util.JavacTask;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CompilerHelper {
    private final boolean generatePack;

    // JSR 199 compiler
    private final JavaCompiler compiler;

    CompilerHelper(JavaCompiler compiler, boolean generatePack) {
        this.compiler = compiler;
        this.generatePack = generatePack;
    }

    Map<String, byte[]> compile(MemoryJavaFileManager manager,
                                Iterable<? extends JavaFileObject> compUnits,
                                Writer err, String sourcePath, final String classPath) {
        // to collect errors, warnings etc.
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();

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

        if (classPath != null) {
            options.add("-classpath");
            options.add(classPath);
        }

        // create a compilation task
        JavacTask task =
                (JavacTask) compiler.getTask(err, manager, diagnostics,
                        options, null, compUnits);
        Verifier btraceVerifier = new Verifier();
        task.setTaskListener(btraceVerifier);

        // we add BTrace Verifier as a (JSR 269) Processor
        List<Processor> processors = new ArrayList<>(1);
        processors.add(btraceVerifier);
        task.setProcessors(processors);

        final PrintWriter perr = (err instanceof PrintWriter) ?
                (PrintWriter) err :
                new PrintWriter(err);

        // print dignostics messages in case of failures.
        if (task.call() == false || containsErrors(diagnostics)) {
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

                        BTraceProbeNode bpn = (BTraceProbeNode) new BTraceProbeFactory(SharedSettings.GLOBAL).createProbe(classData);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        try (DataOutputStream dos = new DataOutputStream(bos)) {
                            BTraceProbePersisted bpp = BTraceProbePersisted.from(bpn);
                            bpp.write(dos);
                        }

                        classData = bos.toByteArray();
                    }
                    result.put(name, classData);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(perr);
        } finally {
            try {
                manager.close();
            } catch (IOException exp) {
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
        } catch (IOException e) {

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void printDiagnostic(Diagnostic diagnostic, final PrintWriter perr) {
        perr.println(diagnostic);
    }

    /**
     * Checks if the list of diagnostic messages contains at least one error. Certain
     * {@link JavacTask} implementations may return success error code even though errors were
     * reported.
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