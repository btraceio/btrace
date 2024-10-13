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

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.MethodID;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.core.annotations.Where;
import org.openjdk.btrace.core.types.AnyType;
import org.openjdk.btrace.instr.templates.TemplateExpanderVisitor;
import org.openjdk.btrace.instr.templates.impl.MethodTrackingExpander;

/**
 * This instruments a probed class with BTrace probe action class.
 *
 * @author A. Sundararajan
 */
public class Instrumentor extends ClassVisitor {
  private final BTraceProbe bcn;
  private final ClassLoader cl;
  private final Collection<OnMethod> applicableOnMethods;
  private final Set<OnMethod> calledOnMethods = new HashSet<>();

  private String className, superName;

  private final boolean useHiddenClasses;

  private static boolean useHiddenClassesInTest = false;

  private Instrumentor(
      ClassLoader cl, BTraceProbe bcn, Collection<OnMethod> applicables, ClassVisitor cv) {
    super(ASM9, cv);
    this.cl = cl;
    this.bcn = bcn;
    BTraceRuntime.Impl rt = bcn.getRuntime();
    // 'rt' is null only during instrumentation tests; we want to default to in-situ instrumentation
    // there
    useHiddenClasses = useHiddenClassesInTest || (rt != null && rt.version() >= 15);
    applicableOnMethods = applicables;
  }

  static final Instrumentor create(
      BTraceClassReader cr, BTraceProbe bcn, ClassVisitor cv, ClassLoader cl) {
    if (cr.isInterface()) {
      // do not instrument interfaces
      return null;
    }

    Collection<OnMethod> applicables = bcn.getApplicableHandlers(cr);
    if (applicables != null && !applicables.isEmpty()) {
      return new Instrumentor(cl, bcn, applicables, cv);
    }
    return null;
  }

  private static String getLevelStrSafe(OnMethod om) {
    return om.getLevel() != null ? om.getLevel().getValue().toString() : "";
  }

  private static void reportPatternSyntaxException(String pattern) {
    System.err.println("btrace ERROR: invalid regex pattern - " + pattern);
  }

  public final boolean hasMatch() {
    return !calledOnMethods.isEmpty();
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    className = name;
    this.superName = superName;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {

    List<OnMethod> appliedOnMethods = new ArrayList<>();

    if (applicableOnMethods.isEmpty()
        || (access & ACC_ABSTRACT) != 0
        || name.startsWith(Constants.BTRACE_METHOD_PREFIX)) {
      return super.visitMethod(access, name, desc, signature, exceptions);
    }

    Set<OnMethod> annotationMatchers = new HashSet<>();

    for (OnMethod om : applicableOnMethods) {
      if (om.getLocation().getValue() == Kind.LINE) {
        appliedOnMethods.add(om);
      } else {
        if (om.isMethodAnnotationMatcher()) {
          annotationMatchers.add(om);
          continue;
        }
        String methodName = om.getMethod();
        boolean regexMatch = om.isMethodRegexMatcher();
        if (methodName.isEmpty()) {
          methodName = ".*"; // match all the methods
          regexMatch = true;
        }
        if (methodName.equals("#")) {
          methodName = om.getTargetName(); // match just the same-named method
        }

        if (methodName.equals(name) && typeMatches(om.getType(), desc, om.isExactTypeMatch())) {
          appliedOnMethods.add(om);
        } else if (regexMatch) {
          try {
            if (name.matches(methodName)
                && typeMatches(om.getType(), desc, om.isExactTypeMatch())) {
              appliedOnMethods.add(om);
            }
          } catch (PatternSyntaxException pse) {
            reportPatternSyntaxException(name);
          }
        }
      }
    }

    if (annotationMatchers.isEmpty() && appliedOnMethods.isEmpty()) {
      return super.visitMethod(access, name, desc, signature, exceptions);
    }

    MethodVisitor methodVisitor;

    methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

    InstrumentingMethodVisitor mHelper =
        new InstrumentingMethodVisitor(access, className, name, desc, methodVisitor);

    methodVisitor = mHelper;

    methodVisitor = new TemplateExpanderVisitor(methodVisitor, mHelper, bcn, className, name, desc);

    for (OnMethod om : appliedOnMethods) {
      methodVisitor = instrumentorFor(om, methodVisitor, mHelper, access, name, desc);
    }

    return new MethodVisitor(ASM9, methodVisitor) {
      @Override
      public AnnotationVisitor visitAnnotation(String annoDesc, boolean visible) {
        for (OnMethod om : annotationMatchers) {
          String extAnnoName = Type.getType(annoDesc).getClassName();
          String annoName = om.getMethod();
          if (om.isMethodRegexMatcher()) {
            try {
              if (extAnnoName.matches(annoName)) {
                mv = instrumentorFor(om, mv, mHelper, access, name, desc);
              }
            } catch (PatternSyntaxException pse) {
              reportPatternSyntaxException(extAnnoName);
            }
          } else if (annoName.equals(extAnnoName)) {
            mv = instrumentorFor(om, mv, mHelper, access, name, desc);
          }
        }
        return mv.visitAnnotation(annoDesc, visible);
      }
    };
  }

  private String getMethodOrFieldName(
      boolean fqn, int opcode, String owner, String name, String desc) {
    StringBuilder mName = new StringBuilder();
    if (fqn) {
      switch (opcode) {
        case INVOKEDYNAMIC:
          {
            mName.append("dynamic");
            break;
          }
        case INVOKEINTERFACE:
          {
            mName.append("interface");
            break;
          }
        case INVOKESPECIAL:
          {
            mName.append("special");
            break;
          }
        case INVOKESTATIC:
          {
            mName.append("static");
            break;
          }
        case INVOKEVIRTUAL:
          {
            mName.append("virtual");
            break;
          }
        case PUTSTATIC:
        case GETSTATIC:
          {
            mName.append("static field");
            break;
          }
        case PUTFIELD:
        case GETFIELD:
          {
            mName.append("field");
            break;
          }
      }
      mName.append(' ');
      mName.append(TypeUtils.descriptorToSimplified(desc, owner, name));
    } else {
      mName.append(name);
    }
    return mName.toString();
  }

  private MethodVisitor instrumentorFor(
      OnMethod om,
      MethodVisitor mv,
      MethodInstrumentorHelper mHelper,
      int access,
      String name,
      String desc) {
    Location loc = om.getLocation();
    Where where = loc.getWhere();
    Type[] actionArgTypes = Type.getArgumentTypes(om.getTargetDescriptor());
    int numActionArgs = actionArgTypes.length;

    switch (loc.getValue()) {
      case ARRAY_GET:
        // <editor-fold defaultstate="collapsed" desc="Array Get Instrumentor">
        return new ArrayAccessInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          private final int INDEX_PTR = 0;
          private final int INSTANCE_PTR = 1;
          final int[] argsIndex = {Integer.MIN_VALUE, Integer.MIN_VALUE};

          @Override
          protected void onBeforeArrayLoad(int opcode) {
            Type arrtype = TypeUtils.getArrayType(opcode);
            Type retType = TypeUtils.getElementType(opcode);

            if (locationTypeMismatch(loc, arrtype, retType)) return;

            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), arrtype);

            if (where == Where.AFTER) {
              addExtraTypeInfo(om.getReturnParameter(), retType);
            }
            ValidationResult vr = validateArguments(om, actionArgTypes, new Type[] {Type.INT_TYPE});
            if (vr.isValid()) {
              if (!vr.isAny()) {
                asm.dup2();
                argsIndex[INDEX_PTR] = storeAsNew();
                argsIndex[INSTANCE_PTR] = storeAsNew();
              }
              Label l = levelCheckBefore(om, bcn.getClassName(true));
              if (where == Where.BEFORE) {
                Label l1 = asm.openLinkerCheck();

                loadArguments(
                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                    localVarArg(
                        om.getTargetInstanceParameter(),
                        Constants.OBJECT_TYPE,
                        argsIndex[INSTANCE_PTR]),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                invokeBTraceAction(asm, om);
                asm.closeLinkerCheck(l1);
              }
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }

          @Override
          protected void onAfterArrayLoad(int opcode) {
            if (where == Where.AFTER) {
              Type arrtype = TypeUtils.getArrayType(opcode);
              Type retType = TypeUtils.getElementType(opcode);

              if (locationTypeMismatch(loc, arrtype, retType)) return;

              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), arrtype);
              addExtraTypeInfo(om.getReturnParameter(), retType);
              ValidationResult vr =
                  validateArguments(om, actionArgTypes, new Type[] {Type.INT_TYPE});
              if (vr.isValid()) {
                Label l = levelCheckAfter(om, bcn.getClassName(true));

                int retValIndex = -1;
                Type actionArgRetType =
                    om.getReturnParameter() != -1
                        ? actionArgTypes[om.getReturnParameter()]
                        : Type.VOID_TYPE;
                if (om.getReturnParameter() != -1) {
                  asm.dupArrayValue(opcode);
                  retValIndex = storeNewLocal(retType);
                }

                Label l1 = asm.openLinkerCheck();

                loadArguments(
                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                    localVarArg(
                        om.getTargetInstanceParameter(),
                        Constants.OBJECT_TYPE,
                        argsIndex[INSTANCE_PTR]),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    localVarArg(
                        om.getReturnParameter(),
                        retType,
                        retValIndex,
                        TypeUtils.isAnyType(actionArgRetType)),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));
                invokeBTraceAction(asm, om);

                asm.closeLinkerCheck(l1);

                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }
        }; // </editor-fold>

      case ARRAY_SET:
        // <editor-fold defaultstate="collapsed" desc="Array Set Instrumentor">
        return new ArrayAccessInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          private final int INDEX_PTR = 0, VALUE_PTR = 1, INSTANCE_PTR = 2;
          final int[] argsIndex = {-1, -1, -1, -1};

          @Override
          protected void onBeforeArrayStore(int opcode) {
            Type elementType = TypeUtils.getElementType(opcode);
            Type arrayType = TypeUtils.getArrayType(opcode);

            if (locationTypeMismatch(loc, arrayType, elementType)) return;

            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), arrayType);

