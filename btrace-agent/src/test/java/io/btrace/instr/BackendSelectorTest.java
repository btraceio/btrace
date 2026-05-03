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

import org.junit.jupiter.api.Test;

class BackendSelectorTest {

  @Test
  void asmBackendSelectedForJava8() {
    InstrumentationBackend b = BackendSelector.select(52);
    assertInstanceOf(AsmInstrumentationBackend.class, b);
  }

  @Test
  void asmBackendSelectedForJava25() {
    InstrumentationBackend b = BackendSelector.select(69);
    assertInstanceOf(AsmInstrumentationBackend.class, b);
  }

  @Test
  void nonAsmBackendOrFallbackForJava26Plus() {
    InstrumentationBackend b = BackendSelector.select(70);
    assertNotNull(b);
  }

  @Test
  void getMajorReadsVersionFromBytes() {
    byte[] fakeHeader = {
      (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x45
    };
    assertEquals(69, InstrumentUtils.getMajor(fakeHeader));
  }
}
