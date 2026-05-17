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

import java.util.Collection;

/**
 * The default instrumentation backend; delegates to the existing ASM-based pipeline. Supports class
 * file major versions up to {@value #MAX_ASM_MAJOR_VERSION} (Java 25), which is the ceiling for ASM
 * 9.9.x.
 */
final class AsmInstrumentationBackend implements InstrumentationBackend {

  /** Highest class file major version ASM 9.9.x can parse without throwing. */
  static final int MAX_ASM_MAJOR_VERSION = 69; // Java 25

  @Override
  public boolean supports(int classFileMajorVersion) {
    return classFileMajorVersion <= MAX_ASM_MAJOR_VERSION;
  }

  @Override
  public byte[] instrument(
      ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes) {
    BTraceClassReader cr = InstrumentUtils.newClassReader(loader, classfileBuffer);
    BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
    for (BTraceProbe p : probes) {
      cw.addInstrumentor(p, loader);
    }
    return cw.instrument();
  }
}
