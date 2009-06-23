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
import com.sun.btrace.util.MethodID;
import java.util.Collections;
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

    final private Set<String> emptyMethods;

    public Instrumentor(Class clazz, 
            String btraceClassName, ClassReader btraceClass, 
            List<OnMethod> onMethods, Set<String> emptyMethods, ClassVisitor cv) {
        super(cv);
        this.clazz = clazz;
        this.btraceClassName = btraceClassName.replace('.', '/');
        this.btraceClass = btraceClass;
        this.onMethods = onMethods;
        this.applicableOnMethods = new ArrayList<OnMethod>();
        this.calledOnMethods = new HashSet<OnMethod>();
        this.emptyMethods = emptyMethods;
    }

    public Instrumentor(Class clazz,
            String btraceClassName, byte[] btraceCode, 
            List<OnMethod> onMethods, Set<String> emptyMethods, ClassVisitor cv) {
        this(clazz, btraceClassName, new ClassReader(btraceCode), onMethods, emptyMethods, cv);
    }

    public void visit(int version, int access, String name, 
        String signature, String superName, String[] interfaces) {        
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

        // don't instrument empty methods
        if (emptyMethods.contains(MethodID.create(name, desc))) return methodVisitor;

        if (applicableOnMethods.size() == 0 ||
            (access & ACC_ABSTRACT) != 0    ||
            (access & ACC_NATIVE) != 0      ||
            name.startsWith(BTRACE_METHOD_PREFIX)) {
            return methodVisitor;
        }
     
        for (OnMethod om : applicableOnMethods) {
            if (om.getLocation().getValue() == Kind.LINE) {
                methodVisitor = instrumentorFor(om, methodVisitor, access, name, desc);
            } else {
                String methodName = om.getMethod();
                if (methodName.equals("")) {
                    methodName = om.getTargetName();
                }
                if (methodName.equals(name) &&
                    typeMatches(om.getType(), desc)) {
                    methodVisitor = instrumentorFor(om, methodVisitor, access, name, desc);
                } else if (methodName.charAt(0) == '/' &&
                           REGEX_SPECIFIER.matcher(methodName).matches()) {
                    methodName = methodName.substring(1, methodName.length() - 1);
                    if (name.matches(methodName) &&
                        typeMatches(om.getType(), desc)) {
                        methodVisitor = instrumentorFor(om, methodVisitor, access, name, desc);
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
                                mv = instrumentorFor(om, mv, access, name, desc);
                            }
                        } else if (annoName.equals(extAnnoName)) {
                            mv = instrumentorFor(om, mv, access, name, desc);
                        }
                    }                   
                }               
                return mv.visitAnnotation(annoDesc, visible);
            }
        }; 
    }

    private MethodVisitor instrumentorFor(
        final OnMethod om, MethodVisitor mv,
        int access, String name, String desc) {
        final Location loc = om.getLocation();
        final Where where = loc.getWhere();
        final Type[] actionArgTypes = Type.getArgumentTypes(om.getTargetDescriptor());
        final int numActionArgs = actionArgTypes.length;

        switch (loc.getValue()) {            
            case ARRAY_GET:
                return new ArrayAccessInstrumentor(mv, access, name, desc) {
                    @Override
                    protected void onBeforeArrayLoad(int opcode) {
                        if (numActionArgs == 2 && where == Where.BEFORE &&
                            TypeUtils.getArrayType(opcode).equals(actionArgTypes[0]) &&
                            Type.INT_TYPE.equals(actionArgTypes[1])) {
                            dup2();
                            invokeBTraceAction(this, om);
                        }
                    }

                    @Override
                    protected void onAfterArrayLoad(int opcode) {
                        if (numActionArgs == 1 && where == Where.AFTER &&
                            TypeUtils.getElementType(opcode).equals(actionArgTypes[0])) {                                        
                            dupArrayValue(opcode);
                            invokeBTraceAction(this, om);
                        }
                    }
                };

            case ARRAY_SET:
                return new ArrayAccessInstrumentor(mv, access, name, desc) {
                    private int maxLocal = 0;

                    @Override
                    public void visitCode() {
                        maxLocal = getArgumentTypes().length;
                        super.visitCode();
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (var > maxLocal) { maxLocal = var; }
                        super.visitVarInsn(opcode, var);
                    }

                    @Override
                    protected void onBeforeArrayStore(int opcode) {
                        Type elementType = TypeUtils.getElementType(opcode);
                        if (where == Where.BEFORE &&
                            numActionArgs == 3 &&
                            TypeUtils.getArrayType(opcode).equals(actionArgTypes[0]) &&
                            Type.INT_TYPE.equals(actionArgTypes[1]) &&
                            elementType.equals(actionArgTypes[2])) {
                            storeLocal(elementType, maxLocal + 1);
                            dup2();
                            loadLocal(elementType, maxLocal + 1);
                            invokeBTraceAction(this, om);
                            storeLocal(elementType, maxLocal + 1);
                        }
                    }

                    @Override
                    protected void onAfterArrayStore(int opcode) {
                        if (numActionArgs == 0 && where == Where.AFTER) {
                            invokeBTraceAction(this, om);
                        }
                    }
                };

            case CALL:
                return new MethodCallInstrumentor(mv, access, name, desc) {
                    private String className = loc.getClazz();
                    private String methodName = loc.getMethod();
                    private int maxLocal = 0;

                    @Override
                    public void visitCode() {
                        maxLocal = getArgumentTypes().length + (isStatic() ? 0 : 1);
                        super.visitCode();
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (var > maxLocal) { maxLocal = var; }
                        super.visitVarInsn(opcode, var);
                    }

                    private void backupArgs(boolean isStaticCall, Type[] callArgs) {
                        int upper = callArgs.length - 1;

                        for (int i = 0;i<callArgs.length;i++) {
                            storeLocal(callArgs[upper - i], maxLocal + i);
                        }

                        if (! isStaticCall) {
                            storeLocal(TypeUtils.objectType, maxLocal + callArgs.length); // store *callee*
                        }
                    }

                    private void restoreArgs(boolean isStaticCall, Type[] callArgs) {
                        int upper = callArgs.length - 1;
                        if (!isStaticCall) {
                            loadLocal(TypeUtils.objectType, maxLocal + callArgs.length);
                        }

                        for (int i = callArgs.length - 1; i > -1; i--) {
                            loadLocal(callArgs[upper - i], maxLocal + i);
                        }
                    }

                    private void preMatchAction(boolean isStaticCall, String method, Type[] args, Type returnType, int probeArgsLength) {
                        int argPtrRev = args.length - 1;
                        int argPtr = 0;
                        
                        for(int i=0;i<probeArgsLength;i++) {
                            if (i == om.getSelfParameter()) {
                                if (!isStatic()) {
                                    loadLocal(TypeUtils.objectType, 0); // load *this*
                                }
                            } else if (i == om.getCalledMethodParameter()) {
                                super.visitLdcInsn(method);
                            } else if (i == om.getCalledInstanceParameter()) {
                                if (!isStaticCall) {
                                    loadLocal(TypeUtils.objectType, maxLocal + args.length); // load the callee instance
                                }
                            } else if (i == om.getReturnParameter() && returnType != null) {
                                loadLocal(returnType, maxLocal + args.length + 1);
                            } else {
                                loadLocal(args[argPtr++], maxLocal + argPtrRev--);
                            }
                        }
                    }

                    private void preAnyTypeAction(boolean isStaticCall, String method, Type[] args, Type returnType, int probeArgsLength) {
                        int upper = args.length - 1;
                        for(int i=0;i < probeArgsLength;i++) {
                            if (i == om.getSelfParameter()) {
                                if (!isStatic()) {
                                    loadLocal(TypeUtils.objectType, 0); // load *this*
                                }
                            } else if (i == om.getCalledMethodParameter()) {
                                super.visitLdcInsn(method);
                            } else if (i == om.getCalledInstanceParameter()) {
                                if (!isStaticCall) {
                                    loadLocal(TypeUtils.objectType, maxLocal + args.length); // load the callee instance
                                }
                            } else if (i == om.getReturnParameter() && returnType != null) {
                                loadLocal(returnType, maxLocal + args.length + 1); // load return type
                            } else {
                                // we can safely suppose that the only arg without special annotation would be the AnyType[] arg
                                push(args.length);
                                super.visitTypeInsn(ANEWARRAY, TypeUtils.objectType.getInternalName());
                                for (int argIndex = 0; argIndex < args.length; argIndex++) {
                                    dup();
                                    push(argIndex);
                                    loadLocal(args[upper - argIndex], maxLocal + argIndex);
                                    box(args[upper - argIndex]);
                                    arrayStore(TypeUtils.objectType);
                                }
                            }
                        }
                    }

                    private void injectBtrace(boolean isStaticCall, boolean usesAnytype, String method, Type[] calledMethodArgs, Type returnType, Type[] actionArgTypes) {
                        if (usesAnytype) {
                            preAnyTypeAction(isStaticCall, method, calledMethodArgs, returnType, actionArgTypes.length);
                        } else {
                            preMatchAction(isStaticCall, method, calledMethodArgs, returnType, actionArgTypes.length);
                        }
                        invokeBTraceAction(this, om);
                    }

                    private CallValidationResult cvr;
                    @Override
                    protected void onBeforeCallMethod(int opcode, String owner, 
                        String name, String desc) {
                        if (isStatic() && om.getSelfParameter() > -1) return; // invalid combination; a static method can not provide *this*

                        if (matches(className, owner.replace('/', '.')) &&
                            matches(methodName, name) &&
                            typeMatches(loc.getType(), desc)) {
                            
                            String method = name + desc;
                            Type[] calledMethodArgs = Type.getArgumentTypes(desc);
                            boolean doCall = false;
                            boolean isStaticCall = (opcode == INVOKESTATIC);
                            if (isStaticCall) {
                                doCall = om.getCalledInstanceParameter() == -1;
                            } else {
                                if (where == Where.BEFORE && name.equals(CONSTRUCTOR)) {
                                    return;
                                }
                                doCall = true;
                            }
                            if (doCall) {
                                Type returnType = (where == Where.AFTER ? Type.getReturnType(desc) : null);
                                cvr = validateCall(owner, className, om, calledMethodArgs, returnType, actionArgTypes, numActionArgs);
                                if (cvr.callValid) {
                                    // will store the call args into local variables
                                    backupArgs(isStaticCall, calledMethodArgs);
                                    if (where == Where.BEFORE) {
                                        // this will also retrieve the call args from the backup variables
                                        injectBtrace(isStaticCall, cvr.usesAnytype, method, calledMethodArgs, returnType, actionArgTypes);
                                    }
                                    // put the call args back on stack so the method call can find them
                                    restoreArgs(isStaticCall, calledMethodArgs);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterCallMethod(int opcode,
                        String owner, String name, String desc) {
                        if (cvr == null || !cvr.callValid) {
                            return;
                        }
                        if (isStatic() && om.getSelfParameter() != -1) {
                            return;
                        }
                        if (where == Where.AFTER &&
                            matches(className, owner.replace('/', '.')) &&
                            matches(methodName, name) &&
                            typeMatches(loc.getType(), desc)) {
                                Type returnType = Type.getReturnType(desc);
                                int argsLength = Type.getArgumentTypes(desc).length;
                                String method = name + desc;
                                boolean withReturn = om.getReturnParameter() != -1 && returnType != Type.VOID_TYPE;
                                if (withReturn) {
                                    // store the return value to a local variable
                                    storeLocal(returnType, maxLocal + argsLength + 1); // store the return value
                                }
                                // will also retrieve the call args and the return value from the backup variables
                                injectBtrace(opcode == INVOKESTATIC, cvr.usesAnytype, method, Type.getArgumentTypes(desc), returnType, actionArgTypes);
                                if (withReturn) {
                                    loadLocal(returnType, maxLocal + argsLength + 1); // restore the return value
                                }
                        }                            
                    }
                };

            case CATCH:
                return new CatchInstrumentor(mv, access, name, desc) {
                    @Override
                    protected void onCatch(String type) {
                        if (numActionArgs == 1 &&
                            Type.getObjectType(type).equals(actionArgTypes[0])) {
                            dup();
                            invokeBTraceAction(this, om);
                        }
                    }
                };

            case CHECKCAST:
                return new TypeCheckInstrumentor(mv, access, name, desc) {
                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.CHECKCAST && numActionArgs == 1 && 
                            Type.getType(desc).equals(actionArgTypes[0])) {
                            dup();
                            invokeBTraceAction(this, om);
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
                };

            case ENTRY:
                return new MethodEntryInstrumentor(mv, access, name, desc) {
                    private int maxLocal = 0;
                    private CallValidationResult cvr;

                    @Override
                    public void visitCode() {
                        maxLocal = getArgumentTypes().length;
                        super.visitCode();
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (var > maxLocal) { maxLocal = var; }
                        super.visitVarInsn(opcode, var);
                    }
//
//                    private void backupArgs(Type[] callArgs) {
//                        int upper = callArgs.length - 1;
//
//                        for (int i = 0;i<callArgs.length;i++) {
//                            storeLocal(callArgs[upper - i], maxLocal + i);
//                        }
//                    }
//
//                    private void restoreArgs(Type[] callArgs) {
//                        int upper = callArgs.length - 1;
//
//                        for (int i = callArgs.length - 1; i > -1; i--) {
//                            loadLocal(callArgs[upper - i], maxLocal + i);
//                        }
//                    }

                    private void preMatchAction(Type[] args, int probeArgsLength) {
                        int argPtrRev = args.length - 1;
                        int argPtr = 0;

                        int decrement = isStatic() ? 1 : 0;
                        for(int i=0;i<probeArgsLength;i++) {
                            if (i == om.getSelfParameter()) {
                                if (!isStatic()) {
                                    loadLocal(TypeUtils.objectType, 0); // load *this*
                                }
                            } else {
                                loadLocal(args[argPtr++], maxLocal + argPtrRev-- - decrement);
                            }
                        }
                    }

                    private void preAnyTypeAction(Type[] args, int probeArgsLength) {
                        int decrement = isStatic() ? 1 : 0;
                        int upper = args.length - 1;
                        for(int i=0;i < probeArgsLength;i++) {
                            if (i == om.getSelfParameter()) {
                                if (!isStatic()) {
                                    loadLocal(TypeUtils.objectType, 0); // load *this*
                                }
                            } else {
                                // we can safely suppose that the only arg without special annotation would be the AnyType[] arg
                                push(args.length);
                                super.visitTypeInsn(ANEWARRAY, TypeUtils.objectType.getInternalName());
                                for (int argIndex = 0; argIndex < args.length; argIndex++) {
                                    dup();
                                    push(argIndex);
                                    loadLocal(args[upper - argIndex], maxLocal + argIndex - decrement);
                                    box(args[upper - argIndex]);
                                    arrayStore(TypeUtils.objectType);
                                }
                            }
                        }
                    }

                    private void injectBtrace(boolean usesAnytype, Type[] calledMethodArgs, Type[] actionArgTypes) {
                        if (usesAnytype) {
                            preAnyTypeAction(calledMethodArgs, actionArgTypes.length);
                        } else {
                            preMatchAction(calledMethodArgs, actionArgTypes.length);
                        }
                        invokeBTraceAction(this, om);
                    }

                    private void callAction() {
                        if (isStatic() && om.getSelfParameter() > -1) return; // invalid combination; a static method can not provide *this*

                        Type[] calledMethodArgs = Type.getArgumentTypes(getDescriptor());

//                        Type returnType = (where == Where.AFTER ? Type.getReturnType(getDescriptor()) : null);
                        cvr = validateCall(className, null, om, calledMethodArgs, null, actionArgTypes, numActionArgs);
                        if (cvr.callValid) {
                            // this will also retrieve the call args
                            injectBtrace(cvr.usesAnytype, calledMethodArgs, actionArgTypes);
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
                };

            case ERROR:
                return new ErrorReturnInstrumentor(mv, access, name, desc) {
                    @Override
                    protected void onErrorReturn() {
                        switch (numActionArgs) {
                            case 0:
                                invokeBTraceAction(this, om);
                            break;
                            case 1:
                                if (TypeUtils.isThrowable(actionArgTypes[0])) {
                                    dup();
                                    invokeBTraceAction(this, om);
                                }
                            break;
                        }                        
                    }
                };

            case FIELD_GET:
                return new FieldAccessInstrumentor(mv, access, name, desc) {
                    private String className = loc.getClazz();
                    private String fieldName = loc.getField();

                    @Override
                    protected void onBeforeGetField(int opcode, String owner, 
                        String name, String desc) {
                        if (where == Where.BEFORE &&
                            matches(className, owner.replace('/', '.')) &&
                            matches(fieldName, name)) {
                            if (opcode == GETFIELD) {
                                switch (numActionArgs) {
                                    case 0:
                                        invokeBTraceAction(this, om);
                                    break;
                                    case 1:
                                        if (TypeUtils.isObject(actionArgTypes[0]) ||
                                            TypeUtils.isCompatible(actionArgTypes[0], Type.getObjectType(owner))) {
                                            dup();
                                            invokeBTraceAction(this, om);
                                        }
                                    break;
                                }
                            } else if (opcode == GETSTATIC && numActionArgs == 0) {
                                invokeBTraceAction(this, om);
                            }
                        }
                    }

                    @Override
                    protected void onAfterGetField(int opcode, String owner, 
                        String name, String desc) {
                        if (where == Where.AFTER &&
                            matches(className, owner.replace('/', '.')) &&
                            matches(fieldName, name)) {
                            switch (numActionArgs) {
                                case 0:
                                    invokeBTraceAction(this, om);
                                break;
                                case 1:
                                    if (TypeUtils.isObject(actionArgTypes[0]) ||
                                        TypeUtils.isCompatible(actionArgTypes[0], Type.getType(desc))) {
                                        dupValue(desc);
                                        invokeBTraceAction(this, om);
                                    }
                                break;
                            }
                        }
                    }
                };

            case FIELD_SET:
                return new FieldAccessInstrumentor(mv, access, name, desc) {
                    private String className = loc.getClazz();
                    private String fieldName = loc.getField();
                    private int maxLocal = 0;

                    @Override
                    public void visitCode() {
                        maxLocal = getArgumentTypes().length;
                        super.visitCode();
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (var > maxLocal) { maxLocal = var; }
                        super.visitVarInsn(opcode, var);
                    }

                    @Override
                    protected void onBeforePutField(int opcode, String owner, 
                        String name, String desc) {
                        if (where == Where.BEFORE &&
                            matches(className, owner.replace('/', '.')) &&
                            matches(fieldName, name)) {
                            switch (numActionArgs) {
                                case 0:
                                    invokeBTraceAction(this, om);
                                    break;
                                case 2: {
                                    Type fieldType = Type.getType(desc);
                                    if ((TypeUtils.isObject(actionArgTypes[0]) ||
                                        TypeUtils.isCompatible(actionArgTypes[0], Type.getObjectType(owner))) &&
                                        fieldType.equals(actionArgTypes[1])) {
                                        switch (fieldType.getSize()) {
                                            case 1:
                                                dup2();
                                                invokeBTraceAction(this, om);
                                            break;
                                            case 2:
                                                storeLocal(fieldType, maxLocal + 1);
                                                dup();
                                                loadLocal(fieldType, maxLocal + 1);
                                                invokeBTraceAction(this, om);
                                                loadLocal(fieldType, maxLocal + 1);
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }

                    @Override
                    protected void onAfterPutField(int opcode,
                        String owner, String name, String desc) {
                        if (where == Where.AFTER &&
                            matches(className, owner) &&                            
                            matches(fieldName, name) &&
                            numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        }
                    }
                };

            case INSTANCEOF:
                return new TypeCheckInstrumentor(mv, access, name, desc) {
                    private void callAction(int opcode, String desc) {
                        if (numActionArgs == 1 && opcode == Opcodes.INSTANCEOF) {                            
                            dup();
                            invokeBTraceAction(this, om);
                        }
                    }

                    @Override
                    protected void onBeforeTypeCheck(int opcode, String desc) {
                        if (where == Where.BEFORE &&
                            Type.getType(desc).equals(actionArgTypes[0])) {
                            callAction(opcode, desc);
                        }
                    }

                    @Override
                    protected void onAfterTypeCheck(int opcode, String desc) {
                        if (where == Where.AFTER &&
                            Type.BOOLEAN_TYPE.equals(actionArgTypes[0])) {
                            callAction(opcode, desc);
                        }
                    }
                };

            case LINE:
                return new LineNumberInstrumentor(mv, access, name, desc) {
                    private int onLine = loc.getLine();
                    private void callOnLine(int line) {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else if (numActionArgs == 1 &&
                            actionArgTypes[0].equals(Type.INT_TYPE)) {
                            push(line);
                            invokeBTraceAction(this, om);
                        } 
                    }
        
                    @Override
                    protected void onBeforeLine(int line) {
                        if ((line == onLine || onLine == -1) &&
                            where == Where.BEFORE) {
                            callOnLine(line);
                        }
                    }

                    @Override
                    protected void onAfterLine(int line) {
                        if ((line == onLine || onLine == -1) &&
                            where == Where.AFTER) {
                            callOnLine(line);
                        }
                    }
                };

            case NEW:
                return new ObjectAllocInstrumentor(mv, access, name, desc) {
                    @Override
                    protected void onObjectNew(String desc) {                        
                        String extName = desc.replace('/', '.');
                        if (matches(loc.getClazz(), extName)) {
                            switch (numActionArgs) {
                                case 0:
                                    invokeBTraceAction(this, om);
                                break;
                                case 1:
                                    if (TypeUtils.isString(actionArgTypes[0])) {
                                        visitLdcInsn(extName);
                                        invokeBTraceAction(this, om);
                                    }
                                break;
                            }
                        }
                    }
                };

            case NEWARRAY:
                return new ArrayAllocInstrumentor(mv, access, name, desc) {
                    @Override
                    protected void onBeforeArrayNew(String desc, int dims) {
                        if (numActionArgs == 0 && where == Where.BEFORE) {
                            invokeBTraceAction(this, om);
                        }      
                    }

                    @Override
                    protected void onAfterArrayNew(String desc, int dims) {
                        if (where == Where.AFTER) {
                            if (numActionArgs == 1 &&
                                TypeUtils.isCompatible(actionArgTypes[0], Type.getType(desc))) {
                                dup();
                                invokeBTraceAction(this, om);                        
                            }
                        }
                    }
                };
          
            case RETURN:
                return new MethodReturnInstrumentor(mv, access, name, desc) {
                    private void callAction(int retOpCode) {
                if (om.getSelfParameter() != -1) {
                            if (isStatic()) {
                                return;
                    }

                            if (isConstructor()) return;

                    if (!(TypeUtils.isObject(actionArgTypes[om.getSelfParameter()]) ||
                                    TypeUtils.isCompatible(actionArgTypes[om.getSelfParameter() - 1], Type.getObjectType(className)))
                                && !(TypeUtils.isCompatible(actionArgTypes[om.getReturnParameter() -1], Type.getObjectType(className)))) {
                        // TODO issue a warning
                                return;
                    }
                }
                        int decrement = (om.getSelfParameter() != -1 ? 1 : 0) + (om.getReturnParameter() != -1 ? 1 : 0);
                        Type[] tmpArgArray = new Type[actionArgTypes.length - decrement];

                        int selfIndex = om.getSelfParameter();
                        int returnIndex = om.getReturnParameter();

                        int counter = 0;
                        for(int argIndex=0;argIndex<actionArgTypes.length;argIndex++) {
                            if (argIndex != selfIndex && argIndex != returnIndex) {
                                tmpArgArray[counter++] = actionArgTypes[argIndex];
                    }
                }

                        if (tmpArgArray.length == 1 && TypeUtils.isAnyTypeArray(tmpArgArray[0])) {
                            if (om.getSelfParameter() == 1) {
                                loadThis();
                    }
                            if (om.getReturnParameter() == 1) {
                                dupReturnValue(retOpCode);
                }
                            loadArgumentArray();
                            if (om.getSelfParameter() == 2) {
                                    loadThis();
                                }
                            if (om.getReturnParameter() == 2) {
                                dupReturnValue(retOpCode);
                            }
                            invokeBTraceAction(this, om);
                            return;
                                }

                        if (tmpArgArray.length == 0 || TypeUtils.isCompatible(tmpArgArray, getArgumentTypes())) {
                            loadArguments(om, retOpCode);
                            invokeBTraceAction(this, om);
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
                };

            case SYNC_ENTRY:
                return new SynchronizedInstrumentor(className, mv, access, name, desc) {
                    private void callAction() {
                        switch (numActionArgs) {
                            case 0:
                                invokeBTraceAction(this, om);
                            break;
                            case 1:
                                if (TypeUtils.isObjectOrAnyType(actionArgTypes[0])) {
                                    dup();
                                    invokeBTraceAction(this, om);
                                }
                            break;
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
                return new SynchronizedInstrumentor(className, mv, access, name, desc) {
                    private void callAction() {
                        switch (numActionArgs) {
                            case 0:
                                invokeBTraceAction(this, om);
                            break;
                            case 1:
                                if (TypeUtils.isObjectOrAnyType(actionArgTypes[0])) {
                                    dup();
                                    invokeBTraceAction(this, om);
                                }
                            break;
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
                return new ThrowInstrumentor(mv, access, name, desc) {
                    @Override
                    protected void onThrow() {
                        switch (numActionArgs) {
                            case 0:
                                invokeBTraceAction(this, om);
                                break;
                            case 1:
                                if (TypeUtils.isThrowable(actionArgTypes[0])) {
                                    dup();
                                    invokeBTraceAction(this, om);                           
                                }
                            break;
                        }
                    }
                };
        }
        return mv;
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
                    verifier.getOnMethods(), Collections.EMPTY_SET, writer));
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
