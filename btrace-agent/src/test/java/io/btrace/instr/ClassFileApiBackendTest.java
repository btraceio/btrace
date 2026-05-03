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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * Tests for ClassFileApiBackend. All tests are enabled only on JDK 24+ where java.lang.classfile is
 * available (and the backend class is loadable).
 */
@EnabledForJreRange(min = JRE.JAVA_24)
class ClassFileApiBackendTest {

  @Test
  void supportsVersionAbove69() {
    InstrumentationBackend backend = BackendSelector.select(70);
    assertFalse(
        backend instanceof AsmInstrumentationBackend,
        "Expected ClassFile API backend for version 70 on JDK 24+");
    assertTrue(backend.supports(70));
    assertTrue(backend.supports(80));
    assertFalse(backend.supports(69));
  }

  @Test
  void returnsNullWhenNoProbesMatch() {
    InstrumentationBackend backend = BackendSelector.select(70);
    // Pass empty probe list — no match possible
    byte[] result = backend.instrument(null, buildMinimalClass(70), Collections.emptyList());
    assertNull(result);
  }

  /**
   * Loads the bytes of a known class file on the classpath and patches bytes 6-7 to simulate a
   * future/EA JDK class version. This produces a syntactically valid class body (only the version
   * header is patched), which is sufficient for ClassFileApiBackend to parse.
   */
  static byte[] buildMinimalClass(int majorVersion) {
    try {
      byte[] bytes;
      try (java.io.InputStream is =
          ClassFileApiBackendTest.class.getResourceAsStream(
              "/io/btrace/instr/AsmInstrumentationBackend.class")) {
        if (is == null)
          throw new IllegalStateException("AsmInstrumentationBackend.class not found on classpath");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        bytes = baos.toByteArray();
      }
      bytes[6] = (byte) (majorVersion >> 8);
      bytes[7] = (byte) (majorVersion & 0xFF);
      return bytes;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
