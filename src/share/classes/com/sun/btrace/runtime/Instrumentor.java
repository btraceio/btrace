/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.runtime;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Where;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassAdapter;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.MethodAdapter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.util.LocalVariablesSorter;
import com.sun.btrace.util.TimeStampGenerator;
import com.sun.btrace.util.TimeStampHelper;
import static com.sun.btrace.runtime.Constants.*;

/**
 * This instruments a probed class with BTrace probe
 * action class. 
 *
 * @author A. Sundararajan
 */
public class Instrumentor extends ClassAdapter {
    private String btraceClassName;
    private ClassReader btraceClass;
    private List<OnMethod> onMethods;
    private List<OnMethod> applicableOnMethods;
    private Set<OnMethod> calledOnMethods;
    private String className;
    private Class clazz;

    private boolean usesTimeStamp = false;
    private boolean timeStampExisting = false;


    public Instrumentor(Class clazz, 
            String btraceClassName, ClassReader btraceClass, 
            List<OnMethod> onMethods, ClassVisitor cv) {
        super(cv);
        this.clazz = clazz;
        this.btraceClassName = btraceClassName.replace('.', '/');
        this.btraceClass = btraceClass;
        this.onMethods = onMethods;
        this.applicableOnMethods = new ArrayList<OnMethod>();
        this.calledOnMethods = new HashSet<OnMethod>();
    }

    public Instrumentor(Class clazz,
            String btraceClassName, byte[] btraceCode, 
            List<OnMethod> onMethods, ClassVisitor cv) {
        this(clazz, btraceClassName, new ClassReader(btraceCode), onMethods, cv);
    }

