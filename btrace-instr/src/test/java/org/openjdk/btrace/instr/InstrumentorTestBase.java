/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

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
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.MethodID;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;
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

      Field lastFld = MethodID.class.getDeclaredField("lastMehodId");
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
        Class<?> rtClz = Class.forName("org.openjdk.btrace.runtime.BTraceRuntimeImpl_9");
        rtClz.getMethod("defineClass", byte[].class).invoke(null, code);
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
      Files.write(target, diff.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
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
      try (OutputStream os = new FileOutputStream(new File(System.getProperty("java.io.tmpdir"), "dummy.class"))) {
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
      Files.write(FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "jingle.class"), traceCode);
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
    byte[] result = new byte[0];

    byte[] buffer = new byte[1024];

    int read = -1;
    while ((read = is.read(buffer)) > 0) {
      byte[] newresult = new byte[result.length + read];
      System.arraycopy(result, 0, newresult, 0, result.length);
      System.arraycopy(buffer, 0, newresult, result.length, read);
      result = newresult;
    }
    return result;
  }
}
