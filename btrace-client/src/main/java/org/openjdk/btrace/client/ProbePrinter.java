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
package io.btrace.client;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;
import io.btrace.core.SharedSettings;
import io.btrace.core.extensions.Permission;
import io.btrace.instr.BTraceProbe;
import io.btrace.instr.BTraceProbeFactory;
import io.btrace.instr.OnMethod;
import io.btrace.instr.OnProbe;

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

      Set<Permission> requiredPermissions = probe.getRequiredPermissions();
      if (!requiredPermissions.isEmpty()) {
        System.out.println("Required permissions: " + requiredPermissions);
      }

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
