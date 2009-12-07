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

import com.sun.btrace.AnyType;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;
import com.sun.btrace.org.objectweb.asm.Type;

class TypeUtils {
    private TypeUtils() {}

    public static final Type objectType =
        Type.getType(Object.class);
    public static final Type stringType =
        Type.getType(String.class);
    public static final Type throwableType =
        Type.getType(Throwable.class);
    public static final Type objectArrayType =
        Type.getType(Object[].class);
    public static final Type anyType =
        Type.getType(AnyType.class);
    public static final Type anyTypeArray =
        Type.getType(AnyType[].class);

    public static boolean isAnyType(Type t) {
        return t.equals(anyType);
    }

    public static boolean isAnyTypeArray(Type t) {
        return t.equals(anyTypeArray);
    }

    public static boolean isObject(Type t) {
        return t.equals(objectType);
    }

    public static boolean isObjectOrAnyType(Type t) {
        return isObject(t) || isAnyType(t);
    }

    public static boolean isString(Type t) {
        return t.equals(stringType);
    }

    public static boolean isThrowable(Type t) {
        return t.equals(throwableType);
    }

    public static boolean isCompatible(Type left, Type right) {
        if (left.equals(right)) {
            return true;
        } else if(isAnyType(left)) {
            int sort2 = right.getSort();
            return (sort2 == Type.OBJECT || sort2 == Type.ARRAY);               
        } else {
            try {
                // those classes should already have been loaded at this point
                Class clzLeft = Class.forName(left.getClassName());
                Class clzRight = Class.forName(right.getClassName());
                return (clzLeft.isAssignableFrom(clzRight));
            } catch (Exception e) {}
            return false;
        }
    }

    public static boolean isCompatible(Type[] args1, Type[] args2) {
        if (args1.length != args2.length) {
            return false;
        }
        for (int i = 0; i < args1.length; i++) {
            if (! args1[i].equals(args2[i])) {
                int sort2 = args2[i].getSort();
                /*
                 * if destination is AnyType and right side is
                 * Object or Array (i.e., any reference type)
                 * then we allow it - because AnyType is mapped to
                 * java.lang.Object.
                 */
                if (isAnyType(args1[i]) && 
                   (sort2 == Type.OBJECT || sort2 == Type.ARRAY)) {
                    continue;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public static Type getArrayType(int arrayOpcode) {
        switch (arrayOpcode) {
            case IALOAD: 
            case IASTORE: 
                return Type.getType("[I");

            case BALOAD:
            case BASTORE:
                return Type.getType("[B");

            case AALOAD: 
            case AASTORE:
                return objectArrayType;

            case CALOAD:
            case CASTORE: 
                return Type.getType("[C");

            case FALOAD:
            case FASTORE: 
                return Type.getType("[F");

            case SALOAD:
            case SASTORE: 
                return Type.getType("[S");

            case LALOAD: 
            case LASTORE:
                return Type.getType("[J");

            case DALOAD:
            case DASTORE:
                return Type.getType("[D");

            default:    
                throw new RuntimeException("invalid array opcode");
        }
    }

    public static Type getElementType(int arrayOpcode) {
        switch (arrayOpcode) {
            case IALOAD: 
            case IASTORE: 
                return Type.INT_TYPE;

            case BALOAD:
            case BASTORE:
                return Type.BYTE_TYPE;

            case AALOAD: 
            case AASTORE:
                return objectType;

            case CALOAD:
            case CASTORE: 
                return Type.CHAR_TYPE;

            case FALOAD:
            case FASTORE: 
                return Type.FLOAT_TYPE;

            case SALOAD:
            case SASTORE: 
                return Type.SHORT_TYPE;

            case LALOAD: 
            case LASTORE:
                return Type.LONG_TYPE;

            case DALOAD:
            case DASTORE:
                return Type.DOUBLE_TYPE;

            default:    
                throw new RuntimeException("invalid array opcode");
        }
    }

    private static final Map<String, String> primitives;

    static {
        primitives = new HashMap<String, String>();
        primitives.put("void", "V");
        primitives.put("byte", "B");
        primitives.put("char", "C");
        primitives.put("double", "D");
        primitives.put("float", "F");
        primitives.put("int", "I");
        primitives.put("long", "J");
        primitives.put("short", "S");
        primitives.put("boolean", "Z");
    }

    public static String declarationToDescriptor(String decl) {
        int leftParen = decl.indexOf('(');
        int rightParen = decl.indexOf(')');
        if (leftParen == -1 || rightParen == -1) {
            throw new IllegalArgumentException();
        }

        StringBuilder buf = new StringBuilder();
        String descriptor;

        buf.append('(');
        String args = decl.substring(leftParen + 1, rightParen);
        StringTokenizer st = new StringTokenizer(args, ",");
        while (st.hasMoreTokens()) {
            String arg = st.nextToken().trim();
            descriptor = primitives.get(arg);
            if (arg.length() == 0) {
                throw new IllegalArgumentException();
            }
            if (descriptor == null) {
                descriptor = objectOrArrayType(arg);
            }
            buf.append(descriptor);
        }
        buf.append(')');

        String returnType = decl.substring(0, leftParen).trim();
        descriptor = primitives.get(returnType);
        if (returnType.length() == 0) {
            throw new IllegalArgumentException();
        }
        if (descriptor == null) {
            descriptor = objectOrArrayType(returnType);
        }
        buf.append(descriptor);
        return buf.toString();
    }    

    public static String getJavaType(String desc) {
        int arrIndex = desc.lastIndexOf("[") + 1;
        desc = desc.substring(arrIndex);
        if (desc.startsWith("L")) {
            desc = desc.substring(1, desc.length() - 1).replace('/', '.');
        } else {
            for(Map.Entry<String, String> entry : primitives.entrySet()) {
                if (entry.getValue().equals(desc)) {
                    desc = entry.getKey();
                    break;
                }
            }
        }
        StringBuilder sb = new StringBuilder(desc);
        for(int i=0;i<arrIndex;i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    public static String objectOrArrayType(String type) {
        StringBuilder buf = new StringBuilder();
        int index = 0;
        while ((index = type.indexOf("[]", index) + 1) > 0) {
            buf.append('[');
        }
        String t = type.substring(0, type.length() - buf.length() * 2);
        String desc = primitives.get(t);
        if (desc != null) {
            buf.append(desc);
        } else {
            buf.append('L');
            if (t.indexOf('.') < 0) {
                buf.append(t);
            } else {
                buf.append(t.replace('.', '/'));
            }
            buf.append(';');
        }
        return buf.toString();
    }    
}
