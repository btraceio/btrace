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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.MethodID;
import io.btrace.core.SharedSettings;
import io.btrace.runtime.BTraceRuntimeAccess;
import io.btrace.runtime.auxiliary.Auxiliary;
import sun.misc.Unsafe;

/**
 * @author Jaroslav Bachorik
 */
public abstract class InstrumentorTestBase {
  private static final boolean DEBUG = false;
  private static final SharedSettings settings = SharedSettings.GLOBAL;
  private static final BTraceProbeFactory factory = new BTraceProbeFactory(settings);
  private static Unsafe unsafe;
  private static Field uccn = null;

  protected byte[] originalBC;
  protected byte[] transformedBC;
  protected byte[] traceCode;
  private static ClassLoader cl;

  static {
    try {
      Field unsafeFld = AtomicInteger.class.getDeclaredField("unsafe");
      unsafeFld.setAccessible(true);
      unsafe = (Unsafe) unsafeFld.get(null);
      resetClassLoader();
    } catch (Exception e) {
    }
  }

  @BeforeAll
  public static void classStartup() throws Exception {
    BTraceRuntime.class.getName();
    uccn = BTraceRuntimeAccess.class.getDeclaredField("uniqueClientClassNames");
    uccn.setAccessible(true);
    uccn.set(null, true);
    settings.setTrusted(true);
  }

  protected final void enableUniqueClientClassNameCheck() throws Exception {
    uccn.set(null, true);
  }

  protected final void disableUniqueClientClassNameCheck() throws Exception {
    uccn.set(null, false);
  }

  @AfterEach
  public void tearDown() {
    cleanup();
  }

