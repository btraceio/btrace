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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.annotations.Where;
import org.openjdk.btrace.runtime.Interval;

/**
 * Base class for all out method instrumenting classes.
 *
 * @author A. Sundararajan
 */
public class MethodInstrumentor extends BTraceMethodVisitor {
  protected final Assembler asm;
  MethodInstrumentor parent = null;
  private final int access;
  private final String parentClz;
  private final String superClz;
  private final String name;
  private final String desc;
  private final ClassLoader cl;
  private int levelCheckVar = Integer.MIN_VALUE;
  private final Type returnType;
  private final Type[] argumentTypes;
  private final Map<Integer, Type> extraTypes;
  private Label skipLabel;
  private boolean prologueVisited = false;

  public MethodInstrumentor(
      ClassLoader cl,
      MethodVisitor mv,
      MethodInstrumentorHelper mHelper,
      String parentClz,
      String superClz,
      int access,
      String name,
      String desc) {
    super(mv, mHelper);
    this.parentClz = parentClz;
    this.superClz = superClz;
    this.access = access;
    this.name = name;
    this.desc = desc;
    returnType = Type.getReturnType(desc);
    argumentTypes = Type.getArgumentTypes(desc);
    extraTypes = new HashMap<>();
    asm = new Assembler(this, mHelper);
    this.cl = cl;
  }

  private static boolean isLevelCheck(Level level) {
    return level != null && !level.getValue().equals(Interval.ge(0));
  }

  protected void visitMethodPrologue() {}

  @Override
  public final void visitCode() {
    if (!isConstructor()) {
      prologueVisited = true;
      visitMethodPrologue();
    }
    super.visitCode();
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean iface) {
    super.visitMethodInsn(opcode, owner, name, desc, iface);
    if (isConstructor() && !prologueVisited) {
      if (name.equals(Constants.CONSTRUCTOR)
          && (owner.equals(getParentClz())
              || (getSuperClz() != null && owner.equals(getSuperClz())))) {
        // super or this class constructor call.
        // do method entry after that!
        prologueVisited = true;
        visitMethodPrologue();
      }
    }
  }

  @Override
  public void visitInsn(int opcode) {
    switch (opcode) {
      case IRETURN:
      case ARETURN:
      case FRETURN:
      case LRETURN:
      case DRETURN:
      case RETURN:
        if (!prologueVisited) {
          prologueVisited = true;
          visitMethodPrologue();
        }
        break;
      default:
        break;
    }
    super.visitInsn(opcode);
  }

  public int getAccess() {
    return access;
  }

  public final String getName() {
    return getName(false);
  }

  public final String getName(boolean fqn) {
    StringBuilder sb = new StringBuilder();
    if (fqn) {
      sb.append(Modifier.toString(access))
          .append(' ')
          .append(TypeUtils.descriptorToSimplified(desc, parentClz, name));
    } else {
      sb.append(name);
    }
    return sb.toString();
  }

  public final Label getSkipLabel() {
    return skipLabel;
  }

  public final void setSkipLabel(Label skipLabel) {
    this.skipLabel = skipLabel;
  }

  public final String getDescriptor() {
    return desc;
  }

  public final Type getReturnType() {
    return returnType;
  }

  protected void addExtraTypeInfo(int index, Type type) {
    if (index != -1) {
      extraTypes.put(index, type);
    }
  }

  protected void loadArguments(ArgumentProvider... argumentProviders) {
    Arrays.sort(argumentProviders, ArgumentProvider.COMPARATOR);

    for (ArgumentProvider provider : argumentProviders) {
      if (provider != null) provider.provide();
    }
  }

  protected void loadArguments(
      ValidationResult vr,
      Type[] actionArgTypes,
      boolean isStatic,
      ArgumentProvider... argumentProviders) {
    int ptr = isStatic ? 0 : 1;
    List<ArgumentProvider> argProvidersList =
        new ArrayList<>(argumentProviders.length + vr.getArgCnt());
    argProvidersList.addAll(Arrays.asList(argumentProviders));
    for (int i = 0; i < vr.getArgCnt(); i++) {
      int index = vr.getArgIdx(i);
      Type t = actionArgTypes[index];
      if (TypeUtils.isAnyTypeArray(t)) {
        argProvidersList.add(anytypeArg(index, ptr));
        ptr++;
      } else {
        argProvidersList.add(localVarArg(index, t, ptr));
        ptr += actionArgTypes[index].getSize();
      }
    }
    loadArguments(argProvidersList);
  }

  private void loadArguments(List<ArgumentProvider> argumentProviders) {
    argumentProviders.sort(ArgumentProvider.COMPARATOR);
    for (ArgumentProvider ap : argumentProviders) {
      if (ap != null) {
        ap.provide();
      }
    }
  }

