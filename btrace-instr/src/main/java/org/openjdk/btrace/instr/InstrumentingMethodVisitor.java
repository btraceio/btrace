/*
 * Copyright (c) 2017, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */

package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A method visitor providing support for introducing new local variables in bytecode recomputing
 * stackmap frames as necessary. It also provides an API for downstream visitors to hint insertion
 * of stackmap frames at required locations.
 */
public final class InstrumentingMethodVisitor extends MethodVisitor
    implements MethodInstrumentorHelper {
  private static final Logger log = LoggerFactory.getLogger(InstrumentingMethodVisitor.class);

  interface FrameDiagnosticListener {
    void onFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack);
  }

  static final Object TOP_EXT = -2;
  private final VariableMapper variableMapper;
  private final SimulatedStack stack = new SimulatedStack();
  private final List<Object> locals = new ArrayList<>();
  private final Set<LocalVarSlot> newLocals = new HashSet<>(3);
  private final LocalVarTypes localTypes = new LocalVarTypes();
  private final Set<Integer> frameOffsets = new HashSet<>();
  private final Map<Label, SavedState> jumpTargetStates = new HashMap<>();
  private final Map<Label, Set<Label>> tryCatchHandlerMap = new HashMap<>();
  private final String owner;
  private final String desc;
  private int argsSize = 0;
  private int localsTailPtr = 0;
  private int pc = 0, lastFramePc = Integer.MIN_VALUE;
  private final FrameDiagnosticListener frameDiagnosticListener;

  public InstrumentingMethodVisitor(int access, String owner, String desc, MethodVisitor mv) {
    this(access, owner, desc, mv, (type, nLocal, local, nStack, stack) -> {});
  }

  InstrumentingMethodVisitor(int access, String owner, String desc, MethodVisitor mv, FrameDiagnosticListener frameDiagnosticListener) {
    super(ASM9, mv);
    this.owner = owner;
    this.desc = desc;

    initLocals((access & ACC_STATIC) == 0, "<init>".equals(name));
    variableMapper = new VariableMapper(argsSize);
    this.frameDiagnosticListener = frameDiagnosticListener;
  }

  private static Object toSlotType(Type t) {
    if (t == null) {
      return null;
    }
    switch (t.getSort()) {
      case Type.BOOLEAN:
      case Type.CHAR:
      case Type.BYTE:
      case Type.SHORT:
      case Type.INT:
        {
          return INTEGER;
        }
      case Type.FLOAT:
        {
          return FLOAT;
        }
      case Type.LONG:
        {
          return LONG;
        }
      case Type.DOUBLE:
        {
          return DOUBLE;
        }
      default:
        {
          return t == Constants.NULL_TYPE
              ? NULL
              : (t == Constants.TOP_TYPE ? TOP : t.getInternalName());
        }
    }
  }

  @Override
  public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
    if (lastFramePc == pc) {
      return;
    }
    lastFramePc = pc;

    switch (type) {
      case F_NEW: // fallthrough
      case F_FULL:
        {
          locals.clear();
          this.stack.reset();

          if (nLocal > 0 && locals != null) {
            locals.addAll(Arrays.asList(local).subList(0, nLocal));
            localsTailPtr = nLocal;
          } else {
            localsTailPtr = 0;
          }

          if (nStack > 0 && stack != null) {
            for (int i = 0; i < nStack; i++) {
              Object e = stack[i];
              this.stack.push(e);
            }
          }
          break;
        }
      case F_SAME:
        {
          this.stack.reset();
          break;
        }
      case F_SAME1:
        {
          this.stack.reset();
          Object e = stack[0];
          this.stack.push(e);
          break;
        }
      case F_APPEND:
        {
          this.stack.reset();
          int top = locals.size();
          for (int i = 0; i < nLocal; i++) {
            Object e = local[i];
            if (localsTailPtr < top) {
              locals.set(localsTailPtr, e);
            } else {
              locals.add(e);
            }
            localsTailPtr++;
          }
          break;
        }
      case F_CHOP:
        {
          this.stack.reset();
          for (int i = 0; i < nLocal; i++) {
            locals.remove(--localsTailPtr);
          }
          break;
        }
    }

    Object[] localsArr = computeFrameLocals();
    localTypes.replaceWith(localsArr);

    int off = 0;
    for (int i = 0; i < localsArr.length; i++) {
      Object val = localsArr[i];
      if (val == TOP_EXT) {
        off++;
        continue;
      }
      if (off > 0) {
        localsArr[i - off] = localsArr[i];
      }
    }
    localsArr = Arrays.copyOf(localsArr, localsArr.length - off);
    Object[] tmpStack = this.stack.toArray(true);

    superVisitFrame(F_NEW, localsArr.length, localsArr, tmpStack.length, tmpStack);
  }

  void superVisitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
    frameDiagnosticListener.onFrame(type, nLocal, local, nStack, stack);
    super.visitFrame(type, nLocal, local, nStack, stack);
  }

  @Override
  public void visitMultiANewArrayInsn(String arrayTypeName, int dims) {
    Type arrayType = Type.getObjectType(arrayTypeName);
    for (int i = 0; i < dims; i++) {
      stack.pop();
    }
    stack.push(arrayType.getDescriptor());
    super.visitMultiANewArrayInsn(arrayTypeName, dims);
    pc++;
  }

  @Override
  public void visitLookupSwitchInsn(Label label, int[] ints, Label[] labels) {
    stack.pop();
    SavedState state = new SavedState(localTypes, stack, newLocals, SavedState.CONDITIONAL);
    jumpTargetStates.put(label, state);
    for (Label l : labels) {
      jumpTargetStates.put(l, state);
    }
    super.visitLookupSwitchInsn(label, ints, labels);
    pc++;
  }

  @Override
  public void visitTableSwitchInsn(int i, int i1, Label label, Label... labels) {
    stack.pop();
    SavedState state = new SavedState(localTypes, stack, newLocals, SavedState.CONDITIONAL);
    jumpTargetStates.put(label, state);
    for (Label l : labels) {
      jumpTargetStates.put(l, state);
    }
    super.visitTableSwitchInsn(i, i1, label, labels);
    pc++;
  }

  @Override
  public void visitLdcInsn(Object o) {
    Type t = Type.getType(o.getClass());
    switch (t.getInternalName()) {
      case "java/lang/Integer":
        {
          pushToStack(Type.INT_TYPE);
          break;
        }
      case "java/lang/Long":
        {
          pushToStack(Type.LONG_TYPE);
          break;
        }
      case "java/lang/Byte":
        {
          pushToStack(Type.BYTE_TYPE);
          break;
        }
      case "java/lang/Short":
        {
          pushToStack(Type.SHORT_TYPE);
          break;
        }
      case "java/lang/Character":
        {
          pushToStack(Type.CHAR_TYPE);
          break;
        }
      case "java/lang/Boolean":
        {
          pushToStack(Type.BOOLEAN_TYPE);
          break;
        }
      case "java/lang/Float":
        {
          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case "java/lang/Double":
        {
          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      default:
        {
          pushToStack(t);
        }
    }
    super.visitLdcInsn(o);
    pc++;
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    super.visitJumpInsn(opcode, label);
    pc++;
    switch (opcode) {
      case IFEQ:
      case IFGE:
      case IFGT:
      case IFLE:
      case IFLT:
      case IFNE:
      case IFNONNULL:
      case IFNULL:
        {
          stack.pop();
          break;
        }
      case IF_ACMPEQ:
      case IF_ACMPNE:
      case IF_ICMPEQ:
      case IF_ICMPGE:
      case IF_ICMPGT:
      case IF_ICMPLE:
      case IF_ICMPLT:
      case IF_ICMPNE:
        {
          stack.pop();
          stack.pop();
          break;
        }
    }
    jumpTargetStates.put(
        label,
        new SavedState(
            localTypes,
            stack,
            newLocals,
            opcode == GOTO || opcode == JSR ? SavedState.UNCONDITIONAL : SavedState.CONDITIONAL));
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String desc, Handle handle, Object... bsmArgs) {
    Type[] args = Type.getArgumentTypes(desc);
    Type ret = Type.getReturnType(desc);

    for (int i = args.length - 1; i >= 0; i--) {
      if (!args[i].equals(Type.VOID_TYPE)) {
        popFromStack(args[i]);
      }
    }
    super.visitInvokeDynamicInsn(name, desc, handle, bsmArgs);
    pc++;

    if (!ret.equals(Type.VOID_TYPE)) {
      pushToStack(ret);
    }
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itfc) {
    Type[] args = Type.getArgumentTypes(desc);
    Type ret = Type.getReturnType(desc);

    for (int i = args.length - 1; i >= 0; i--) {
      if (!args[i].equals(Type.VOID_TYPE)) {
        popFromStack(args[i]);
      }
    }

    if (opcode != INVOKESTATIC) {
      stack.pop();
    }
    super.visitMethodInsn(opcode, owner, name, desc, itfc);
    pc++;

    if (!ret.equals(Type.VOID_TYPE)) {
      pushToStack(ret);
    }
    if (opcode == INVOKESPECIAL && name.equals("<init>")) {
      if (stack.peek() instanceof Label) {
        stack.pop();
        pushToStack(Type.getObjectType(owner));
      }
    }
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String desc) {
    Type t = Type.getType(desc);
    super.visitFieldInsn(opcode, owner, name, desc);
    pc++;

    if (opcode == PUTFIELD || opcode == PUTSTATIC) {
      popFromStack(t);
    }
    if (opcode == GETFIELD || opcode == PUTFIELD) {
      stack.pop(); // pop 'this'
    }
    if (opcode == GETFIELD || opcode == GETSTATIC) {
      pushToStack(t);
    }
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    super.visitTypeInsn(opcode, type);
    pc++;

    switch (opcode) {
      case NEW:
        {
          pushToStack(Type.getObjectType(type));
          break;
        }
      case ANEWARRAY:
        {
          stack.pop();
          Type elementType = type.endsWith(";") ? Type.getType(type) : Type.getObjectType(type);
          Type arrayType = Type.getType("[" + elementType);
          pushToStack(arrayType);
          break;
        }
      case INSTANCEOF:
        {
          stack.pop();
          pushToStack(Type.BOOLEAN_TYPE);
          break;
        }
      case CHECKCAST:
        {
          stack.pop();
          pushToStack(Type.getObjectType(type));
          break;
        }
    }
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    int size = 1;

    switch (opcode) {
      case DLOAD:
      case LLOAD:
      case DSTORE:
      case LSTORE:
        {
          size++;
          break;
        }
    }
    var = variableMapper.remap(var, size);

    boolean isPush = false;
    Type opType = null;
    switch (opcode) {
      case ILOAD:
        {
          opType = Type.INT_TYPE;
          isPush = true;
          break;
        }
      case LLOAD:
        {
          opType = Type.LONG_TYPE;
          isPush = true;
          break;
        }
      case FLOAD:
        {
          opType = Type.FLOAT_TYPE;
          isPush = true;
          break;
        }
      case DLOAD:
        {
          opType = Type.DOUBLE_TYPE;
          isPush = true;
          break;
        }
      case ALOAD:
        {
          Object o = localTypes.getType(var);
          opType = fromSlotType(o);
          isPush = true;
          break;
        }
      case ISTORE:
        {
          opType = Type.INT_TYPE;
          break;
        }
      case LSTORE:
        {
          opType = Type.LONG_TYPE;
          break;
        }
      case FSTORE:
        {
          opType = Type.FLOAT_TYPE;
          break;
        }
      case DSTORE:
        {
          opType = Type.DOUBLE_TYPE;
          break;
        }
      case ASTORE:
        {
          opType = fromSlotType(stack.peek());
          break;
        }
    }

    assert opType != null;

    if (isPush) {
      pushToStack(opType);
    } else {
      popFromStack(opType);
      localTypes.setType(var, opType);
    }

    super.visitVarInsn(opcode, var);
    pc++;
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    super.visitIntInsn(opcode, operand);
    pc++;

    switch (opcode) {
      case BIPUSH:
      case SIPUSH:
        {
          stack.push(INTEGER);
          break;
        }
      case NEWARRAY:
        {
          popFromStack(Type.INT_TYPE); // size
          switch (operand) {
            case T_BOOLEAN:
              {
                pushToStack(Type.getObjectType("[Z"));
                break;
              }
            case T_CHAR:
              {
                pushToStack(Type.getObjectType("[C"));
                break;
              }
            case T_FLOAT:
              {
                pushToStack(Type.getObjectType("[F"));
                break;
              }
            case T_DOUBLE:
              {
                pushToStack(Type.getObjectType("[D"));
                break;
              }
            case T_BYTE:
              {
                pushToStack(Type.getObjectType("[B"));
                break;
              }
            case T_SHORT:
              {
                pushToStack(Type.getObjectType("[S"));
                break;
              }
            case T_INT:
              {
                pushToStack(Type.getObjectType("[I"));
                break;
              }
            case T_LONG:
              {
                pushToStack(Type.getObjectType("[J"));
                break;
              }
          }
          break;
        }
    }
  }

  @Override
  public void visitInsn(int opcode) {
    super.visitInsn(opcode);
    pc++;

    switch (opcode) {
      case ACONST_NULL:
        {
          stack.push(NULL);
          break;
        }
      case ICONST_0:
      case ICONST_1:
      case ICONST_2:
      case ICONST_3:
      case ICONST_4:
      case ICONST_5:
      case ICONST_M1:
        {
          pushToStack(Type.INT_TYPE);
          break;
        }
      case FCONST_0:
      case FCONST_1:
      case FCONST_2:
        {
          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case LCONST_0:
      case LCONST_1:
        {
          pushToStack(Type.LONG_TYPE);
          break;
        }
      case DCONST_0:
      case DCONST_1:
        {
          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      case AALOAD:
        {
          stack.pop(); // index
          Object target = stack.pop();

          if (target instanceof String) {
            Type t;
            String typeStr = (String) target;
            if (typeStr.startsWith("[")) {
              if (typeStr.contains("/") && !typeStr.endsWith(";")) {
                typeStr += ";";
              }
              // Type.getElementType() will give the non-array type which we don't want here
              // For a multi-dimensional array the element type is the lower dimension array
              typeStr = typeStr.substring(1);
              t = Type.getType(typeStr);
            } else {
              t = Type.getObjectType(typeStr);
            }
            pushToStack(t);
          } else if (target == NULL) {
            pushToStack(Constants.NULL_TYPE);
          } else {
            pushToStack(Constants.OBJECT_TYPE);
          }
          break;
        }
      case IALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.INT_TYPE);
          break;
        }
      case FALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case BALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.BYTE_TYPE);
          break;
        }
      case CALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.CHAR_TYPE);
          break;
        }
      case SALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.SHORT_TYPE);
          break;
        }
      case LALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.LONG_TYPE);
          break;
        }
      case DALOAD:
        {
          stack.pop();
          stack.pop();

          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      case AASTORE:
      case IASTORE:
      case FASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
      case LASTORE:
      case DASTORE:
        {
          stack.pop(); // val
          stack.pop(); // index
          stack.pop(); // arrayref

          break;
        }
      case POP:
        {
          stack.pop1();
          break;
        }
      case POP2:
        {
          stack.pop1();
          stack.pop1();
          break;
        }
      case DUP:
        {
          stack.push1(stack.peek());
          break;
        }
      case DUP_X1:
        {
          Object x = stack.pop1();
          Object y = stack.pop1();
          stack.push1(x);
          stack.push1(y);
          stack.push1(x);
          break;
        }
      case DUP_X2:
        {
          Object x = stack.pop1();
          Object y = stack.pop1();
          Object z = stack.pop1();
          stack.push1(x);
          stack.push1(z);
          stack.push1(y);
          stack.push1(x);
          break;
        }
      case DUP2:
        {
          Object x = stack.pop1();
          Object y = stack.peek();
          stack.push1(x);
          stack.push1(y);
          stack.push1(x);
          break;
        }
      case DUP2_X1:
        {
          Object x2 = stack.pop1();
          Object x1 = stack.pop1();
          Object y = stack.pop1();
          stack.push1(x1);
          stack.push1(x2);
          stack.push1(y);
          stack.push1(x1);
          stack.push1(x2);
          break;
        }
      case DUP2_X2:
        {
          Object x2 = stack.pop1();
          Object x1 = stack.pop1();
          Object y2 = stack.pop1();
          Object y1 = stack.pop1();
          stack.push1(x1);
          stack.push1(x2);
          stack.push1(y1);
          stack.push1(y2);
          stack.push1(x1);
          stack.push1(x2);
          break;
        }
      case SWAP:
        {
          Object x = stack.pop1();
          Object y = stack.pop1();
          stack.push1(x);
          stack.push1(y);
          break;
        }
      case IADD:
      case ISUB:
      case IMUL:
      case IDIV:
      case IREM:
      case IAND:
      case IOR:
      case IXOR:
      case ISHR:
      case ISHL:
      case IUSHR:
        {
          popFromStack(Type.INT_TYPE);
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.INT_TYPE);
          break;
        }
      case FADD:
      case FSUB:
      case FMUL:
      case FDIV:
      case FREM:
        {
          popFromStack(Type.FLOAT_TYPE);
          popFromStack(Type.FLOAT_TYPE);
          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case LADD:
      case LSUB:
      case LMUL:
      case LDIV:
      case LREM:
      case LAND:
      case LOR:
      case LXOR:
      case LSHR:
      case LSHL:
      case LUSHR:
        {
          popFromStack(Type.LONG_TYPE);
          popFromStack(Type.LONG_TYPE);
          pushToStack(Type.LONG_TYPE);
          break;
        }
      case DADD:
      case DSUB:
      case DMUL:
      case DDIV:
      case DREM:
        {
          popFromStack(Type.DOUBLE_TYPE);
          popFromStack(Type.DOUBLE_TYPE);
          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      case I2L:
        {
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.LONG_TYPE);
          break;
        }
      case I2F:
        {
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case I2B:
        {
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.BYTE_TYPE);
          break;
        }
      case I2C:
        {
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.CHAR_TYPE);
          break;
        }
      case I2S:
        {
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.SHORT_TYPE);
          break;
        }
      case I2D:
        {
          popFromStack(Type.INT_TYPE);
          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      case L2I:
        {
          popFromStack(Type.LONG_TYPE);
          pushToStack(Type.INT_TYPE);
          break;
        }
      case L2F:
        {
          popFromStack(Type.LONG_TYPE);
          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case L2D:
        {
          popFromStack(Type.LONG_TYPE);
          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      case F2I:
        {
          popFromStack(Type.FLOAT_TYPE);
          pushToStack(Type.INT_TYPE);
          break;
        }
      case F2L:
        {
          popFromStack(Type.FLOAT_TYPE);
          pushToStack(Type.LONG_TYPE);
          break;
        }
      case F2D:
        {
          popFromStack(Type.FLOAT_TYPE);
          pushToStack(Type.DOUBLE_TYPE);
          break;
        }
      case D2I:
        {
          popFromStack(Type.DOUBLE_TYPE);
          pushToStack(Type.INT_TYPE);
          break;
        }
      case D2F:
        {
          popFromStack(Type.DOUBLE_TYPE);
          pushToStack(Type.FLOAT_TYPE);
          break;
        }
      case D2L:
        {
          popFromStack(Type.DOUBLE_TYPE);
          pushToStack(Type.LONG_TYPE);
          break;
        }
      case LCMP:
        {
          popFromStack(Type.LONG_TYPE);
          popFromStack(Type.LONG_TYPE);

          pushToStack(Type.INT_TYPE);
          break;
        }
      case FCMPL:
      case FCMPG:
        {
          popFromStack(Type.FLOAT_TYPE);
          popFromStack(Type.FLOAT_TYPE);

          pushToStack(Type.INT_TYPE);
          break;
        }
      case DCMPL:
      case DCMPG:
        {
          popFromStack(Type.DOUBLE_TYPE);
          popFromStack(Type.DOUBLE_TYPE);

          pushToStack(Type.INT_TYPE);
          break;
        }
      case IRETURN:
        {
          popFromStack(Type.INT_TYPE);
          break;
        }
      case LRETURN:
        {
          popFromStack(Type.LONG_TYPE);
          break;
        }
      case FRETURN:
        {
          popFromStack(Type.FLOAT_TYPE);
          break;
        }
      case DRETURN:
        {
          popFromStack(Type.DOUBLE_TYPE);
          break;
        }
      case ARETURN:
        {
          popFromStack(Type.getReturnType(desc));
          break;
        }
      case ATHROW:
        {
          popFromStack(Constants.THROWABLE_TYPE);
          break;
        }
      case ARRAYLENGTH:
        {
          stack.pop();
          pushToStack(Type.INT_TYPE);
          break;
        }
      case MONITORENTER:
      case MONITOREXIT:
        {
          stack.pop();
          break;
        }
    }
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    super.visitIincInsn(variableMapper.remap(var, 1), increment);
    pc++;
  }

  @Override
  public void visitLocalVariable(
      String name, String desc, String signature, Label start, Label end, int index) {
    int newIndex = variableMapper.map(index, start);
    if (newIndex != 0xFFFFFFFF) {
      super.visitLocalVariable(
          name, desc, signature, start, end, newIndex == Integer.MIN_VALUE ? 0 : newIndex);
    }
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(
      int typeRef,
      TypePath typePath,
      Label[] start,
      Label[] end,
      int[] index,
      String desc,
      boolean visible) {
    Type t = Type.getType(desc);
    int cnt = 0;
    int[] newIndex = new int[index.length];
    for (int i = 0; i < newIndex.length; ++i) {
      int idx = variableMapper.map(index[i]);
      if (idx != 0xFFFFFFFF) {
        newIndex[cnt++] = idx;
      }
    }
    return super.visitLocalVariableAnnotation(
        typeRef, typePath, start, end, Arrays.copyOf(newIndex, cnt), desc, visible);
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String exception) {
    addTryCatchHandler(start, handler);
    super.visitTryCatchBlock(start, end, handler, exception);
  }

  @Override
  public void visitLabel(Label label) {
    variableMapper.noteLabel(label);
    SavedState ss = jumpTargetStates.get(label);
    if (ss != null) {
      if (ss.kind != SavedState.CONDITIONAL) {
        reset();
      }
      localTypes.mergeWith(ss.lvTypes.toArray());
      stack.replaceWith(ss.sStack.toArray());
      if (ss.kind == SavedState.EXCEPTION) {
        stack.push(toSlotType(Constants.THROWABLE_TYPE));
      }
      for (LocalVarSlot lvs : newLocals) {
        if (!ss.newLocals.contains(lvs)) {
          lvs.expire();
        }
      }
      newLocals.clear();
      newLocals.addAll(ss.newLocals);
    }
    Set<Label> handlers = tryCatchHandlerMap.get(label);
    if (handlers != null) {
      for (Label handler : handlers) {
        if (!jumpTargetStates.containsKey(handler)) {
          jumpTargetStates.put(
              handler, new SavedState(localTypes, stack, newLocals, SavedState.EXCEPTION));
        }
      }
    }
    super.visitLabel(label);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    super.visitMaxs(Math.max(stack.maxStack, maxStack), localTypes.maxSize());
  }

  @Override
  public void insertFrameReplaceStack(Label l, Type... stackTypes) {
    if (pc == lastFramePc) {
      return;
    }
    lastFramePc = pc;

    if (!frameOffsets.add(l.getOffset())) {
      return;
    }

    Object[] localsArr = trimLocalVars(localTypes.toArray(true));

    stack.reset();
    for (Type t : stackTypes) {
      stack.push(toSlotType(t));
    }

    Object[] stackSlots = stack.toArray(true);

    superVisitFrame(F_NEW, localsArr.length, localsArr, stackSlots.length, stackSlots);
  }

  @Override
  public void insertFrameAppendStack(Label l, Type... stackTypes) {
    if (pc == lastFramePc) {
      return;
    }
    lastFramePc = pc;

    if (!frameOffsets.add(l.getOffset())) {
      return;
    }

    Object[] localsArr = trimLocalVars(localTypes.toArray(true));

    for (Type t : stackTypes) {
      stack.push(toSlotType(t));
    }

    Object[] stackSlots = stack.toArray(true);

    superVisitFrame(F_NEW, localsArr.length, localsArr, stackSlots.length, stackSlots);
  }

  @Override
  public void insertFrameSameStack(Label l) {
    if (pc == lastFramePc) {
      return;
    }

    if (!frameOffsets.add(l.getOffset())) {
      return;
    }

    lastFramePc = pc;

    Object[] localsArr = trimLocalVars(localTypes.toArray(true));
    Object[] stackSlots = stack.toArray(true);

    superVisitFrame(F_NEW, localsArr.length, localsArr, stackSlots.length, stackSlots);
  }

  @Override
  public void addTryCatchHandler(Label start, Label handler) {
    Set<Label> handlers = tryCatchHandlerMap.get(start);
    if (handlers == null) {
      handlers = new HashSet<>();
    }
    handlers.add(handler);
    tryCatchHandlerMap.put(start, handlers);
  }

  @Override
  public int storeAsNew() {
    Type t = fromSlotType(peekFromStack());
    int idx = newVar(t);
    visitVarInsn(t.getOpcode(ISTORE), idx);
    return idx;
  }

  @Override
  public final int newVar(Type t) {
    int size = t == Constants.NULL_TYPE ? 1 : t.getSize();
    int idx = variableMapper.newVarIdx(size);

    int var = VariableMapper.unmask(idx == Integer.MIN_VALUE ? 0 : idx);
    newLocals.add(new LocalVarSlot(var, toSlotType(t)));

    localTypes.setType(var, t);

    return idx;
  }

  private void initLocals(boolean isInstance, boolean isConstructor) {
    int nextMappedVar = 0;
    if (isInstance) {
      locals.add(isConstructor ? Opcodes.UNINITIALIZED_THIS : owner);
      nextMappedVar++;
      localsTailPtr++;
    }
    for (Type t : Type.getArgumentTypes(desc)) {
      locals.add(toSlotType(t));
      nextMappedVar += t.getSize();
      localsTailPtr++;
    }
    localTypes.replaceWith(locals.toArray(new Object[0]));
    argsSize = nextMappedVar;
  }

  private Object[] computeFrameLocals() {
    return computeFrameLocals(argsSize, locals, newLocals, variableMapper);
  }

  // package accessible for targeted tests
  static Object[] computeFrameLocals(
      int argsSize,
      List<Object> locals,
      Set<LocalVarSlot> newLocals,
      VariableMapper variableMapper) {
    newLocals = newLocals != null ? newLocals : Collections.emptySet();
    Object[] localsArr;
    int nextMappedVar = variableMapper.getNextMappedVar();
    if (nextMappedVar > argsSize) {
      int arrSize = Math.max(locals.size(), nextMappedVar);
      localsArr = new Object[arrSize];
      int idx = 0;
      for (Object e : locals) {
        if (idx < argsSize) {
          localsArr[idx] = e;
          if (e == LONG || e == DOUBLE) {
            localsArr[++idx] = TOP_EXT;
          }
        } else {
          int var = variableMapper.map(idx);
          if (var != 0xFFFFFFFF) {
            localsArr[var] = e;
            if (e == LONG || e == DOUBLE) {
              int off = var + 1;
              if (off == localsArr.length) {
                localsArr = Arrays.copyOf(localsArr, localsArr.length + 1);
              }
              localsArr[off] = TOP_EXT;
              idx++;
            }
          }
        }
        idx++;
      }
      for (LocalVarSlot lvs : newLocals) {
        int ptr = lvs.idx != Integer.MIN_VALUE ? lvs.idx : 0;
        localsArr[ptr] = lvs.isExpired() ? TOP : lvs.type;
        if (lvs.type == LONG || lvs.type == DOUBLE) {
          localsArr[ptr + 1] = TOP_EXT;
        }
      }
    } else {
      localsArr = locals.toArray(new Object[0]);
    }
    for (int m : variableMapper.mappings()) {
      if (m != 0) {
        if (localsArr[m] == null) {
          localsArr[m] = TOP;
        }
      }
    }
    localsArr = trimLocalVars(localsArr);
    return localsArr;
  }

  private static Object[] trimLocalVars(Object[] localsArr) {
    Object[] tmp = new Object[localsArr.length];
    int idx = 0;
    int firstEmpty = -1, emptyRunLen = 0;
    for (Object o : localsArr) {
      if (o == null) {
        if (firstEmpty == -1) {
          firstEmpty = idx;
        }
        emptyRunLen++;
        tmp[idx++] = TOP;
      } else if (o == TOP) {
        if (firstEmpty == -1) {
          firstEmpty = idx;
        }
        emptyRunLen++;
        tmp[idx++] = TOP;
      } else {
        firstEmpty = -1;
        emptyRunLen = 0;
        tmp[idx++] = o;
      }
    }
    if (firstEmpty > -1 && (firstEmpty + emptyRunLen) == localsArr.length) {
      // the frame locals are ending with uninterrupted run of TOP slots; safe to cut them
      return Arrays.copyOf(tmp, firstEmpty);
    } else {
      return Arrays.copyOf(tmp, idx);
    }
  }

  private void reset() {
    localTypes.reset();
    stack.reset();
    newLocals.clear();
  }

  private Object peekFromStack() {
    Object o = stack.peek();
    if (o == null || o == TOP_EXT) {
      o = stack.peekX1();
    }
    return o;
  }

  @SuppressWarnings("UnusedReturnValue")
  private Object popFromStack(Type t) {
    return stack.pop();
  }

  private void pushToStack(Type t) {
    stack.push(toSlotType(t));
  }

  private Type fromSlotType(Object slotType) {
    if (slotType == INTEGER) {
      return Type.INT_TYPE;
    }
    if (slotType == FLOAT) {
      return Type.FLOAT_TYPE;
    }
    if (slotType == LONG) {
      return Type.LONG_TYPE;
    }
    if (slotType == DOUBLE) {
      return Type.DOUBLE_TYPE;
    }
    if (slotType == UNINITIALIZED_THIS) {
      return Type.getObjectType(owner);
    }
    if (slotType == NULL) {
      return Constants.NULL_TYPE;
    }
    if (slotType == TOP) {
      return Constants.TOP_TYPE;
    }
    if (slotType instanceof Integer) {
      log.warn("Unknown slot type: {}", slotType);
      return Constants.OBJECT_TYPE;
    }
    return slotType != null ? Type.getObjectType((String) slotType) : Constants.OBJECT_TYPE;
  }

  static final class LocalVarSlot {
    final int idx;
    final Object type;
    private boolean expired = false;

    LocalVarSlot(int idx, Object type) {
      this.idx = Math.abs(idx);
      this.type = type;
    }

    void expire() {
      expired = true;
    }

    boolean isExpired() {
      return expired;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 97 * hash + idx;
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      LocalVarSlot other = (LocalVarSlot) obj;
      return idx == other.idx;
    }
  }

  static final class SimulatedStack {
    private static final int DEFAULT_CAPACITY = 16;
    private int stackPtr = 0;
    private int maxStack = 0;
    private Object[] stack = new Object[DEFAULT_CAPACITY];

    public SimulatedStack() {}

    SimulatedStack(Object[] other) {
      replaceWith(other);
    }

    private void fitResize(int ptr) {
      if (ptr >= stack.length) {
        stack = Arrays.copyOf(stack, Math.max(stack.length * 2, stackPtr + 1));
      }
    }

    public void push1(Object val) {
      fitResize(stackPtr);
      stack[stackPtr++] = val;
      maxStack = Math.max(stackPtr, maxStack);
    }

    public void push(Object val) {
      fitResize(stackPtr);

      stack[stackPtr++] = val;
      if (val == LONG || val == DOUBLE) {
        fitResize(stackPtr);
        stack[stackPtr++] = TOP_EXT;
      }
      maxStack = Math.max(stackPtr, maxStack);
    }

    public Object pop1() {
      if (hasData()) {
        return stack[--stackPtr];
      }
      return TOP;
    }

    public Object pop() {
      if (hasData()) {
        Object val = stack[--stackPtr];
        if (val == TOP_EXT) {
          val = stack[--stackPtr];
        }
        return val;
      }
      return TOP;
    }

    public Object peek() {
      if (hasData()) {
        return stack[stackPtr - 1];
      }
      return TOP;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public Object peekX1() {
      if (stackPtr > 1) {
        return stack[stackPtr - 2];
      }
      return TOP;
    }

    public Object peek(int offset) {
      if (stackPtr > offset) {
        return stack[stackPtr - offset - 1];
      }
      return TOP;
    }

    public boolean hasData() {
      return stackPtr != 0;
    }

    public int size() {
      return stackPtr;
    }

    public void reset() {
      stackPtr = 0;
      stack = new Object[DEFAULT_CAPACITY];
    }

    public Object[] toArray() {
      return toArray(false);
    }

    public Object[] toArray(boolean compress) {
      Object[] ret = new Object[stackPtr];
      int localCnt = 0;
      for (int i = 0; i < stackPtr; i++) {
        Object o = stack[i];
        if (o != null) {
          if (!compress || o != TOP_EXT) {
            ret[localCnt++] = o;
          }
        }
      }
      return Arrays.copyOf(ret, localCnt);
    }

    public void replaceWith(Object[] other) {
      if (other.length > 0) {
        Object[] arr = new Object[other.length * 2];
        int idx = 0;
        for (int ptr = 0; ptr < other.length; ptr++) {
          Object o = other[ptr];
          arr[idx++] = o;
          if (o == DOUBLE || o == LONG) {
            int next = ptr + 1;
            if (next >= other.length || (other[next] != null && other[next] != TOP_EXT)) {
              arr[idx++] = TOP_EXT;
            }
          }
        }
        stack = Arrays.copyOf(arr, idx);
        stackPtr = idx;
      } else {
        reset();
      }
      maxStack = Math.max(stackPtr, maxStack);
    }
  }

  static class LocalVarTypes {
    private static final int DEFAULT_SIZE = 4;
    private Object[] locals;
    private int lastVarPtr = -1;
    private int maxVarPtr = -1;

    LocalVarTypes() {
      locals = new Object[DEFAULT_SIZE];
    }

    LocalVarTypes(Object[] vals) {
      replaceWith(vals);
    }

    public void setType(int idx, Type t) {
      int padding = t.getSize() - 1;
      if ((idx + padding) >= locals.length) {
        locals = Arrays.copyOf(locals, Math.round((idx + padding + 1) * 1.5f));
      }
      locals[idx] = toSlotType(t);
      if (padding == 1) {
        locals[idx + 1] = TOP_EXT;
      }
      setLastVarPtr(Math.max(idx + padding, lastVarPtr));
    }

    public Object getType(int idx) {
      return idx < locals.length ? locals[idx] : null;
    }

    public final void replaceWith(Object[] other) {
      Object[] arr = new Object[other.length * 2];
      int idx = 0;
      for (int i = 0; i < other.length; i++) {
        Object o = other[i];
        arr[idx++] = o;
        if (o == LONG || o == DOUBLE) {
          int lookup = i + 1;
          if (lookup == other.length || other[lookup] != TOP_EXT) {
            arr[idx++] = TOP_EXT;
          }
        }
      }
      locals = Arrays.copyOf(arr, idx);
      setLastVarPtr(idx - 1);
    }

    public void mergeWith(Object[] other) {
      Object[] arr = new Object[Math.max(other.length * 2, Math.max(lastVarPtr + 1, DEFAULT_SIZE))];
      int idx = 0;
      for (Object o : other) {
        arr[idx++] = o == null ? TOP : o;
      }
      while (idx <= lastVarPtr) {
        arr[idx++] = TOP;
      }
      locals = arr;
      setLastVarPtr(idx - 1);
    }

    public Object[] toArray() {
      return toArray(false);
    }

    public Object[] toArray(boolean compress) {
      Object[] ret = new Object[size()];
      int localCnt = 0;
      for (int i = 0; i <= lastVarPtr; i++) {
        Object o = locals[i];
        if (o != null) {
          if (!compress || o != TOP_EXT) {
            ret[localCnt++] = o;
          }
        } else {
          ret[localCnt++] = TOP;
        }
      }
      return Arrays.copyOf(ret, localCnt);
    }

    public void reset() {
      locals = new Object[DEFAULT_SIZE];
      setLastVarPtr(-1);
    }

    public int size() {
      return lastVarPtr + 1;
    }

    public int maxSize() {
      return maxVarPtr + 1;
    }

    public boolean isEmpty() {
      return size() == 0;
    }

    private void setLastVarPtr(int ptr) {
      lastVarPtr = ptr;
      maxVarPtr = Math.max(lastVarPtr, maxVarPtr);
    }
  }

  private static final class SavedState {
    static final int CONDITIONAL = 0;
    static final int UNCONDITIONAL = 1;
    static final int EXCEPTION = 2;

    private final LocalVarTypes lvTypes;
    private final SimulatedStack sStack;
    private final Collection<LocalVarSlot> newLocals;
    private final int kind;

    SavedState(
        LocalVarTypes lvTypes,
        SimulatedStack sStack,
        Collection<LocalVarSlot> newLocals,
        int kind) {
      this.lvTypes = new LocalVarTypes(lvTypes.toArray());
      this.sStack = new SimulatedStack(sStack.toArray());
      this.newLocals = new HashSet<>(newLocals);
      this.kind = kind;
    }
  }

  static final class Introspection {
    final SimulatedStack stack;
    final LocalVarTypes lvTypes;
    final Collection<LocalVarSlot> newLocals;
    final Map<Label, SavedState> jumpTable;

    private Introspection(InstrumentingMethodVisitor v) {
      this.stack = new SimulatedStack(v.stack.toArray(false));
      this.lvTypes = new LocalVarTypes(v.localTypes.toArray(false));
      this.newLocals = new HashSet<>(v.newLocals);
      this.jumpTable = new HashMap<>(v.jumpTargetStates);
    }
  }

  Introspection introspect() {
    return new Introspection(this);
  }
}
