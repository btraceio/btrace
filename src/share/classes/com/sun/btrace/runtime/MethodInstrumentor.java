/*
 * Copyright (c) 2008-2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.CONSTRUCTOR;
import com.sun.btrace.util.LocalVariableHelper;

/**
 * Base class for all out method instrumenting classes.
 *
 * @author A. Sundararajan
 */
public class MethodInstrumentor extends MethodVisitor implements LocalVariableHelper {
    public static final String JAVA_LANG_THREAD_LOCAL =
        Type.getInternalName(ThreadLocal.class);
    public static final String JAVA_LANG_THREAD_LOCAL_GET = "get";
    public static final String JAVA_LANG_THREAD_LOCAL_GET_DESC = "()Ljava/lang/Object;";
    public static final String JAVA_LANG_THREAD_LOCAL_SET = "set";
    public static final String JAVA_LANG_THREAD_LOCAL_SET_DESC = "(Ljava/lang/Object;)V";

    public static final String JAVA_LANG_STRING =
        Type.getInternalName(String.class);
    public static final String JAVA_LANG_STRING_DESC =
        Type.getDescriptor(String.class);

    public static final String JAVA_LANG_NUMBER =
        Type.getInternalName(Number.class);
    public static final String JAVA_LANG_BOOLEAN =
        Type.getInternalName(Boolean.class);
    public static final String JAVA_LANG_CHARACTER =
        Type.getInternalName(Character.class);
    public static final String JAVA_LANG_BYTE =
        Type.getInternalName(Byte.class);
    public static final String JAVA_LANG_SHORT =
        Type.getInternalName(Short.class);
    public static final String JAVA_LANG_INTEGER =
        Type.getInternalName(Integer.class);
    public static final String JAVA_LANG_LONG =
        Type.getInternalName(Long.class);
    public static final String JAVA_LANG_FLOAT =
        Type.getInternalName(Float.class);
    public static final String JAVA_LANG_DOUBLE =
        Type.getInternalName(Double.class);

    public static final String BOX_VALUEOF = "valueOf";
    public static final String BOX_BOOLEAN_DESC = "(Z)Ljava/lang/Boolean;";
    public static final String BOX_CHARACTER_DESC = "(C)Ljava/lang/Character;";
    public static final String BOX_BYTE_DESC = "(B)Ljava/lang/Byte;";
    public static final String BOX_SHORT_DESC = "(S)Ljava/lang/Short;";
    public static final String BOX_INTEGER_DESC = "(I)Ljava/lang/Integer;";
    public static final String BOX_LONG_DESC = "(J)Ljava/lang/Long;";
    public static final String BOX_FLOAT_DESC = "(F)Ljava/lang/Float;";
    public static final String BOX_DOUBLE_DESC = "(D)Ljava/lang/Double;";

    public static final String BOOLEAN_VALUE = "booleanValue";
    public static final String CHAR_VALUE = "charValue";
    public static final String BYTE_VALUE = "byteValue";
    public static final String SHORT_VALUE = "shortValue";
    public static final String INT_VALUE = "intValue";
    public static final String LONG_VALUE = "longValue";
    public static final String FLOAT_VALUE = "floatValue";
    public static final String DOUBLE_VALUE = "doubleValue";

    public static final String BOOLEAN_VALUE_DESC= "()Z";
    public static final String CHAR_VALUE_DESC= "()C";
    public static final String BYTE_VALUE_DESC= "()B";
    public static final String SHORT_VALUE_DESC= "()S";
    public static final String INT_VALUE_DESC= "()I";
    public static final String LONG_VALUE_DESC= "()J";
    public static final String FLOAT_VALUE_DESC= "()F";
    public static final String DOUBLE_VALUE_DESC= "()D";

    final protected static class ValidationResult {
        final static private int[] EMPTY_ARRAY = new int[0];
        final private boolean isValid;
        final private int[] argsIndex;

        public ValidationResult(boolean valid, int[] argsIndex) {
            this.isValid = valid;
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
            return argsIndex.length == 0;
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
            final ValidationResult other = (ValidationResult) obj;
            if (this.isValid != other.isValid) {
                return false;
            }
            if (!Arrays.equals(this.argsIndex, other.argsIndex)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + (this.isValid ? 1 : 0);
            hash = 59 * hash + Arrays.hashCode(this.argsIndex);
            return hash;
        }
    }

