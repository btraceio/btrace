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
package io.btrace.compiler;

import static org.junit.jupiter.api.Assertions.fail;

import io.btrace.core.SharedSettings;
import io.btrace.instr.BTraceProbe;
import io.btrace.instr.BTraceProbeFactory;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

public class JfrEventsTest {
  @Test
  public void testCompile() throws Exception {
    URL input = JfrEventsTest.class.getResource("/JfrEventsProbe.java");
    File inputFile = new File(input.toURI());
    Map<String, byte[]> data =
        new Compiler(true)
            .compile(
                inputFile,
                new PrintWriter(System.err),
                null,
                System.getProperty("java.class.path"));
    BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);
    for (byte[] bytes : data.values()) {
      BTraceProbe probe = factory.createProbe(bytes);
      verifyCode(probe.getFullBytecode());
      verifyCode(probe.getDataHolderBytecode());
    }
  }

  private void verifyCode(byte[] code) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    CheckClassAdapter.verify(new ClassReader(code), true, pw);
    if (sw.toString().contains("AnalyzerException")) {
      System.err.println(sw);
      fail();
    }
  }
}
