/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import static org.openjdk.btrace.instr.Constants.*;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.openjdk.btrace.runtime.Interval;

/**
 * Convenient fluent wrapper over the ASM method visitor
 *
 * @author Jaroslav Bachorik
 */
@SuppressWarnings("UnusedReturnValue")
public final class Assembler {
  private final MethodVisitor mv;
  private final MethodInstrumentorHelper mHelper;

  public Assembler(MethodVisitor mv, MethodInstrumentorHelper mHelper) {
    this.mv = mv;
    this.mHelper = mHelper;
  }

  public Assembler push(int value) {
    if (value >= -1 && value <= 5) {
      mv.visitInsn(ICONST_0 + value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      mv.visitIntInsn(BIPUSH, value);
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      mv.visitIntInsn(SIPUSH, value);
    } else {
      mv.visitLdcInsn(value);
    }
    return this;
  }

  public Assembler arrayLoad(Type type) {
    mv.visitInsn(type.getOpcode(IALOAD));
    return this;
  }

  public Assembler arrayStore(Type type) {
    mv.visitInsn(type.getOpcode(IASTORE));
    return this;
  }

  public Assembler jump(int opcode, Label l) {
    mv.visitJumpInsn(opcode, l);
    return this;
  }

  public Assembler ldc(Object o) {
    if (o == null) {
      return loadNull();
    }
    if (o instanceof Integer) {
      int i = (int) o;
      if (i >= -1 && i <= 5) {
        int opcode = -1;
        switch (i) {
          case 0:
            {
              opcode = ICONST_0;
              break;
            }
          case 1:
            {
              opcode = ICONST_1;
              break;
            }
          case 2:
            {
              opcode = ICONST_2;
              break;
            }
          case 3:
            {
              opcode = ICONST_3;
              break;
            }
          case 4:
            {
              opcode = ICONST_4;
              break;
            }
          case 5:
            {
              opcode = ICONST_5;
              break;
            }
          case -1:
            {
              opcode = ICONST_M1;
              break;
            }
        }
        mv.visitInsn(opcode);
        return this;
      }
    }
    if (o instanceof Long) {
      long l = (long) o;
      if (l >= 0 && l <= 1) {
        int opcode = -1;
        switch ((int) l) {
          case 0:
            {
              opcode = LCONST_0;
              break;
            }
          case 1:
            {
              opcode = LCONST_1;
              break;
            }
        }
        mv.visitInsn(opcode);
        return this;
      }
    }
    mv.visitLdcInsn(o);
    return this;
  }

  public Assembler sub(Type t) {
    int opcode = -1;
    switch (t.getSort()) {
      case Type.SHORT:
      case Type.BYTE:
      case Type.INT:
        {
          opcode = ISUB;
          break;
        }
      case Type.LONG:
        {
          opcode = LSUB;
          break;
        }
      case Type.FLOAT:
        {
          opcode = FSUB;
          break;
        }
      case Type.DOUBLE:
        {
          opcode = DSUB;
          break;
        }
    }
    if (opcode != -1) {
      mv.visitInsn(opcode);
    }
    return this;
  }

  public Assembler loadNull() {
    mv.visitInsn(ACONST_NULL);
    return this;
  }

  public Assembler loadLocal(Type type, int index) {
    mv.visitVarInsn(type.getOpcode(ILOAD), index);
    return this;
  }

  public Assembler storeLocal(Type type, int index) {
    mv.visitVarInsn(type.getOpcode(ISTORE), index);
    return this;
  }

  public Assembler storeField(Type owner, String name, Type t) {
    mv.visitFieldInsn(PUTFIELD, owner.getInternalName(), name, t.getDescriptor());
    return this;
  }

  public Assembler storeStaticField(Type owner, String name, Type t) {
    mv.visitFieldInsn(PUTSTATIC, owner.getInternalName(), name, t.getDescriptor());
    return this;
  }

  public Assembler loadField(Type owner, String name, Type t) {
    mv.visitFieldInsn(GETFIELD, owner.getInternalName(), name, t.getDescriptor());
    return this;
  }

  public Assembler loadStaticField(Type owner, String name, Type t) {
    mv.visitFieldInsn(GETSTATIC, owner.getInternalName(), name, t.getDescriptor());
    return this;
  }

  public Assembler pop() {
    mv.visitInsn(POP);
    return this;
  }

  public Assembler dup() {
    mv.visitInsn(DUP);
    return this;
  }

  public Assembler dup_x1() {
    mv.visitInsn(DUP_X1);
    return this;
  }

  public Assembler dup_x2() {
    mv.visitInsn(DUP_X2);
    return this;
  }

  public Assembler dup2() {
    mv.visitInsn(DUP2);
    return this;
  }

  public Assembler dup2_x1() {
    mv.visitInsn(DUP2_X1);
    return this;
  }

  public Assembler dup2_x2() {
    mv.visitInsn(DUP2_X2);
    return this;
  }

  public Assembler swap() {
    mv.visitInsn(SWAP);
    return this;
  }

  public Assembler newInstance(Type t) {
    mv.visitTypeInsn(NEW, t.getInternalName());
    return this;
  }

  public Assembler newArray(Type t) {
    mv.visitTypeInsn(ANEWARRAY, t.getInternalName());
    return this;
  }

  public Assembler dupArrayValue(int arrayOpcode) {
    switch (arrayOpcode) {
      case IALOAD:
      case FALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
      case IASTORE:
      case FASTORE:
      case AASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        dup();
        break;

      case LALOAD:
      case DALOAD:
      case LASTORE:
      case DASTORE:
        dup2();
        break;
    }
    return this;
  }

  public Assembler dupReturnValue(int returnOpcode) {
    switch (returnOpcode) {
      case IRETURN:
      case FRETURN:
      case ARETURN:
        mv.visitInsn(DUP);
        break;
      case LRETURN:
      case DRETURN:
        mv.visitInsn(DUP2);
        break;
      case RETURN:
        break;
      default:
        throw new IllegalArgumentException("not return");
    }
    return this;
  }

  public Assembler dupValue(Type type) {
    switch (type.getSize()) {
      case 1:
        dup();
        break;
      case 2:
        dup2();
        break;
    }
    return this;
  }

  public Assembler dupValue(String desc) {
    int typeCode = desc.charAt(0);
    switch (typeCode) {
      case '[':
      case 'L':
      case 'Z':
      case 'C':
      case 'B':
      case 'S':
      case 'I':
        mv.visitInsn(DUP);
        break;
      case 'J':
      case 'D':
        mv.visitInsn(DUP2);
        break;
      default:
        throw new RuntimeException("invalid signature");
    }
    return this;
  }

  public Assembler box(Type type) {
    return box(type.getDescriptor());
  }

  public Assembler box(String desc) {
    int typeCode = desc.charAt(0);
    switch (typeCode) {
      case '[':
      case 'L':
        break;
      case 'Z':
        invokeStatic(BOOLEAN_BOXED_INTERNAL, BOX_VALUEOF, BOX_BOOLEAN_DESC);
        break;
      case 'C':
        invokeStatic(CHARACTER_BOXED_INTERNAL, BOX_VALUEOF, BOX_CHARACTER_DESC);
        break;
      case 'B':
        invokeStatic(BYTE_BOXED_INTERNAL, BOX_VALUEOF, BOX_BYTE_DESC);
        break;
      case 'S':
        invokeStatic(SHORT_BOXED_INTERNAL, BOX_VALUEOF, BOX_SHORT_DESC);
        break;
      case 'I':
        invokeStatic(INTEGER_BOXED_INTERNAL, BOX_VALUEOF, BOX_INTEGER_DESC);
        break;
      case 'J':
        invokeStatic(LONG_BOXED_INTERNAL, BOX_VALUEOF, BOX_LONG_DESC);
        break;
      case 'F':
        invokeStatic(FLOAT_BOXED_INTERNAL, BOX_VALUEOF, BOX_FLOAT_DESC);
        break;
      case 'D':
        invokeStatic(DOUBLE_BOXED_INTERNAL, BOX_VALUEOF, BOX_DOUBLE_DESC);
        break;
    }
    return this;
  }

  public Assembler unbox(Type type) {
    return unbox(type.getDescriptor());
  }

  public Assembler unbox(String desc) {
    int typeCode = desc.charAt(0);
    switch (typeCode) {
      case '[':
      case 'L':
        mv.visitTypeInsn(CHECKCAST, Type.getType(desc).getInternalName());
        break;
      case 'Z':
        mv.visitTypeInsn(CHECKCAST, BOOLEAN_BOXED_INTERNAL);
        invokeVirtual(BOOLEAN_BOXED_INTERNAL, BOOLEAN_VALUE, BOOLEAN_VALUE_DESC);
        break;
      case 'C':
        mv.visitTypeInsn(CHECKCAST, CHARACTER_BOXED_INTERNAL);
        invokeVirtual(CHARACTER_BOXED_INTERNAL, CHAR_VALUE, CHAR_VALUE_DESC);
        break;
      case 'B':
        mv.visitTypeInsn(CHECKCAST, NUMBER_INTERNAL);
        invokeVirtual(NUMBER_INTERNAL, BYTE_VALUE, BYTE_VALUE_DESC);
        break;
      case 'S':
        mv.visitTypeInsn(CHECKCAST, NUMBER_INTERNAL);
        invokeVirtual(NUMBER_INTERNAL, SHORT_VALUE, SHORT_VALUE_DESC);
        break;
      case 'I':
        mv.visitTypeInsn(CHECKCAST, NUMBER_INTERNAL);
        invokeVirtual(NUMBER_INTERNAL, INT_VALUE, INT_VALUE_DESC);
        break;
      case 'J':
        mv.visitTypeInsn(CHECKCAST, NUMBER_INTERNAL);
        invokeVirtual(NUMBER_INTERNAL, LONG_VALUE, LONG_VALUE_DESC);
        break;
      case 'F':
        mv.visitTypeInsn(CHECKCAST, NUMBER_INTERNAL);
        invokeVirtual(NUMBER_INTERNAL, FLOAT_VALUE, FLOAT_VALUE_DESC);
        break;
      case 'D':
        mv.visitTypeInsn(CHECKCAST, NUMBER_INTERNAL);
        invokeVirtual(NUMBER_INTERNAL, DOUBLE_VALUE, DOUBLE_VALUE_DESC);
        break;
    }
    return this;
  }

  public Assembler defaultValue(String desc) {
    int typeCode = desc.charAt(0);
    switch (typeCode) {
      case '[':
      case 'L':
        mv.visitInsn(ACONST_NULL);
        break;
      case 'Z':
      case 'C':
      case 'B':
      case 'S':
      case 'I':
        mv.visitInsn(ICONST_0);
        break;
      case 'J':
        mv.visitInsn(LCONST_0);
        break;
      case 'F':
        mv.visitInsn(FCONST_0);
        break;
      case 'D':
        mv.visitInsn(DCONST_0);
        break;
    }
    return this;
  }

  public Assembler println(String msg) {
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitLdcInsn(msg);
    invokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
    return this;
  }

  // print the object on the top of the stack
  public Assembler printObject() {
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(
        INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
    return this;
  }

  public Assembler invokeVirtual(String owner, String method, String desc) {
    mv.visitMethodInsn(INVOKEVIRTUAL, owner, method, desc, false);
    return this;
  }

  public Assembler invokeSpecial(String owner, String method, String desc) {
    mv.visitMethodInsn(INVOKESPECIAL, owner, method, desc, false);
    return this;
  }

  public Assembler invokeStatic(String owner, String method, String desc) {
    mv.visitMethodInsn(INVOKESTATIC, owner, method, desc, false);
    return this;
  }

  public Assembler invokeDynamic(
      String name, String descriptor, Handle bootstrap, Object... bootstrapArguments) {
    mv.visitInvokeDynamicInsn(name, descriptor, bootstrap, bootstrapArguments);
    return this;
  }

  public Assembler invokeInterface(String owner, String method, String desc) {
    mv.visitMethodInsn(INVOKEVIRTUAL, owner, method, desc, true);
    return this;
  }

  public Assembler getStatic(String owner, String name, String desc) {
    mv.visitFieldInsn(GETSTATIC, owner, name, desc);
    return this;
  }

  public Assembler putStatic(String owner, String name, String desc) {
    mv.visitFieldInsn(PUTSTATIC, owner, name, desc);
    return this;
  }

  public Assembler label(Label l) {
    mv.visitLabel(l);
    return this;
  }

  public Assembler addLevelCheck(String clsName, Level level, Label jmp) {
    return addLevelCheck(clsName, level.getValue(), jmp);
  }

  public Assembler addLevelCheck(String clsName, Interval itv, Label jmp) {
    getStatic(clsName, "$btrace$$level", INT_DESC);
    if (itv.getA() <= 0) {
      if (itv.getB() != Integer.MAX_VALUE) {
        ldc(itv.getB());
        jump(IF_ICMPGT, jmp);
      }
    } else if (itv.getA() < itv.getB()) {
      if (itv.getB() == Integer.MAX_VALUE) {
        ldc(itv.getA());
        jump(IF_ICMPLT, jmp);
      } else {
        ldc(itv.getA());
        jump(IF_ICMPLT, jmp);
        getStatic(clsName, "$btrace$$level", INT_DESC);
        ldc(itv.getB());
        jump(IF_ICMPGT, jmp);
      }
    }
    return this;
  }

  /**
   * Compares the instrumentation level interval against the runtime value.
   *
   * <p>If the runtime value is fitting the level interval there will be 0 on stack upon return from
   * this method. Otherwise there will be -1.
   *
   * @param clsName The probe class name
   * @param level The probe instrumentation level
   * @return itself
   */
  public Assembler compareLevel(String clsName, Level level) {
    Interval itv = level.getValue();
    if (itv.getA() <= 0) {
      if (itv.getB() != Integer.MAX_VALUE) {
        ldc(itv.getB());
        getStatic(clsName, BTRACE_LEVEL_FLD, INT_DESC);
        sub(Type.INT_TYPE);
      }
    } else if (itv.getA() < itv.getB()) {
      if (itv.getB() == Integer.MAX_VALUE) {
        getStatic(clsName, BTRACE_LEVEL_FLD, INT_DESC);
        ldc(itv.getA());
        sub(Type.INT_TYPE);
      } else {
        Label l1 = new Label();
        Label l2 = new Label();
        ldc(itv.getA());
        jump(IF_ICMPLT, l1);
        getStatic(clsName, BTRACE_LEVEL_FLD, INT_DESC);
        ldc(itv.getB());
        jump(IF_ICMPGT, l1);
        ldc(0);
        Label l3 = new Label();
        label(l3);
        mHelper.insertFrameSameStack(l3);
        jump(GOTO, l2);
        label(l1);
        mHelper.insertFrameSameStack(l1);
        ldc(-1);
        label(l2);
        mHelper.insertFrameSameStack(l2);
      }
    }
    return this;
  }

  public Label openLinkerCheck() {
    Label l = new Label();
    invokeStatic(Constants.LINKING_FLAG_INTERNAL, "get", "()I");
    // if the linking flag is 0, then we are not in a reentrant call
    jump(IFNE, l);
    return l;
  }

  public void closeLinkerCheck(Label l) {
    label(l);
    mHelper.insertFrameSameStack(l);
  }
}
