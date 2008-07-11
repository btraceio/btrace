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
                        if (numActionArgs == 0 && where == Where.BEFORE) {
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
                        maxLocal = getArgumentTypes().length;
                        super.visitCode();
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        if (var > maxLocal) { maxLocal = var; }
                        super.visitVarInsn(opcode, var);
                    }

                    private void callAction(boolean isStatic, Type[] args) {  
                        if (! isStatic) {
                            storeLocal(TypeUtils.objectType, maxLocal + 1);
                        }
                        for (int i = 0; i < args.length; i++) {
                            storeLocal(args[i], maxLocal + i + ((isStatic)? 0 : 1));
                        }                            
                        for (int i = args.length - 1; i > -1; i--) {
                            loadLocal(args[i], maxLocal + i + ((isStatic)? 0 : 1));
                        }                            
                        invokeBTraceAction(this, om);
                        if (! isStatic) {
                            storeLocal(TypeUtils.objectType, maxLocal + 1);
                        }
                        for (int i = args.length - 1; i > -1; i--) {
                            loadLocal(args[i], maxLocal + i + ((isStatic)? 0 : 1));
                        }
                    }

                    @Override
                    protected void onBeforeCallMethod(int opcode, String owner, 
                        String name, String desc) {
                        if (where == Where.BEFORE &&
                            matches(className, owner.replace('/', '.')) &&
                            matches(methodName, name) &&
                            typeMatches(loc.getType(), desc)) {
                            if (numActionArgs == 0) {
                                invokeBTraceAction(this, om);
                            } else {
                                Type[] calledMethodArgs = Type.getArgumentTypes(desc);
                                if (opcode == INVOKESTATIC) {                  
                                    if (TypeUtils.isCompatible(actionArgTypes, calledMethodArgs)) {
                                        callAction(true, calledMethodArgs);
                                    }
                                } else {
                                    /*
                                     * It is not safe to call a method before constructor
                                     * call passing (the uninitialized) object as argument. 
                                     * Bytecode verifier will not like it!
                                     */
                                    if (name.equals(CONSTRUCTOR)) {
                                        return;
                                    }
                                    if (calledMethodArgs.length + 1 == numActionArgs) {
                                        Type[] tmp = new Type[numActionArgs - 1];
                                        System.arraycopy(actionArgTypes, 1, tmp, 0, tmp.length);
                                        if ((TypeUtils.isObject(actionArgTypes[0]) ||
                                            TypeUtils.isCompatible(actionArgTypes[0], Type.getObjectType(owner))) &&
                                            TypeUtils.isCompatible(tmp, calledMethodArgs)) {
                                            callAction(false, calledMethodArgs);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterCallMethod(int opcode,
                        String owner, String name, String desc) {
                        if (where == Where.AFTER &&
                            matches(className, owner.replace('/', '.')) &&
                            matches(methodName, name) &&
                            typeMatches(loc.getType(), desc)) {
                            Type rt = getReturnType();
                            switch (numActionArgs) {
                                case 0:
                                    invokeBTraceAction(this, om);
                                break;
                                case 1:
                                    if (!rt.equals(Type.VOID_TYPE) &&
                                        TypeUtils.isCompatible(actionArgTypes[0], rt)) {
                                        dupValue(rt);
                                        invokeBTraceAction(this, om);
                                    }
                                break;
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
                    private void callAction(boolean isStatic) {
                        if (! isStatic) {
                            loadThis();
                        }
                        loadArguments();
                        invokeBTraceAction(this, om);
                    }

                    @Override
                    protected void onMethodEntry() {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else {
                            boolean isStatic = ((getAccess() & ACC_STATIC) != 0);
                            if (numActionArgs == 1 &&
                                TypeUtils.isAnyTypeArray(actionArgTypes[0])) {
                                loadArgumentArray();
                                invokeBTraceAction(this, om);
                                return;
                            }

                            if (isStatic) {
                                Type[] probedMethodArgs = getArgumentTypes();       
                                if (TypeUtils.isCompatible(actionArgTypes, probedMethodArgs)) {
                                    callAction(true);
                                }
                            } else {
                                Type[] probedMethodArgs = getArgumentTypes();
                                if (probedMethodArgs.length + 1 == numActionArgs) {
                                    Type[] tmp = new Type[numActionArgs - 1];
                                    System.arraycopy(actionArgTypes, 1, tmp, 0, tmp.length);
                                    if ((TypeUtils.isObject(actionArgTypes[0]) ||
                                        TypeUtils.isCompatible(actionArgTypes[0], Type.getObjectType(className))) &&
                                        TypeUtils.isCompatible(tmp, probedMethodArgs)) {
                                        callAction(false);
                                    }
                                }
                            }
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
                    @Override
                    protected void onMethodReturn(int opcode) {
                        switch (numActionArgs) {
                            case 0:
                                invokeBTraceAction(this, om);
                            break;
                            case 1: {
                                Type rt = getReturnType();
                                if (!rt.equals(Type.VOID_TYPE) &&
                                    TypeUtils.isCompatible(actionArgTypes[0], getReturnType())) {
                                    dupReturnValue(opcode);
                                    invokeBTraceAction(this, om);
                                }
                            }
                            break;
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
