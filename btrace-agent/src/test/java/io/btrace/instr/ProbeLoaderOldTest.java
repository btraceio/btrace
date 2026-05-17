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

import java.io.InputStream;

import io.btrace.core.SharedSettings;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProbeLoaderOldTest {
  private static BTraceProbeFactory BPF;
  private InputStream classStream;
  private byte[] defData;

  @BeforeAll
  public static void setupClass() throws Exception {
    BPF = new BTraceProbeFactory(SharedSettings.GLOBAL);
  }

  @BeforeEach
  public void setup() throws Exception {
    classStream =
        ProbeLoaderOldTest.class.getResourceAsStream("/resources/classdata/TraceScript.clazz");
  }

  @Test
  public void testPersistedProbeLoad() throws Exception {
    BTraceProbe bp;
    long t1 = System.nanoTime();
    try {
      bp = BPF.createProbe(classStream);
    } finally {
      System.err.println("# Creating probe took: " + (System.nanoTime() - t1) + "ns");
    }
    Assertions.assertNotNull(bp);
    Assertions.assertNotNull(bp.getClassName());
  }
}