  public void loadThis() {
    if ((access & ACC_STATIC) != 0) {
      throw new IllegalStateException("no 'this' inside static method");
    }
    visitVarInsn(ALOAD, 0);
  }

  public int[] backupStack(Type[] methodArgTypes, boolean isStatic) {
    int[] backupArgsIndexes = new int[methodArgTypes.length + 1];
    int upper = methodArgTypes.length - 1;

    for (int i = 0; i < methodArgTypes.length; i++) {
      Type t = methodArgTypes[upper - i];
      int index = storeAsNew();
      backupArgsIndexes[upper - i + 1] = index;
    }

    if (!isStatic) {
      int index = storeAsNew(); // store *callee*
      backupArgsIndexes[0] = index;
    }
    return backupArgsIndexes;
  }

  public void restoreStack(int[] backupArgsIndexes, boolean isStatic) {
    restoreStack(backupArgsIndexes, argumentTypes, isStatic);
  }

  public void restoreStack(int[] backupArgsIndexes, Type[] methodArgTypes, boolean isStatic) {
    int upper = methodArgTypes.length - 1;
    if (!isStatic) {
      asm.loadLocal(Constants.OBJECT_TYPE, backupArgsIndexes[0]);
    }

    for (int i = methodArgTypes.length - 1; i > -1; i--) {
      asm.loadLocal(methodArgTypes[upper - i], backupArgsIndexes[upper - i + 1]);
    }
  }

  protected final ArgumentProvider localVarArg(int index, Type type, int ptr) {
    return new LocalVarArgProvider(asm, index, type, ptr);
  }

  protected final ArgumentProvider localVarArg(int index, Type type, int ptr, boolean boxValue) {
    return new LocalVarArgProvider(asm, index, type, ptr, boxValue);
  }

  protected final ArgumentProvider constArg(int index, Object val) {
    return new ConstantArgProvider(asm, index, val);
  }

  protected final ArgumentProvider selfArg(int index, Type type) {
    return isStatic() ? constArg(index, null) : localVarArg(index, type, 0);
  }

  protected final ArgumentProvider anytypeArg(int index, int basePtr) {
    return new AnyTypeArgProvider(asm, index, basePtr);
  }

  protected final ArgumentProvider anytypeArg(int index, int basePtr, Type... argTypes) {
    return new AnyTypeArgProvider(asm, index, basePtr, argTypes);
  }

  protected final boolean isStatic() {
    return (getAccess() & ACC_STATIC) != 0;
  }

  protected final boolean isConstructor() {
    return Constants.CONSTRUCTOR.equals(name);
  }

  public void returnValue() {
    super.visitInsn(returnType.getOpcode(IRETURN));
  }

  protected String getParentClz() {
    return parentClz;
  }

  protected String getSuperClz() {
    return superClz;
  }