    final private static ValidationResult INVALID = new ValidationResult(false);
    final private static ValidationResult ANY = new ValidationResult(true);

    protected abstract class ArgumentProvider {
        private int index;

        public ArgumentProvider(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        final public void provide() {
            if (index > -1) {
                doProvide();
            }
        }

        abstract protected void doProvide();
    }

    protected class LocalVarArgProvider extends ArgumentProvider {
        private Type type;
        private int ptr;

        public LocalVarArgProvider(int index, Type type, int ptr) {
            super(index);
            this.type = type;
            this.ptr = ptr;
        }

        public void doProvide() {
            loadLocal(type, ptr);
        }

        @Override
        public String toString() {
            return "LocalVar #" + ptr + " of type " + type + " (@" + getIndex() + ")";
        }

    }

    protected class ConstantArgProvider extends ArgumentProvider {
        private Object constant;

        public ConstantArgProvider(int index, Object constant) {
            super(index);
            this.constant = constant;
        }

        public void doProvide() {
            visitLdcInsn(constant);
        }

        @Override
        public String toString() {
            return "Constant " + constant + " (@" + getIndex() + ")";
        }
    }

    protected class AnyTypeArgProvider extends ArgumentProvider {
        private int argPtr;
        private Type[] myArgTypes;
        public AnyTypeArgProvider(int index, int basePtr) {
            this(index, basePtr, argumentTypes);
        }

        public AnyTypeArgProvider(int index, int basePtr, Type[] argTypes) {
            super(index);
            this.argPtr = basePtr;
            this.myArgTypes = argTypes;
        }


        public void doProvide() {
            push(myArgTypes.length);
            visitTypeInsn(ANEWARRAY, TypeUtils.objectType.getInternalName());
            for (int j = 0; j < myArgTypes.length; j++) {
                dup();
                push(j);
                Type argType = myArgTypes[j];
                loadLocal(argType, argPtr);
                box(argType);
                arrayStore(TypeUtils.objectType);
                argPtr += argType.getSize();
            }
        }

    }

    private final int access;
    private final String parentClz;
    private final String superClz;
    private final String name;
    private final String desc;
    private Type returnType;
    private Type[] argumentTypes;
    private Map<Integer, Type> extraTypes;
    private Label skipLabel;

    private final LocalVariableHelper lvt;

    public MethodInstrumentor(
        LocalVariableHelper lvt, String parentClz,
        String superClz, int access, String name, String desc
    ) {
        super(ASM4, (MethodVisitor)lvt);
        this.parentClz = parentClz;
        this.superClz = superClz;
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.returnType = Type.getReturnType(desc);
        this.argumentTypes = Type.getArgumentTypes(desc);
        extraTypes = new HashMap<Integer, Type>();
        this.lvt = lvt;
    }

    public int getAccess() {
        return access;
    }

    public final String getName() {
        return getName(false);
    }

    public final String getName(boolean fqn) {
        return (fqn ? parentClz + "." : "") + name + (fqn ? desc : "");
    }

    public int storeNewLocal(Type type) {
        return lvt.storeNewLocal(type);
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

    protected void loadArguments(ArgumentProvider ... argumentProviders) {
        Arrays.sort(argumentProviders, new Comparator<ArgumentProvider>() {
            public int compare(ArgumentProvider o1, ArgumentProvider o2) {
                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 != null && o2 == null) return -1;
                if (o2 != null && o1 == null) return 1;

                if (o1.getIndex() == o2.getIndex()) {
                    return 0;
                }
                if (o1.getIndex() < o2.getIndex()) {
                    return -1;
                }
                return 1;
            }
        });

        for(ArgumentProvider provider : argumentProviders) {
            if (provider != null) provider.provide();
        }
    }

    public void loadThis() {
        if ((access & ACC_STATIC) != 0) {
            throw new IllegalStateException("no 'this' inside static method");
        }
        super.visitVarInsn(ALOAD, 0);
    }

