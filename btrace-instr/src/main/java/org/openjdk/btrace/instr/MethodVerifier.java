/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.services.api.Service;

/**
 * This class verifies that the BTrace "action" method is safe - boundedness and read-only rules are
 * checked such as no backward jumps (loops), no throw/new/invoke etc.
 *
 * @author A. Sundararajan
 */
final class MethodVerifier extends StackTrackingMethodVisitor {
  private static final Set<String> PRIMITIVE_WRAPPER_TYPES;
  private static final Set<String> UNBOX_METHODS;

  static {
    PRIMITIVE_WRAPPER_TYPES = new HashSet<>();
    UNBOX_METHODS = new HashSet<>();

    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Boolean");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Byte");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Character");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Short");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Integer");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Long");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Float");
    PRIMITIVE_WRAPPER_TYPES.add("java/lang/Double");
    UNBOX_METHODS.add("booleanValue");
    UNBOX_METHODS.add("byteValue");
    UNBOX_METHODS.add("charValue");
    UNBOX_METHODS.add("shortValue");
    UNBOX_METHODS.add("intValue");
    UNBOX_METHODS.add("longValue");
    UNBOX_METHODS.add("floatValue");
    UNBOX_METHODS.add("doubleValue");
  }

  private final String className;
  private final String methodName;
  private final String methodDesc;
  private final int access;
  private final Map<Label, Label> labels;
  private Object delayedClzLoad = null;

  public MethodVerifier(
      BTraceMethodNode parent,
      int access,
      String className,
      String methodName,
      String desc,
      ClassLoader ctxClassLoader) {
    super(parent, className, desc, ((access & ACC_STATIC) == ACC_STATIC));
    this.className = className;
    this.methodName = methodName;
    methodDesc = desc;
    this.access = access;
    labels = new HashMap<>();
  }

  static boolean isPrimitiveWrapper(String type) {
    return PRIMITIVE_WRAPPER_TYPES.contains(type);
  }

  static boolean isUnboxMethod(String name) {
    return UNBOX_METHODS.contains(name);
  }

  private BTraceMethodNode getParent() {
    return (BTraceMethodNode) mv;
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
    if (getParent().isBTraceHandler()) { // only btrace handlers are enforced to be public
      if ((access & ACC_PUBLIC) == 0 && !methodName.equals(Constants.CLASS_INITIALIZER)) {
        Verifier.reportError("method.should.be.public", methodName + methodDesc);
      }
      if (!Type.getReturnType(methodDesc).equals(Type.VOID_TYPE)) {
        Verifier.reportError("return.type.should.be.void", methodName + methodDesc);
      }
    }
    validateSamplerLocation();
    labels.clear();
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
  public void visitJumpInsn(int opcode, Label label) {
    if (labels.get(label) != null) {
      Verifier.reportError("no.loops");
    }
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLabel(Label label) {
    labels.put(label, label);
    super.visitLabel(label);
  }

  @Override
  public void visitLdcInsn(Object cst) {
    if (cst instanceof Type) {
      delayedClzLoad = cst;
    }
    super.visitLdcInsn(cst);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    if (!isJfrEventType(owner)) {
      switch (opcode) {
        case INVOKEVIRTUAL:
          if (isPrimitiveWrapper(owner) && isUnboxMethod(name)) {
            // allow primitive type unbox methods.
            // These calls are generated by javac for auto-unboxing
            // and can't be caught by source AST analyzer as well.
          } else if (owner.equals(Type.getInternalName(StringBuilder.class))) {
            // allow string concatenation via StringBuilder
          } else {
            List<StackItem> args = getMethodParams(desc, false);
            if (!isServiceTarget(args.get(0))) {
              Verifier.reportError("no.method.calls", owner + "." + name + desc);
            }
          }
          break;
        case INVOKEINTERFACE:
          Verifier.reportError("no.method.calls", owner + "." + name + desc);
          break;
        case INVOKESPECIAL:
          if (owner.equals(Constants.OBJECT_INTERNAL) && name.equals(Constants.CONSTRUCTOR)) {
            // allow object initializer
          } else if (owner.equals(Type.getInternalName(StringBuilder.class))) {
            // allow string concatenation via StringBuilder
          } else {
            Verifier.reportError("no.method.calls", owner + "." + name + desc);
          }
          break;
        case INVOKESTATIC:
          if (owner.equals(Constants.SERVICE)) {
            delayedClzLoad = null;
          } else if (!owner.equals(Constants.BTRACE_UTILS)
              && !owner.startsWith(Constants.BTRACE_UTILS + "$")
              && !owner.equals(className)) {
            if ("valueOf".equals(name) && isPrimitiveWrapper(owner)) {
              // allow primitive wrapper boxing methods.
              // These calls are generated by javac for autoboxing
              // and can't be caught sourc AST analyzer as well.
            } else {
              Verifier.reportError("no.method.calls", owner + "." + name + desc);
            }
          }
          break;
      }
    }
    if (delayedClzLoad != null) {
      Verifier.reportError("no.class.literals", delayedClzLoad.toString());
    }
    super.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    Verifier.reportError("no.array.creation");
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
    Verifier.reportError("no.catch");
  }

  @Override
  public void visitTypeInsn(int opcode, String desc) {
    if (opcode == ANEWARRAY) {
      Verifier.reportError("no.array.creation", desc);
    }
    if (opcode == NEW) {
      // allow StringBuilder creation for string concatenation
      if (desc.equals(Type.getInternalName(StringBuilder.class))) {
        super.visitTypeInsn(NEW, desc);
        return;
      }
      Verifier.reportError("no.new.object", desc);
    }
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    if (opcode == RET) {
      Verifier.reportError("no.try");
    }
    super.visitVarInsn(opcode, var);
  }

  private boolean isServiceTarget(StackItem si) {
    if (si instanceof ResultItem) {
      ResultItem ri = (ResultItem) si;
      if (ri.getOwner().equals(Type.getInternalName(Service.class))) {
        return true;
      } else if (ri.getOwner().equals(className) && getParent().isFieldInjected(ri.getName())) {
        return true;
      }
    }
    for (StackItem p : si.getParents()) {
      if (isServiceTarget(p)) {
        return true;
      }
    }
    return false;
  }

  private void validateSamplerLocation() {
    BTraceMethodNode mn = getParent();
    // if not sampled just return
    if (!mn.isSampled()) return;

    OnMethod om = mn.getOnMethod();
    if (om == null && mn.isSampled()) {
      Verifier.reportError("sampler.invalid.location", methodName + methodDesc);
      return;
    }
    if (om != null && om.getSamplerKind() != Sampled.Sampler.None) {
      switch (om.getLocation().getValue()) {
        case ENTRY:
        case RETURN:
        case ERROR:
        case CALL:
          {
            // ok
            break;
          }
        default:
          {
            Verifier.reportError("sampler.invalid.location", methodName + methodDesc);
          }
      }
    }
  }

  private boolean isJfrEventType(String typeName) {
    return "org/openjdk/btrace/core/jfr/JfrEvent".equals(typeName);
  }
}
