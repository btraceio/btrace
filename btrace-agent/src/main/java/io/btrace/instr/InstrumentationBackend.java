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
 * SPI for bytecode instrumentation backends. BTrace ships two implementations: {@link
 * AsmInstrumentationBackend} (default, class file versions &le; 69) and {@code ClassFileApiBackend}
 * (JDK 24+, used for versions &gt; 69 that ASM cannot parse).
 */
interface InstrumentationBackend {

  /** Returns {@code true} when this backend can process the given class file major version. */
  boolean supports(int classFileMajorVersion);

  /**
   * Instruments {@code classfileBuffer} by applying all applicable probes.
   *
   * @param loader the classloader loading the target class (may be {@code null})
   * @param classfileBuffer raw class file bytes
   * @param probes all currently registered probes
   * @return transformed class bytes if at least one probe matched, {@code null} otherwise
   */
  byte[] instrument(ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes);
}