    public void visit(int version, int access, String name, 
        String signature, String superName, String[] interfaces) {
        usesTimeStamp = false;
        timeStampExisting = false;
        className = name;
        // we filter the probe methods applicable for this particular
        // class by brute force walking. FIXME: should I optimize?
        String externalName = name.replace('/', '.');
        for (OnMethod om : onMethods) {
            String probeClazz = om.getClazz();
            if (probeClazz.length() == 0) {
                continue;
            }
            char firstChar = probeClazz.charAt(0);
            if (firstChar == '/' &&
                REGEX_SPECIFIER.matcher(probeClazz).matches()) {
                probeClazz = probeClazz.substring(1, probeClazz.length() - 1);
                if (externalName.matches(probeClazz)) {
                    applicableOnMethods.add(om);
                }
            } else if (firstChar == '+') {
                // super type being matched.
                String superType = probeClazz.substring(1);
                // internal name of super type.
                String superTypeInternal = superType.replace('.', '/');
                /*
                 * If we are redefining a class, then we have a Class object
                 * of it and we can walk through it's hierarchy to match for
                 * specified super type. But, if we are loading it a fresh, then
                 * we can not walk through super hierarchy. We just check the
                 * immediate super class and directly implemented interfaces
                 */
                if (ClassFilter.isSubTypeOf(this.clazz, superType) ||
                    superName.equals(superTypeInternal) ||
                    isInArray(interfaces, superTypeInternal)) {
                    applicableOnMethods.add(om);
                }
            } else if (probeClazz.equals(externalName)) {
                applicableOnMethods.add(om);
            }
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        String extName = Type.getType(desc).getClassName();
        for (OnMethod om : onMethods) {
            String probeClazz = om.getClazz();
            if (probeClazz.length() > 0 && probeClazz.charAt(0) == '@') {
                probeClazz = probeClazz.substring(1);
                if (probeClazz.length() == 0) {
                    continue;
                }
                if (REGEX_SPECIFIER.matcher(probeClazz).matches()) {
                    probeClazz = probeClazz.substring(1, probeClazz.length() - 1);
                    if (extName.matches(probeClazz)) {
                        applicableOnMethods.add(om);
                    }
                } else if (probeClazz.equals(extName)) { 
                    applicableOnMethods.add(om);
                }
            }                        
        }
        return av;
    }

    public MethodVisitor visitMethod(final int access, final String name, 
        final String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, 
                signature, exceptions);

        if (applicableOnMethods.size() == 0 ||
            (access & ACC_ABSTRACT) != 0    ||
            (access & ACC_NATIVE) != 0      ||
            name.startsWith(BTRACE_METHOD_PREFIX)) {
            return methodVisitor;
        }

        if (name.equals(TimeStampHelper.TIME_STAMP_NAME)) {
            timeStampExisting = true;
            return methodVisitor;
        }

        // used to create new local variables while keeping the class internals consistent
        // Call "int index = lvs.newVar(<type>)" to create a new local variable.
        // Then use the generated index to get hold of the variable
        LocalVariablesSorter.Memento externalState = new LocalVariablesSorter.Memento();
        final LocalVariablesSorter lvs = new LocalVariablesSorter(access, desc, methodVisitor, externalState);
        methodVisitor = lvs;

        for (OnMethod om : applicableOnMethods) {
            if (om.getLocation().getValue() == Kind.LINE) {
                methodVisitor = instrumentorFor(om, methodVisitor, lvs, access, name, desc);
            } else {
                String methodName = om.getMethod();
                if (methodName.equals("")) {
                    methodName = om.getTargetName();
                }
                if (methodName.equals(name) &&
                    typeMatches(om.getType(), desc)) {
                    methodVisitor = instrumentorFor(om, methodVisitor, lvs, access, name, desc);
                } else if (methodName.charAt(0) == '/' &&
                           REGEX_SPECIFIER.matcher(methodName).matches()) {
                    methodName = methodName.substring(1, methodName.length() - 1);
                    if (name.matches(methodName) &&
                        typeMatches(om.getType(), desc)) {
                        methodVisitor = instrumentorFor(om, methodVisitor, lvs, access, name, desc);
                    }
                }
//                lvs[0] = new LocalVariablesSorter(access, desc, methodVisitor, externalState);
            }
        }

        return new MethodAdapter(methodVisitor) {
            public AnnotationVisitor visitAnnotation(String annoDesc,
                                  boolean visible) { 
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
                            if (extAnnoName.matches(annoName)) {
                                mv = instrumentorFor(om, mv, lvs, access, name, desc);
                            }
                        } else if (annoName.equals(extAnnoName)) {
                            mv = instrumentorFor(om, mv, lvs, access, name, desc);
                        }
                    }                   
                }               
                return mv.visitAnnotation(annoDesc, visible);
            }
        }; 
    }

    private MethodVisitor instrumentorFor(
        final OnMethod om, MethodVisitor mv, final LocalVariablesSorter lvs,
        int access, String name, String desc) {
        final Location loc = om.getLocation();
        final Where where = loc.getWhere();
        final Type[] actionArgTypes = Type.getArgumentTypes(om.getTargetDescriptor());
        final int numActionArgs = actionArgTypes.length;

        // a helper structure for creating timestamps
        final int[] tsindex = new int[]{-1, -1};
        
        switch (loc.getValue()) {            
            case ARRAY_GET:
                // <editor-fold defaultstate="collapsed" desc="Array Get Instrumentor">
                return new ArrayAccessInstrumentor(mv, access, name, desc) {
                    int[] argsIndex = new int[]{-1, -1};
                    final private int INSTANCE_PTR = 0;
                    final private int INDEX_PTR = 1;

                    @Override
                    protected void onBeforeArrayLoad(int opcode) {
                        Type arrtype = TypeUtils.getArrayType(opcode);
                        Type retType = TypeUtils.getElementType(opcode);
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        if (where == Where.AFTER) {
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                        }
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrtype, Type.INT_TYPE});
                        if (vr.isValid()) {
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup2();
                                    argsIndex[INDEX_PTR] = lvs.newLocal(Type.INT_TYPE);
                                    argsIndex[INSTANCE_PTR] = lvs.newLocal(arrtype);
                                }
                                if (where == Where.BEFORE) {
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrtype, argsIndex[INSTANCE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                }
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayLoad(int opcode) {
                        if (where == Where.AFTER) {
                            Type arrtype = TypeUtils.getArrayType(opcode);
                            Type retType = TypeUtils.getElementType(opcode);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrtype, Type.INT_TYPE});
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    int retValIndex = -1;
                                    if (om.getReturnParameter() != -1) {
                                        dupArrayValue(opcode);
                                        retValIndex = lvs.newLocal(retType);
                                    }

                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrtype, argsIndex[INSTANCE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getReturnParameter(), retType, retValIndex),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));
                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case ARRAY_SET:
                // <editor-fold defaultstate="collapsed" desc="Array Set Instrumentor">
                return new ArrayAccessInstrumentor(mv, access, name, desc) {
                    int[] argsIndex = new int[]{-1, -1, -1};
                    final private int INSTANCE_PTR = 0, INDEX_PTR = 1, VALUE_PTR = 2;

                    @Override
                    protected void onBeforeArrayStore(int opcode) {
                        Type elementType = TypeUtils.getElementType(opcode);
                        Type arrayType = TypeUtils.getArrayType(opcode);

                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrayType, Type.INT_TYPE, elementType});
                        if (vr.isValid()) {
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    argsIndex[VALUE_PTR] = lvs.newLocal(elementType);
                                    dup2();
                                    argsIndex[INDEX_PTR] = lvs.newLocal(Type.INT_TYPE);
                                    argsIndex[INSTANCE_PTR] = lvs.newLocal(TypeUtils.getArrayType(opcode));
                                    loadLocal(elementType, argsIndex[VALUE_PTR]);
                                }

                                if (where == Where.BEFORE) {
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrayType, argsIndex[INSTANCE_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(VALUE_PTR), elementType, argsIndex[VALUE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                }
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayStore(int opcode) {
                        if (where == Where.AFTER) {
                            Type elementType = TypeUtils.getElementType(opcode);
                            Type arrayType = TypeUtils.getArrayType(opcode);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrayType, Type.INT_TYPE, elementType});
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrayType, argsIndex[INSTANCE_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(VALUE_PTR), elementType, argsIndex[VALUE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case CALL:
                // <editor-fold defaultstate="collapsed" desc="Method Call Instrumentor">
                return new MethodCallInstrumentor(mv, access, name, desc) {

                    private String localClassName = loc.getClazz();
                    private String localMethodName = loc.getMethod();
                    private int returnVarIndex = -1;
                    int[] backupArgsIndexes;

                    private void injectBtrace(ValidationResult vr, final String method, final Type returnType) {
                        ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 6];
                        for(int i=0;i<vr.getArgCnt();i++) {
                            int index = vr.getArgIdx(i);
                            Type t = actionArgTypes[index];
                            if (TypeUtils.isAnyTypeArray(t)) {
                                actionArgs[i] = new AnyTypeArgProvider(i, backupArgsIndexes[i+1]);
                            } else {
                                actionArgs[i] = new LocalVarArgProvider(index, actionArgTypes[index], backupArgsIndexes[i+1]);;
                            }
                            
                        }
                        actionArgs[actionArgTypes.length] = new LocalVarArgProvider(om.getReturnParameter(), returnType, returnVarIndex);
                        actionArgs[actionArgTypes.length + 1] = new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, backupArgsIndexes[0]);
                        actionArgs[actionArgTypes.length + 2] = new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), method);
                        actionArgs[actionArgTypes.length + 3] = new ConstantArgProvider(om.getClassNameParameter(), className);
                        actionArgs[actionArgTypes.length + 4] = new ConstantArgProvider(om.getMethodParameter(), getName());
                        actionArgs[actionArgTypes.length + 5] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);

                        loadArguments(actionArgs);

                        invokeBTraceAction(this, om);
                    }

                    @Override
                    protected void onBeforeCallMethod(int opcode, String owner, String name, String desc) {
                        if (isStatic() && om.getSelfParameter() > -1) {
                            return; // invalid combination; a static method can not provide *this*
                        }
                        if (matches(localClassName, owner.replace('/', '.'))
                                && matches(localMethodName, name)
                                && typeMatches(loc.getType(), desc)) {

                            String method = name + desc;
                            Type[] calledMethodArgs = Type.getArgumentTypes(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            if (where == Where.AFTER) {
                                addExtraTypeInfo(om.getReturnParameter(), Type.getReturnType(desc));
                            }
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    boolean isStaticCall = (opcode == INVOKESTATIC);
                                    if (isStaticCall) {
                                        if (om.getTargetInstanceParameter() != -1) {
                                            return;

                                        }
                                    } else {
                                        if (where == Where.BEFORE && name.equals(CONSTRUCTOR)) {
                                            return;
                                        }
                                    }
                                    // will store the call args into local variables
                                    backupArgsIndexes = backupStack(lvs, Type.getArgumentTypes(desc), isStaticCall);
                                    if (where == Where.BEFORE) {
                                        injectBtrace(vr, method, Type.getReturnType(desc));
                                    }
                                    // put the call args back on stack so the method call can find them
                                    restoreStack(backupArgsIndexes, Type.getArgumentTypes(desc), isStaticCall);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterCallMethod(int opcode,
                            String owner, String name, String desc) {
                        if (isStatic() && om.getSelfParameter() != -1) {
                            return;
                        }
                        if (where == Where.AFTER
                                && matches(localClassName, owner.replace('/', '.'))
                                && matches(localMethodName, name)
                                && typeMatches(loc.getType(), desc)) {
                            Type returnType = Type.getReturnType(desc);
                            Type[] calledMethodArgs = Type.getArgumentTypes(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), returnType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    String method = name + desc;
                                    boolean withReturn = om.getReturnParameter() != -1 && returnType != Type.VOID_TYPE;
                                    if (withReturn) {
                                        // store the return value to a local variable
                                        int index = lvs.newLocal(returnType);
                                        returnVarIndex = index;
                                    }
                                    // will also retrieve the call args and the return value from the backup variables
                                    injectBtrace(vr, method, returnType);
                                    if (withReturn) {
                                        loadLocal(returnType, returnVarIndex); // restore the return value
                                    }
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case CATCH:
                // <editor-fold defaultstate="collapsed" desc="Catch Instrumentor">
                return new CatchInstrumentor(mv, access, name, desc) {

                    @Override
                    protected void onCatch(String type) {
                        Type exctype = Type.getObjectType(type);
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{exctype});
                        if (vr.isValid()) {
                            int index = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    index = lvs.newLocal(exctype);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), exctype, index),
                                    new ConstantArgProvider(om.getClassNameParameter(), className),
                                    new ConstantArgProvider(om.getMethodParameter(), getName()),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }
                };// </editor-fold>

            case CHECKCAST:
                // <editor-fold defaultstate="collapsed" desc="CheckCast Instrumentor">
                return new TypeCheckInstrumentor(mv, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.CHECKCAST) {
                            // TODO not really usefull
                            // It would be better to check for the original and desired type
                            Type castType = Type.getObjectType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{castType});
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    int castTypeIndex = -1;
                                    if (!vr.isAny()) {
                                        dup();
                                        castTypeIndex = lvs.newLocal(castType);
                                    }
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(0), castType, castTypeIndex),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
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
                return new MethodEntryInstrumentor(mv, access, name, desc) {
                    private void injectBtrace(ValidationResult vr) {
                        lvs.freeze();
                        try {
                            ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 3];
                            int ptr = isStatic() ? 0 : 1;
                            for(int i=0;i<vr.getArgCnt();i++) {
                                int index = vr.getArgIdx(i);
                                Type t = actionArgTypes[index];
                                if (TypeUtils.isAnyTypeArray(t)) {
                                    actionArgs[i] = new AnyTypeArgProvider(index, ptr);
                                    ptr++;
                                } else {
                                    actionArgs[i] = new LocalVarArgProvider(index, t, ptr);
                                    ptr += actionArgTypes[index].getSize();
                                }
                            }
                            actionArgs[actionArgTypes.length] = new ConstantArgProvider(om.getMethodParameter(), getName());
                            actionArgs[actionArgTypes.length + 1] = new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", "."));
                            actionArgs[actionArgTypes.length + 2] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);
                            loadArguments(actionArgs);

                            invokeBTraceAction(this, om);
                        } finally {
                            lvs.unfreeze();
                        }
                    }

                    private void callAction() {
                        if (isStatic() && om.getSelfParameter() > -1) {
                            return; // invalid combination; a static method can not provide *this*
                        }
                        Type[] calledMethodArgs = Type.getArgumentTypes(getDescriptor());
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                        if (vr.isValid()) {
                            injectBtrace(vr);
                        }
                    }

                    @Override
                    protected void onMethodEntry() {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else {
                            callAction();
                        }
                    }
                };// </editor-fold>

            case ERROR:
                // <editor-fold defaultstate="collapsed" desc="Error Instrumentor">
                return new ErrorReturnInstrumentor(mv, access, name, desc) {

                    @Override
                    protected void onErrorReturn() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                        if (vr.isValid()) {
                            int throwableIndex = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    throwableIndex = lvs.newLocal(TypeUtils.throwableType);
                                }

                                loadArguments(new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.throwableType, throwableIndex),
                                    new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                    new ConstantArgProvider(om.getMethodParameter(), getName()),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }
                };// </editor-fold>

            case FIELD_GET:
                // <editor-fold defaultstate="collapsed" desc="Field Get Instrumentor">
                return new FieldAccessInstrumentor(mv, access, name, desc) {

                    int calledInstanceIndex = -1;
                    private String targetClassName = loc.getClazz();
                    private String targetFieldName = loc.getField();

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
                                lvs.freeze();
                                try {
                                    if (om.getTargetInstanceParameter() != -1) {
                                        dup();
                                        calledInstanceIndex = lvs.newLocal(TypeUtils.objectType);
                                    }
                                    if (where == Where.BEFORE) {
                                        loadArguments(
                                            new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                            new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    }
                                } finally {
                                    lvs.unfreeze();
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
                                lvs.freeze();
                                try {
                                    if (om.getReturnParameter() != -1) {
                                        dupValue(desc);
                                        returnValIndex = lvs.newLocal(fldType);
                                    }

                                    loadArguments(
                                        new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                        new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                        new LocalVarArgProvider(om.getReturnParameter(), fldType, returnValIndex),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case FIELD_SET:
                // <editor-fold defaultstate="collapsed" desc="Field Set Instrumentor">
                return new FieldAccessInstrumentor(mv, access, name, desc) {
                    private String targetClassName = loc.getClazz();
                    private String targetFieldName = loc.getField();
                    private int calledInstanceIndex = -1;
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
                                lvs.freeze();
                                try {
                                    if (!vr.isAny()) {
                                        fldValueIndex = lvs.newLocal(fieldType);
                                    }
                                    if (om.getTargetInstanceParameter() != -1) {
                                        dup();
                                        calledInstanceIndex = lvs.newLocal(TypeUtils.objectType);
                                    }
                                    if (!vr.isAny()) {
                                        // need to put the set value back on stack
                                        loadLocal(fieldType, fldValueIndex);
                                    }

                                    if (where == Where.BEFORE) {
                                        loadArguments(
                                            new LocalVarArgProvider(vr.getArgIdx(0), fieldType, fldValueIndex),
                                            new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                            new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    }
                                } finally {
                                    lvs.unfreeze();
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
                                lvs.freeze();
                                try {
                                    loadArguments(
                                            new LocalVarArgProvider(vr.getArgIdx(0), fieldType, fldValueIndex),
                                            new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                            new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case INSTANCEOF:
                // <editor-fold defaultstate="collapsed" desc="InstanceOf Instrumentor">
                return new TypeCheckInstrumentor(mv, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.INSTANCEOF) {
                            // TODO not really usefull
                            // It would be better to check for the original and desired type
                            Type castType = Type.getObjectType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{castType});
                            if (vr.isValid()) {
                                int castTypeIndex = -1;
                                lvs.freeze();
                                try {
                                    if (!vr.isAny()) {
                                        dup();
                                        castTypeIndex = lvs.newLocal(castType);
                                    }

                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(0), castType, castTypeIndex),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName()),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
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

            case LINE:
                // <editor-fold defaultstate="collapsed" desc="Line Instrumentor">
                return new LineNumberInstrumentor(mv, access, name, desc) {

                    private int onLine = loc.getLine();

                    private void callOnLine(int line) {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE});
                        if (vr.isValid()) {
                            lvs.freeze();
                            try {
                                loadArguments(
                                    new ConstantArgProvider(vr.getArgIdx(0), line),
                                    new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                    new ConstantArgProvider(om.getMethodParameter(), getName()),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
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
                return new ObjectAllocInstrumentor(mv, access, name, desc) {

                    @Override
                    protected void beforeObjectNew(String desc) {
                        if (loc.getWhere() == Where.BEFORE) {
                            String extName = desc.replace('/', '.');
                            if (matches(loc.getClazz(), extName)) {
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType});
                                if (vr.isValid()) {
                                    lvs.freeze();
                                    try {
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
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
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType});
                                if (vr.isValid()) {
                                    int returnValIndex = -1;
                                    lvs.freeze();
                                    try {
                                        if (om.getReturnParameter() != -1) {
                                            dupValue(instType);
                                            returnValIndex = lvs.newLocal(instType);
                                        }
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new LocalVarArgProvider(om.getReturnParameter(), instType, returnValIndex),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
                                    }
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case NEWARRAY:
                // <editor-fold defaultstate="collapsed" desc="New Array Instrumentor">
                return new ArrayAllocInstrumentor(mv, access, name, desc) {

                    @Override
                    protected void onBeforeArrayNew(String desc, int dims) {
                        if (where == Where.BEFORE) {
                            String extName = TypeUtils.getJavaType(desc);
                            String type = TypeUtils.objectOrArrayType(loc.getClazz());
                            if (matches(type, desc)) {
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType, Type.INT_TYPE});
                                if (vr.isValid()) {
                                    lvs.freeze();
                                    try {
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new ConstantArgProvider(vr.getArgIdx(1), dims),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayNew(String desc, int dims) {
                        if (where == Where.AFTER) {
                            String extName = TypeUtils.getJavaType(desc);
                            String type = TypeUtils.objectOrArrayType(loc.getClazz());
                            if (matches(type, desc)) {
                                StringBuilder arrayType = new StringBuilder();
                                for (int i = 0; i < dims; i++) {
                                    arrayType.append("[");
                                }
                                arrayType.append(desc);
                                Type instType = Type.getObjectType(arrayType.toString());
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                addExtraTypeInfo(om.getReturnParameter(), instType);
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType, Type.INT_TYPE});
                                if (vr.isValid()) {
                                    int returnValIndex = -1;
                                    lvs.freeze();
                                    try {
                                        if (om.getReturnParameter() != -1) {
                                            dupValue(instType);
                                            returnValIndex = lvs.newLocal(instType);
                                        }
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new ConstantArgProvider(vr.getArgIdx(1), dims),
                                            new LocalVarArgProvider(om.getReturnParameter(), instType, returnValIndex),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName()),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
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
                MethodReturnInstrumentor mri = new MethodReturnInstrumentor(mv, access, name, desc) {

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
                        lvs.freeze();
                        try {
                            if (om.getReturnParameter() != -1) {
                                dupReturnValue(retOpCode);
                                retValIndex = lvs.newLocal(getReturnType());
                            }
                            if (om.getDurationParameter() != -1) {
                                usesTimeStamp = true;
                            }

                            ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 5];
                            int ptr = isStatic() ? 0 : 1;
                            for(int i=0;i<vr.getArgCnt();i++) {
                                int index = vr.getArgIdx(i);
                                Type t = actionArgTypes[index];
                                if (TypeUtils.isAnyTypeArray(t)) {
                                    actionArgs[i] = new AnyTypeArgProvider(i, ptr);
                                    ptr++;
                                } else {
                                    actionArgs[i] = new LocalVarArgProvider(index, t, ptr);
                                    ptr += actionArgTypes[index].getSize();
                                }
                            }
                            actionArgs[actionArgTypes.length] = new ConstantArgProvider(om.getMethodParameter(), getName());
                            actionArgs[actionArgTypes.length + 1] = new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", "."));
                            actionArgs[actionArgTypes.length + 2] = new LocalVarArgProvider(om.getReturnParameter(), getReturnType(), retValIndex);
                            actionArgs[actionArgTypes.length + 3] = new ArgumentProvider(om.getDurationParameter()) {
                                public void doProvide() {
                                    if (tsindex[0] != -1 && tsindex[1] != -1) {
                                        loadLocal(Type.LONG_TYPE, tsindex[1]);
                                        loadLocal(Type.LONG_TYPE, tsindex[0]);
                                        visitInsn(LSUB);
                                    }
                                }
                            };
                            actionArgs[actionArgTypes.length + 4] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);

                            loadArguments(actionArgs);

                            invokeBTraceAction(this, om);
                        } finally {
                            lvs.unfreeze();
                        }
                    }

                    @Override
                    protected void onMethodReturn(int opcode) {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else {
                            callAction(opcode);
                        }
                    }

                    @Override
                    public boolean usesTimeStamp() {
                        return vr.isValid() && om.getDurationParameter() != -1;
                    }
                };
                if (om.getDurationParameter() != -1) {
                    return new TimeStampGenerator(lvs, tsindex, className, access, name, desc, mri, new int[]{RETURN, IRETURN, FRETURN, DRETURN, LRETURN, ARETURN});
                } else {
                    return mri;
                }// </editor-fold>

            case SYNC_ENTRY:
                // <editor-fold defaultstate="collapsed" desc="SyncEntry Instrumentor">
                return new SynchronizedInstrumentor(className, lvs, access, name, desc) {

                    private void callAction() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.objectType});
                        if (vr.isValid()) {
                            int index = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    index = lvs.newLocal(TypeUtils.objectType);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.objectType, index),
                                    new ConstantArgProvider(om.getClassNameParameter(), className),
                                    new ConstantArgProvider(om.getMethodParameter(), getName()),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onBeforeSyncEntry() {
                        if (where == Where.BEFORE) {
                            callAction();
                        }
                    }

                    @Override
                    protected void onAfterSyncEntry() {
                        if (where == Where.AFTER) {
                            callAction();
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
                return new SynchronizedInstrumentor(className, lvs, access, name, desc) {

                    private void callAction() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.objectType});
                        if (vr.isValid()) {
                            int index = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    index = lvs.newLocal(TypeUtils.objectType);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.objectType, index),
                                    new ConstantArgProvider(om.getClassNameParameter(), className),
                                    new ConstantArgProvider(om.getMethodParameter(), getName()),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onBeforeSyncEntry() {
                    }

                    @Override
                    protected void onAfterSyncEntry() {
                    }

                    @Override
                    protected void onBeforeSyncExit() {
                        if (where == Where.BEFORE) {
                            callAction();
                        }
                    }

                    @Override
                    protected void onAfterSyncExit() {
                        if (where == Where.AFTER) {
                            callAction();
                        }
                    }
                };// </editor-fold>

            case THROW:
                // <editor-fold defaultstate="collapsed" desc="Throw Instrumentor">
                return new ThrowInstrumentor(mv, access, name, desc) {

                    @Override
                    protected void onThrow() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                        if (vr.isValid()) {
                            int throwableIndex = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    throwableIndex = lvs.newLocal(TypeUtils.throwableType);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.throwableType, throwableIndex),
                                    new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                    new ConstantArgProvider(om.getMethodParameter(),getName()),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }
                };// </editor-fold>
        }
        return mv;
    }

    private void introduceTimeStampHelper() {
        if (usesTimeStamp && !timeStampExisting) {
            TimeStampHelper.generateTimeStampGetter(this);
        }
    }

    public void visitEnd() {
        int size = applicableOnMethods.size();
        List<MethodCopier.MethodInfo> mi = new ArrayList<MethodCopier.MethodInfo>(size);
        for (OnMethod om : calledOnMethods) {
            mi.add(new MethodCopier.MethodInfo(om.getTargetName(), 
                     om.getTargetDescriptor(),
                     getActionMethodName(om.getTargetName()),
                     ACC_STATIC | ACC_PRIVATE));
        }
        introduceTimeStampHelper();
        MethodCopier copier = new MethodCopier(btraceClass, cv, mi) {
            @Override 
            protected MethodVisitor addMethod(int access, String name, String desc,
                        String signature, String[] exceptions) {
                desc = desc.replace(ANYTYPE_DESC, OBJECT_DESC);
                if (signature != null) {
                    signature = signature.replace(ANYTYPE_DESC, OBJECT_DESC);
                }
                return super.addMethod(access, name, desc, signature, exceptions);
            }
        };
        copier.visitEnd();
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
        ClassWriter writer = InstrumentUtils.newClassWriter();
        Verifier verifier = new Verifier(new Preprocessor(writer));
        InstrumentUtils.accept(new ClassReader(buf), verifier);
        buf = writer.toByteArray();
        FileOutputStream fos = new FileOutputStream(className);
        fos.write(buf);
        fos.close();
        String targetClass = args[1].replace('.', '/') + ".class";
        fis = new FileInputStream(targetClass);
        writer = InstrumentUtils.newClassWriter();  
        ClassReader reader = new ClassReader(fis);        
        InstrumentUtils.accept(reader, new Instrumentor(null,
                    verifier.getClassName(), buf, 
                    verifier.getOnMethods(), writer));
        fos = new FileOutputStream(targetClass);
        fos.write(writer.toByteArray());
    }

    private String getActionMethodName(String name) {
        return Constants.BTRACE_METHOD_PREFIX + 
               btraceClassName.replace('/', '$') + "$" + name;
    }

    private void invokeBTraceAction(MethodInstrumentor mv, OnMethod om) {
        mv.invokeStatic(className, getActionMethodName(om.getTargetName()),
            om.getTargetDescriptor().replace(ANYTYPE_DESC, OBJECT_DESC));
        calledOnMethods.add(om);
    }

    private boolean matches(String pattern, String input) {
        if (pattern.length() == 0) {
            return false;
        }
        if (pattern.charAt(0) == '/' &&
            REGEX_SPECIFIER.matcher(pattern).matches()) {
            return input.matches(pattern.substring(1, pattern.length() - 1));
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
}

