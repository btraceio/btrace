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

import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import io.btrace.core.VerifierException;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * Refuses a probe in which one guarded handler calls another. The preprocessor starts every guarded
 * handler ({@link Preprocessor#GUARDED_ANNOTS}) with {@code if (!enter()) return;}, and {@code
 * enter()} fails on a thread already running probe code, so the called handler returns at once: the
 * call compiles and silently does nothing. Handlers the preprocessor does not guard
 * ({@code @OnExit}, {@code @OnLowMemory}) may call guarded ones; code shared between handlers
 * belongs in a plain helper method.
 */
final class HandlerCallVerifier {
  private HandlerCallVerifier() {}

  static void check(ClassReader cr) throws VerifierException {
    Set<String> guarded = new HashSet<>();
    Set<String> unguardedHandlers = new HashSet<>();
    cr.accept(
        new ClassVisitor(ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exceptions) {
            String id = CallGraph.methodId(name, desc);
            return new MethodVisitor(ASM9) {
              @Override
              public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                if (Preprocessor.GUARDED_ANNOTS.contains(annotation)) {
                  guarded.add(id);
                } else if (Preprocessor.RT_AWARE_ANNOTS.contains(annotation)) {
                  unguardedHandlers.add(id);
                }
                return null;
              }
            };
          }
        },
        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    if (guarded.isEmpty()) {
      return;
    }
    String className = cr.getClassName();
    cr.accept(
        new ClassVisitor(ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exceptions) {
            if (unguardedHandlers.contains(CallGraph.methodId(name, desc))) {
              return null;
            }
            return new MethodVisitor(ASM9) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String callee, String calleeDesc, boolean itf) {
                if (opcode == INVOKESTATIC
                    && owner.equals(className)
                    && guarded.contains(CallGraph.methodId(callee, calleeDesc))) {
                  Verifier.reportError("no.handler.calls", name + " calls " + callee);
                }
              }
            };
          }
        },
        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }
}