  @BeforeEach
  public void startup() {
    try {
      originalBC = null;
      transformedBC = null;
      traceCode = null;
      resetClassLoader();

      Field lastFld = MethodID.class.getDeclaredField("lastMethodId");
      Field mapFld = MethodID.class.getDeclaredField("methodIds");

      lastFld.setAccessible(true);
      mapFld.setAccessible(true);

      AtomicInteger last = (AtomicInteger) lastFld.get(null);
      Map<String, Integer> map = (Map<String, Integer>) mapFld.get(null);

      last.set(1);
      map.clear();
      disableUniqueClientClassNameCheck();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected static final void resetClassLoader() {
    cl = new ClassLoader(InstrumentorTestBase.class.getClassLoader()) {};
  }

  protected void cleanup() {
    originalBC = null;
    transformedBC = null;
    traceCode = null;
  }

  protected void load(final String traceName, final String clzName) throws Exception {
    loadTraceCode(traceName);
    loadClass(clzName);
  }

  @SuppressWarnings("ClassNewInstance")
  protected void loadClass(String origName) throws Exception {
    if (transformedBC != null) {
      String clzName = new ClassReader(transformedBC).getClassName().replace('.', '/');
      Class<?> clz = unsafe.defineClass(clzName, transformedBC, 0, transformedBC.length, cl, null);
      try {
        clz.newInstance();
      } catch (NoSuchFieldError | NoClassDefFoundError e) {
        // expected; ignore
      }
    } else {
      System.err.println("Unable to instrument class " + origName);
      transformedBC = originalBC;
    }
  }

  @SuppressWarnings("ClassNewInstance")
  protected void loadTraceCode(String origName) throws Exception {
    if (traceCode != null) {
      String traceName = new ClassReader(traceCode).getClassName().replace('.', '/');
      Class<?> clz = unsafe.defineClass(traceName, traceCode, 0, traceCode.length, cl, null);
      clz.newInstance();
    } else {
      System.err.println("Unable to process trace " + origName);
    }
  }

  @SuppressWarnings("ClassNewInstance")
  public static void loadCode(String origName, byte[] code) throws Exception {
    if (code != null) {
      ClassReader cr = new ClassReader(code);
      String traceName = cr.getClassName().replace('.', '/');
      Class<?> clz = null;
      try {
        clz = unsafe.defineClass(traceName, code, 0, code.length, cl, null);
        clz.newInstance();
      } catch (NoSuchMethodError e) {
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv =
            new ClassVisitor(Opcodes.ASM9, cw) {
              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
                int idx = name.lastIndexOf('/');
                name =
                    Auxiliary.class.getPackage().getName().replace('.', '/')
                        + '/'
                        + name.substring(idx + 1);
                super.visit(version, access, name, signature, superName, interfaces);
              }
            };
        cr.accept(cv, ClassReader.SKIP_DEBUG);
        code = cw.toByteArray();
        Class<?> rtClz = Class.forName("io.btrace.runtime.BTraceRuntimeImpl_9");
        rtClz.getMethod("defineClassInAuxiliary", byte[].class).invoke(null, code);
      }
    } else {
      System.err.println("Unable to process trace " + origName);
    }
  }

  protected void checkTransformation(String name) throws IOException {
    checkTransformation(name, true);
  }

  protected void checkTransformation(String name, boolean verify) throws IOException {
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(transformedBC);

    if (verify) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      CheckClassAdapter.verify(cr, true, pw);
      if (sw.toString().contains("AnalyzerException")) {
        System.err.println(sw);
        fail();
      }
    }

    String diff = diff();
    if (DEBUG) {
      System.err.println(diff);
    }
    //    if (name.isEmpty()) {
    //      assertTrue(diff.isEmpty());
    //    }
    Path target = Paths.get(System.getProperty("test.resources"), "instrumentorTestData", name);
    if (Boolean.getBoolean("update.test.data")) {
      Files.createDirectories(target.getParent());
      Files.write(
          target,
          diff.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } else {
      String expected = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
      assertEquals(expected, diff);
    }
  }

  protected void checkTrace(String expected) throws IOException {
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(traceCode);

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    CheckClassAdapter.verify(cr, false, pw);
    if (sw.toString().contains("AnalyzerException")) {
      System.err.println(sw);
      fail();
    }
  }

  protected void transform(String traceName) throws Exception {
    transform(traceName, false);
  }

  protected void transform(String traceName, boolean unsafe) throws Exception {
    settings.setTrusted(unsafe);
    BTraceClassReader cr = InstrumentUtils.newClassReader(cl, originalBC);
    BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
    BTraceProbe btrace = loadTrace(traceName, unsafe);

    cw.addInstrumentor(btrace);

    transformedBC = cw.instrument();

    if (transformedBC != null) {
      try (OutputStream os =
          new FileOutputStream(new File(System.getProperty("java.io.tmpdir"), "dummy.class"))) {
        os.write(transformedBC);
      }
    } else {
      // if the instrumentation returns 'null' the original code is to be used
      transformedBC = originalBC;
    }

    //        load(traceName, cr.getJavaClassName());

    System.err.println("==== " + traceName);
  }

  public static String asmify(byte[] bytecode) {
    StringWriter sw = new StringWriter();
    TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
    new org.objectweb.asm.ClassReader(bytecode).accept(acv, 0);
    return sw.toString();
  }

  public static String asmify(ClassNode cn) {
    ClassWriter cw = new ClassWriter(0);
    cn.accept(cw);
    return asmify(cw.toByteArray());
  }

  private String diff() throws IOException {
    String origCode = asmify(originalBC);
    String transCode = asmify(transformedBC);
    return diff(transCode, origCode);
  }

  private String diff(String modified, String orig) throws IOException {
    StringBuilder sb = new StringBuilder();

    String[] modArr = modified.split("\\n");
    String[] orgArr = orig.split("\\n");

    // number of lines of each file
    int modLen = modArr.length;
    int origLen = orgArr.length;

    // opt[i][j] = length of LCS of x[i..M] and y[j..N]
    int[][] opt = new int[modLen + 1][origLen + 1];

    // compute length of LCS and all subproblems via dynamic programming
    for (int i = modLen - 1; i >= 0; i--) {
      for (int j = origLen - 1; j >= 0; j--) {
        if (modArr[i].equals(orgArr[j])) {
          opt[i][j] = opt[i + 1][j + 1] + 1;
        } else {
          opt[i][j] = Math.max(opt[i + 1][j], opt[i][j + 1]);
        }
      }
    }

    // recover LCS itself and print out non-matching lines to standard output
    int modIndex = 0;
    int origIndex = 0;
    while (modIndex < modLen && origIndex < origLen) {
      if (modArr[modIndex].equals(orgArr[origIndex])) {
        modIndex++;
        origIndex++;
      } else if (opt[modIndex + 1][origIndex] >= opt[modIndex][origIndex + 1]) {
        sb.append(modArr[modIndex++].trim()).append('\n');
      } else {
        origIndex++;
      }
    }

    // dump out one remainder of one string if the other is exhausted
    while (modIndex < modLen || origIndex < origLen) {
      if (modIndex == modLen) {
        origIndex++;
      } else if (origIndex == origLen) {
        sb.append(orgArr[modIndex++].trim()).append('\n');
      }
    }
    return sb.toString().trim();
  }

  protected BTraceProbe loadTrace(String name) throws IOException {
    return loadTrace(name, false);
  }

  protected BTraceProbe loadTrace(String name, boolean unsafe) throws IOException {
    byte[] traceData = loadFile("traces/" + name + ".class");

    BTraceProbe bcn = factory.createProbe(traceData);
    traceCode = bcn.getFullBytecode();

    if (DEBUG) {
      System.err.println("=== Loaded Trace: " + bcn + "\n");
      System.err.println(asmify(this.traceCode));
      Files.write(
          FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "jingle.class"),
          traceCode);
    }

    bcn.checkVerified();

    return bcn;
  }

  protected byte[] loadTargetClass(String name) throws IOException {
    originalBC = loadResource("/resources/" + name + ".class");
    if (originalBC == null) {
      originalBC = loadResource("/resources/" + name + ".clazz");
    }
    return originalBC;
  }

  private byte[] loadResource(final String path) throws IOException {
    try (final InputStream is = InstrumentorTestBase.class.getResourceAsStream(path)) {
      if (is == null) {
        System.err.println("Unable to load resource: " + path);
        return null;
      }
      return loadFile(is);
    }
  }

  private byte[] loadFile(String path) throws IOException {
    File f = new File("./build/classes/" + path);
    try (InputStream is = new FileInputStream(f)) {
      byte[] data = loadFile(is);
      return data;
    }
  }

  private byte[] loadFile(InputStream is) throws IOException {
    // ByteArrayOutputStream uses geometric growth (2× on resize)
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
    byte[] buffer = new byte[8192];
    int read;

    while ((read = is.read(buffer)) != -1) {
      baos.write(buffer, 0, read); // Appends to internal buffer
    }

    return baos.toByteArray(); // Single final copy
  }
}
