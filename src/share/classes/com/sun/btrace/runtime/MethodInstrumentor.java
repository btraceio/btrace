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

import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 * Base class for all out method instrumenting classes.
 *
 * @author A. Sundararajan
 */
public class MethodInstrumentor extends MethodAdapter {
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
    public static final String BOX_CHARACTER_DESC = "(B)Ljava/lang/Character;";
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

    private final int access;
    private final String name;
    private final String desc;
    private Type returnType;
    private Type[] argumentTypes;

    public MethodInstrumentor(MethodVisitor mv, int access, 
        String name, String desc) {
        super(mv);
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.returnType = Type.getReturnType(desc);
        this.argumentTypes = Type.getArgumentTypes(desc);
    }

    public int getAccess() {
        return access;
    }

    public final String getName() {
        return name;
    }

    public final String getDescriptor() {
        return desc;
    }

    public final Type getReturnType() {
        return returnType;
    }

    public final Type[] getArgumentTypes() {
        return argumentTypes;
    }    
    
    public void loadArgument(int arg) {
        loadLocal(argumentTypes[arg], getArgumentIndex(arg));
    }

    public void loadArguments(final int arg, final int count) {
        int index = getArgumentIndex(arg);
        for (int i = 0; i < count; ++i) {
            Type t = argumentTypes[arg + i];
            loadLocal(t, index);
            index += t.getSize();
        }
    }

    public void loadArguments() {
        loadArguments(0, argumentTypes.length);
    }

    public void loadThis() {
        if ((access & ACC_STATIC) != 0) {
            throw new IllegalStateException("no 'this' inside static method");
        }
        super.visitVarInsn(ALOAD, 0);
    }

    public void loadArgumentArray() {
        loadArgumentArray(true);
    }

    public void loadArgumentArray(boolean includeThis) {
        int count = argumentTypes.length;
        boolean isStatic = ((access & ACC_STATIC) != 0);
        if (!isStatic && includeThis) {
            count++;
        }
        push(count);
        super.visitTypeInsn(ANEWARRAY, TypeUtils.objectType.getDescriptor());
        if (!isStatic && includeThis) {
            dup();
            push(0);
            loadThis();
            arrayStore(TypeUtils.objectType);
        }
        int start = isStatic? 0 : 1;
        for (int i = 0; i < argumentTypes.length; i++) {
            dup();
            push(i + start);
            loadArgument(i);
            box(argumentTypes[i]);
            arrayStore(TypeUtils.objectType);
        }
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
            super.visitLdcInsn(new Integer(value));
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

    // Internals only below this point
    private int getArgumentIndex(int arg) {
        int index = (access & ACC_STATIC) == 0 ? 1 : 0;
        for (int i = 0; i < arg; i++) {
            index += argumentTypes[i].getSize();
        }
        return index;
    }
} 