            ValidationResult vr =
                validateArguments(om, actionArgTypes, new Type[] {Type.INT_TYPE, elementType});
            if (vr.isValid()) {
              Type argElementType = Type.VOID_TYPE;

              if (!vr.isAny()) {
                int elementIdx = vr.getArgIdx(VALUE_PTR);
                argElementType = elementIdx > -1 ? actionArgTypes[elementIdx] : Type.VOID_TYPE;
                argsIndex[VALUE_PTR] = storeAsNew();
                asm.dup2();
                argsIndex[INDEX_PTR] = storeAsNew();
                argsIndex[INSTANCE_PTR] = storeAsNew();
                asm.loadLocal(elementType, argsIndex[VALUE_PTR]);
              }

              Label l = levelCheckBefore(om, bcn.getClassName(true));

              if (where == Where.BEFORE) {
                Label l1 = asm.openLinkerCheck();

                loadArguments(
                    localVarArg(
                        om.getTargetInstanceParameter(), arrayType, argsIndex[INSTANCE_PTR]),
                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                    localVarArg(
                        vr.getArgIdx(VALUE_PTR),
                        elementType,
                        argsIndex[VALUE_PTR],
                        TypeUtils.isAnyType(argElementType)),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                invokeBTraceAction(asm, om);

                asm.closeLinkerCheck(l1);
              }
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }

          @Override
          protected void onAfterArrayStore(int opcode) {
            if (where == Where.AFTER) {
              Type elementType = TypeUtils.getElementType(opcode);
              Type arrayType = TypeUtils.getArrayType(opcode);

              if (locationTypeMismatch(loc, arrayType, elementType)) return;

              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), arrayType);

              ValidationResult vr =
                  validateArguments(om, actionArgTypes, new Type[] {Type.INT_TYPE, elementType});
              if (vr.isValid()) {
                int elementIdx = vr.getArgIdx(VALUE_PTR);
                Type argElementType = elementIdx > -1 ? actionArgTypes[elementIdx] : Type.VOID_TYPE;

                Label l = levelCheckAfter(om, bcn.getClassName(true));
                Label l1 = asm.openLinkerCheck();

                loadArguments(
                    localVarArg(
                        om.getTargetInstanceParameter(), arrayType, argsIndex[INSTANCE_PTR]),
                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                    localVarArg(
                        vr.getArgIdx(VALUE_PTR),
                        elementType,
                        argsIndex[VALUE_PTR],
                        TypeUtils.isAnyType(argElementType)),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    isStatic()
                        ? constArg(om.getSelfParameter(), null)
                        : localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                invokeBTraceAction(asm, om);
                asm.closeLinkerCheck(l1);
                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }
        }; // </editor-fold>

      case CALL:
        // <editor-fold defaultstate="collapsed" desc="Method Call Instrumentor">
        return new MethodCallInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {

          private final String localClassName = loc.getClazz();
          private final String localMethodName = loc.getMethod();
          int[] backupArgsIndices;
          private int returnVarIndex = -1;
          private boolean generatingCode = false;

          private void injectBtrace(
              ValidationResult vr,
              String method,
              Type[] callArgTypes,
              Type returnType,
              boolean staticCall) {
            ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 7];
            for (int i = 0; i < vr.getArgCnt(); i++) {
              int index = vr.getArgIdx(i);
              Type t = actionArgTypes[index];
              if (TypeUtils.isAnyTypeArray(t)) {
                if (i < backupArgsIndices.length - 1) {
                  actionArgs[i] = anytypeArg(index, backupArgsIndices[i + 1], callArgTypes);
                } else {
                  actionArgs[i] =
                      new ArgumentProvider(asm, index) {

                        @Override
                        protected void doProvide() {
                          asm.push(0).newArray(Constants.OBJECT_TYPE);
                        }
                      };
                }
              } else {
                actionArgs[i] = localVarArg(index, actionArgTypes[index], backupArgsIndices[i + 1]);
              }
            }
            actionArgs[actionArgTypes.length] =
                localVarArg(om.getReturnParameter(), returnType, returnVarIndex);
            actionArgs[actionArgTypes.length + 1] =
                staticCall
                    ? constArg(om.getTargetInstanceParameter(), null)
                    : localVarArg(
                        om.getTargetInstanceParameter(),
                        Constants.OBJECT_TYPE,
                        backupArgsIndices.length == 0 ? -1 : backupArgsIndices[0]);
            actionArgs[actionArgTypes.length + 2] =
                constArg(om.getTargetMethodOrFieldParameter(), method);
            actionArgs[actionArgTypes.length + 3] =
                constArg(om.getClassNameParameter(), className.replace('/', '.'));
            actionArgs[actionArgTypes.length + 4] =
                constArg(om.getMethodParameter(), getName(om.isMethodFqn()));
            actionArgs[actionArgTypes.length + 5] =
                selfArg(om.getSelfParameter(), Type.getObjectType(className));
            actionArgs[actionArgTypes.length + 6] =
                new ArgumentProvider(asm, om.getDurationParameter()) {
                  @Override
                  public void doProvide() {
                    MethodTrackingExpander.DURATION.insert(mv);
                  }
                };

            loadArguments(actionArgs);

            invokeBTraceAction(asm, om);
          }

          @Override
          protected void onBeforeCallMethod(int opcode, String cOwner, String cName, String cDesc) {
            if (!generatingCode) {
              try {
                generatingCode = true;
                if (matches(localClassName, cOwner.replace('/', '.'))
                        && matches(localMethodName, cName)
                        && typeMatches(loc.getType(), cDesc, om.isExactTypeMatch())) {

                  /*
                   * Generate a synthetic method id for the method call.
                   * It will be different from the 'native' method id
                   * in order to prevent double accounting for one hit.
                   */
                  int parentMid = MethodID.getMethodId(className, name, desc);
                  int mid = MethodID.getMethodId("c$" + parentMid + "$" + cOwner, cName, cDesc);

                  String method =
                          getMethodOrFieldName(om.isTargetMethodOrFieldFqn(), opcode, cOwner, cName, cDesc);
                  Type[] calledMethodArgs = Type.getArgumentTypes(cDesc);
                  addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                  addExtraTypeInfo(om.getTargetInstanceParameter(), Type.getObjectType(cOwner));
                  if (where == Where.AFTER) {
                    addExtraTypeInfo(om.getReturnParameter(), Type.getReturnType(cDesc));
                  }
                  ValidationResult vr = validateArguments(om, actionArgTypes, calledMethodArgs);
                  if (vr.isValid()) {
                    boolean isStaticCall = (opcode == INVOKESTATIC);
                    if (!isStaticCall) {
                      if (where == Where.BEFORE && cName.equals(Constants.CONSTRUCTOR)) {
                        return;
                      }
                    }

                    if (om.getDurationParameter() != -1) {
                      MethodTrackingExpander.ENTRY.insert(
                              mv,
                              MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                              MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                              MethodTrackingExpander.$TIMED,
                              MethodTrackingExpander.$METHODID + "=" + mid,
                              MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om));
                    } else {
                      MethodTrackingExpander.ENTRY.insert(
                              mv,
                              MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                              MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                              MethodTrackingExpander.$METHODID + "=" + mid,
                              MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om));
                    }

                    Type[] argTypes = Type.getArgumentTypes(cDesc);
                    boolean shouldBackup = !vr.isAny() || om.getTargetInstanceParameter() != -1;

                    // will store the call args into local variables
                    backupArgsIndices = shouldBackup ? backupStack(argTypes, isStaticCall) : new int[0];

                    if (where == Where.BEFORE) {
                      MethodTrackingExpander.TEST_SAMPLE.insert(
                              mv, MethodTrackingExpander.$METHODID + "=" + mid);
                      Label l = levelCheckBefore(om, bcn.getClassName(true));

                      Label l1 = asm.openLinkerCheck();

                      injectBtrace(vr, method, argTypes, Type.getReturnType(cDesc), isStaticCall);

                      asm.closeLinkerCheck(l1);
                      if (l != null) {
                        mv.visitLabel(l);
                        insertFrameSameStack(l);
                      }
                      MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
                    }

                    // put the call args back on stack so the method call can find them
                    if (shouldBackup) {
                      restoreStack(backupArgsIndices, argTypes, isStaticCall);
                    }
                  }
                }
              } finally {
                generatingCode = false;
              }
            }
          }

          @Override
          protected void onAfterCallMethod(int opcode, String cOwner, String cName, String cDesc) {
            if (matches(localClassName, cOwner.replace('/', '.'))
                && matches(localMethodName, cName)
                && typeMatches(loc.getType(), cDesc, om.isExactTypeMatch())) {

              int parentMid = MethodID.getMethodId(className, name, desc);
              int mid = MethodID.getMethodId("c$" + parentMid + "$" + cOwner, cName, cDesc);

              Type returnType = Type.getReturnType(cDesc);
              Type[] calledMethodArgs = Type.getArgumentTypes(cDesc);
              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), Type.getObjectType(cOwner));
              addExtraTypeInfo(om.getReturnParameter(), returnType);
              ValidationResult vr = validateArguments(om, actionArgTypes, calledMethodArgs);
              if (vr.isValid()) {
                if (om.getDurationParameter() == -1) {
                  MethodTrackingExpander.EXIT.insert(
                      mv, MethodTrackingExpander.$METHODID + "=" + mid);
                }
                if (where == Where.AFTER) {
                  if (om.getDurationParameter() != -1) {
                    MethodTrackingExpander.TEST_SAMPLE.insert(
                        mv,
                        MethodTrackingExpander.$TIMED,
                        MethodTrackingExpander.$METHODID + "=" + mid);
                  } else {
                    MethodTrackingExpander.TEST_SAMPLE.insert(
                        mv, MethodTrackingExpander.$METHODID + "=" + mid);
                  }

                  Label l = levelCheckAfter(om, bcn.getClassName(true));

                  String method =
                      getMethodOrFieldName(
                          om.isTargetMethodOrFieldFqn(), opcode, cOwner, cName, cDesc);
                  boolean withReturn =
                      om.getReturnParameter() != -1 && !returnType.equals(Type.VOID_TYPE);

                  Label l1 = asm.openLinkerCheck();
                  if (withReturn) {
                    // store the return value to a local variable if not augmented return
                    if (Type.getReturnType(om.getTargetDescriptor()).getSort() == Type.VOID) {
                      asm.dupValue(returnType);
                    }
                    returnVarIndex = storeAsNew();
                  }
                  // will also retrieve the call args and the return value from the backup variables
                  injectBtrace(vr, method, calledMethodArgs, returnType, opcode == INVOKESTATIC);

                  asm.closeLinkerCheck(l1);
                  if (l != null) {
                    mv.visitLabel(l);
                    insertFrameSameStack(l);
                  }

                  MethodTrackingExpander.ELSE_SAMPLE.insert(mv);

                  if (parent == null) {
                    MethodTrackingExpander.RESET.insert(mv);
                  }
                }
              }
            }
          }
        }; // </editor-fold>

      case CATCH:
        // <editor-fold defaultstate="collapsed" desc="Catch Instrumentor">
        return new CatchInstrumentor(cl, mv, mHelper, className, superName, access, name, desc) {
          @Override
          protected void onCatch(String type) {
            Type exctype = Type.getObjectType(type);
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), exctype);
            ValidationResult vr =
                validateArguments(om, actionArgTypes, Type.getArgumentTypes(getDescriptor()));
            if (vr.isValid()) {
              int index = -1;
              Label l = levelCheck(om, bcn.getClassName(true));

              if (om.getTargetInstanceParameter() != -1) {
                asm.dup();
                index = storeAsNew();
              }
              Label l1 = asm.openLinkerCheck();

              loadArguments(
                  localVarArg(om.getTargetInstanceParameter(), exctype, index),
                  constArg(om.getClassNameParameter(), className.replace('/', '.')),
                  constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                  selfArg(om.getSelfParameter(), Type.getObjectType(className)));

              invokeBTraceAction(asm, om);

              asm.closeLinkerCheck(l1);

              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }
        }; // </editor-fold>

      case CHECKCAST:
        // <editor-fold defaultstate="collapsed" desc="CheckCast Instrumentor">
        return new TypeCheckInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {

          private void callAction(int opcode, String desc) {
            if (opcode == CHECKCAST) {
              Type castType = Type.getObjectType(desc);
              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), Constants.OBJECT_TYPE);
              ValidationResult vr =
                  validateArguments(om, actionArgTypes, new Type[] {Constants.STRING_TYPE});
              if (vr.isValid()) {
                int castTypeIndex = -1;
                Label l = levelCheck(om, bcn.getClassName(true));

                if (!vr.isAny()) {
                  asm.dup();
                  castTypeIndex = storeAsNew();
                }

                Label l1 = asm.openLinkerCheck();

                loadArguments(
                    constArg(vr.getArgIdx(0), castType.getClassName()),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)),
                    localVarArg(
                        om.getTargetInstanceParameter(), Constants.OBJECT_TYPE, castTypeIndex));

                invokeBTraceAction(asm, om);

                asm.closeLinkerCheck(l1);

                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }

          @Override
          protected void onBeforeTypeCheck(int opcode, String desc) {
            if (where == Where.BEFORE) {
              callAction(opcode, desc);
            }
          }

          @Override
          protected void onAfterTypeCheck(int opcode, String desc) {
            if (where == Where.AFTER) {
              callAction(opcode, desc);
            }
          }
        }; // </editor-fold>

      case ENTRY:
        // <editor-fold defaultstate="collapsed" desc="Method Entry Instrumentor">
        return new MethodReturnInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          private final ValidationResult vr;

          {
            Type[] calledMethodArgs = Type.getArgumentTypes(getDescriptor());
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            vr = validateArguments(om, actionArgTypes, calledMethodArgs);
          }

          private void injectBtrace() {
            Label l1 = asm.openLinkerCheck();

            if (numActionArgs > 0) {
              loadArguments(
                      vr,
                      actionArgTypes,
                      isStatic(),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));
            }
            invokeBTraceAction(asm, om);
            asm.closeLinkerCheck(l1);
          }

          @Override
          protected void visitMethodPrologue() {
            if (vr.isValid() || vr.isAny()) {
              if (om.getSamplerKind() != Sampled.Sampler.None) {
                MethodTrackingExpander.ENTRY.insert(
                    mv,
                    MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                    MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                    MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om));
              }
            }
            super.visitMethodPrologue();
          }

          @Override
          protected void onMethodEntry() {
            if (vr.isValid() || vr.isAny()) {
              if (om.getSamplerKind() != Sampled.Sampler.None) {
                MethodTrackingExpander.TEST_SAMPLE.insert(mv, MethodTrackingExpander.$TIMED);
              }
              Label l = levelCheck(om, bcn.getClassName(true));
              injectBtrace();
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
              if (om.getSamplerKind() != Sampled.Sampler.None) {
                MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
              }
            }
          }

          @Override
          protected void onMethodReturn(int opcode) {
            if (vr.isValid() || vr.isAny()) {
              if (om.getSamplerKind() == Sampled.Sampler.Adaptive) {
                MethodTrackingExpander.EXIT.insert(mv);
              }
            }
          }
        }; // </editor-fold>

      case ERROR:
        // <editor-fold defaultstate="collapsed" desc="Error Instrumentor">
        return new ErrorReturnInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          final ValidationResult vr;
          private boolean generatingCode = false;

          {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), Constants.THROWABLE_TYPE);
            vr = validateArguments(om, actionArgTypes, Type.getArgumentTypes(getDescriptor()));
          }

          @Override
          protected void onErrorReturn() {
            if (vr.isValid()) {
              int throwableIndex = -1;

              MethodTrackingExpander.TEST_SAMPLE.insert(mv, MethodTrackingExpander.$TIMED);

              if (om.getTargetInstanceParameter() != -1) {
                asm.dup();
                throwableIndex = storeAsNew();
              }

              ArgumentProvider[] actionArgs = new ArgumentProvider[5];

              actionArgs[0] =
                  localVarArg(
                      om.getTargetInstanceParameter(), Constants.THROWABLE_TYPE, throwableIndex);
              actionArgs[1] = constArg(om.getClassNameParameter(), className.replace('/', '.'));
              actionArgs[2] = constArg(om.getMethodParameter(), getName(om.isMethodFqn()));
              actionArgs[3] = selfArg(om.getSelfParameter(), Type.getObjectType(className));
              actionArgs[4] =
                  new ArgumentProvider(asm, om.getDurationParameter()) {
                    @Override
                    public void doProvide() {
                      MethodTrackingExpander.DURATION.insert(mv);
                    }
                  };

              Label l = levelCheck(om, bcn.getClassName(true));

              Label l1 = asm.openLinkerCheck();

              loadArguments(vr, actionArgTypes, isStatic(), actionArgs);

              invokeBTraceAction(asm, om);

              asm.closeLinkerCheck(l1);

              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }

              MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
            }
          }

          @Override
          protected void visitMethodPrologue() {
            if (vr.isValid()) {
              if (!generatingCode) {
                try {
                  generatingCode = true;
                  if (om.getDurationParameter() != -1) {
                    MethodTrackingExpander.ENTRY.insert(
                        mv,
                        MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                        MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                        MethodTrackingExpander.$TIMED,
                        MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om));
                  } else {
                    MethodTrackingExpander.ENTRY.insert(
                        mv,
                        MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                        MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                        MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om));
                  }
                } finally {
                  generatingCode = false;
                }
              }
            }
            super.visitMethodPrologue();
          }
        };
        // </editor-fold>

      case FIELD_GET:
        // <editor-fold defaultstate="collapsed" desc="Field Get Instrumentor">
        return new FieldAccessInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {

          private final String targetClassName = loc.getClazz();
          private final String targetFieldName = loc.getField();
          int calledInstanceIndex = Integer.MIN_VALUE;

          @Override
          protected void onBeforeGetField(int opcode, String owner, String name, String desc) {
            if (matches(targetClassName, owner.replace('/', '.'))
                && matches(targetFieldName, name)) {

              Type fldType = Type.getType(desc);
              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), Type.getObjectType(owner));
              if (where == Where.AFTER) {
                addExtraTypeInfo(om.getReturnParameter(), fldType);
              }
              ValidationResult vr = validateArguments(om, actionArgTypes, new Type[0]);
              if (vr.isValid()) {
                if (!isStaticAccess && om.getTargetInstanceParameter() != -1) {
                  asm.dup();
                  calledInstanceIndex = storeAsNew();
                }

                Label l = levelCheckBefore(om, bcn.getClassName(true));

                if (where == Where.BEFORE) {
                  Label l1 = asm.openLinkerCheck();

                  loadArguments(
                      isStaticAccess
                          ? constArg(om.getTargetInstanceParameter(), null)
                          : localVarArg(
                              om.getTargetInstanceParameter(),
                              Constants.OBJECT_TYPE,
                              calledInstanceIndex),
                      constArg(
                          om.getTargetMethodOrFieldParameter(),
                          getMethodOrFieldName(
                              om.isTargetMethodOrFieldFqn(),
                              opcode,
                              owner,
                              name,
                              desc)),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                  invokeBTraceAction(asm, om);
                  asm.closeLinkerCheck(l1);
                }
                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }

          @Override
          protected void onAfterGetField(int opcode, String owner, String name, String desc) {
            if (where == Where.AFTER
                && matches(targetClassName, owner.replace('/', '.'))
                && matches(targetFieldName, name)) {
              Type fldType = Type.getType(desc);

              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), Type.getObjectType(owner));
              addExtraTypeInfo(om.getReturnParameter(), fldType);
              ValidationResult vr = validateArguments(om, actionArgTypes, new Type[0]);
              if (vr.isValid()) {
                int returnValIndex = -1;
                Label l = levelCheckAfter(om, bcn.getClassName(true));

                if (om.getReturnParameter() != -1) {
                  asm.dupValue(desc);
                  returnValIndex = storeAsNew();
                }

                Label l1 = asm.openLinkerCheck();
                loadArguments(
                    isStaticAccess
                        ? constArg(om.getTargetInstanceParameter(), null)
                        : localVarArg(
                            om.getTargetInstanceParameter(),
                            Constants.OBJECT_TYPE,
                            calledInstanceIndex),
                    constArg(
                        om.getTargetMethodOrFieldParameter(),
                        getMethodOrFieldName(
                            om.isTargetMethodOrFieldFqn(),
                            opcode,
                            owner,
                            name,
                            desc)),
                    localVarArg(om.getReturnParameter(), fldType, returnValIndex),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                invokeBTraceAction(asm, om);
                asm.closeLinkerCheck(l1);
                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }
        }; // </editor-fold>

      case FIELD_SET:
        // <editor-fold defaultstate="collapsed" desc="Field Set Instrumentor">
        return new FieldAccessInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          private final String targetClassName = loc.getClazz();
          private final String targetFieldName = loc.getField();
          private int calledInstanceIndex = Integer.MIN_VALUE;
          private int fldValueIndex = -1;

          @Override
          protected void onBeforePutField(int opcode, String owner, String name, String desc) {
            if (matches(targetClassName, owner.replace('/', '.'))
                && matches(targetFieldName, name)) {

              Type fieldType = Type.getType(desc);

              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), Type.getObjectType(owner));
              ValidationResult vr = validateArguments(om, actionArgTypes, new Type[] {fieldType});

              if (vr.isValid()) {
                if (!vr.isAny()) {
                  // store the field value
                  fldValueIndex = storeAsNew();
                }

                if (!isStaticAccess && om.getTargetInstanceParameter() != -1) {
                  asm.dup();
                  calledInstanceIndex = storeAsNew();
                }

                if (!vr.isAny()) {
                  // need to put the set value back on stack
                  asm.loadLocal(fieldType, fldValueIndex);
                }

                Label l = levelCheckBefore(om, bcn.getClassName(true));

                if (where == Where.BEFORE) {
                  Label l1 = asm.openLinkerCheck();
                  loadArguments(
                      localVarArg(vr.getArgIdx(0), fieldType, fldValueIndex),
                      isStaticAccess
                          ? constArg(om.getTargetInstanceParameter(), null)
                          : localVarArg(
                              om.getTargetInstanceParameter(),
                              Constants.OBJECT_TYPE,
                              calledInstanceIndex),
                      constArg(
                          om.getTargetMethodOrFieldParameter(),
                          getMethodOrFieldName(
                              om.isTargetMethodOrFieldFqn(),
                              opcode,
                              owner,
                              name,
                              desc)),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                  invokeBTraceAction(asm, om);
                  asm.closeLinkerCheck(l1);
                }
                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }

          @Override
          protected void onAfterPutField(int opcode, String owner, String name, String desc) {
            if (where == Where.AFTER
                && matches(targetClassName, owner.replace('/', '.'))
                && matches(targetFieldName, name)) {
              Type fieldType = Type.getType(desc);

              addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
              addExtraTypeInfo(om.getTargetInstanceParameter(), Type.getObjectType(owner));
              ValidationResult vr = validateArguments(om, actionArgTypes, new Type[] {fieldType});

              if (vr.isValid()) {
                Label l = levelCheckAfter(om, bcn.getClassName(true));

                Label l1 = asm.openLinkerCheck();
                loadArguments(
                    localVarArg(vr.getArgIdx(0), fieldType, fldValueIndex),
                    isStaticAccess
                        ? constArg(om.getTargetInstanceParameter(), null)
                        : localVarArg(
                            om.getTargetInstanceParameter(),
                            Constants.OBJECT_TYPE,
                            calledInstanceIndex),
                    constArg(
                        om.getTargetMethodOrFieldParameter(),
                        getMethodOrFieldName(
                            om.isTargetMethodOrFieldFqn(),
                            opcode,
                            owner,
                            name,
                            desc)),
                    constArg(om.getClassNameParameter(), className.replace('/', '.')),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                invokeBTraceAction(asm, om);
                asm.closeLinkerCheck(l1);
                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }
        }; // </editor-fold>

      case INSTANCEOF:
        // <editor-fold defaultstate="collapsed" desc="InstanceOf Instrumentor">
        return new TypeCheckInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          ValidationResult vr;
          Type castType = Constants.OBJECT_TYPE;
          int castTypeIndex = -1;

          {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), Constants.OBJECT_TYPE);
          }

          private void callAction(String cName) {
            if (vr.isValid()) {
              Label l = levelCheck(om, bcn.getClassName(true));

              Label l1 = asm.openLinkerCheck();
              loadArguments(
                  constArg(vr.getArgIdx(0), cName),
                  constArg(om.getClassNameParameter(), className.replace('/', '.')),
                  constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                  selfArg(om.getSelfParameter(), Type.getObjectType(className)),
                  localVarArg(
                      om.getTargetInstanceParameter(), Constants.OBJECT_TYPE, castTypeIndex));

              invokeBTraceAction(asm, om);
              asm.closeLinkerCheck(l1);
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }

          @Override
          protected void onBeforeTypeCheck(int opcode, String desc) {
            if (opcode == INSTANCEOF) {
              castType = Type.getObjectType(desc);
              vr = validateArguments(om, actionArgTypes, new Type[] {Constants.STRING_TYPE});
              if (vr.isValid()) {
                if (!vr.isAny()) {
                  asm.dup();
                  castTypeIndex = storeAsNew();
                }
                if (where == Where.BEFORE) {
                  callAction(castType.getClassName());
                }
              }
            }
          }

          @Override
          protected void onAfterTypeCheck(int opcode, String desc) {
            if (opcode == INSTANCEOF) {
              castType = Type.getObjectType(desc);
              vr = validateArguments(om, actionArgTypes, new Type[] {Constants.STRING_TYPE});
              if (vr.isValid()) {
                if (where == Where.AFTER) {
                  callAction(castType.getClassName());
                }
              }
            }
          }
        }; // </editor-fold>

      case LINE:
        // <editor-fold defaultstate="collapsed" desc="Line Instrumentor">
        return new LineNumberInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {

          private final int onLine = loc.getLine();

          private void callOnLine(int line) {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            ValidationResult vr = validateArguments(om, actionArgTypes, new Type[] {Type.INT_TYPE});
            if (vr.isValid()) {
              Label l = levelCheck(om, bcn.getClassName(true));
              Label l1 = asm.openLinkerCheck();
              loadArguments(
                  constArg(vr.getArgIdx(0), line),
                  constArg(om.getClassNameParameter(), className.replace('/', '.')),
                  constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                  selfArg(om.getSelfParameter(), Type.getObjectType(className)));

              invokeBTraceAction(asm, om);
              asm.closeLinkerCheck(l1);
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }

          @Override
          protected void onBeforeLine(int line) {
            if ((line == onLine || onLine == -1) && where == Where.BEFORE) {
              callOnLine(line);
            }
          }

          @Override
          protected void onAfterLine(int line) {
            if ((line == onLine || onLine == -1) && where == Where.AFTER) {
              callOnLine(line);
            }
          }
        }; // </editor-fold>

      case NEW:
        // <editor-fold defaultstate="collapsed" desc="New Instance Instrumentor">
        return new ObjectAllocInstrumentor(
            cl,
            mv,
            mHelper,
            className,
            superName,
            access,
            name,
            desc,
            om.getReturnParameter() != -1) {

          @Override
          protected void beforeObjectNew(String desc) {
            if (loc.getWhere() == Where.BEFORE) {
              String extName = desc.replace('/', '.');
              if (matches(loc.getClazz(), extName)) {
                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                ValidationResult vr =
                    validateArguments(om, actionArgTypes, new Type[] {Constants.STRING_TYPE});
                if (vr.isValid()) {
                  Label l = levelCheck(om, bcn.getClassName(true));
                  Label l1 = asm.openLinkerCheck();
                  loadArguments(
                      constArg(vr.getArgIdx(0), extName),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                  invokeBTraceAction(asm, om);
                  asm.closeLinkerCheck(l1);
                  if (l != null) {
                    mv.visitLabel(l);
                    insertFrameSameStack(l);
                  }
                }
              }
            }
          }

          @Override
          protected void afterObjectNew(String desc) {
            if (loc.getWhere() == Where.AFTER) {
              String extName = desc.replace('/', '.');
              if (matches(loc.getClazz(), extName)) {
                Type instType = Type.getObjectType(desc);

                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                addExtraTypeInfo(om.getReturnParameter(), instType);
                ValidationResult vr =
                    validateArguments(om, actionArgTypes, new Type[] {Constants.STRING_TYPE});
                if (vr.isValid()) {
                  int returnValIndex = -1;
                  Label l = levelCheck(om, bcn.getClassName(true));
                  if (om.getReturnParameter() != -1) {
                    asm.dupValue(instType);
                    returnValIndex = storeAsNew();
                  }
                  Label l1 = asm.openLinkerCheck();
                  loadArguments(
                      constArg(vr.getArgIdx(0), extName),
                      localVarArg(om.getReturnParameter(), instType, returnValIndex),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                  invokeBTraceAction(asm, om);
                  asm.closeLinkerCheck(l1);
                  if (l != null) {
                    mv.visitLabel(l);
                    insertFrameSameStack(l);
                  }
                }
              }
            }
          }
        }; // </editor-fold>

      case NEWARRAY:
        // <editor-fold defaultstate="collapsed" desc="New Array Instrumentor">
        return new ArrayAllocInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {

          @Override
          protected void onBeforeArrayNew(String desc, int dims) {
            if (where == Where.BEFORE) {
              String extName = TypeUtils.getJavaType(desc);
              String type = loc.getClazz();
              if (matches(type, extName)) {
                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                ValidationResult vr =
                    validateArguments(
                        om, actionArgTypes, new Type[] {Constants.STRING_TYPE, Type.INT_TYPE});
                if (vr.isValid()) {
                  Label l = levelCheck(om, bcn.getClassName(true));
                  Label l1 = asm.openLinkerCheck();
                  loadArguments(
                      constArg(vr.getArgIdx(0), extName),
                      constArg(vr.getArgIdx(1), dims),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                  invokeBTraceAction(asm, om);
                  asm.closeLinkerCheck(l1);
                  if (l != null) {
                    mv.visitLabel(l);
                    insertFrameSameStack(l);
                  }
                }
              }
            }
          }

          @Override
          protected void onAfterArrayNew(String desc, int dims) {
            if (where == Where.AFTER) {
              String extName = TypeUtils.getJavaType(desc);
              String type = loc.getClazz();
              if (matches(type, extName)) {
                StringBuilder arrayType = new StringBuilder();
                for (int i = 0; i < dims; i++) {
                  arrayType.append("[");
                }
                arrayType.append(desc);
                Type instType = Type.getObjectType(arrayType.toString());
                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                addExtraTypeInfo(om.getReturnParameter(), instType);
                ValidationResult vr =
                    validateArguments(
                        om, actionArgTypes, new Type[] {Constants.STRING_TYPE, Type.INT_TYPE});
                if (vr.isValid()) {
                  int returnValIndex = -1;
                  Label l = levelCheck(om, bcn.getClassName(true));
                  if (om.getReturnParameter() != -1) {
                    asm.dupValue(instType);
                    returnValIndex = storeAsNew();
                  }
                  Label l1 = asm.openLinkerCheck();
                  loadArguments(
                      constArg(vr.getArgIdx(0), extName),
                      constArg(vr.getArgIdx(1), dims),
                      localVarArg(om.getReturnParameter(), instType, returnValIndex),
                      constArg(om.getClassNameParameter(), className.replace('/', '.')),
                      constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                      selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                  invokeBTraceAction(asm, om);
                  asm.closeLinkerCheck(l1);
                  if (l != null) {
                    mv.visitLabel(l);
                    insertFrameSameStack(l);
                  }
                }
              }
            }
          }
        }; // </editor-fold>

      case RETURN:
        // <editor-fold defaultstate="collapsed" desc="Return Instrumentor">
        if (where != Where.BEFORE) {
          return mv;
        }
        return new MethodReturnInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          int retValIndex;

          final ValidationResult vr;
          private boolean generatingCode = false;

          {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getReturnParameter(), getReturnType());

            vr = validateArguments(om, actionArgTypes, Type.getArgumentTypes(getDescriptor()));
          }

          private void callAction(int retOpCode) {
            if (!vr.isValid()) {
              return;
            }

            Label l1 = asm.openLinkerCheck();

            boolean boxReturnValue = false;
            Type probeRetType = getReturnType();
            if (om.getReturnParameter() != -1) {
              Type retType =
                  Type.getArgumentTypes(om.getTargetDescriptor())[om.getReturnParameter()];
              if (probeRetType.equals(Type.VOID_TYPE)) {
                if (TypeUtils.isAnyType(retType)) {
                  // no return value but still tracking
                  // let's push a synthetic AnyType value on stack
                  asm.getStatic(
                      Type.getInternalName(AnyType.class), "VOID", Constants.ANYTYPE_DESC);
                  probeRetType = Constants.OBJECT_TYPE;
                } else if (Constants.VOIDREF_TYPE.equals(retType)) {
                  // intercepting return from method not returning value (void)
                  // the receiver accepts java.lang.Void only so let's push NULL on stack
                  asm.loadNull();
                  probeRetType = Constants.VOIDREF_TYPE;
                }
              } else {
                if (Type.getReturnType(om.getTargetDescriptor()).getSort() == Type.VOID) {
                  asm.dupReturnValue(retOpCode);
                }
                boxReturnValue = TypeUtils.isAnyType(retType);
              }
              retValIndex = storeAsNew();
            }

            loadArguments(
                vr,
                actionArgTypes,
                isStatic(),
                constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                constArg(om.getClassNameParameter(), className.replace("/", ".")),
                localVarArg(om.getReturnParameter(), probeRetType, retValIndex, boxReturnValue),
                selfArg(om.getSelfParameter(), Type.getObjectType(className)),
                new ArgumentProvider(asm, om.getDurationParameter()) {
                  @Override
                  public void doProvide() {
                    MethodTrackingExpander.DURATION.insert(mv);
                  }
                });

            invokeBTraceAction(asm, om);
            asm.closeLinkerCheck(l1);
          }

          @Override
          protected void onMethodReturn(int opcode) {
            if (vr.isValid() || vr.isAny()) {
              MethodTrackingExpander.TEST_SAMPLE.insert(mv, MethodTrackingExpander.$TIMED);

              Label l = levelCheck(om, bcn.getClassName(true));
              if (numActionArgs == 0) {
                invokeBTraceAction(asm, om);
              } else {
                callAction(opcode);
              }
              MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }

          @Override
          protected void onMethodEntry() {
            if (vr.isValid() || vr.isAny()) {
              try {
                if (!generatingCode) {
                  generatingCode = true;

                  if (om.getDurationParameter() != -1) {
                    MethodTrackingExpander.ENTRY.insert(
                        mv,
                        MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                        MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                        MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om),
                        MethodTrackingExpander.$TIMED);
                  } else {
                    MethodTrackingExpander.ENTRY.insert(
                        mv,
                        MethodTrackingExpander.$MEAN + "=" + om.getSamplerMean(),
                        MethodTrackingExpander.$SAMPLER + "=" + om.getSamplerKind(),
                        MethodTrackingExpander.$LEVEL + "=" + getLevelStrSafe(om));
                  }
                }
              } finally {
                generatingCode = false;
              }
            }
          }
        };
        // </editor-fold>

      case SYNC_ENTRY:
        // <editor-fold defaultstate="collapsed" desc="SyncEntry Instrumentor">
        return new SynchronizedInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          int storedObjIdx = -1;
          final ValidationResult vr;

          {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), Constants.OBJECT_TYPE);
            vr = validateArguments(om, actionArgTypes, Type.getArgumentTypes(getDescriptor()));
          }

          @Override
          protected void onBeforeSyncEntry() {
            if (vr.isValid()) {
              Label l = levelCheckBefore(om, bcn.getClassName(true));

              if (om.getTargetInstanceParameter() != -1) {

                if (isSyncMethod) {
                  if (!isStatic) {
                    storedObjIdx = 0;
                  } else {
                    asm.ldc(Type.getObjectType(className));
                    storedObjIdx = storeAsNew();
                  }
                } else {
                  asm.dup();
                  storedObjIdx = storeAsNew();
                }
              }

              if (where == Where.BEFORE) {
                Label l1 = asm.openLinkerCheck();
                loadArguments(
                    vr,
                    actionArgTypes,
                    isStatic(),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                    localVarArg(
                        om.getTargetInstanceParameter(), Constants.OBJECT_TYPE, storedObjIdx),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));
                invokeBTraceAction(asm, om);
                asm.closeLinkerCheck(l1);
              }
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }

          @Override
          protected void onAfterSyncEntry() {
            if (where == Where.AFTER) {
              if (vr.isValid()) {
                Label l = levelCheckAfter(om, bcn.getClassName(true));

                Label l1 = asm.openLinkerCheck();
                loadArguments(
                    vr,
                    actionArgTypes,
                    isStatic(),
                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                    localVarArg(
                        om.getTargetInstanceParameter(), Constants.OBJECT_TYPE, storedObjIdx),
                    selfArg(om.getSelfParameter(), Type.getObjectType(className)));

                invokeBTraceAction(asm, om);
                asm.closeLinkerCheck(l1);
                if (l != null) {
                  mv.visitLabel(l);
                  insertFrameSameStack(l);
                }
              }
            }
          }

          @Override
          protected void onBeforeSyncExit() {}

          @Override
          protected void onAfterSyncExit() {}
        }; // </editor-fold>

      case SYNC_EXIT:
        // <editor-fold defaultstate="collapsed" desc="SyncExit Instrumentor">
        return new SynchronizedInstrumentor(
            cl, mv, mHelper, className, superName, access, name, desc) {
          int storedObjIdx = -1;
          final ValidationResult vr;

          {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), Constants.OBJECT_TYPE);
            vr = validateArguments(om, actionArgTypes, Type.getArgumentTypes(getDescriptor()));
          }

          private void loadActionArgs() {
            loadArguments(
                vr,
                actionArgTypes,
                isStatic(),
                constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                constArg(om.getClassNameParameter(), className.replace("/", ".")),
                localVarArg(om.getTargetInstanceParameter(), Constants.OBJECT_TYPE, storedObjIdx),
                selfArg(om.getSelfParameter(), Type.getObjectType(className)),
                new MethodInstrumentor.ArgumentProvider(asm, om.getDurationParameter()) {
                  @Override
                  public void doProvide() {
                    MethodTrackingExpander.DURATION.insert(mv);
                  }
                });
          }

          @Override
          protected void onBeforeSyncExit() {
            if (!vr.isValid()) {
              return;
            }
            Label l = levelCheckBefore(om, bcn.getClassName(true));

            if (om.getTargetInstanceParameter() != -1) {
              if (isSyncMethod) {
                if (!isStatic) {
                  storedObjIdx = 0;
                } else {
                  asm.ldc(Type.getObjectType(className));
                  storedObjIdx = storeAsNew();
                }
              } else {
                asm.dup();
                storedObjIdx = storeAsNew();
              }
            }
            if (where == Where.BEFORE) {
              Label l1 = asm.openLinkerCheck();
              loadActionArgs();
              invokeBTraceAction(asm, om);
              asm.closeLinkerCheck(l1);
            }
            if (l != null) {
              mv.visitLabel(l);
              insertFrameSameStack(l);
            }
          }

          @Override
          protected void onAfterSyncExit() {
            if (!vr.isValid()) {
              return;
            }
            if (where == Where.AFTER) {
              loadActionArgs();
              invokeBTraceAction(asm, om);
            }
          }

          @Override
          protected void onAfterSyncEntry() {
            if (!vr.isValid()) {
              return;
            }
            if (om.getDurationParameter() != -1) {
              MethodTrackingExpander.ENTRY.insert(mv, MethodTrackingExpander.$TIMED);
            }
          }

          @Override
          protected void onBeforeSyncEntry() {}
        }; // </editor-fold>

      case THROW:
        // <editor-fold defaultstate="collapsed" desc="Throw Instrumentor">
        return new ThrowInstrumentor(cl, mv, mHelper, className, superName, access, name, desc) {

          @Override
          protected void onThrow() {
            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
            addExtraTypeInfo(om.getTargetInstanceParameter(), Constants.THROWABLE_TYPE);
            ValidationResult vr =
                validateArguments(om, actionArgTypes, Type.getArgumentTypes(getDescriptor()));
            if (vr.isValid()) {
              int throwableIndex = -1;
              Label l = levelCheck(om, bcn.getClassName(true));
              if (om.getTargetInstanceParameter() != -1) {
                asm.dup();
                throwableIndex = storeAsNew();
              }
              Label l1 = asm.openLinkerCheck();
              loadArguments(
                  localVarArg(
                      om.getTargetInstanceParameter(), Constants.THROWABLE_TYPE, throwableIndex),
                  constArg(om.getClassNameParameter(), className.replace('/', '.')),
                  constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                  selfArg(om.getSelfParameter(), Type.getObjectType(className)));

              invokeBTraceAction(asm, om);
              asm.closeLinkerCheck(l1);
              if (l != null) {
                mv.visitLabel(l);
                insertFrameSameStack(l);
              }
            }
          }
        }; // </editor-fold>
    }
    return mv;
  }

  @Override
  public void visitEnd() {
    if (!useHiddenClasses) {
      bcn.copyHandlers(
          new CopyingVisitor(className, false, this) {
            @Override
            protected String getActionMethodName(String name) {
              return Instrumentor.this.getActionMethodName(name);
            }
          });
    }
    cv.visitEnd();
  }

  static String getActionMethodName(BTraceProbe bp, String name) {
    return InstrumentUtils.getActionPrefix(bp.getClassName(true)) + name;
  }

  private String getActionMethodName(String name) {
    return getActionMethodName(bcn, name);
  }

  private void invokeBTraceAction(Assembler asm, OnMethod om) {
    if (useHiddenClasses) {
      MethodType mt =
          MethodType.methodType(
              CallSite.class,
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              String.class);

      asm.invokeDynamic(
          getActionMethodName(om.getTargetName()),
          om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC),
          new Handle(
              H_INVOKESTATIC,
              "org/openjdk/btrace/runtime/Indy",
              "bootstrap",
              mt.toMethodDescriptorString(),
              false),
          bcn.getClassName(true));
    } else {
      asm.invokeStatic(
          className,
          getActionMethodName(om.getTargetName()),
          om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC));
    }
    calledOnMethods.add(om);
    om.setCalled();
  }

  /**
   * Currently used for regex matching in the 'location' attribute
   *
   * @param pattern
   * @param input
   * @return
   */
  private boolean matches(String pattern, String input) {
    if (pattern.length() == 0) {
      return false;
    }
    if (pattern.charAt(0) == '/' && Constants.REGEX_SPECIFIER.matcher(pattern).matches()) {
      try {
        return input.matches(pattern.substring(1, pattern.length() - 1));
      } catch (PatternSyntaxException pse) {
        reportPatternSyntaxException(pattern.substring(1, pattern.length() - 1));
        return false;
      }
    } else {
      return pattern.equals(input);
    }
  }

  private boolean typeMatches(String decl, String desc, boolean exactTypeMatch) {
    // empty type declaration matches any method signature
    if (decl.isEmpty()) {
      return true;
    } else {
      String d = TypeUtils.declarationToDescriptor(decl);
      Type[] argTypesLeft = Type.getArgumentTypes(d);
      Type[] argTypesRight = Type.getArgumentTypes(desc);
      Type retTypeLeft = Type.getReturnType(d);
      Type retTypeRight = Type.getReturnType(desc);
      return InstrumentUtils.isAssignable(retTypeLeft, retTypeRight, cl, exactTypeMatch)
          && InstrumentUtils.isAssignable(argTypesLeft, argTypesRight, cl, exactTypeMatch);
    }
  }

  boolean hasCushionMethods() {
    return !useHiddenClasses;
  }
}
