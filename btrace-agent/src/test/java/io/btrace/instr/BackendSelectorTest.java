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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import org.junit.jupiter.api.Test;

class BackendSelectorTest {

  @Test
  void asmBackendSelectedForJava8() {
    InstrumentationBackend b = BackendSelector.select(52);
    assertInstanceOf(AsmInstrumentationBackend.class, b);
  }

  @Test
  void asmBackendSelectedForJava27() {
    InstrumentationBackend b = BackendSelector.select(71);
    assertInstanceOf(AsmInstrumentationBackend.class, b);
  }

  @Test
  void java28PlusUsesClassFileApiBackendOrSaysWhyNot() {
    if (javaFeatureVersion() >= 24) {
      assertEquals(
          "io.btrace.instr.ClassFileApiBackend", BackendSelector.select(72).getClass().getName());
    } else {
      // The backend targets Java 24 and cannot load here; the reason must surface, not ASM.
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> BackendSelector.select(72));
      assertTrue(e.getMessage().contains("needs the ClassFile API backend"), e.getMessage());
      assertNotNull(e.getCause());
    }
  }

  @Test
  void loadedClassFileApiBackendHandlesVersionsAboveTheAsmCeiling() {
    InstrumentationBackend classFileApi = new StubBackend();
    assertSame(classFileApi, BackendSelector.choose(72, classFileApi, null));
    assertInstanceOf(
        AsmInstrumentationBackend.class, BackendSelector.choose(71, classFileApi, null));
  }

  @Test
  void failedClassFileApiBackendIsReportedInsteadOfFallingBackToAsm() {
    Throwable loadFailure = new NoClassDefFoundError("java/lang/classfile/ClassFile");
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> BackendSelector.choose(72, null, loadFailure));
    assertSame(loadFailure, e.getCause());
    assertTrue(e.getMessage().contains("major version 72"), e.getMessage());
    assertTrue(e.getMessage().contains("java/lang/classfile/ClassFile"), e.getMessage());
  }

  private static int javaFeatureVersion() {
    String spec = System.getProperty("java.specification.version");
    return Integer.parseInt(spec.startsWith("1.") ? spec.substring(2) : spec);
  }

  private static final class StubBackend implements InstrumentationBackend {
    @Override
    public boolean supports(int classFileMajorVersion) {
      return true;
    }

    @Override
    public byte[] instrument(
        ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes) {
      return null;
    }
  }

  @Test
  void getMajorReadsVersionFromBytes() {
    byte[] fakeHeader = {
      (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x45
    };
    assertEquals(69, InstrumentUtils.getMajor(fakeHeader));
  }
}
