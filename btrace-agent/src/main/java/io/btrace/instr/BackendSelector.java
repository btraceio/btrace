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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses an {@link InstrumentationBackend} based on the class file major version. The ASM backend
 * handles versions &le; {@link AsmInstrumentationBackend#MAX_ASM_MAJOR_VERSION}. For higher
 * versions the JDK ClassFile API backend is attempted; it is loaded reflectively so there is no
 * compile-time dependency on {@code java.lang.classfile} in the main (Java 8-compiled) source set.
 */
final class BackendSelector {
  private static final Logger log = LoggerFactory.getLogger(BackendSelector.class);

  private static final InstrumentationBackend ASM = new AsmInstrumentationBackend();
  private static final InstrumentationBackend CLASSFILE_API = loadClassFileApiBackend();

  private BackendSelector() {}

  private static InstrumentationBackend loadClassFileApiBackend() {
    try {
      Class<?> cls =
          Class.forName(
              "io.btrace.instr.ClassFileApiBackend", true, BackendSelector.class.getClassLoader());
      return (InstrumentationBackend) cls.getDeclaredConstructor().newInstance();
    } catch (Throwable t) {
      log.debug("ClassFile API backend unavailable (expected on JDK < 24): {}", t.getMessage());
      return null;
    }
  }

  /**
   * Returns the most appropriate backend for the given class file major version.
   *
   * <p>Uses the ASM backend for versions &le; 69 (Java 25). For higher versions uses the ClassFile
   * API backend when available, otherwise falls back to ASM.
   */
  static InstrumentationBackend select(int classFileMajorVersion) {
    if (!ASM.supports(classFileMajorVersion) && CLASSFILE_API != null) {
      return CLASSFILE_API;
    }
    return ASM;
  }
}
