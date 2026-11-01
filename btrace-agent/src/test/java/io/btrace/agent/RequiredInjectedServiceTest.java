/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class RequiredInjectedServiceTest {
  private static final String BSM_DESC =
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
          + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;"
          + "Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

  /** A probe class whose two handlers read {@code metrics} and one reads {@code optionalSvc}. */
  private static byte[] probeClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
    Handle bsm =
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "io/btrace/runtime/ExtensionIndy",
            "bootstrapFieldGet",
            BSM_DESC,
            false);
    String[][] reads = {
      {"metrics", "io.btrace.metrics.MetricsService", "0", "UNSPECIFIED"},
      {"metrics", "io.btrace.metrics.MetricsService", "0", "UNSPECIFIED"},
      {"optionalSvc", "io.btrace.utils.PrinterService", "1", "THROW"}
    };
    int idx = 0;
    for (String[] read : reads) {
      MethodVisitor mv =
          cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "h" + idx++, "()V", null, null);
      mv.visitCode();
      String type = "L" + read[1].replace('.', '/') + ";";
      mv.visitInvokeDynamicInsn(
          read[0], "()" + type, bsm, read[1], "SIMPLE", "", Integer.valueOf(read[2]), read[3]);
      mv.visitInsn(Opcodes.POP);
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void scanFindsEachInjectedFieldOnce() {
    List<InjectedServiceUse> uses = InjectedServiceUse.scan(probeClass());
    assertEquals(2, uses.size());
    assertEquals("metrics", uses.get(0).fieldName);
    assertEquals("io.btrace.metrics.MetricsService", uses.get(0).serviceClassName);
    assertEquals(0, uses.get(0).optionalFlag);
    assertEquals("optionalSvc", uses.get(1).fieldName);
    assertEquals(1, uses.get(1).optionalFlag);
    assertEquals("THROW", uses.get(1).injectMode);
  }

  @Test
  void unavailableRequiredServiceRefusesTheProbe() {
    List<InjectedServiceUse> uses = InjectedServiceUse.scan(probeClass());
    String error =
        Client.requiredServiceError(
            "TestProbe",
            uses,
            use -> use.optionalFlag == 0 ? "No implementation available" : null,
            svc -> "btrace-metrics: Blocked by policy (denyExtensions)");
    assertTrue(error.startsWith("Probe TestProbe was not deployed:"), error);
    assertTrue(
        error.contains(
            "required @Injected service io.btrace.metrics.MetricsService (field 'metrics')"
                + " is unavailable: btrace-metrics: Blocked by policy (denyExtensions)"),
        error);
    assertTrue(error.contains("@Injected(optional = true)"), error);
  }

  @Test
  void availableServicesDeployTheProbe() {
    assertNull(
        Client.requiredServiceError(
            "TestProbe", InjectedServiceUse.scan(probeClass()), use -> null, svc -> null));
    assertNull(Client.requiredServiceError("TestProbe", Arrays.asList(), use -> "x", svc -> null));
  }
}
