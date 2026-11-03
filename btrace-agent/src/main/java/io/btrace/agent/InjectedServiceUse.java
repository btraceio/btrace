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
package io.btrace.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * An {@code @Injected} field read by a probe, as linked by the {@code
 * ExtensionIndy.bootstrapFieldGet} invokedynamic call sites in the probe's processed bytecode.
 */
final class InjectedServiceUse {
  private static final String INDY_OWNER = "io/btrace/runtime/ExtensionIndy";
  private static final String INDY_NAME = "bootstrapFieldGet";

  final String fieldName;
  final String serviceClassName;
  final int optionalFlag;
  final String injectMode;

  InjectedServiceUse(
      String fieldName, String serviceClassName, int optionalFlag, String injectMode) {
    this.fieldName = fieldName;
    this.serviceClassName = serviceClassName;
    this.optionalFlag = optionalFlag;
    this.injectMode = injectMode;
  }

  /** Lists the injected services the probe class reads, one entry per field. */
  static List<InjectedServiceUse> scan(byte[] classBytes) {
    Map<String, InjectedServiceUse> uses = new LinkedHashMap<>();
    if (classBytes == null) {
      return new ArrayList<>();
    }
    new ClassReader(classBytes)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitInvokeDynamicInsn(
                      String name, String desc, Handle bsm, Object... bsmArgs) {
                    if (INDY_OWNER.equals(bsm.getOwner())
                        && INDY_NAME.equals(bsm.getName())
                        && bsmArgs.length >= 5) {
                      uses.putIfAbsent(
                          name,
                          new InjectedServiceUse(
                              name,
                              String.valueOf(bsmArgs[0]),
                              bsmArgs[3] instanceof Integer ? (Integer) bsmArgs[3] : 0,
                              String.valueOf(bsmArgs[4])));
                    }
                  }
                };
              }
            },
            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return new ArrayList<>(uses.values());
  }
}