  protected ValidationResult validateArguments(
      OnMethod om, Type[] actionArgTypes, Type[] methodArgTypes) {
    int specialArgsCount = 0;

    if (om.getSelfParameter() != -1) {
      Type selfType = extraTypes.get(om.getSelfParameter());
      if (selfType == null) {
        if (!TypeUtils.isObject(actionArgTypes[om.getSelfParameter()])) {
          report(
              "Invalid @Self parameter. @Self parameter is not java.lang.Object. Expected "
                  + Constants.OBJECT_TYPE
                  + ", Received "
                  + actionArgTypes[om.getSelfParameter()]);
          return ValidationResult.INVALID;
        }
      } else {
        if (!InstrumentUtils.isAssignable(
            actionArgTypes[om.getSelfParameter()], selfType, cl, om.isExactTypeMatch())) {
          report(
              "Invalid @Self parameter. @Self parameter is not compatible. Expected "
                  + selfType
                  + ", Received "
                  + actionArgTypes[om.getSelfParameter()]);
          return ValidationResult.INVALID;
        }
      }
      specialArgsCount++;
    }
    if (om.getReturnParameter() != -1) {
      Type type = extraTypes.get(om.getReturnParameter());
      if (type == null) {
        type = returnType;
      }
      if (type == null) {
        if (!TypeUtils.isObject(actionArgTypes[om.getReturnParameter()])) {
          report(
              "Invalid @Return parameter. @Return parameter is not java.lang.Object. Expected "
                  + Constants.OBJECT_TYPE
                  + ", Received "
                  + actionArgTypes[om.getReturnParameter()]);
          return ValidationResult.INVALID;
        }
      } else {
        if (!InstrumentUtils.isAssignable(
            actionArgTypes[om.getReturnParameter()], type, cl, om.isExactTypeMatch())) {
          report(
              "Invalid @Return parameter. Expected '"
                  + returnType
                  + ", received "
                  + actionArgTypes[om.getReturnParameter()]);
          return ValidationResult.INVALID;
        }
      }
      specialArgsCount++;
    }
    if (om.getTargetMethodOrFieldParameter() != -1) {
      if (!(InstrumentUtils.isAssignable(
          actionArgTypes[om.getTargetMethodOrFieldParameter()],
          Constants.STRING_TYPE,
          cl,
          om.isExactTypeMatch()))) {
        report(
            "Invalid @TargetMethodOrField parameter. Expected "
                + Constants.STRING_TYPE
                + ", received "
                + actionArgTypes[om.getTargetMethodOrFieldParameter()]);
        return ValidationResult.INVALID;
      }
      specialArgsCount++;
    }
    if (om.getTargetInstanceParameter() != -1) {
      Type calledType = extraTypes.get(om.getTargetInstanceParameter());
      if (calledType == null) {
        if (!TypeUtils.isObject(actionArgTypes[om.getTargetInstanceParameter()])) {
          report(
              "Invalid @TargetInstance parameter. @TargetInstance parameter is not java.lang.Object. Expected "
                  + Constants.OBJECT_TYPE
                  + ", Received "
                  + actionArgTypes[om.getTargetInstanceParameter()]);
          return ValidationResult.INVALID;
        }
      } else {
        if (!InstrumentUtils.isAssignable(
            actionArgTypes[om.getTargetInstanceParameter()],
            calledType,
            cl,
            om.isExactTypeMatch())) {
          report(
              "Invalid @TargetInstance parameter. Expected "
                  + Constants.OBJECT_TYPE
                  + ", received "
                  + actionArgTypes[om.getTargetInstanceParameter()]);
          return ValidationResult.INVALID;
        }
      }
      specialArgsCount++;
    }
    if (om.getDurationParameter() != -1) {
      if (!actionArgTypes[om.getDurationParameter()].equals(Type.LONG_TYPE)) {
        return ValidationResult.INVALID;
      }
      specialArgsCount++;
    }
    if (om.getClassNameParameter() != -1) {
      if (!(InstrumentUtils.isAssignable(
          actionArgTypes[om.getClassNameParameter()],
          Constants.STRING_TYPE,
          cl,
          om.isExactTypeMatch()))) {
        return ValidationResult.INVALID;
      }
      specialArgsCount++;
    }
    if (om.getMethodParameter() != -1) {
      if (!(InstrumentUtils.isAssignable(
          actionArgTypes[om.getMethodParameter()],
          Constants.STRING_TYPE,
          cl,
          om.isExactTypeMatch()))) {
        return ValidationResult.INVALID;
      }
      specialArgsCount++;
    }

    Type[] cleansedArgArray = new Type[actionArgTypes.length - specialArgsCount];
    int[] cleansedArgIndex = new int[cleansedArgArray.length];

    int counter = 0;
    for (int argIndex = 0; argIndex < actionArgTypes.length; argIndex++) {
      if (argIndex != om.getSelfParameter()
          && argIndex != om.getClassNameParameter()
          && argIndex != om.getMethodParameter()
          && argIndex != om.getReturnParameter()
          && argIndex != om.getTargetInstanceParameter()
          && argIndex != om.getTargetMethodOrFieldParameter()
          && argIndex != om.getDurationParameter()) {
        cleansedArgArray[counter] = actionArgTypes[argIndex];
        cleansedArgIndex[counter] = argIndex;
        counter++;
      }
    }
    if (cleansedArgArray.length == 0) {
      return ValidationResult.ANY;
    } else {
      if (!TypeUtils.isAnyTypeArray(cleansedArgArray[0])
          && !InstrumentUtils.isAssignable(
              cleansedArgArray, methodArgTypes, cl, om.isExactTypeMatch())) {
        return ValidationResult.INVALID;
      }
    }
    return new ValidationResult(true, cleansedArgIndex);
  }

  private Label levelCheck(OnMethod om, String className, boolean saveResult) {
    Label l = null;
    Level level = om.getLevel();
    if (isLevelCheck(level)) {
      l = new Label();
      if (saveResult) {
        // must store the level in a local var to be consistent
        asm.compareLevel(className, level).dup();
        levelCheckVar = storeAsNew();
        asm.jump(IFLT, l);
      } else {
        asm.addLevelCheck(className, level, l);
      }
    }
    return l;
  }

  protected Label levelCheck(OnMethod om, String className) {
    return levelCheck(om, className, false);
  }

  protected Label levelCheckBefore(OnMethod om, String className) {
    return levelCheck(om, className, om.getLocation().getWhere() == Where.AFTER);
  }

