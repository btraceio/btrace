package org.openjdk.btrace.compiler;

import java.io.*;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import org.openjdk.btrace.core.Messages;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;

public class CompilerIntegrationTest {
  @Test
  public void testCompileJfr() throws Exception {
    URL input = CompilerIntegrationTest.class.getResource("/JfrEventsProbe.java");
    File inputFile = new File(input.toURI());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (PrintWriter pw = new PrintWriter(new PrintStream(baos))) {
      Map<String, byte[]> data =
          new Compiler(true).compile(inputFile, pw, null, System.getProperty("java.class.path"));
      BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);
      for (byte[] bytes : data.values()) {
        BTraceProbe probe = factory.createProbe(bytes);
        byte[] code = probe.getDataHolderBytecode();
        CheckClassAdapter.verify(new ClassReader(code), true, new PrintWriter(System.err));
      }
    }
    Assertions.assertEquals(0, baos.size());
  }

  @Test
  public void testCompileJfrInvalid() throws Exception {
    URL input = CompilerIntegrationTest.class.getResource("/JfrEventsProbeInvalid.java");
    File inputFile = new File(input.toURI());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (PrintWriter pw = new PrintWriter(new PrintStream(baos))) {
      Map<String, byte[]> data =
          new Compiler(true).compile(inputFile, pw, null, System.getProperty("java.class.path"));
    }
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(baos.toByteArray())))) {
      String line;
      while ((line = br.readLine()) != null) {
        Assertions.assertTrue(line.contains(Messages.get("jfr.event.invalid.field")));
      }
    }
  }

  @Test
  public void testCompileSharedLocals() throws Exception {
    URL input = CompilerIntegrationTest.class.getResource("/ArgsReturnSharedLocal.java");
    File inputFile = new File(input.toURI());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (PrintWriter pw = new PrintWriter(new PrintStream(baos))) {
      Map<String, byte[]> data =
          new Compiler(true).compile(inputFile, pw, null, System.getProperty("java.class.path"));
      BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);
      for (byte[] bytes : data.values()) {
        BTraceProbe probe = factory.createProbe(bytes);
        byte[] code = probe.getDataHolderBytecode();
        CheckClassAdapter.verify(new ClassReader(code), true, new PrintWriter(System.err));
      }
    }

    Assertions.assertEquals(0, baos.size());
  }

  @Test
  public void testCompileSharedLocalsMutateImmutable() throws Exception {
    URL input =
        CompilerIntegrationTest.class.getResource("/ArgsReturnSharedLocalMutateImmutable.java");
    File inputFile = new File(input.toURI());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (PrintWriter pw = new PrintWriter(new PrintStream(baos))) {
      Map<String, byte[]> data =
          new Compiler(true).compile(inputFile, pw, null, System.getProperty("java.class.path"));
    }

    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(baos.toByteArray())))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.contains("error:")) {
          Assertions.assertTrue(line.contains(Messages.get("write.to.immutable.parameter")), line);
        }
      }
    }
  }
}
