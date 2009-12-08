/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.util.Arrays;
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
        final LocalVariablesSorter lvs = new LocalVariablesSorter(access, desc, methodVisitor);

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
                return new ArrayAccessInstrumentor(lvs, access, name, desc) {

                    private int arrIndexIndex = -1;
                    private int arrInstanceIndex = -1;

                    @Override
                    protected void onBeforeArrayLoad(int opcode) {
                        Type arrtype = TypeUtils.getArrayType(opcode);
                        Type retType = TypeUtils.getElementType(opcode);
                        addExtraTypeInfo(om.getReturnParameter(), retType);
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrtype, Type.INT_TYPE});
                        if (vr != ValidationResult.INVALID) {
                            dup2();
                            arrIndexIndex = lvs.newLocal(Type.INT_TYPE);
                            arrInstanceIndex = lvs.newLocal(arrtype);
                            storeLocal(Type.INT_TYPE, arrIndexIndex);
                            storeLocal(arrtype, arrInstanceIndex);

                            if (where == Where.BEFORE) {
                                Arguments args = new Arguments(actionArgTypes, new int[]{arrInstanceIndex, arrIndexIndex}, om);
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));

                                args.load();
                                invokeBTraceAction(this, om);
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayLoad(int opcode) {
                        if (where == Where.AFTER) {
                            Type arrtype = TypeUtils.getArrayType(opcode);
                            Type retType = TypeUtils.getElementType(opcode);
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrtype, Type.INT_TYPE});
                            if (vr != ValidationResult.INVALID) {
                                dupArrayValue(opcode);

                                int retValIndex = lvs.newLocal(retType);
                                storeLocal(retType, retValIndex);

                                Arguments args = new Arguments(actionArgTypes, new int[]{arrInstanceIndex, arrIndexIndex}, om);
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                args.addArgument(om.getReturnParameter(), new LocalVarArgProvider(retType, retValIndex));

                                args.load();
                                invokeBTraceAction(this, om);
                            }
                        }
                    }
                };// </editor-fold>

            case ARRAY_SET:
                // <editor-fold defaultstate="collapsed" desc="Array Set Instrumentor">
                return new ArrayAccessInstrumentor(lvs, access, name, desc) {

                    private int arrayInstanceIndex = -1, arrayIndexIndex = -1, arrayValueIndex = -1;

                    @Override
                    protected void onBeforeArrayStore(int opcode) {
                        Type elementType = TypeUtils.getElementType(opcode);
                        Type arrayType = TypeUtils.getArrayType(opcode);

                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrayType, Type.INT_TYPE, elementType});
                        if (vr != ValidationResult.INVALID) {
                            arrayInstanceIndex = lvs.newLocal(TypeUtils.getArrayType(opcode));
                            arrayIndexIndex = lvs.newLocal(Type.INT_TYPE);
                            arrayValueIndex = lvs.newLocal(elementType);

                            storeLocal(elementType, arrayValueIndex);
                            dup2();
                            storeLocal(Type.INT_TYPE, arrayIndexIndex);
                            storeLocal(arrayType, arrayInstanceIndex);
                            loadLocal(elementType, arrayValueIndex);

                            if (where == Where.BEFORE) {
                                Arguments args = new Arguments(actionArgTypes, new int[]{arrayInstanceIndex, arrayIndexIndex, arrayValueIndex}, om);
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));

                                args.load();
                                invokeBTraceAction(this, om);
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayStore(int opcode) {
                        if (where == Where.AFTER) {
                            Type elementType = TypeUtils.getElementType(opcode);
                            Type arrayType = TypeUtils.getArrayType(opcode);

                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrayType, Type.INT_TYPE, elementType});
                            if (vr != ValidationResult.INVALID) {
                                Arguments args = new Arguments(actionArgTypes, new int[]{arrayInstanceIndex, arrayIndexIndex, arrayValueIndex}, om);
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));

                                args.load();
                                invokeBTraceAction(this, om);
                            }
                        }
                    }
                };// </editor-fold>

            case CALL:
                // <editor-fold defaultstate="collapsed" desc="Method Call Instrumentor">
                return new MethodCallInstrumentor(lvs, access, name, desc) {

                    private String localClassName = loc.getClazz();
                    private String localMethodName = loc.getMethod();
                    private int returnVarIndex = -1;
                    int[] backupArgsIndexes;

                    private void injectBtrace(final String method, final Type returnType) {
                        Arguments args = new Arguments(actionArgTypes, Arrays.copyOfRange(backupArgsIndexes, 1, backupArgsIndexes.length), om);

                        args.addArgument(om.getReturnParameter(), new LocalVarArgProvider(returnType, returnVarIndex));
                        args.addArgument(om.getCalledInstanceParameter(), new LocalVarArgProvider(TypeUtils.objectType, backupArgsIndexes[0]));
                        args.addArgument(om.getCalledMethodParameter(), new ConstantArgProvider(method));
                        args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className));
                        args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                        args.load();

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
                            if (om.getCalledInstanceParameter() != -1) {
                                addExtraTypeInfo(om.getCalledInstanceParameter(), actionArgTypes[om.getCalledInstanceParameter()]);
                            }
                            if (where == Where.AFTER) {
                                addExtraTypeInfo(om.getReturnParameter(), Type.getReturnType(desc));
                            }
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr != ValidationResult.INVALID) {
                                boolean isStaticCall = (opcode == INVOKESTATIC);
                                if (isStaticCall) {
                                    if (om.getCalledInstanceParameter() != -1) {
                                        return;

                                    }
                                } else {
                                    if (where == Where.BEFORE && name.equals(CONSTRUCTOR)) {
                                        return;
                                    }
                                }
                                // will store the call args into local variables
                                backupArgsIndexes = backupStack(lvs, isStaticCall);
                                if (where == Where.BEFORE) {
                                    injectBtrace(method, Type.getReturnType(desc));
                                }
                                // put the call args back on stack so the method call can find them
                                restoreStack(backupArgsIndexes, isStaticCall);
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
                            addExtraTypeInfo(om.getCalledInstanceParameter(), actionArgTypes[om.getCalledInstanceParameter()]);
                            addExtraTypeInfo(om.getReturnParameter(), returnType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr != ValidationResult.INVALID) {
                                String method = name + desc;
                                boolean withReturn = om.getReturnParameter() != -1 && returnType != Type.VOID_TYPE;
                                if (withReturn) {
                                    // store the return value to a local variable
                                    int index = lvs.newLocal(returnType);
                                    storeLocal(returnType, index); // store the return value
                                    returnVarIndex = index;
                                }
                                // will also retrieve the call args and the return value from the backup variables
                                injectBtrace(method, returnType);
                                if (withReturn) {
                                    loadLocal(returnType, returnVarIndex); // restore the return value
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case CATCH:
                // <editor-fold defaultstate="collapsed" desc="Catch Instrumentor">
                return new CatchInstrumentor(lvs, access, name, desc) {

                    @Override
                    protected void onCatch(String type) {
                        Type exctype = Type.getObjectType(type);
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{exctype});
                        if (vr != ValidationResult.INVALID) {
                            dup();
                            int index = lvs.newLocal(exctype);
                            storeLocal(exctype, index);
                            Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[] {new LocalVarArgProvider(exctype, index)}, om);
                            args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className));
                            args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                            args.load();
                            invokeBTraceAction(this, om);
                        }
                    }
                };// </editor-fold>

            case CHECKCAST:
                // <editor-fold defaultstate="collapsed" desc="CheckCast Instrumentor">
                return new TypeCheckInstrumentor(lvs, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.CHECKCAST) {
                            // TODO not really usefull
                            // It would be better to check for the original and desired type
                            Type castType = Type.getObjectType(desc);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{castType});
                            if (vr != ValidationResult.INVALID) {
                                int castTypeIndex = lvs.newLocal(castType);
                                dup();
                                storeLocal(castType, castTypeIndex);

                                Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new LocalVarArgProvider(castType, castTypeIndex)}, om);
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                args.load();
                                invokeBTraceAction(this, om);
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
                return new MethodEntryInstrumentor(lvs, access, name, desc) {
                    private void injectBtrace() {
                        Arguments args = new Arguments(actionArgTypes, om);
                        args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(getName()));
                        args.addArgument(om.getCalledMethodParameter(), new ConstantArgProvider(className.replace("/", ".")));
                        args.load();

                        invokeBTraceAction(this, om);
                    }

                    private void callAction() {
                        if (isStatic() && om.getSelfParameter() > -1) {
                            return; // invalid combination; a static method can not provide *this*
                        }
                        Type[] calledMethodArgs = Type.getArgumentTypes(getDescriptor());
                        if (om.getSelfParameter() != -1) {
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        }
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                        if (vr != ValidationResult.INVALID) {
                            injectBtrace();
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
                return new ErrorReturnInstrumentor(lvs, access, name, desc) {

                    @Override
                    protected void onErrorReturn() {
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                        if (vr != ValidationResult.INVALID) {
                            int throwableIndex = lvs.newLocal(TypeUtils.throwableType);
                            dup();
                            storeLocal(TypeUtils.throwableType, throwableIndex);

                            Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new LocalVarArgProvider(TypeUtils.throwableType, throwableIndex)}, om);
                            args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                            args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                            args.load();
                            invokeBTraceAction(this, om);
                        }
                    }
                };// </editor-fold>

            case FIELD_GET:
                // <editor-fold defaultstate="collapsed" desc="Field Get Instrumentor">
                return new FieldAccessInstrumentor(lvs, access, name, desc) {

                    int calledInstanceIndex = -1;
                    private String className = loc.getClazz();
                    private String fieldName = loc.getField();

                    @Override
                    protected void onBeforeGetField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getCalledInstanceParameter() != -1 && isStaticAccess) {
                            return;


                        }
                        if (matches(className, owner.replace('/', '.'))
                                && matches(fieldName, name)) {

                            Type fldType = Type.getType(desc);
                            addExtraTypeInfo(om.getReturnParameter(), fldType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[0]);
                            if (vr != ValidationResult.INVALID) {
                                dup();
                                calledInstanceIndex = lvs.newLocal(TypeUtils.objectType);
                                storeLocal(TypeUtils.objectType, calledInstanceIndex);

                                if (where == Where.BEFORE) {
                                    Arguments args = new Arguments(actionArgTypes, om);
                                    args.addArgument(om.getCalledInstanceParameter(), new LocalVarArgProvider(TypeUtils.objectType, calledInstanceIndex));
                                    args.addArgument(om.getCalledMethodParameter(), new ConstantArgProvider(fieldName));
                                    args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                    args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                    args.load();
                                    invokeBTraceAction(this, om);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterGetField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getCalledInstanceParameter() != -1 && isStaticAccess) {
                            return;


                        }
                        if (where == Where.AFTER
                                && matches(className, owner.replace('/', '.'))
                                && matches(fieldName, name)) {
                            Type fldType = Type.getType(desc);

                            addExtraTypeInfo(om.getReturnParameter(), fldType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[0]);
                            if (vr != ValidationResult.INVALID) {
                                int returnValIndex = lvs.newLocal(fldType);
                                dupValue(desc);
                                storeLocal(fldType, returnValIndex);

                                Arguments args = new Arguments(actionArgTypes, om);
                                args.addArgument(om.getCalledInstanceParameter(), new LocalVarArgProvider(TypeUtils.objectType, calledInstanceIndex));
                                args.addArgument(om.getCalledMethodParameter(), new ConstantArgProvider(fieldName));
                                args.addArgument(om.getReturnParameter(), new LocalVarArgProvider(fldType, returnValIndex));
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                args.load();
                                invokeBTraceAction(this, om);
                            }
                        }
                    }
                };// </editor-fold>

            case FIELD_SET:
                // <editor-fold defaultstate="collapsed" desc="Field Set Instrumentor">
                return new FieldAccessInstrumentor(lvs, access, name, desc) {

                    private String className = loc.getClazz();
                    private String fieldName = loc.getField();
                    private int calledInstanceIndex = -1;
                    private int fldValueIndex = -1;

                    @Override
                    protected void onBeforePutField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getCalledInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (matches(className, owner.replace('/', '.'))
                                && matches(fieldName, name)) {

                            Type fieldType = Type.getType(desc);

                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{fieldType});

                            if (vr != ValidationResult.INVALID) {
                                fldValueIndex = lvs.newLocal(fieldType);
                                storeLocal(fieldType, fldValueIndex);
                                dup();
                                calledInstanceIndex = lvs.newLocal(TypeUtils.objectType);
                                storeLocal(TypeUtils.objectType, calledInstanceIndex);
                                loadLocal(fieldType, fldValueIndex);

                                if (where == Where.BEFORE) {
                                    Arguments args = new Arguments(actionArgTypes, new int[]{fldValueIndex}, om);
                                    args.addArgument(om.getCalledInstanceParameter(), new LocalVarArgProvider(TypeUtils.objectType, calledInstanceIndex));
                                    args.addArgument(om.getCalledMethodParameter(), new ConstantArgProvider(fieldName));
                                    args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                    args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                    args.load();

                                    invokeBTraceAction(this, om);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterPutField(int opcode,
                            String owner, String name, String desc) {
                        if (om.getCalledInstanceParameter() != -1 && isStaticAccess) {
                            return;

                        }
                        if (where == Where.AFTER
                                && matches(className, owner.replace('/', '.'))
                                && matches(fieldName, name)) {
                            Type fieldType = Type.getType(desc);

                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{fieldType});

                            if (vr != ValidationResult.INVALID) {
                                Arguments args = new Arguments(actionArgTypes, new int[]{fldValueIndex}, om);
                                args.addArgument(om.getCalledInstanceParameter(), new LocalVarArgProvider(TypeUtils.objectType, calledInstanceIndex));
                                args.addArgument(om.getCalledMethodParameter(), new ConstantArgProvider(fieldName));
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                args.load();
                                invokeBTraceAction(this, om);
                            }
                        }
                    }
                };// </editor-fold>

            case INSTANCEOF:
                // <editor-fold defaultstate="collapsed" desc="InstanceOf Instrumentor">
                return new TypeCheckInstrumentor(lvs, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.INSTANCEOF) {
                            // TODO not really usefull
                            // It would be better to check for the original and desired type
                            Type castType = Type.getObjectType(desc);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{castType});
                            if (vr != ValidationResult.INVALID) {
                                int castTypeIndex = lvs.newLocal(castType);
                                dup();
                                storeLocal(castType, castTypeIndex);

                                Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new LocalVarArgProvider(castType, castTypeIndex)}, om);
                                args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                args.load();
                                invokeBTraceAction(this, om);
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
                return new LineNumberInstrumentor(lvs, access, name, desc) {

                    private int onLine = loc.getLine();

                    private void callOnLine(int line) {
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE});
                        if (vr != ValidationResult.INVALID) {
                            Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new ConstantArgProvider(line)}, om);
                            args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                            args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                            args.load();
                            invokeBTraceAction(this, om);
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
                return new ObjectAllocInstrumentor(lvs, access, name, desc) {

                    @Override
                    protected void beforeObjectNew(String desc) {
                        if (loc.getWhere() == Where.BEFORE) {
                            String extName = desc.replace('/', '.');
                            if (matches(loc.getClazz(), extName)) {
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType});
                                if (vr != ValidationResult.INVALID) {
                                    Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new ConstantArgProvider(extName)}, om);
                                    args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                    args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                    args.load();

                                    invokeBTraceAction(this, om);
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

                                addExtraTypeInfo(om.getReturnParameter(), instType);
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType});
                                if (vr != ValidationResult.INVALID) {
                                    dupValue(instType);
                                    int returnValIndex = lvs.newLocal(instType);
                                    storeLocal(instType, returnValIndex);
                                    Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new ConstantArgProvider(extName)}, om);
                                    args.addArgument(om.getReturnParameter(), new LocalVarArgProvider(instType, returnValIndex));
                                    args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                    args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                    args.load();
                                    invokeBTraceAction(this, om);
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case NEWARRAY:
                // <editor-fold defaultstate="collapsed" desc="New Array Instrumentor">
                return new ArrayAllocInstrumentor(lvs, access, name, desc) {

                    @Override
                    protected void onBeforeArrayNew(String desc, int dims) {
                        if (where == Where.BEFORE) {
                            String extName = TypeUtils.getJavaType(desc);
                            String type = TypeUtils.objectOrArrayType(loc.getClazz());
                            if (matches(type, desc)) {
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType, Type.INT_TYPE});
                                if (vr != ValidationResult.INVALID) {
                                    Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new ConstantArgProvider(extName), new ConstantArgProvider(dims)}, om);
                                    args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                    args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                    args.load();

                                    invokeBTraceAction(this, om);
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
                                addExtraTypeInfo(om.getReturnParameter(), instType);
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType, Type.INT_TYPE});
                                if (vr != ValidationResult.INVALID) {
                                    dupValue(instType);
                                    int returnValIndex = lvs.newLocal(instType);
                                    storeLocal(instType, returnValIndex);
                                    Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new ConstantArgProvider(extName), new ConstantArgProvider(dims)}, om);
                                    args.addArgument(om.getReturnParameter(), new LocalVarArgProvider(instType, returnValIndex));
                                    args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                                    args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                                    args.load();
                                    invokeBTraceAction(this, om);
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
                MethodReturnInstrumentor mri = new MethodReturnInstrumentor(lvs, access, name, desc) {

                    int retValIndex;

                    private void callAction(int retOpCode) {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, Type.getArgumentTypes(getDescriptor()));

                        if (vr == ValidationResult.INVALID) {
                            return;

                        }
                        if (om.getReturnParameter() != -1) {
                            retValIndex = lvs.newLocal(getReturnType());
                            dupReturnValue(retOpCode);
                            storeLocal(getReturnType(), retValIndex);
                        }
                        if (om.getDurationParameter() != -1) {
                            usesTimeStamp = true;
                        }
                        Arguments args = new Arguments(actionArgTypes, om);
                        args.addArgument(om.getDurationParameter(), new ArgumentProvider() {

                            public void provide() {
                                if (tsindex[0] != -1 && tsindex[1] != -1) {
                                    loadLocal(Type.LONG_TYPE, tsindex[1]);
                                    loadLocal(Type.LONG_TYPE, tsindex[0]);
                                    visitInsn(LSUB);
                                }
                            }
                        });
                        args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className));
                        args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                        args.addArgument(om.getReturnParameter(), new LocalVarArgProvider(getReturnType(), retValIndex));

                        args.load();
                        invokeBTraceAction(this, om);
                    }

                    @Override
                    protected void onMethodReturn(int opcode) {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else {
                            callAction(opcode);
                        }
                    }
                };
                if (om.getDurationParameter() != -1) {
                    return new TimeStampGenerator(lvs, tsindex, className, access, name, desc, mri, new int[]{RETURN, IRETURN, FRETURN, DRETURN, LRETURN, ARETURN});
                } else {
                    return mri;
                }// </editor-fold>

            case SYNC_ENTRY:
                return new SynchronizedInstrumentor(className, lvs, access, name, desc) {
                    private void callAction() {
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.objectType});
                        if (vr != ValidationResult.INVALID) {
                            dup();
                            int index = lvs.newLocal(TypeUtils.objectType);
                            storeLocal(TypeUtils.objectType, index);
                            Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new LocalVarArgProvider(TypeUtils.objectType, index)}, om);
                            args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className));
                            args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                            args.load();
                            invokeBTraceAction(this, om);
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
                };

            case SYNC_EXIT:
                return new SynchronizedInstrumentor(className, lvs, access, name, desc) {
                    private void callAction() {
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.objectType});
                        if (vr != ValidationResult.INVALID) {
                            dup();
                            int index = lvs.newLocal(TypeUtils.objectType);
                            storeLocal(TypeUtils.objectType, index);
                            Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new LocalVarArgProvider(TypeUtils.objectType, index)}, om);
                            args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className));
                            args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                            args.load();
                            invokeBTraceAction(this, om);
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
                };

            case THROW:
                // <editor-fold defaultstate="collapsed" desc="Throw Instrumentor">
                return new ThrowInstrumentor(lvs, access, name, desc) {

                    @Override
                    protected void onThrow() {
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                        if (vr != ValidationResult.INVALID) {
                            dup();
                            int throwableIndex = lvs.newLocal(TypeUtils.throwableType);
                            storeLocal(TypeUtils.throwableType, throwableIndex);
                            Arguments args = new Arguments(actionArgTypes, new ArgumentProvider[]{new LocalVarArgProvider(TypeUtils.throwableType, throwableIndex)}, om);
                            args.addArgument(om.getClassNameParameter(), new ConstantArgProvider(className.replace("/", ".")));
                            args.addArgument(om.getMethodParameter(), new ConstantArgProvider(getName()));
                            args.load();
                            
                            invokeBTraceAction(this, om);
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

    
    private static class CallValidationResult {
        final public boolean callValid;
        final public boolean usesAnytype;

        public CallValidationResult(boolean callValid, boolean usesAnytype) {
            this.callValid = callValid;
            this.usesAnytype = usesAnytype;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CallValidationResult other = (CallValidationResult) obj;
            if (this.callValid != other.callValid) {
                return false;
            }
            if (this.usesAnytype != other.usesAnytype) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + (this.callValid ? 1 : 0);
            hash = 79 * hash + (this.usesAnytype ? 1 : 0);
            return hash;
        }
    }

    private CallValidationResult validateCall(String selfClassName, String instanceClassName, OnMethod om, Type[] calledMethodArgs, Type returnType, Type actionArgTypes[], int numActionArgs) {
        int extraArgCount = (om.getReturnParameter() > -1 ? 1 : 0) + (om.getCalledMethodParameter() > -1 ? 1 : 0) + (om.getSelfParameter() > -1 ? 1 : 0) + (om.getCalledInstanceParameter() > -1 ? 1 : 0);
        boolean extraArgsValid = true;

        int anyTypeIndex = -1;
        if (calledMethodArgs.length + extraArgCount == numActionArgs) {
            Type[] tmp = new Type[numActionArgs - extraArgCount];
            int counter = 0;

            for(int i=0;i<numActionArgs;i++) {
                if (om.getCalledMethodParameter() == i) {
                    extraArgsValid &= TypeUtils.isString(actionArgTypes[i]);
                    continue;
                } else if (om.getSelfParameter() == i) {
                    extraArgsValid &= TypeUtils.isObject(actionArgTypes[i]) || TypeUtils.isCompatible(actionArgTypes[i], Type.getObjectType(selfClassName));
                    continue;
                } else if (om.getCalledInstanceParameter() == i) {
                    extraArgsValid &= TypeUtils.isObject(actionArgTypes[i]) || TypeUtils.isCompatible(actionArgTypes[i], Type.getObjectType(instanceClassName));
                    continue;
                } else if (returnType != null && om.getReturnParameter() == i) {
                    extraArgsValid &= TypeUtils.isCompatible(actionArgTypes[i], returnType);
                    continue;
                }
                if (anyTypeIndex > -1) {
                    // already has found AnyType[] for args; INVALID!
                    extraArgsValid = false;
                    break;
                }
                if (TypeUtils.isAnyTypeArray(actionArgTypes[i])) {
                    anyTypeIndex = i;
                }
                tmp[counter++] = actionArgTypes[i];
            }

            return new CallValidationResult(extraArgsValid &&
                (anyTypeIndex > -1 || TypeUtils.isCompatible(tmp, calledMethodArgs)), anyTypeIndex > -1);
        }
        return new CallValidationResult(false, false);
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

