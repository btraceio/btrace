/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.util;

import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.org.objectweb.asm.TypePath;

/**
 * This class is heavily based on the original <b>org.objectweb.asm.commons.LocalVariablesSorter</b> class.
 * However, the original class does not behave correctly when one attempts to use
 * the newly introduced local variables - the index is re-remapped a few times.
 * Due to this multiple re-mapping the local variable indeces get messed up.
 * <p>
 * This class is modified to return a negative value when creating a new local
 * variable. This is later used to recognize the indices not to remap.
 * Also, when creating a new local variable the top stack value is automatically
 * stored there.
 *
 * @author Chris Nokleberg
 * @author Eugene Kuleshov
 * @author Eric Bruneton
 * @author Jaroslav Bachorik
 */
public class LocalVariableHelperImpl extends MethodVisitor implements LocalVariableHelper {
        private static final Type OBJECT_TYPE = Type
            .getObjectType("java/lang/Object");

    /**
     * Mapping from old to new local variable indexes. A local variable at index
     * i of size 1 is remapped to 'mapping[2*i]', while a local variable at
     * index i of size 2 is remapped to 'mapping[2*i+1]'.
     */
    private int[] mapping = new int[40];

    /**
     * Array used to store stack map local variable types after remapping.
     */
    private Object[] newLocals = new Object[20];

    /**
     * Index of the first local variable, after formal parameters.
     */
    protected final int firstLocal;

    /**
     * Index of the next local variable to be created by {@link #storeNewLocal}.
     */
    protected int nextLocal;

    /**
     * Indicates if at least one local variable has moved due to remapping.
     */
    private boolean changed;

    /**
     * Creates a new {@link LocalVariablesSorter}. <i>Subclasses must not use
     * this constructor</i>. Instead, they must use the
     * {@link #LocalVariablesSorter(int, int, String, MethodVisitor)} version.
     *
     * @param access
     *            access flags of the adapted method.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     * @param mv
     *            the method visitor to which this adapter delegates calls.
     * @throws IllegalStateException
     *             If a subclass calls this constructor.
     */
    public LocalVariableHelperImpl(final MethodVisitor mv, final int access, final String desc) {
        this(access, desc, mv);
        if (getClass() != LocalVariableHelperImpl.class) {
            throw new IllegalStateException();
        }
    }

    /**
     * Creates a new {@link LocalVariablesSorter}.
     * @param access
     *            access flags of the adapted method.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     * @param mv
     *            the method visitor to which this adapter delegates calls.
     */
    protected LocalVariableHelperImpl(final int access,
            final String desc, final MethodVisitor mv) {
        super(Opcodes.ASM5, mv);
        Type[] args = Type.getArgumentTypes(desc);
        nextLocal = (Opcodes.ACC_STATIC & access) == 0 ? 1 : 0;
        for (int i = 0; i < args.length; i++) {
            nextLocal += args[i].getSize();
        }
        firstLocal = nextLocal;
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        Type type;
        switch (opcode) {
        case Opcodes.LLOAD:
        case Opcodes.LSTORE:
            type = Type.LONG_TYPE;
            break;

        case Opcodes.DLOAD:
        case Opcodes.DSTORE:
            type = Type.DOUBLE_TYPE;
            break;

        case Opcodes.FLOAD:
        case Opcodes.FSTORE:
            type = Type.FLOAT_TYPE;
            break;

        case Opcodes.ILOAD:
        case Opcodes.ISTORE:
            type = Type.INT_TYPE;
            break;

        default:
            // case Opcodes.ALOAD:
            // case Opcodes.ASTORE:
            // case RET:
            type = OBJECT_TYPE;
            break;
        }
        mv.visitVarInsn(opcode, var >= 0 ? remap(var, type) : (-var));
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        mv.visitIincInsn(remap(var, Type.INT_TYPE), increment);
    }

    @Override
    public void visitMaxs(final int maxStack, final int maxLocals) {
        mv.visitMaxs(maxStack, nextLocal);
    }

    @Override
    public void visitLocalVariable(final String name, final String desc,
            final String signature, final Label start, final Label end,
            final int index) {
        int newIndex = remap(index, Type.getType(desc));
        mv.visitLocalVariable(name, desc, signature, start, end, newIndex);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
            TypePath typePath, Label[] start, Label[] end, int[] index,
            String desc, boolean visible) {
        Type t = Type.getType(desc);
        int[] newIndex = new int[index.length];
        for (int i = 0; i < newIndex.length; ++i) {
            newIndex[i] = remap(index[i], t);
        }
        return mv.visitLocalVariableAnnotation(typeRef, typePath, start, end,
                newIndex, desc, visible);
    }

