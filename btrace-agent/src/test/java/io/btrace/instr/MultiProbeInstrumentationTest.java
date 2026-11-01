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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

/**
 * Several probes applied to one class in a single transformation, as the agent does when more than
 * one client is attached. Each probe gets its own {@link Instrumentor}, chained one into the next,
 * so the code and stack map frames one probe emits pass through the instrumentors of the probes
 * registered before it.
 */
class MultiProbeInstrumentationTest extends InstrumentorTestBase {
  @Test
  void errorProbeAfterReturnDurationProbePassesVerification() throws Exception {
    loadTargetClass("OnMethodTest");
    transformAll("onmethod/ArgsDuration", "onmethod/ArgsDurationErr");

    assertVerifies();
  }

  @Test
  void returnDurationProbeAfterErrorProbePassesVerification() throws Exception {
    loadTargetClass("OnMethodTest");
    transformAll("onmethod/ArgsDurationErr", "onmethod/ArgsDuration");

    assertVerifies();
  }

  private void transformAll(String... traceNames) throws Exception {
    ClassLoader loader = MultiProbeInstrumentationTest.class.getClassLoader();
    BTraceClassReader cr = InstrumentUtils.newClassReader(loader, originalBC);
    BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
    for (String traceName : traceNames) {
      cw.addInstrumentor(loadTrace(traceName), loader);
    }
    transformedBC = cw.instrument();
    assertNotNull(transformedBC, "no probe matched");
  }

  /**
   * Links the transformed class, which runs HotSpot's verifier against the emitted stack map
   * frames. ASM's analyzer recomputes frames on its own and would not notice a wrong one.
   */
  private void assertVerifies() {
    String name = new ClassReader(transformedBC).getClassName().replace('/', '.');
    VerifyingLoader loader = new VerifyingLoader(MultiProbeInstrumentationTest.class);
    assertDoesNotThrow(() -> Class.forName(name, true, loader.define(name, transformedBC)));
  }

  private static final class VerifyingLoader extends ClassLoader {
    VerifyingLoader(Class<?> parentOf) {
      super(parentOf.getClassLoader());
    }

    ClassLoader define(String name, byte[] code) {
      defineClass(name, code, 0, code.length);
      return this;
    }
  }
}
