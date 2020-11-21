package org.openjdk.btrace.client;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;
import org.openjdk.btrace.core.SharedSettings;
import org.openjdk.btrace.instr.BTraceProbe;
import org.openjdk.btrace.instr.BTraceProbeFactory;
import org.openjdk.btrace.instr.OnMethod;
import org.openjdk.btrace.instr.OnProbe;

public final class ProbePrinter {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.out.println("Usage: btracep <probe_file>");
      System.exit(0);
    }
    Path probePath = Paths.get(args[0]);

    try (InputStream probeDataStream =
        new BufferedInputStream(new FileInputStream(probePath.toFile()))) {
      BTraceProbe probe =
          new BTraceProbeFactory(SharedSettings.GLOBAL).createProbe(probeDataStream);

      probe.checkVerified();
      System.out.println("Name: " + probe.getClassName(false));
      System.out.println("Verified: " + probe.isVerified());
      System.out.println("Transforming: " + probe.isTransforming());

      System.out.println("=== Probe handlers");
      for (OnMethod om : probe.onmethods()) {
        System.out.println(om);
      }
      for (OnProbe op : probe.onprobes()) {
        System.out.println(op);
      }

      System.out.println("=== Dataholder class");
      ClassReader dataholderReader = new ClassReader(probe.getDataHolderBytecode());
      dataholderReader.accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);

      System.out.println("=== Full probe class");
      ClassReader probeReader = new ClassReader(probe.getFullBytecode());
      probeReader.accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);
    }
  }
}
