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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * ClassFile API counterpart to {@link LinkerInstrumentor} for class-file versions that ASM
 * cannot parse (major &gt; 69, i.e. Java 26+). Injects the same {@code LinkingFlag} guards
 * into {@code MethodHandleNatives.linkCallSite} and
 * {@code MethodHandleNatives.linkMethodHandleConstant} as the ASM version does, preventing
 * BTrace probes from firing while an {@code invokedynamic} call-site is being linked.
 */
public final class ClassFileApiLinkerGuard {

  private static final ClassDesc CD_LINKING_FLAG =
      ClassDesc.ofInternalName("io/btrace/runtime/LinkingFlag");
  private static final MethodTypeDesc GUARD_MTD = MethodTypeDesc.ofDescriptor("()I");
  private static final MethodTypeDesc RESET_MTD = MethodTypeDesc.ofDescriptor("()V");

  private ClassFileApiLinkerGuard() {}

  /**
   * Instruments the supplied {@code MethodHandleNatives} class bytes with the linker guard.
   *
   * @param classData raw bytes of {@code java.lang.invoke.MethodHandleNatives}
   * @return instrumented bytes, or {@code null} if instrumentation failed
   */
  public static byte[] addGuard(byte[] classData) {
    ClassFile cf = ClassFile.of();
    ClassModel classModel = cf.parse(classData);
    return cf.transformClass(
        classModel,
        ClassTransform.transformingMethods(
            mm -> {
              String name = mm.methodName().stringValue();
              return name.equals("linkCallSite") || name.equals("linkMethodHandleConstant");
            },
            (methodBuilder, methodElement) -> {
              if (methodElement instanceof java.lang.classfile.CodeModel cm) {
                methodBuilder.transformCode(cm, new LinkerGuardCodeTransform());
              } else {
                methodBuilder.with(methodElement);
              }
            }));
  }

  /**
   * Stateful CodeTransform that wraps a single method body with the LinkingFlag guard:
   *
   * <pre>
   * LinkingFlag.guardLinking();   // at entry
   * try {
   *   &lt;original body&gt;             // each ARETURN prepended with reset()
   * } catch (Throwable t) {
   *   LinkingFlag.reset();
   *   throw t;
   * }
   * </pre>
   */
  private static final class LinkerGuardCodeTransform implements CodeTransform {

    private Label startLabel;
    private Label endLabel;
    private Label handlerLabel;

    @Override
    public void atStart(CodeBuilder cb) {
      startLabel = cb.newLabel();
      endLabel = cb.newLabel();
      handlerLabel = cb.newLabel();

      // Mark the beginning of the try block.
      cb.labelBinding(startLabel);

      // guardLinking() returns an int we discard.
      cb.invokestatic(CD_LINKING_FLAG, "guardLinking", GUARD_MTD);
      cb.pop();
    }

    @Override
    public void accept(CodeBuilder cb, CodeElement element) {
      // Both linkCallSite and linkMethodHandleConstant return a reference type (ARETURN).
      if (element instanceof ReturnInstruction) {
        cb.invokestatic(CD_LINKING_FLAG, "reset", RESET_MTD);
      }
      cb.with(element);
    }

    @Override
    public void atEnd(CodeBuilder cb) {
      // Bind end-of-try and handler labels together, then register the catch-all range.
      cb.labelBinding(endLabel);
      cb.labelBinding(handlerLabel);
      cb.exceptionCatchAll(startLabel, endLabel, handlerLabel);

      // catch (Throwable t) { reset(); throw t; }
      int exSlot = cb.allocateLocal(TypeKind.REFERENCE);
      cb.astore(exSlot);
      cb.invokestatic(CD_LINKING_FLAG, "reset", RESET_MTD);
      cb.aload(exSlot);
      cb.athrow();
    }
  }
}