  protected Label levelCheckAfter(OnMethod om, String className) {
    Label l = null;
    if (levelCheckVar != Integer.MIN_VALUE) {
      Level level = om.getLevel();
      if (isLevelCheck(level)) {
        l = new Label();
        asm.loadLocal(Type.INT_TYPE, levelCheckVar).jump(IFLT, l);
      }
    } else {
      l = levelCheck(om, className);
    }
    return l;
  }

  protected final boolean isPrologueVisited() {
    return prologueVisited;
  }

  private void report(String msg) {
    String out = "[" + getName(true) + "] " + msg;
    System.err.println(out);
  }

  protected static final class ValidationResult {
    private static final int[] EMPTY_ARRAY = new int[0];
    protected static final ValidationResult INVALID = new ValidationResult(false);
    protected static final ValidationResult ANY = new ValidationResult(true);
    private final boolean isValid;
    private final int[] argsIndex;

    public ValidationResult(boolean valid, int[] argsIndex) {
      if (argsIndex == null) {
        Thread.dumpStack();
      }
      isValid = valid;
      this.argsIndex = argsIndex;
    }

    public ValidationResult(boolean valid) {
      this(valid, EMPTY_ARRAY);
    }

    public int getArgIdx(int ptr) {
      return ptr > -1 && ptr < argsIndex.length ? argsIndex[ptr] : -1;
    }

    public int getArgCnt() {
      return argsIndex.length;
    }

    public boolean isAny() {
      return isValid && argsIndex.length == 0;
    }

    public boolean isValid() {
      return isValid;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ValidationResult other = (ValidationResult) obj;
      if (isValid != other.isValid) {
        return false;
      }
      return Arrays.equals(argsIndex, other.argsIndex);
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 59 * hash + (isValid ? 1 : 0);
      hash = 59 * hash + Arrays.hashCode(argsIndex);
      return hash;
    }
  }

  public abstract static class ArgumentProvider {
    static final Comparator<ArgumentProvider> COMPARATOR =
        (o1, o2) -> {
          if (o1 == null && o2 == null) {
            return 0;
          }
          if (o1 != null && o2 == null) return -1;
          if (o1 == null) return 1;

          if (o1.index == o2.index) {
            return 0;
          }
          if (o1.index < o2.index) {
            return -1;
          }
          return 1;
        };
    protected final Assembler asm;
    private final int index;

    public ArgumentProvider(Assembler asm, int index) {
      this.index = index;
      this.asm = asm;
    }

    public int getIndex() {
      return index;
    }

    public final void provide() {
      if (index > -1) {
        doProvide();
      }
    }

    protected abstract void doProvide();
  }

  private static class LocalVarArgProvider extends ArgumentProvider {
    private final Type type;
    private final int ptr;
    private final boolean boxValue;

    public LocalVarArgProvider(Assembler asm, int index, Type type, int ptr) {
      this(asm, index, type, ptr, false);
    }

    public LocalVarArgProvider(Assembler asm, int index, Type type, int ptr, boolean boxValue) {
      super(asm, index);
      this.type = type;
      this.ptr = ptr;
      this.boxValue = boxValue;
    }

    @Override
    public void doProvide() {
      asm.loadLocal(type, ptr);
      if (boxValue) {
        asm.box(type);
      }
    }

    @Override
    public String toString() {
      return "LocalVar #" + ptr + " of type " + type + " (@" + getIndex() + ")";
    }
  }

  private static class ConstantArgProvider extends ArgumentProvider {
    private final Object constant;

    public ConstantArgProvider(Assembler asm, int index, Object constant) {
      super(asm, index);
      this.constant = constant;
    }

    @Override
    public void doProvide() {
      asm.ldc(constant);
    }

    @Override
    public String toString() {
      return "Constant " + constant + " (@" + getIndex() + ")";
    }
  }

  protected class AnyTypeArgProvider extends ArgumentProvider {
    private int argPtr;
    private final Type[] myArgTypes;

    public AnyTypeArgProvider(Assembler asm, int index, int basePtr) {
      this(asm, index, basePtr, argumentTypes);
    }

    public AnyTypeArgProvider(Assembler asm, int index, int basePtr, Type[] argTypes) {
      super(asm, index);
      argPtr = basePtr;
      myArgTypes = argTypes;
    }

    @Override
    public void doProvide() {
      asm.push(myArgTypes.length);
      asm.newArray(Constants.OBJECT_TYPE);
      for (int j = 0; j < myArgTypes.length; j++) {
        Type argType = myArgTypes[j];
        asm.dup().push(j).loadLocal(argType, argPtr).box(argType).arrayStore(Constants.OBJECT_TYPE);
        argPtr += argType.getSize();
      }
    }
  }
}
