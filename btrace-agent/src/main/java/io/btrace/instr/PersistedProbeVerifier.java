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

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_ENUM;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BASTORE;
import static org.objectweb.asm.Opcodes.CASTORE;
import static org.objectweb.asm.Opcodes.DASTORE;
import static org.objectweb.asm.Opcodes.FASTORE;
import static org.objectweb.asm.Opcodes.IASTORE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LASTORE;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RET;
import static org.objectweb.asm.Opcodes.SASTORE;

import io.btrace.core.VerifierException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * The safety rules for a probe the agent reads back from a pack (the {@code btracec} default
 * output, or a probe persisted by a client), checked on its bytecode. A pack carries no proof that
 * it was verified when compiled: whoever can reach the agent can hand it any bytes, so the agent
 * checks them itself unless it runs in trusted mode.
 */
final class PersistedProbeVerifier {
  private PersistedProbeVerifier() {}

  /**
   * @param classData the probe class bytecode
   * @param isServiceType whether an internal class name is an injected extension service type,
   *     whose construction the probe may contain
   * @throws VerifierException on the first rule the probe breaks
   */
  static void verify(byte[] classData, Predicate<String> isServiceType) throws VerifierException {
    ClassReader cr = new ClassReader(classData);
    cr.accept(
        new ClassVisitor(ASM9) {
          private String className;
          private final CallGraph graph = new CallGraph();

          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            if ((access & ACC_INTERFACE) != 0 || (access & ACC_ENUM) != 0) {
              Verifier.reportError("btrace.program.should.be.class");
            }
            if ((access & ACC_PUBLIC) == 0) {
              Verifier.reportError("class.should.be.public", name);
            }

            if (!superName.equals(Constants.OBJECT_INTERNAL)) {
              Verifier.reportError("object.superclass.required", superName);
            }
            if (interfaces != null && interfaces.length > 0) {
              Verifier.reportError("no.interface.implementation");
            }
            className = name;
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (className.equals(outerName)) {
              Verifier.reportError("no.nested.class");
            }
          }

          @Override
          public void visitOuterClass(String s, String s1, String s2) {
            Verifier.reportError("no.outer.class");
          }

          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
            if (!Constants.BTRACE_DESC.equals(descriptor)) {
              return av;
            }
            // verifyBytecode runs only when the agent is not in trusted mode, so a probe that
            // asks for trust is refused here, as the class verifier refuses it for a plain class
            return new AnnotationVisitor(ASM9, av) {
              @Override
              public void visit(String name, Object value) {
                if (("unsafe".equals(name) || "trusted".equals(name))
                    && Boolean.TRUE.equals(value)) {
                  Verifier.reportError("agent.unsafe.not.allowed");
                }
                super.visit(name, value);
              }
            };
          }

          @Override
          public FieldVisitor visitField(
              int access, String name, String desc, String sig, Object dflt) {
            if ((access & ACC_STATIC) == 0) {
              Verifier.reportError("agent.no.instance.variables", name);
            }
            return super.visitField(access, name, desc, sig, dflt);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String methodName, String desc, String sig, String[] exceptions) {
            if ((access & ACC_SYNCHRONIZED) != 0) {
              Verifier.reportError(
                  "no.synchronized.methods",
                  TypeUtils.descriptorToSimplified(desc, className, methodName));
            }

            if (!methodName.equals(Constants.CONSTRUCTOR)) {
              if ((access & ACC_STATIC) == 0) {
                Verifier.reportError(
                    "no.instance.method",
                    TypeUtils.descriptorToSimplified(desc, className, methodName));
              }
            }

            if (methodName.equals(Constants.CLASS_INITIALIZER)) {
              return super.visitMethod(access, methodName, desc, sig, exceptions);
            }

            return new MethodVisitor(
                ASM9, super.visitMethod(access, methodName, desc, sig, exceptions)) {
              private final Map<Label, Label> labels = new HashMap<>();
              private final String methodId = CallGraph.methodId(methodName, desc);
              private boolean handler;

              @Override
              public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                handler |= descriptor.startsWith("Lio/btrace/core/annotations/");
                Verifier.verifyHandlerSignature(descriptor, methodName, desc);
                return super.visitAnnotation(descriptor, visible);
              }

              @Override
              public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (opcode == PUTFIELD) {
                  Verifier.reportError("no.assignment");
                }

                if (opcode == PUTSTATIC) {
                  if (!owner.equals(className)) {
                    Verifier.reportError("no.assignment");
                  }
                }
                super.visitFieldInsn(opcode, owner, name, desc);
              }

              @Override
              public void visitInsn(int opcode) {
                switch (opcode) {
                  case IASTORE:
                  case LASTORE:
                  case FASTORE:
                  case DASTORE:
                  case AASTORE:
                  case BASTORE:
                  case CASTORE:
                  case SASTORE:
                    Verifier.reportError("no.assignment");
                    break;
                  case ATHROW:
                    Verifier.reportError("no.throw");
                    break;
                  case MONITORENTER:
                  case MONITOREXIT:
                    Verifier.reportError("no.synchronized.blocks");
                    break;
                }
                super.visitInsn(opcode);
              }

              @Override
              public void visitIntInsn(int opcode, int operand) {
                if (opcode == NEWARRAY) {
                  Verifier.reportError("no.array.creation");
                }
                super.visitIntInsn(opcode, operand);
              }

              @Override
              public void visitLabel(Label label) {
                // A jump to a label already visited goes backwards: a loop. Without recording
                // the labels here the check below never fired and a pack could carry loops.
                labels.put(label, label);
                super.visitLabel(label);
              }

              @Override
              public void visitJumpInsn(int opcode, Label label) {
                if (labels.get(label) != null) {
                  Verifier.reportError("no.loops");
                }
                super.visitJumpInsn(opcode, label);
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean itfc) {
                if (opcode == INVOKESTATIC && owner.equals(className)) {
                  graph.addEdge(methodId, CallGraph.methodId(name, desc));
                }
                switch (opcode) {
                  case INVOKEVIRTUAL:
                    if (MethodVerifier.isPrimitiveWrapper(owner)
                        && MethodVerifier.isUnboxMethod(name)) {
                      // allow primitive type unbox methods.
                      // These calls are generated by javac for auto-unboxing `
                      // and can't be caught by source AST analyzer as well.
                    } else if (owner.equals(Constants.STRING_BUILDER_INTERNAL)) {
                      // allow string concatenation via StringBuilder
                    } else if (owner.equals(Constants.THREAD_LOCAL_INTERNAL)) {
                      // allow ThreadLocal methods
                    } else if (owner.equals(Constants.BTRACERTACCESS_INTERNAL)) {
                      // allow BTraceRuntimeAccess methods
                    } else if (owner.equals(Constants.BTRACERTBRIDGE_INTERNAL)) {
                      // allow BTraceRuntimeImplBase methods
                    } else {
                      if (!isServiceType.test(owner)) {
                        Verifier.reportError("no.method.calls", owner + "." + name + desc);
                      }
                    }
                    break;
                  case INVOKEINTERFACE:
                    // allow BTraceRuntimeBridge interface methods (leave(), enter(), etc.)
                    if (!owner.equals(Constants.BTRACERTBRIDGE_INTERNAL)
                        && !isServiceType.test(owner)) {
                      Verifier.reportError("no.method.calls", owner + "." + name + desc);
                    }
                    break;
                  case INVOKESPECIAL:
                    if (owner.equals(Constants.OBJECT_INTERNAL)
                        && name.equals(Constants.CONSTRUCTOR)) {
                      // allow object initializer
                    } else if (owner.equals(Constants.STRING_BUILDER_INTERNAL)) {
                      // allow string concatenation via StringBuilder
                    } else if (owner.equals(Constants.THREAD_LOCAL_INTERNAL)) {
                      // allow ThreadLocal methods
                    } else if (isServiceType.test(owner)) {
                      // allow services invocations
                    } else {
                      Verifier.reportError("no.method.calls", owner + "." + name + desc);
                    }
                    break;
                  case INVOKESTATIC:
                    if (!owner.startsWith("io/btrace/") && !owner.equals(className)) {
                      if ("valueOf".equals(name) && MethodVerifier.isPrimitiveWrapper(owner)) {
                        // allow primitive wrapper boxing methods.
                        // These calls are generated by javac for autoboxing
                        // and can't be caught sourc AST analyzer as well.
                      } else {
                        Verifier.reportError("no.method.calls", owner + "." + name + desc);
                      }
                    }
                    break;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itfc);
              }

              @Override
              public void visitMultiANewArrayInsn(String desc, int dims) {
                Verifier.reportError("no.array.creation");
              }

              @Override
              public void visitTypeInsn(int opcode, String desc) {
                if (opcode == ANEWARRAY) {
                  Verifier.reportError("no.array.creation", desc);
                }
                if (opcode == NEW) {
                  // allow StringBuilder creation for string concatenation
                  if (!desc.equals(Constants.STRING_BUILDER_INTERNAL)
                      && !isServiceType.test(desc)) {
                    Verifier.reportError("no.new.object", desc);
                  }
                }
                super.visitTypeInsn(opcode, desc);
              }

              @Override
              public void visitVarInsn(int opcode, int var) {
                if (opcode == RET) {
                  Verifier.reportError("no.try");
                }
                super.visitVarInsn(opcode, var);
              }

              @Override
              public void visitEnd() {
                if (handler) {
                  graph.addStarting(methodId);
                }
                super.visitEnd();
              }
            };
          }

          @Override
          public void visitEnd() {
            if (graph.hasCycle()) {
              Verifier.reportError("execution.loop.danger");
            }
            super.visitEnd();
          }
        },
        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    // Last, so a handler that recurses is reported as the endless loop it is
    HandlerCallVerifier.check(cr);
  }
}
