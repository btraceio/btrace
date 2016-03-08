/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime;

import com.sun.btrace.AnyType;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.annotations.Where;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import com.sun.btrace.util.templates.TemplateExpanderVisitor;
import java.util.regex.PatternSyntaxException;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.util.LocalVariableHelperImpl;
import com.sun.btrace.util.LocalVariableHelper;
import com.sun.btrace.util.MethodID;
import com.sun.btrace.util.templates.impl.MethodTrackingExpander;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * This instruments a probed class with BTrace probe
 * action class.
 *
 * @author A. Sundararajan
 */
public class Instrumentor extends ClassVisitor {
    private final BTraceClassNode bcn;
    private final List<OnMethod> applicableOnMethods = new ArrayList<>();
    private final Set<OnMethod> calledOnMethods = new HashSet<>();
    private String className, superName;
    private Class clazz;
    private ClassLoader loader;

    public Instrumentor(ClassLoader loader, Class clazz,
            BTraceClassNode bcn, ClassVisitor cv) {
        super(ASM5, cv);
        this.clazz = clazz;
        this.bcn = bcn;
        this.loader = loader;
    }

    final public boolean hasMatch() {
        return !calledOnMethods.isEmpty();
    }

    @Override
    public void visit(int version, int access, String name,
        String signature, String superName, String[] interfaces) {
        className = name;
        this.superName = superName;
        // we filter the probe methods applicable for this particular
        // class by brute force walking. FIXME: should I optimize?
        String externalName = name.replace('/', '.');
        for (OnMethod om : bcn.getOnMethods()) {
            String probeClazz = om.getClazz();
            if (probeClazz.length() == 0) {
                continue;
            }
            char firstChar = probeClazz.charAt(0);
            if (firstChar == '/' &&
                REGEX_SPECIFIER.matcher(probeClazz).matches()) {
                probeClazz = probeClazz.substring(1, probeClazz.length() - 1);
                try {
                    if (externalName.matches(probeClazz)) {
                        applicableOnMethods.add(om);
                    }
                } catch (PatternSyntaxException pse) {
                    reportPatternSyntaxException(probeClazz);
                }
            } else if (firstChar == '+') {
                // super type being matched.
                String superType = probeClazz.substring(1);
                // internal name of super type.
                String superTypeInternal = superType.replace('.', '/');
                /*
                 * The following checks are ordered in the order of complexity
                 * <ol>
                 * <li>Check that the class is actually the probe declared super type</li>
                 * <li>Check that the immediate superclass == probe declared super type</li>
                 * <li>Check whether the immediate interfaces contain the probe declared super type</li>
                 * <li>Collect the type hierarchy closure for the class and check if it contains
                 *     the probe declared super type</li>
                 * </ol>
                 */
                if (name.equals(superTypeInternal) ||
                    superName.equals(superTypeInternal) ||
                    isInArray(interfaces, superTypeInternal) ||
                    ClassFilter.isSubTypeOf(name, loader, superTypeInternal)) {
                    applicableOnMethods.add(om);
                }
            } else if (probeClazz.equals(externalName)) {
                applicableOnMethods.add(om);
            }
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        String extName = Type.getType(desc).getClassName();
        for (OnMethod om : bcn.getOnMethods()) {
            String probeClazz = om.getClazz();
            if (probeClazz.length() > 0 && probeClazz.charAt(0) == '@') {
                probeClazz = probeClazz.substring(1);
                if (probeClazz.length() == 0) {
                    continue;
                }
                if (REGEX_SPECIFIER.matcher(probeClazz).matches()) {
                    probeClazz = probeClazz.substring(1, probeClazz.length() - 1);
                    try {
                        if (extName.matches(probeClazz)) {
                            applicableOnMethods.add(om);
                        }
                    } catch (PatternSyntaxException pse) {
                        reportPatternSyntaxException(probeClazz);
                    }
                } else if (probeClazz.equals(extName)) {
                    applicableOnMethods.add(om);
                }
            }
        }
        return av;
    }

    @Override
    public MethodVisitor visitMethod(int access, final String name,
        final String desc, String signature, String[] exceptions) {

        List<OnMethod> appliedOnMethods = new LinkedList<>();

        if (applicableOnMethods.isEmpty() ||
            (access & ACC_ABSTRACT) != 0) {
            return new TemplateExpanderVisitor(
                new LocalVariableHelperImpl(
                    super.visitMethod(access, name, desc, signature, exceptions),
                    access,
                    desc
                ),
                className, name, desc
            );
        }

        for (OnMethod om : applicableOnMethods) {
            if (om.getLocation().getValue() == Kind.LINE) {
                appliedOnMethods.add(om);
            } else {
                String methodName = om.getMethod();
                if (methodName.equals("")) {
                    methodName = "/.*/"; // match all the methods
                }
                if (methodName.equals("#")) {
                    methodName = om.getTargetName(); // match just the same-named method
                }

                if (methodName.equals(name) &&
                    typeMatches(om.getType(), desc)) {
                    appliedOnMethods.add(om);
                } else if (methodName.charAt(0) == '/' &&
                           REGEX_SPECIFIER.matcher(methodName).matches()) {
                    methodName = methodName.substring(1, methodName.length() - 1);
                    try {
                        if (name.matches(methodName) &&
                            typeMatches(om.getType(), desc)) {
                            appliedOnMethods.add(om);
                        }
                    } catch (PatternSyntaxException pse) {
                        reportPatternSyntaxException(name);
                    }
                }
            }
        }

        if (appliedOnMethods.isEmpty()) {
            return new TemplateExpanderVisitor(
                new LocalVariableHelperImpl(
                    super.visitMethod(access, name, desc, signature, exceptions),
                    access,
                    desc
                ),
                className, name, desc
            );
        }

        MethodVisitor methodVisitor;

        methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        LocalVariableHelperImpl lvs = new LocalVariableHelperImpl(methodVisitor, access, desc);

        TemplateExpanderVisitor tse = new TemplateExpanderVisitor(
            lvs, className, name, desc
        );

        if (name.startsWith(BTRACE_METHOD_PREFIX)) {
            return tse;
        }

        LocalVariableHelper visitor = tse;

        for (OnMethod om : appliedOnMethods) {
            visitor = instrumentorFor(om, visitor, access, name, desc);
        }

        final int mAccess = access;
        return new MethodVisitor(Opcodes.ASM5, (MethodVisitor)visitor) {
            @Override
            public AnnotationVisitor visitAnnotation(String annoDesc,
                                  boolean visible) {
                LocalVariableHelper visitor = (LocalVariableHelper)mv;
                for (OnMethod om : applicableOnMethods) {
                    String extAnnoName = Type.getType(annoDesc).getClassName();
                    String annoName = om.getMethod();
                    if (annoName.length() > 0 && annoName.charAt(0) == '@') {
                        annoName = annoName.substring(1);
                        if (annoName.length() == 0) {
                            continue;
                        }
                        if (REGEX_SPECIFIER.matcher(annoName).matches()) {
                            annoName = annoName.substring(1, annoName.length() - 1);
                            try {
                                if (extAnnoName.matches(annoName)) {
                                    visitor = instrumentorFor(om, visitor, mAccess, name, desc);
                                }
                            } catch (PatternSyntaxException pse) {
                                reportPatternSyntaxException(extAnnoName);
                            }
                        } else if (annoName.equals(extAnnoName)) {
                            visitor = instrumentorFor(om, visitor, mAccess, name, desc);
                        }
                    }
                }
                return ((MethodVisitor)visitor).visitAnnotation(annoDesc, visible);
            }
        };
    }

    private LocalVariableHelper instrumentorFor(
        final OnMethod om, LocalVariableHelper mv,
        final int access, final String name, final String desc) {
        final Location loc = om.getLocation();
        final Where where = loc.getWhere();
        final Type[] actionArgTypes = Type.getArgumentTypes(om.getTargetDescriptor());
        final int numActionArgs = actionArgTypes.length;

        switch (loc.getValue()) {
            case ARRAY_GET:
                // <editor-fold defaultstate="collapsed" desc="Array Get Instrumentor">
                return new ArrayAccessInstrumentor(mv, className, superName, access, name, desc) {
                    int[] argsIndex = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
                    final private int INDEX_PTR = 0;
                    final private int INSTANCE_PTR = 1;

                    @Override
                    protected void onBeforeArrayLoad(int opcode) {
                        Type arrtype = TypeUtils.getArrayType(opcode);
                        Type retType = TypeUtils.getElementType(opcode);

                        if (!locationTypeMatches(loc, arrtype, retType)) return;

                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        addExtraTypeInfo(om.getTargetInstanceParameter(), arrtype);

                        if (where == Where.AFTER) {
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                        }
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE});
                        if (vr.isValid()) {
                            if (!vr.isAny()) {
                                asm.dup2();
                                argsIndex[INDEX_PTR] = storeNewLocal(Type.INT_TYPE);
                                argsIndex[INSTANCE_PTR] = storeNewLocal(arrtype);
                            }
                            Label l = levelCheckBefore(om, bcn.name);
                            if (where == Where.BEFORE) {
                                loadArguments(
                                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                    localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, argsIndex[INSTANCE_PTR]),
                                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(asm, om);
                            }
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayLoad(int opcode) {
                        if (where == Where.AFTER) {
                            Type arrtype = TypeUtils.getArrayType(opcode);
                            Type retType = TypeUtils.getElementType(opcode);

                            if (!locationTypeMatches(loc, arrtype, retType)) return;

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getTargetInstanceParameter(), arrtype);
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE});
                            if (vr.isValid()) {
                                Label l = levelCheckAfter(om, bcn.name);

                                int retValIndex = -1;
                                Type actionArgRetType = om.getReturnParameter() != -1 ?
                                                            actionArgTypes[om.getReturnParameter()] :
                                                            Type.VOID_TYPE;
                                if (om.getReturnParameter() != -1) {
                                    asm.dupArrayValue(opcode);
                                    retValIndex = storeNewLocal(retType);
                                }

                                loadArguments(
                                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                    localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, argsIndex[INSTANCE_PTR]),
                                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getReturnParameter(), retType, retValIndex, TypeUtils.isAnyType(actionArgRetType)),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(asm, om);

                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case ARRAY_SET:
                // <editor-fold defaultstate="collapsed" desc="Array Set Instrumentor">
                return new ArrayAccessInstrumentor(mv, className, superName, access, name, desc) {
                    int[] argsIndex = new int[]{-1, -1, -1, -1};
                    final private int INDEX_PTR = 0, VALUE_PTR = 1, INSTANCE_PTR = 2;

                    @Override
                    protected void onBeforeArrayStore(int opcode) {
                        Type elementType = TypeUtils.getElementType(opcode);
                        Type arrayType = TypeUtils.getArrayType(opcode);

                        if (!locationTypeMatches(loc, arrayType, elementType)) return;

                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        addExtraTypeInfo(om.getTargetInstanceParameter(), arrayType);

                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE, elementType});
                        if (vr.isValid()) {
                            Type argElementType = Type.VOID_TYPE;

                            if (!vr.isAny()) {
                                int elementIdx = vr.getArgIdx(VALUE_PTR);
                                argElementType = elementIdx > -1 ?
                                                    actionArgTypes[elementIdx] :
                                                    Type.VOID_TYPE;
                                argsIndex[VALUE_PTR] = storeNewLocal(elementType);
                                asm.dup2();
                                argsIndex[INDEX_PTR] = storeNewLocal(Type.INT_TYPE);
                                argsIndex[INSTANCE_PTR] = storeNewLocal(TypeUtils.getArrayType(opcode));
                                asm.loadLocal(elementType, argsIndex[VALUE_PTR]);
                            }

                            Label l = levelCheckBefore(om, bcn.name);

                            if (where == Where.BEFORE) {
                                loadArguments(
                                    localVarArg(om.getTargetInstanceParameter(), arrayType, argsIndex[INSTANCE_PTR]),
                                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                    localVarArg(vr.getArgIdx(VALUE_PTR), elementType, argsIndex[VALUE_PTR], TypeUtils.isAnyType(argElementType)),
                                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(asm, om);
                            }
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayStore(int opcode) {
                        if (where == Where.AFTER) {
                            Type elementType = TypeUtils.getElementType(opcode);
                            Type arrayType = TypeUtils.getArrayType(opcode);

                            if (!locationTypeMatches(loc, arrayType, elementType)) return;

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getTargetInstanceParameter(), arrayType);

                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE, elementType});
                            if (vr.isValid()) {
                                int elementIdx = vr.getArgIdx(VALUE_PTR);
                                Type argElementType = elementIdx > -1 ?
                                                       actionArgTypes[elementIdx] :
                                                       Type.VOID_TYPE;

                                Label l = levelCheckAfter(om, bcn.name);

                                loadArguments(
                                    localVarArg(om.getTargetInstanceParameter(), arrayType, argsIndex[INSTANCE_PTR]),
                                    localVarArg(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                    localVarArg(vr.getArgIdx(VALUE_PTR), elementType, argsIndex[VALUE_PTR], TypeUtils.isAnyType(argElementType)),
                                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(asm, om);
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case CALL:
                // <editor-fold defaultstate="collapsed" desc="Method Call Instrumentor">
                return new MethodCallInstrumentor(mv, className, superName, access, name, desc) {

                    private final String localClassName = loc.getClazz();
                    private final String localMethodName = loc.getMethod();
                    private int returnVarIndex = -1;

                    int[] backupArgsIndices;
                    private boolean generatingCode = false;

                    private void injectBtrace(ValidationResult vr, final String method, final Type[] callArgTypes, final Type returnType) {
                        ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 7];
                        for(int i=0;i<vr.getArgCnt();i++) {
                            int index = vr.getArgIdx(i);
                            Type t = actionArgTypes[index];
                            if (TypeUtils.isAnyTypeArray(t)) {
                                if (i < backupArgsIndices.length - 1) {
                                    actionArgs[i] = anytypeArg(index, backupArgsIndices[i+1], callArgTypes);
                                } else {
                                    actionArgs[i] = new ArgumentProvider(asm, index) {

                                        @Override
                                        protected void doProvide() {
                                            asm.push(0)
                                               .newArray(OBJECT_TYPE);
                                        }
                                    };
                                }
                            } else {
                                actionArgs[i] = localVarArg(index, actionArgTypes[index], backupArgsIndices[i+1]);
                            }
                        }
                        actionArgs[actionArgTypes.length] = localVarArg(om.getReturnParameter(), returnType, returnVarIndex);
                        actionArgs[actionArgTypes.length + 1] = localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, backupArgsIndices.length == 0 ? -1 : backupArgsIndices[0]);
                        actionArgs[actionArgTypes.length + 2] = constArg(om.getTargetMethodOrFieldParameter(), method);
                        actionArgs[actionArgTypes.length + 3] = constArg(om.getClassNameParameter(), className);
                        actionArgs[actionArgTypes.length + 4] = constArg(om.getMethodParameter(), getName(om.isMethodFqn()));
                        actionArgs[actionArgTypes.length + 5] = localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0);
                        actionArgs[actionArgTypes.length + 6] = new ArgumentProvider(asm, om.getDurationParameter()) {
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
                        if (isStatic() && om.getSelfParameter() > -1) {
                            return; // invalid combination; a static method can not provide *this*
                        }
                        if (matches(localClassName, cOwner.replace('/', '.'))
                                && matches(localMethodName, cName)
                                && typeMatches(loc.getType(), cDesc)) {

                            /*
                            * Generate a synthetic method id for the method call.
                            * It will be diferent from the 'native' method id
                            * in order to prevent double accounting for one hit.
                            */
                            int parentMid = MethodID.getMethodId(className, name, desc);
                            int mid = MethodID.getMethodId("c$" + parentMid + "$" + cOwner, cName, cDesc);

                            String method = (om.isTargetMethodOrFieldFqn() ? (cOwner + ".") : "") + cName + (om.isTargetMethodOrFieldFqn() ? cDesc : "");
                            Type[] calledMethodArgs = Type.getArgumentTypes(cDesc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            if (where == Where.AFTER) {
                                addExtraTypeInfo(om.getReturnParameter(), Type.getReturnType(cDesc));
                            }
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr.isValid()) {
                                boolean isStaticCall = (opcode == INVOKESTATIC);
                                if (isStaticCall) {
                                    if (om.getTargetInstanceParameter() != -1) {
                                        return;

                                    }
                                } else {
                                    if (where == Where.BEFORE && cName.equals(CONSTRUCTOR)) {
                                        return;
                                    }
                                }
                                if (!generatingCode) {
                                    try {
                                        generatingCode = true;
                                        if (om.getDurationParameter() != -1) {
                                            MethodTrackingExpander.ENTRY.insert(mv,
                                                MethodTrackingExpander.$MEAN +
                                                    "=" +
                                                    om.getSamplerMean(),
                                                MethodTrackingExpander.$SAMPLER +
                                                    "=" +
                                                    om.getSamplerKind(),
                                                MethodTrackingExpander.$TIMED,
                                                MethodTrackingExpander.$METHODID +
                                                    "=" +
                                                    mid,
                                                MethodTrackingExpander.$LEVEL +
                                                "=" + getLevelStrSafe(om)
                                            );
                                        } else {
                                            MethodTrackingExpander.ENTRY.insert(mv,
                                                MethodTrackingExpander.$MEAN +
                                                    "=" +
                                                    om.getSamplerMean(),
                                                MethodTrackingExpander.$SAMPLER +
                                                    "=" +
                                                    om.getSamplerKind(),
                                                MethodTrackingExpander.$METHODID +
                                                    "=" +
                                                    mid,
                                                MethodTrackingExpander.$LEVEL +
                                                "=" + getLevelStrSafe(om)
                                            );
                                        }
                                    } finally {
                                        generatingCode = false;
                                    }
                                }


                                Type[] argTypes = Type.getArgumentTypes(cDesc);
                                boolean shouldBackup = !vr.isAny() || om.getTargetInstanceParameter() != -1;

                                // will store the call args into local variables
                                backupArgsIndices = shouldBackup ? backupStack(argTypes, isStaticCall) : new int[0];

                                if (where == Where.BEFORE) {
                                    MethodTrackingExpander.TEST_SAMPLE.insert(mv,
                                        MethodTrackingExpander.$METHODID +
                                            "=" +
                                            mid
                                    );
                                    Label l = levelCheckBefore(om, bcn.name);

                                    injectBtrace(vr, method, argTypes, Type.getReturnType(cDesc));
                                    if (l != null) {
                                        mv.visitLabel(l);
                                    }
                                    MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
                                }

                                // put the call args back on stack so the method call can find them
                                if (shouldBackup) {
                                    restoreStack(backupArgsIndices, argTypes, isStaticCall);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterCallMethod(int opcode,
                            String cOwner, String cName, String cDesc) {
                        if (isStatic() && om.getSelfParameter() != -1) {
                            return;
                        }
                        if (matches(localClassName, cOwner.replace('/', '.'))
                            && matches(localMethodName, cName)
                            && typeMatches(loc.getType(), cDesc)) {

                            int parentMid = MethodID.getMethodId(className, name, desc);
                            int mid = MethodID.getMethodId("c$" + parentMid + "$" + cOwner, cName, cDesc);

                            Type returnType = Type.getReturnType(cDesc);
                            Type[] calledMethodArgs = Type.getArgumentTypes(cDesc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), returnType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr.isValid()) {
                                if (om.getDurationParameter() == -1) {
                                    MethodTrackingExpander.EXIT.insert(mv);
                                }
                                if (where == Where.AFTER) {
                                    if (om.getDurationParameter() != -1) {
                                        MethodTrackingExpander.TEST_SAMPLE.insert(mv,
                                            MethodTrackingExpander.$TIMED,
                                            MethodTrackingExpander.$METHODID
                                                + "="
                                                + mid
                                        );
                                    } else {
                                        MethodTrackingExpander.TEST_SAMPLE.insert(mv,
                                            MethodTrackingExpander.$METHODID
                                                + "="
                                                + mid
                                        );
                                    }

                                    Label l = levelCheckAfter(om, bcn.name);

                                    String method = cName + cDesc;
                                    boolean withReturn = om.getReturnParameter() != -1 && returnType != Type.VOID_TYPE;
                                    if (withReturn) {
                                        // store the return value to a local variable
                                        int index = storeNewLocal(returnType);
                                        returnVarIndex = index;
                                    }
                                    // will also retrieve the call args and the return value from the backup variables
                                    injectBtrace(vr, method, calledMethodArgs, returnType);
                                    if (withReturn) {
                                        if (Type.getReturnType(om.getTargetDescriptor()).getSort() == Type.VOID) {
                                            asm.loadLocal(returnType, returnVarIndex); // restore the return value
                                        }
                                    }
                                    if (l != null) {
                                        mv.visitLabel(l);
                                    }
                                    MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
                                    if (this.parent == null) {
                                        MethodTrackingExpander.RESET.insert(mv);
                                    }
                                }
                            }
                        }
                    }


                };// </editor-fold>

            case CATCH:
                // <editor-fold defaultstate="collapsed" desc="Catch Instrumentor">
                return new CatchInstrumentor(mv, className, superName, access, name, desc) {
                    @Override
                    protected void onCatch(String type) {
                        Type exctype = Type.getObjectType(type);
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{exctype});
                        if (vr.isValid()) {
                            int index = -1;
                            Label l = levelCheck(om, bcn.name);

                            if (!vr.isAny()) {
                                asm.dup();
                                index = storeNewLocal(exctype);
                            }
                            loadArguments(
                                localVarArg(vr.getArgIdx(0), exctype, index),
                                constArg(om.getClassNameParameter(), className),
                                constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                            invokeBTraceAction(asm, om);
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }
                };// </editor-fold>

            case CHECKCAST:
                // <editor-fold defaultstate="collapsed" desc="CheckCast Instrumentor">
                return new TypeCheckInstrumentor(mv, className, superName, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.CHECKCAST) {
                            Type castType = Type.getObjectType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getTargetInstanceParameter(), OBJECT_TYPE);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE});
                            if (vr.isValid()) {
                                int castTypeIndex = -1;
                                Label l = levelCheck(om, bcn.name);

                                if (!vr.isAny()) {
                                    asm.dup();
                                    castTypeIndex = storeNewLocal(castType);
                                }
                                loadArguments(
                                    constArg(vr.getArgIdx(0), castType.getClassName()),
                                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0),
                                    localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, castTypeIndex));

                                invokeBTraceAction(asm, om);
                                if (l != null) {
                                    mv.visitLabel(l);
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
                };// </editor-fold>

            case ENTRY:
                // <editor-fold defaultstate="collapsed" desc="Method Entry Instrumentor">
                return new MethodReturnInstrumentor(mv, className, superName, access, name, desc) {
                    private final ValidationResult vr;

                    {
                        Type[] calledMethodArgs = Type.getArgumentTypes(getDescriptor());
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                    }

                    private void injectBtrace() {
                        ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 3];
                        int ptr = isStatic() ? 0 : 1;
                        for(int i=0;i<vr.getArgCnt();i++) {
                            int index = vr.getArgIdx(i);
                            Type t = actionArgTypes[index];
                            if (TypeUtils.isAnyTypeArray(t)) {
                                actionArgs[i] = anytypeArg(index, ptr);
                                ptr++;
                            } else {
                                actionArgs[i] = localVarArg(index, t, ptr);
                                ptr += actionArgTypes[index].getSize();
                            }
                        }
                        actionArgs[actionArgTypes.length] = constArg(om.getMethodParameter(), getName(om.isMethodFqn()));
                        actionArgs[actionArgTypes.length + 1] = constArg(om.getClassNameParameter(), className.replace("/", "."));
                        actionArgs[actionArgTypes.length + 2] = localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0);
                        loadArguments(actionArgs);

                        invokeBTraceAction(asm, om);
                    }

                    @Override
                    protected void visitMethodPrologue() {
                        if (vr.isValid() || vr.isAny()) {
                            if (om.getSamplerKind() != Sampled.Sampler.None) {
                                MethodTrackingExpander.ENTRY.insert(mv,
                                    MethodTrackingExpander.$SAMPLER +
                                        "=" + om.getSamplerKind(),
                                    MethodTrackingExpander.$MEAN +
                                        "=" + om.getSamplerMean(),
                                    MethodTrackingExpander.$LEVEL +
                                        "=" + getLevelStrSafe(om)
                                );
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
                            Label l = levelCheck(om, bcn.name);

                            if (numActionArgs == 0) {
                                invokeBTraceAction(asm, om);
                            } else {
                                injectBtrace();
                            }
                            if (l != null) {
                                mv.visitLabel(l);
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
                };// </editor-fold>

            case ERROR:
                // <editor-fold defaultstate="collapsed" desc="Error Instrumentor">
                ErrorReturnInstrumentor eri = new ErrorReturnInstrumentor(mv, className, superName, access, name, desc) {
                    ValidationResult vr;
                    {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                    }

                    @Override
                    protected void onErrorReturn() {
                        if (vr.isValid()) {
                            int throwableIndex = -1;

                            MethodTrackingExpander.TEST_SAMPLE.insert(mv, MethodTrackingExpander.$TIMED);

                            if (!vr.isAny()) {
                                asm.dup();
                                throwableIndex = storeNewLocal(TypeUtils.throwableType);
                            }

                            ArgumentProvider[] actionArgs = new ArgumentProvider[5];

                            actionArgs[0] = localVarArg(vr.getArgIdx(0), TypeUtils.throwableType, throwableIndex);
                            actionArgs[1] = constArg(om.getClassNameParameter(), className.replace("/", "."));
                            actionArgs[2] = constArg(om.getMethodParameter(), getName(om.isMethodFqn()));
                            actionArgs[3] = localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0);
                            actionArgs[4] = new ArgumentProvider(asm, om.getDurationParameter()) {
                                @Override
                                public void doProvide() {
                                    MethodTrackingExpander.DURATION.insert(mv);
                                }
                            };

                            Label l = levelCheck(om, bcn.name);

                            loadArguments(actionArgs);

                            invokeBTraceAction(asm, om);
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                            MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
                        }
                    }

                    private boolean generatingCode = false;

                    @Override
                    protected void visitMethodPrologue() {
                        if (vr.isValid()) {
                            if (!generatingCode) {
                                try {
                                    generatingCode = true;
                                    if (om.getDurationParameter() != -1) {
                                        MethodTrackingExpander.ENTRY.insert(mv,
                                            MethodTrackingExpander.$MEAN +
                                                "=" +
                                                om.getSamplerMean(),
                                            MethodTrackingExpander.$SAMPLER +
                                                "=" +
                                                om.getSamplerKind(),
                                            MethodTrackingExpander.$TIMED,
                                            MethodTrackingExpander.$LEVEL +
                                                "=" + getLevelStrSafe(om)
                                        );
                                    } else {
                                        MethodTrackingExpander.ENTRY.insert(mv,
                                            MethodTrackingExpander.$MEAN +
                                                "=" +
                                                om.getSamplerMean(),
                                            MethodTrackingExpander.$SAMPLER +
                                                "=" +
                                                om.getSamplerKind(),
                                            MethodTrackingExpander.$LEVEL +
                                                "=" + getLevelStrSafe(om)
                                        );
                                    }
                                } finally {
                                    generatingCode = false;
                                }
                            }
                        }
                        super.visitMethodPrologue();
                    }
                };
                return eri;
            // </editor-fold>

            case FIELD_GET:
                // <editor-fold defaultstate="collapsed" desc="Field Get Instrumentor">
                return new FieldAccessInstrumentor(mv, className, superName, access, name, desc) {

                    int calledInstanceIndex = Integer.MIN_VALUE;
                    private final String targetClassName = loc.getClazz();
                    private final String targetFieldName = (om.isTargetMethodOrFieldFqn() ? targetClassName + "." : "") + loc.getField();

                    @Override
                    protected void onBeforeGetField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {

                            Type fldType = Type.getType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            if (where == Where.AFTER) {
                                addExtraTypeInfo(om.getReturnParameter(), fldType);
                            }
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[0]);
                            if (vr.isValid()) {
                                if (om.getTargetInstanceParameter() != -1) {
                                    asm.dup();
                                    calledInstanceIndex = storeNewLocal(OBJECT_TYPE);
                                }

                                Label l = levelCheckBefore(om, bcn.name);

                                if (where == Where.BEFORE) {
                                    loadArguments(
                                        localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, calledInstanceIndex),
                                        constArg(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(asm, om);
                                }
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterGetField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (where == Where.AFTER
                                && matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {
                            Type fldType = Type.getType(desc);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), fldType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[0]);
                            if (vr.isValid()) {
                                int returnValIndex = -1;
                                Label l = levelCheckAfter(om, bcn.name);

                                if (om.getReturnParameter() != -1) {
                                    asm.dupValue(desc);
                                    returnValIndex = storeNewLocal(fldType);
                                }

                                loadArguments(
                                    localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, calledInstanceIndex),
                                    constArg(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                    localVarArg(om.getReturnParameter(), fldType, returnValIndex),
                                    constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(asm, om);
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case FIELD_SET:
                // <editor-fold defaultstate="collapsed" desc="Field Set Instrumentor">
                return new FieldAccessInstrumentor(mv, className, superName, access, name, desc) {
                    private final String targetClassName = loc.getClazz();
                    private final String targetFieldName = (om.isTargetMethodOrFieldFqn() ? targetClassName + "." : "") + loc.getField();
                    private int calledInstanceIndex = Integer.MIN_VALUE;
                    private int fldValueIndex = -1;

                    @Override
                    protected void onBeforePutField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {

                            Type fieldType = Type.getType(desc);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{fieldType});

                            if (vr.isValid()) {
                                if (!vr.isAny()) {
                                    // store the field value
                                    fldValueIndex = storeNewLocal(fieldType);
                                }

                                if (om.getTargetInstanceParameter() != -1) {
                                    asm.dup();
                                    calledInstanceIndex = storeNewLocal(OBJECT_TYPE);
                                }

                                if (!vr.isAny()) {
                                    // need to put the set value back on stack
                                    asm.loadLocal(fieldType, fldValueIndex);
                                }

                                Label l = levelCheckBefore(om, bcn.name);

                                if (where == Where.BEFORE) {
                                    loadArguments(
                                        localVarArg(vr.getArgIdx(0), fieldType, fldValueIndex),
                                        localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, calledInstanceIndex),
                                        constArg(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(asm, om);
                                }
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterPutField(int opcode,
                            String owner, String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;

                        }
                        if (where == Where.AFTER
                                && matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {
                            Type fieldType = Type.getType(desc);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{fieldType});

                            if (vr.isValid()) {
                                Label l = levelCheckAfter(om, bcn.name);

                                loadArguments(
                                        localVarArg(vr.getArgIdx(0), fieldType, fldValueIndex),
                                        localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, calledInstanceIndex),
                                        constArg(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(asm, om);
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case INSTANCEOF:
                // <editor-fold defaultstate="collapsed" desc="InstanceOf Instrumentor">
                return new TypeCheckInstrumentor(mv, className, superName, access, name, desc) {
                    ValidationResult vr;
                    Type castType = OBJECT_TYPE;
                    int castTypeIndex = -1;

                    {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        addExtraTypeInfo(om.getTargetInstanceParameter(), OBJECT_TYPE);
                    }

                    private void callAction(String cName) {
                        if (vr.isValid()) {
                            Label l = levelCheck(om, bcn.name);

                            loadArguments(
                                constArg(vr.getArgIdx(0), cName),
                                constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0),
                                localVarArg(om.getTargetInstanceParameter(), OBJECT_TYPE, castTypeIndex));

                            invokeBTraceAction(asm, om);
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }

                    @Override
                    protected void onBeforeTypeCheck(int opcode, String desc) {
                        if (opcode == Opcodes.INSTANCEOF) {
                            castType = Type.getObjectType(desc);
                            vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE});
                            if (vr.isValid()) {
                                if (!vr.isAny()) {
                                    asm.dup();
                                    castTypeIndex = storeNewLocal(castType);
                                }
                                if (where == Where.BEFORE) {
                                    callAction(castType.getClassName());
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterTypeCheck(int opcode, String desc) {
                        if (opcode == Opcodes.INSTANCEOF) {
                            castType = Type.getObjectType(desc);
                            vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE});
                            if (vr.isValid()) {
                                if (where == Where.AFTER) {
                                    callAction(castType.getClassName());
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case LINE:
                // <editor-fold defaultstate="collapsed" desc="Line Instrumentor">
                return new LineNumberInstrumentor(mv, className, superName, access, name, desc) {

                    private final int onLine = loc.getLine();

                    private void callOnLine(int line) {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE});
                        if (vr.isValid()) {
                            Label l = levelCheck(om, bcn.name);
                            loadArguments(
                                constArg(vr.getArgIdx(0), line),
                                constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                            invokeBTraceAction(asm, om);
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }

                    @Override
                    protected void onBeforeLine(int line) {
                        if ((line == onLine || onLine == -1)
                                && where == Where.BEFORE) {
                            callOnLine(line);
                        }
                    }

                    @Override
                    protected void onAfterLine(int line) {
                        if ((line == onLine || onLine == -1)
                                && where == Where.AFTER) {
                            callOnLine(line);
                        }
                    }
                };// </editor-fold>

            case NEW:
                // <editor-fold defaultstate="collapsed" desc="New Instance Instrumentor">
                return new ObjectAllocInstrumentor(mv, className, superName, access, name, desc, om.getReturnParameter() != -1) {

                    @Override
                    protected void beforeObjectNew(String desc) {
                        if (loc.getWhere() == Where.BEFORE) {
                            String extName = desc.replace('/', '.');
                            if (matches(loc.getClazz(), extName)) {
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE});
                                if (vr.isValid()) {
                                    Label l = levelCheck(om, bcn.name);
                                    loadArguments(
                                        constArg(vr.getArgIdx(0), extName),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(asm, om);
                                    if (l != null) {
                                        mv.visitLabel(l);
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
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE});
                                if (vr.isValid()) {
                                    int returnValIndex = -1;
                                    Label l = levelCheck(om, bcn.name);
                                    if (om.getReturnParameter() != -1) {
                                        asm.dupValue(instType);
                                        returnValIndex = storeNewLocal(instType);
                                    }
                                    loadArguments(
                                        constArg(vr.getArgIdx(0), extName),
                                        localVarArg(om.getReturnParameter(), instType, returnValIndex),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(asm, om);
                                    if (l != null) {
                                        mv.visitLabel(l);
                                    }
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case NEWARRAY:
                // <editor-fold defaultstate="collapsed" desc="New Array Instrumentor">
                return new ArrayAllocInstrumentor(mv, className, superName, access, name, desc) {

                    @Override
                    protected void onBeforeArrayNew(String desc, int dims) {
                        if (where == Where.BEFORE) {
                            String extName = TypeUtils.getJavaType(desc);
                            String type = loc.getClazz();
                            if (matches(type, extName)) {
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE, Type.INT_TYPE});
                                if (vr.isValid()) {
                                    Label l = levelCheck(om, bcn.name);
                                    loadArguments(
                                        constArg(vr.getArgIdx(0), extName),
                                        constArg(vr.getArgIdx(1), dims),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(asm, om);
                                    if (l != null) {
                                        mv.visitLabel(l);
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
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{STRING_TYPE, Type.INT_TYPE});
                                if (vr.isValid()) {
                                    int returnValIndex = -1;
                                    Label l = levelCheck(om, bcn.name);
                                    if (om.getReturnParameter() != -1) {
                                        asm.dupValue(instType);
                                        returnValIndex = storeNewLocal(instType);
                                    }
                                    loadArguments(
                                        constArg(vr.getArgIdx(0), extName),
                                        constArg(vr.getArgIdx(1), dims),
                                        localVarArg(om.getReturnParameter(), instType, returnValIndex),
                                        constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                        constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(asm, om);
                                    if (l != null) {
                                        mv.visitLabel(l);
                                    }
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case RETURN:
                // <editor-fold defaultstate="collapsed" desc="Return Instrumentor">
                if (where != Where.BEFORE) {
                    return mv;
                }
                MethodReturnInstrumentor mri = new MethodReturnInstrumentor(mv, className, superName, access, name, desc) {
                    int retValIndex;

                    ValidationResult vr;
                    {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        addExtraTypeInfo(om.getReturnParameter(), getReturnType());

                        vr = validateArguments(om, isStatic(), actionArgTypes, Type.getArgumentTypes(getDescriptor()));
                    }

                    private void callAction(int retOpCode) {
                        if (!vr.isValid()) {
                            return;
                        }

                        try {
                            boolean boxReturnValue = false;
                            Type probeRetType = getReturnType();
                            if (om.getReturnParameter() != -1) {
                                Type retType = Type.getArgumentTypes(om.getTargetDescriptor())[om.getReturnParameter()];
                                if (probeRetType == Type.VOID_TYPE) {
                                    if (TypeUtils.isAnyType(retType)) {
                                        // no return value but still tracking
                                        // let's push a synthetic AnyType value on stack
                                        asm.getStatic(Type.getInternalName(AnyType.class), "VOID", ANYTYPE_DESC);
                                        probeRetType = OBJECT_TYPE;
                                    } else if (VOIDREF_TYPE.equals(retType)) {
                                        // intercepting return from method not returning value (void)
                                        // the receiver accepts java.lang.Void only so let's push NULL on stack
                                        asm.loadNull();
                                        probeRetType = VOIDREF_TYPE;
                                    }
                                } else {
                                    if (Type.getReturnType(om.getTargetDescriptor()).getSort() == Type.VOID) {
                                        asm.dupReturnValue(retOpCode);
                                    }
                                    boxReturnValue = TypeUtils.isAnyType(retType);
                                }
                                retValIndex = storeNewLocal(probeRetType);
                            }

                            ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 5];
                            int ptr = isStatic() ? 0 : 1;
                            for(int i=0;i<vr.getArgCnt();i++) {
                                int index = vr.getArgIdx(i);
                                Type t = actionArgTypes[index];
                                if (TypeUtils.isAnyTypeArray(t)) {
                                    actionArgs[i] = anytypeArg(i, ptr);
                                    ptr++;
                                } else {
                                    actionArgs[i] = localVarArg(index, t, ptr);
                                    ptr += actionArgTypes[index].getSize();
                                }
                            }
                            actionArgs[actionArgTypes.length] = constArg(om.getMethodParameter(), getName(om.isMethodFqn()));
                            actionArgs[actionArgTypes.length + 1] = constArg(om.getClassNameParameter(), className.replace("/", "."));
                            actionArgs[actionArgTypes.length + 2] = localVarArg(om.getReturnParameter(), probeRetType, retValIndex, boxReturnValue);
                            actionArgs[actionArgTypes.length + 3] = localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0);
                            actionArgs[actionArgTypes.length + 4] = new ArgumentProvider(asm, om.getDurationParameter()) {
                                @Override
                                public void doProvide() {
                                    MethodTrackingExpander.DURATION.insert(mv);
                                }
                            };
                            loadArguments(actionArgs);

                            invokeBTraceAction(asm, om);
                        } finally {
                            if (getSkipLabel() != null) {
                                visitLabel(getSkipLabel());
                            }
                        }
                    }

                    @Override
                    protected void onMethodReturn(int opcode) {
                        if (vr.isValid() || vr.isAny()) {
                            MethodTrackingExpander.TEST_SAMPLE.insert(mv, MethodTrackingExpander.$TIMED);

                            Label l = levelCheck(om, bcn.name);
                            if (numActionArgs == 0) {
                                invokeBTraceAction(asm, om);
                            } else {
                                callAction(opcode);
                            }
                            MethodTrackingExpander.ELSE_SAMPLE.insert(mv);
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }

                    private boolean generatingCode = false;

                    @Override
                    protected void onMethodEntry() {
                        if (vr.isValid() || vr.isAny()) {
                            try {
                                if (!generatingCode) {
                                    generatingCode = true;

                                    if (om.getDurationParameter() != -1) {
                                        MethodTrackingExpander.ENTRY.insert(mv,
                                            MethodTrackingExpander.$MEAN +
                                                "=" +
                                                om.getSamplerMean(),
                                            MethodTrackingExpander.$SAMPLER +
                                                "=" +
                                                om.getSamplerKind(),
                                            MethodTrackingExpander.$LEVEL +
                                                "=" + getLevelStrSafe(om),
                                            MethodTrackingExpander.$TIMED
                                        );
                                    } else {
                                        MethodTrackingExpander.ENTRY.insert(mv,
                                            MethodTrackingExpander.$MEAN +
                                                "=" +
                                                om.getSamplerMean(),
                                            MethodTrackingExpander.$SAMPLER +
                                                "=" +
                                                om.getSamplerKind(),
                                            MethodTrackingExpander.$LEVEL +
                                                "=" + getLevelStrSafe(om)
                                        );
                                    }
                                }
                            } finally {
                                generatingCode = false;
                            }
                        }
                    }
                };
                return mri;
                // </editor-fold>

            case SYNC_ENTRY:
                // <editor-fold defaultstate="collapsed" desc="SyncEntry Instrumentor">
                return new SynchronizedInstrumentor(mv, className, superName, access, name, desc) {
                    int storedObjIdx = -1;

                    @Override
                    protected void onBeforeSyncEntry() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{OBJECT_TYPE});
                        if (vr.isValid()) {
                            Label l = levelCheckBefore(om, bcn.name);

                            if (!vr.isAny()) {
                                asm.dup();
                                storedObjIdx = storeNewLocal(OBJECT_TYPE);
                            }

                            if (where == Where.BEFORE) {
                                loadArguments(
                                    localVarArg(vr.getArgIdx(0), OBJECT_TYPE, storedObjIdx),
                                    constArg(om.getClassNameParameter(), className),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(asm, om);
                            }
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }

                    @Override
                    protected void onAfterSyncEntry() {
                        if (where == Where.AFTER) {
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{OBJECT_TYPE});
                            if (vr.isValid()) {
                                Label l = levelCheckAfter(om, bcn.name);

                                loadArguments(
                                    localVarArg(vr.getArgIdx(0), OBJECT_TYPE, storedObjIdx),
                                    constArg(om.getClassNameParameter(), className),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(asm, om);
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onBeforeSyncExit() {
                    }

                    @Override
                    protected void onAfterSyncExit() {
                    }
                };// </editor-fold>

            case SYNC_EXIT:
                // <editor-fold defaultstate="collapsed" desc="SyncExit Instrumentor">
                return new SynchronizedInstrumentor(mv, className, superName, access, name, desc) {
                    int storedObjIdx = -1;

                    @Override
                    protected void onBeforeSyncExit() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        MethodInstrumentor.ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{OBJECT_TYPE});
                        if (vr.isValid()) {
                            Label l = levelCheckBefore(om, bcn.name);
                            if (!vr.isAny()) {
                                asm.dup();
                                storedObjIdx = storeNewLocal(OBJECT_TYPE);
                            }

                            if (where == Where.BEFORE) {
                                loadArguments(
                                    localVarArg(vr.getArgIdx(0), OBJECT_TYPE, storedObjIdx),
                                    constArg(om.getClassNameParameter(), className),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(asm, om);
                            }
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }


                    @Override
                    protected void onAfterSyncExit() {
                        if (where == Where.AFTER) {
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            MethodInstrumentor.ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{OBJECT_TYPE});
                            if (vr.isValid()) {
                                Label l = levelCheckAfter(om, bcn.name);

                                loadArguments(
                                    localVarArg(vr.getArgIdx(0), OBJECT_TYPE, storedObjIdx),
                                    constArg(om.getClassNameParameter(), className),
                                    constArg(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(asm, om);
                                if (l != null) {
                                    mv.visitLabel(l);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterSyncEntry() {
                    }

                    @Override
                    protected void onBeforeSyncEntry() {
                    }
                };// </editor-fold>

            case THROW:
                // <editor-fold defaultstate="collapsed" desc="Throw Instrumentor">
                return new ThrowInstrumentor(mv, className, superName, access, name, desc) {

                    @Override
                    protected void onThrow() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                        if (vr.isValid()) {
                            int throwableIndex = -1;
                            Label l = levelCheck(om, bcn.name);
                            if (!vr.isAny()) {
                                asm.dup();
                                throwableIndex = storeNewLocal(TypeUtils.throwableType);
                            }
                            loadArguments(
                                localVarArg(vr.getArgIdx(0), TypeUtils.throwableType, throwableIndex),
                                constArg(om.getClassNameParameter(), className.replace("/", ".")),
                                constArg(om.getMethodParameter(),getName(om.isMethodFqn())),
                                localVarArg(om.getSelfParameter(), Type.getObjectType(className), 0));

                            invokeBTraceAction(asm, om);
                            if (l != null) {
                                mv.visitLabel(l);
                            }
                        }
                    }
                };// </editor-fold>
        }
        return mv;
    }

    private MethodNode copy(MethodNode n) {
        String[] exceptions = n.exceptions != null ? ((List<String>)n.exceptions).toArray(new String[0]) : null;
        MethodNode mn = new MethodNode(Opcodes.ASM5, n.access, n.name, n.desc, n.signature, exceptions);
        n.accept(mn);
        mn.access = ACC_STATIC | ACC_PRIVATE;
        mn.desc = mn.desc.replace(ANYTYPE_DESC, OBJECT_DESC);
        mn.signature = mn.signature != null ? mn.signature.replace(ANYTYPE_DESC, OBJECT_DESC) : null;
        mn.name = getActionMethodName(mn.name);
        return mn;
    }

    private final ClassVisitor copyingVisitor = new ClassVisitor(Opcodes.ASM5, cv) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, sig, exceptions)) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itfc) {
                    if (owner.equals(bcn.name)) {
                        owner = className;
                        name = getActionMethodName(name);
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, itfc);
                }
            };
        }
    };

    @Override
    public void visitEnd() {
        Set<MethodNode> copyNodes = new TreeSet<>(BTraceMethodNode.COMPARATOR);

        for (OnMethod om : calledOnMethods) {
            BTraceMethodNode bmn = om.getMethodNode();

            MethodNode mn = copy(bmn);

            copyNodes.add(mn);
            for(BTraceMethodNode c : bmn.getCallees()) {
                copyNodes.add(copy(c));
            }
        }
        for(MethodNode mn : copyNodes) {
            mn.accept(copyingVisitor);
        }
        cv.visitEnd();
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java com.sun.btrace.runtime.Instrumentor <btrace-class> <target-class>]");
            System.exit(1);
        }

        String className = args[0].replace('.', '/') + ".class";
        FileInputStream fis = new FileInputStream(className);
        byte[] buf = new byte[(int)new File(className).length()];
        fis.read(buf);
        fis.close();
        BTraceClassNode bcn = BTraceClassNode.from(buf, true);
        Verifier verifier = new Verifier(bcn);
        InstrumentUtils.accept(new ClassReader(buf), verifier);
        buf = bcn.getBytecode();
        FileOutputStream fos = new FileOutputStream(className);
        fos.write(buf);
        fos.close();
        String targetClass = args[1].replace('.', '/') + ".class";
        fis = new FileInputStream(targetClass);
        ClassWriter writer = InstrumentUtils.newClassWriter();
        ClassReader reader = new ClassReader(fis);
        InstrumentUtils.accept(reader, new Instrumentor(ClassLoader.getSystemClassLoader(), null,
                    bcn, writer));
        fos = new FileOutputStream(targetClass);
        fos.write(writer.toByteArray());
    }

    private String getActionMethodName(String name) {
        return InstrumentUtils.getActionPrefix(bcn.name) + name;
    }

    private void invokeBTraceAction(Assembler asm, OnMethod om) {
        asm.invokeStatic(className, getActionMethodName(om.getTargetName()),
            om.getTargetDescriptor().replace(ANYTYPE_DESC, OBJECT_DESC));
        calledOnMethods.add(om);
    }

    private boolean matches(String pattern, String input) {
        if (pattern.length() == 0) {
            return false;
        }
        if (pattern.charAt(0) == '/' &&
            REGEX_SPECIFIER.matcher(pattern).matches()) {
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

    private boolean typeMatches(String decl, String desc) {
        // empty type declaration matches any method signature
        if (decl.isEmpty()) {
            return true;
        } else {
            String d = TypeUtils.declarationToDescriptor(decl);
            Type[] args1 = Type.getArgumentTypes(d);
            Type[] args2 = Type.getArgumentTypes(desc);
            return TypeUtils.isCompatible(args1, args2);
        }
    }

    private static boolean isInArray(String[] candidates, String given) {
        for (String c : candidates) {
            if (c.equals(given)) {
                return true;
            }
        }
        return false;
    }

    private static String getLevelStrSafe(OnMethod om) {
        return om.getLevel() != null ? om.getLevel().getValue().toString(): "";
    }

    private static void reportPatternSyntaxException(String pattern) {
        System.err.println("btrace ERROR: invalid regex pattern - " + pattern);
    }
}