    public int[] backupStack(Type[] methodArgTypes, boolean isStatic) {
        int[] backupArgsIndexes = new int[methodArgTypes.length + 1];
        int upper = methodArgTypes.length - 1;

            for (int i = 0; i < methodArgTypes.length; i++) {
                int index = lvt.storeNewLocal(methodArgTypes[upper - i]);
                backupArgsIndexes[upper -  i + 1] = index;
            }

        if (!isStatic) {
            int index = lvt.storeNewLocal(TypeUtils.objectType); // store *callee*
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
            loadLocal(TypeUtils.objectType, backupArgsIndexes[0]);
        }

            for (int i = methodArgTypes.length - 1; i > -1; i--) {
                loadLocal(methodArgTypes[upper - i], backupArgsIndexes[upper - i + 1]);
            }
        }

    protected final boolean isStatic() {
        return (getAccess() & ACC_STATIC) != 0;
    }

    protected final boolean isConstructor() {
        return CONSTRUCTOR.equals(name);
    }

    public void returnValue() {
        super.visitInsn(returnType.getOpcode(IRETURN));
    }

    public void push(int value) {
        if (value >= -1 && value <= 5) {
            super.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            super.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            super.visitIntInsn(SIPUSH, value);
        } else {
            super.visitLdcInsn(Integer.valueOf(value));
        }
    }

    public void arrayLoad(Type type) {
        super.visitInsn(type.getOpcode(IALOAD));
    }

    public void arrayStore(Type type) {
        super.visitInsn(type.getOpcode(IASTORE));
    }

    public void loadLocal(Type type, int index) {
        super.visitVarInsn(type.getOpcode(ILOAD), index);
    }

    public void storeLocal(Type type, int index) {
        super.visitVarInsn(type.getOpcode(ISTORE), index);
    }

    public void pop() {
        super.visitInsn(POP);
    }

    public void dup() {
        super.visitInsn(DUP);
    }

    public void dup2() {
        super.visitInsn(DUP2);
    }

    public void dupArrayValue(int arrayOpcode) {
        switch (arrayOpcode) {
            case IALOAD: case FALOAD:
            case AALOAD: case BALOAD:
            case CALOAD: case SALOAD:
            case IASTORE: case FASTORE:
            case AASTORE: case BASTORE:
            case CASTORE: case SASTORE:
                dup();
            break;

            case LALOAD: case DALOAD:
            case LASTORE: case DASTORE:
                dup2();
            break;
        }
    }

    public void dupReturnValue(int returnOpcode) {
        switch (returnOpcode) {
            case IRETURN:
            case FRETURN:
            case ARETURN:
                super.visitInsn(DUP);
                return;
            case LRETURN:
            case DRETURN:
                super.visitInsn(DUP2);
                return;
            case RETURN:
                return;
            default:
                throw new IllegalArgumentException("not return");
        }
    }

    public void dupValue(Type type) {
        switch (type.getSize()) {
            case 1:
                dup();
            break;
            case 2:
                dup2();
            break;
        }
    }

    public void dupValue(String desc) {
        int typeCode = desc.charAt(0);
        switch (typeCode) {
            case '[':
            case 'L':
            case 'Z':
            case 'C':
            case 'B':
            case 'S':
            case 'I':
                super.visitInsn(DUP);
                break;
            case 'J':
            case 'D':
                super.visitInsn(DUP2);
                break;
            default:
                throw new RuntimeException("invalid signature");
        }
    }

    public void box(Type type) {
        box(type.getDescriptor());
    }