    @Override
    public void visitFrame(final int type, final int nLocal,
            final Object[] local, final int nStack, final Object[] stack) {
        if (type != Opcodes.F_NEW) { // uncompressed frame
            throw new IllegalStateException(
                    "ClassReader.accept() should be called with EXPAND_FRAMES flag");
        }

        if (!changed) { // optimization for the case where mapping = identity
            mv.visitFrame(type, nLocal, local, nStack, stack);
            return;
        }

        // creates a copy of newLocals
        Object[] oldLocals = new Object[newLocals.length];
        System.arraycopy(newLocals, 0, oldLocals, 0, oldLocals.length);

        // copies types from 'local' to 'newLocals'
        // 'newLocals' already contains the variables added with 'newLocal'

        int index = 0; // old local variable index
        int number = 0; // old local variable number
        for (; number < nLocal; ++number) {
            Object t = local[number];
            int size = t == Opcodes.LONG || t == Opcodes.DOUBLE ? 2 : 1;
            if (t != Opcodes.TOP) {
                Type typ = OBJECT_TYPE;
                if (t == Opcodes.INTEGER) {
                    typ = Type.INT_TYPE;
                } else if (t == Opcodes.FLOAT) {
                    typ = Type.FLOAT_TYPE;
                } else if (t == Opcodes.LONG) {
                    typ = Type.LONG_TYPE;
                } else if (t == Opcodes.DOUBLE) {
                    typ = Type.DOUBLE_TYPE;
                } else if (t instanceof String) {
                    typ = Type.getObjectType((String) t);
                }
                setFrameLocal(remap(index, typ), t);
            }
            index += size;
        }

        // removes TOP after long and double types as well as trailing TOPs

        index = 0;
        number = 0;
        for (int i = 0; index < newLocals.length; ++i) {
            Object t = newLocals[index++];
            if (t != null && t != Opcodes.TOP) {
                newLocals[i] = t;
                number = i + 1;
                if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
                    index += 1;
                }
            } else {
                newLocals[i] = Opcodes.TOP;
            }
        }

        // visits remapped frame
        mv.visitFrame(type, number, newLocals, nStack, stack);

        // restores original value of 'newLocals'
        newLocals = oldLocals;
    }

    // -------------

    /**
     * Creates a new local variable of the given type and
     * stores the top stack value there
     *
     * @param type
     *            the type of the local variable to be created.
     * @return the identifier of the newly created local variable.
     *         it is a negative number -> Math.abs(ident) = the real var index
     */
        @Override
    public int storeNewLocal(final Type type) {
        Object t;
        switch (type.getSort()) {
        case Type.BOOLEAN:
        case Type.CHAR:
        case Type.BYTE:
        case Type.SHORT:
        case Type.INT:
            t = Opcodes.INTEGER;
            break;
        case Type.FLOAT:
            t = Opcodes.FLOAT;
            break;
        case Type.LONG:
            t = Opcodes.LONG;
            break;
        case Type.DOUBLE:
            t = Opcodes.DOUBLE;
            break;
        case Type.ARRAY:
            t = type.getDescriptor();
            break;
        // case Type.OBJECT:
        default:
            t = type.getInternalName();
            break;
        }
        int local = newLocalMapping(type);
        setFrameLocal(local, t);
        changed = true;
        local = -local;
        visitVarInsn(type.getOpcode(Opcodes.ISTORE), local);
        return local;
    }

    private void setFrameLocal(final int local, final Object type) {
        int l = newLocals.length;
        if (local >= l) {
            Object[] a = new Object[Math.max(2 * l, local + 1)];
            System.arraycopy(newLocals, 0, a, 0, l);
            newLocals = a;
        }
        newLocals[local] = type;
    }

    private int remap(final int var, final Type type) {
        if (var >= 0 && var + type.getSize() <= firstLocal) {
            return var;
        }

        int sVar = Math.abs(var);

        int key = 2 * sVar + type.getSize() - 1;
        int size = mapping.length;
        if (key >= size) {
            int[] newMapping = new int[Math.max(2 * size, key + 1)];
            System.arraycopy(mapping, 0, newMapping, 0, size);
            mapping = newMapping;
        }
        int value = mapping[key];
        if (value == 0) {
            value = newLocalMapping(type);
            mapping[key] = value + 1;
        } else {
            value--;
        }
        if (value != var) {
            changed = true;
        }
        return value;
    }

    protected int newLocalMapping(final Type type) {
        int local = nextLocal;
        nextLocal += type.getSize();
        return local;
    }
}
