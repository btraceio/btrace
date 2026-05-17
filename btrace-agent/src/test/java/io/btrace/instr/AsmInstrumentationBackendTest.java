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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsmInstrumentationBackendTest {

  @Test
  void supportsVersionsUpToMaxAsm() {
    AsmInstrumentationBackend backend = new AsmInstrumentationBackend();
    assertTrue(backend.supports(52)); // Java 8
    assertTrue(backend.supports(65)); // Java 21
    assertTrue(backend.supports(69)); // Java 25 — ASM ceiling
    assertFalse(backend.supports(70)); // Java 26 — not yet
    assertFalse(backend.supports(100));
  }

  @Test
  void instrumentWithNoProbesReturnsNull() {
    AsmInstrumentationBackend backend = new AsmInstrumentationBackend();
    byte[] classBytes = loadSelfBytes();
    byte[] result = backend.instrument(null, classBytes, java.util.Collections.emptyList());
    assertNull(result);
  }

  private byte[] loadSelfBytes() {
    try (java.io.InputStream is =
        getClass().getResourceAsStream("/" + getClass().getName().replace('.', '/') + ".class")) {
      assertNotNull(is);
      java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      int n;
      while ((n = is.read(chunk)) != -1) {
        buf.write(chunk, 0, n);
      }
      return buf.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