    public void box(String desc) {
        int typeCode = desc.charAt(0);
        switch (typeCode) {
            case '[':
            case 'L':
                break;
            case 'Z':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BOOLEAN,
                                BOX_VALUEOF,
                                BOX_BOOLEAN_DESC);
                break;
            case 'C':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_CHARACTER,
                                BOX_VALUEOF,
                                BOX_CHARACTER_DESC);
                break;
            case 'B':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BYTE,
                                BOX_VALUEOF,
                                BOX_BYTE_DESC);
                break;
            case 'S':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_SHORT,
                                BOX_VALUEOF,
                                BOX_SHORT_DESC);
                break;
            case 'I':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER,
                                BOX_VALUEOF,
                                BOX_INTEGER_DESC);
                break;
            case 'J':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_LONG,
                                BOX_VALUEOF,
                                BOX_LONG_DESC);
                break;
            case 'F':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_FLOAT,
                                BOX_VALUEOF,
                                BOX_FLOAT_DESC);
                break;
            case 'D':
                super.visitMethodInsn(INVOKESTATIC, JAVA_LANG_DOUBLE,
                                BOX_VALUEOF,
                                BOX_DOUBLE_DESC);
                break;
        }
    }

    public void unbox(Type type) {
        unbox(type.getDescriptor());
    }

    public void unbox(String desc) {
        int typeCode = desc.charAt(0);
        switch (typeCode) {
            case '[':
            case 'L':
                super.visitTypeInsn(CHECKCAST, Type.getType(desc).getInternalName());
                break;
            case 'Z':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_BOOLEAN);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_BOOLEAN,
                                BOOLEAN_VALUE,
                                BOOLEAN_VALUE_DESC);
                break;
            case 'C':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_CHARACTER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_CHARACTER,
                                CHAR_VALUE,
                                CHAR_VALUE_DESC);
                break;
            case 'B':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_NUMBER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_NUMBER,
                                BYTE_VALUE,
                                BYTE_VALUE_DESC);
                break;
            case 'S':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_NUMBER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_NUMBER,
                                SHORT_VALUE,
                                SHORT_VALUE_DESC);
                break;
            case 'I':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_NUMBER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_NUMBER,
                                INT_VALUE,
                                INT_VALUE_DESC);
                break;
            case 'J':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_NUMBER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_NUMBER,
                                LONG_VALUE,
                                LONG_VALUE_DESC);
                break;
            case 'F':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_NUMBER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_NUMBER,
                                FLOAT_VALUE,
                                FLOAT_VALUE_DESC);
                break;
            case 'D':
                super.visitTypeInsn(CHECKCAST, JAVA_LANG_NUMBER);
                super.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_NUMBER,
                                DOUBLE_VALUE,
                                DOUBLE_VALUE_DESC);
                break;
        }
    }

    public void defaultValue(String desc) {
        int typeCode = desc.charAt(0);
        switch (typeCode) {
            case '[':
            case 'L':
                super.visitInsn(ACONST_NULL);
                break;
            case 'Z':
            case 'C':
            case 'B':
            case 'S':
            case 'I':
                super.visitInsn(ICONST_0);
                break;
            case 'J':
                super.visitInsn(LCONST_0);
                break;
            case 'F':
                super.visitInsn(FCONST_0);
                break;
            case 'D':
                super.visitInsn(DCONST_0);
                break;
        }
    }

    public void println(String msg) {
        super.visitFieldInsn(GETSTATIC,
                    "java/lang/System",
                    "out",
                    "Ljava/io/PrintStream;");
        super.visitLdcInsn(msg);
        super.visitMethodInsn(INVOKEVIRTUAL,
                    "java/io/PrintStream",
                    "println",
                    "(Ljava/lang/String;)V");
    }

    // print the object on the top of the stack
    public void printObject() {
        super.visitFieldInsn(GETSTATIC,
                    "java/lang/System",
                    "out",
                    "Ljava/io/PrintStream;");
        super.visitInsn(SWAP);
        super.visitMethodInsn(INVOKEVIRTUAL,
                    "java/io/PrintStream",
                    "println",
                    "(Ljava/lang/Object;)V");
    }

    public void invokeVirtual(String owner, String method, String desc) {
        super.visitMethodInsn(INVOKEVIRTUAL, owner, method, desc);
    }

    public void invokeSpecial(String owner, String method, String desc) {
        super.visitMethodInsn(INVOKESPECIAL, owner, method, desc);
    }

    public void invokeStatic(String owner, String method, String desc) {
        super.visitMethodInsn(INVOKESTATIC, owner, method, desc);
    }

    protected String getParentClz() {
        return parentClz;
    }

    protected String getSuperClz() {
        return superClz;
    }

    protected ValidationResult validateArguments(OnMethod om, boolean staticFlag, Type[] actionArgTypes, Type[] methodArgTypes) {
        int specialArgsCount = 0;

        if (om.getSelfParameter() != -1) {
            if (staticFlag) {
                return INVALID;
            }
            Type selfType = extraTypes.get(om.getSelfParameter());
            if (selfType == null) {
                if (!TypeUtils.isObject(actionArgTypes[om.getSelfParameter()])) {
                    System.err.println("Invalid @Self parameter. @Self parameter is not java.lang.Object. Expected " + TypeUtils.objectType + ", Received " + actionArgTypes[om.getSelfParameter()]);
                    return INVALID;
                }
            } else {
                if (!TypeUtils.isCompatible(actionArgTypes[om.getSelfParameter()], selfType)) {
                    System.err.println("Invalid @Self parameter. @Self parameter is not compatible. Expected " + selfType + ", Received " + actionArgTypes[om.getSelfParameter()]);
                    return INVALID;
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
                    System.err.println("Invalid @Return parameter. @Return parameter is not java.lang.Object. Expected " + TypeUtils.objectType + ", Received " + actionArgTypes[om.getReturnParameter()]);
                    return INVALID;
                }
            } else {
                if (!TypeUtils.isCompatible(actionArgTypes[om.getReturnParameter()], type)) {
                    System.err.println("Invalid @Return parameter. Expected '" + returnType + ", received " + actionArgTypes[om.getReturnParameter()]);
                    return INVALID;
                }
            }
            specialArgsCount++;
        }
        if (om.getTargetMethodOrFieldParameter() != -1) {
            if (!(TypeUtils.isCompatible(actionArgTypes[om.getTargetMethodOrFieldParameter()], Type.getType(String.class)))) {
                System.err.println("Invalid @CalledMethod parameter. Expected " + Type.getType(String.class) + ", received " + actionArgTypes[om.getTargetMethodOrFieldParameter()]);
                return INVALID;
            }
            specialArgsCount++;
        }
        if (om.getTargetInstanceParameter() != -1) {
            Type calledType = extraTypes.get(om.getTargetInstanceParameter());
            if (calledType == null) {
                if (!TypeUtils.isObject(actionArgTypes[om.getTargetInstanceParameter()])) {
                    System.err.println("Invalid @CalledInstance parameter. @CalledInstance parameter is not java.lang.Object. Expected " + TypeUtils.objectType + ", Received " + actionArgTypes[om.getTargetInstanceParameter()]);
                    return INVALID;
                }
            } else {
                if (!TypeUtils.isCompatible(actionArgTypes[om.getTargetInstanceParameter()], calledType)) {
                    System.err.println("Invalid @CalledInstance parameter. Expected " + Type.getType(Object.class) + ", received " + actionArgTypes[om.getTargetInstanceParameter()]);
                    return INVALID;
                }
            }
            specialArgsCount++;
        }
        if (om.getDurationParameter() != -1) {
            if (actionArgTypes[om.getDurationParameter()] != Type.LONG_TYPE) {
                return INVALID;
            }
            specialArgsCount++;
        }
        if (om.getClassNameParameter() != -1) {
            if (!(TypeUtils.isCompatible(actionArgTypes[om.getClassNameParameter()], Type.getType(String.class)))) {
                return INVALID;
            }
            specialArgsCount++;
        }
        if (om.getMethodParameter() != -1) {
            if (!(TypeUtils.isCompatible(actionArgTypes[om.getMethodParameter()], Type.getType(String.class)))) {
                return INVALID;
            }
            specialArgsCount++;
        }

        Type[] cleansedArgArray = new Type[actionArgTypes.length - specialArgsCount];
        int[] cleansedArgIndex = new int[cleansedArgArray.length];

        int counter = 0;
        for (int argIndex = 0; argIndex < actionArgTypes.length; argIndex++) {
            if (argIndex != om.getSelfParameter() &&
                    argIndex != om.getClassNameParameter() &&
                    argIndex != om.getMethodParameter() &&
                    argIndex != om.getReturnParameter() &&
                    argIndex != om.getTargetInstanceParameter() &&
                    argIndex != om.getTargetMethodOrFieldParameter() &&
                    argIndex != om.getDurationParameter()) {
                cleansedArgArray[counter] = actionArgTypes[argIndex];
                cleansedArgIndex[counter] = argIndex;
                counter++;
            }
        }
        if (cleansedArgArray.length == 0) {
            return ANY;
        } else {
            if (cleansedArgArray.length > 0) {
                if (!TypeUtils.isAnyTypeArray(cleansedArgArray[0]) &&
                    !TypeUtils.isCompatible(cleansedArgArray, methodArgTypes)) {
                    return INVALID;
                }
            }
        }
        return new ValidationResult(true, cleansedArgIndex);
    }
}
