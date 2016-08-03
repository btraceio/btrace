/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.org.objectweb.asm.Handle;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This extended {@linkplain MethodVisitor} keeps track of the values
 * currently on the stack and their origin. This way it is eg. possible
 * to allow invocation of virtual methods only on instances obtained only
 * through a certain factory method.
 *
 * @author Jaroslav Bachorik
 */
class StackTrackingMethodVisitor extends MethodVisitor {
    abstract public static class StackItem {
        public static enum Kind {
            VARIABLE, CONSTANT, INSTANCE, RESULT
        }

        private Set<StackItem> parents = new HashSet<>();

        public StackItem(StackItem ... parents) {
            this.parents.addAll(Arrays.asList(parents));
        }

        final public Set<StackItem> getParents() {
            return parents;
        }

        final public void merge(StackItem sl) {
            parents.addAll(sl.getParents());
        }

        abstract public Kind getKind();
    }

    final public static class VariableItem extends StackItem {
        private final int var;

        public VariableItem(int var, StackItem... parents) {
            super(parents);
            this.var = var;
        }

        public int getVar() {
            return var;
        }

        @Override
        public Kind getKind() {
            return Kind.VARIABLE;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 31 * hash + this.var;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final VariableItem other = (VariableItem) obj;
            return this.var == other.var;
        }
    }

    public static class ConstantItem extends StackItem {
        private final Object val;

        public ConstantItem(Object val, StackItem ... parents) {
            super(parents);
            this.val = val;
        }

        public Object getValue() {
            return val;
        }

        @Override
        public Kind getKind() {
            return Kind.CONSTANT;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + (this.val != null ? this.val.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ConstantItem other = (ConstantItem) obj;
            return !(this.val != other.val && (this.val == null || !this.val.equals(other.val)));
        }
    }

    public static class InstanceItem extends StackItem {
        private final Type t;

        public InstanceItem(Type t, StackItem ... parents) {
            super(parents);
            this.t = t;
        }

        public Type getType() {
            return t;
        }

        @Override
        public Kind getKind() {
            return Kind.INSTANCE;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + (this.t != null ? this.t.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final InstanceItem other = (InstanceItem) obj;
            return !(this.t != other.t && (this.t == null || !this.t.equals(other.t)));
        }
    }

    public static class ResultItem extends StackItem {
        public static enum Origin {
            FIELD, METHOD
        }

        private final String owner, name, desc;
        private final Origin origin;

        public ResultItem(String owner, String name, String desc, Origin origin, StackItem ... parents) {
            super(parents);
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.origin = origin;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDesc() {
            return desc;
        }

        public Type getType() {
            return Type.getReturnType(desc);
        }

        public Origin getOrigin() {
            return origin;
        }

        @Override
        public Kind getKind() {
            return Kind.RESULT;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + (this.owner != null ? this.owner.hashCode() : 0);
            hash = 89 * hash + (this.name != null ? this.name.hashCode() : 0);
            hash = 89 * hash + (this.desc != null ? this.desc.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ResultItem other = (ResultItem) obj;
            if ((this.owner == null) ? (other.owner != null) : !this.owner.equals(other.owner)) {
                return false;
            }
            if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
                return false;
            }
            return !((this.desc == null) ? (other.desc != null) : !this.desc.equals(other.desc));
        }
    }

    private final class FrameState {
        private Deque<StackItem> stack;
        final private Map<Integer, StackItem> vars;
        final private Map<Integer, StackItem> args;

        public FrameState(Map<Integer, StackItem> args) {
            this(new LinkedList<StackItem>(), new HashMap<Integer, StackItem>(), args);
        }

        private FrameState(Deque<StackItem> s, Map<Integer, StackItem> v, Map<Integer, StackItem> args) {
            this.stack = new LinkedList<>(s);
            this.vars = new HashMap<>(v);
            this.args = new HashMap<>(args);
        }

        public StackItem peek() {
            return stack.peek();
        }

        public void push(StackItem sl) {
            stack.push(sl);
        }

        public StackItem pop() {
            return stack.pop();
        }

        public void store(StackItem sl, int index) {
            vars.put(index, sl);
        }

        public StackItem load(int index) {
            StackItem si = vars.get(index);
            if (si == null) {
                si = args.get(index);
            }
            return si;
        }

        public FrameState duplicate() {
            return new FrameState(stack, vars, args);
        }

        public boolean isEmpty() {
            return stack.isEmpty();
        }

        public void reset() {
            stack.clear();
            vars.clear();
        }
    }

    private final class State {
        private final Map<Label, Set<FrameState>> stateMap = new HashMap<>();

        private FrameState fState;

        public State(InstanceItem receiver, Type[] args) {
            Map<Integer, StackItem> argMap = new HashMap<>();
            int index = 0;
            if (receiver != null) {
                argMap.put(index++, receiver);
            }
            for(Type t : args) {
                argMap.put(index++, new InstanceItem(t));
                if (t == Type.LONG_TYPE || t == Type.DOUBLE_TYPE) {
                    index++;
                }
            }

            this.fState = new FrameState(argMap);
        }

        public void branch(Label l) {
            if (visitedLabels.contains(l)) return; // back loop should preserve the stack

            Set<FrameState> states = stateMap.get(l);
            if (states == null) {
                states = new HashSet<>();
                stateMap.put(l, states);
            }
            states.add(fState.duplicate());
        }

        public void branch(Label l, Type throwable) {
            if (visitedLabels.contains(l)) return; // back loop should preserve the stack

            Set<FrameState> states = stateMap.get(l);
            if (states == null) {
                states = new HashSet<>();
                stateMap.put(l, states);
            }
            FrameState duplicated = fState.duplicate();
            duplicated.push(new InstanceItem(throwable));
            states.add(duplicated);
        }

        public void join(Label l) {
            Set<FrameState> states = stateMap.remove(l);
            if (states != null) {
                if (fState.isEmpty() && !states.isEmpty()) {
                    FrameState s = states.iterator().next();
                    if (!s.isEmpty()) {
                        fState = s;
                    }
                }
                for(FrameState fs : states) {
                    Iterator<StackItem> i1 = fState.stack.iterator();
                    Iterator<StackItem> i2 = fs.stack.iterator();

                    while (i1.hasNext()) {
                        if (i2.hasNext()) {
                            StackItem target = i1.next();
                            StackItem src = i2.next();

                            target.merge(src);
                        } else {
                            throw new IllegalStateException("Error merging simulated stack");
                        }
                    }
                    if (i2.hasNext()) {
                        throw new IllegalStateException("Error merging simulated stack");
                    }
                }
            }
        }

        public StackItem peek() {
            return fState.peek();
        }

        public void push(StackItem sl) {
            fState.push(sl);
        }

        public StackItem pop() {
            return fState.pop();
        }

        public void store(StackItem sl, int index) {
            fState.store(sl, index);
        }

        public StackItem load(int index) {
            return fState.load(index);
        }

        public void reset() {
            fState.reset();
        }
    }

    private final State state;
    private final Map<Label, Collection<Label>> tryCatchStart = new HashMap<>();
    private final Map<Label, Collection<Label>> tryCatchEnd = new HashMap<>();
    private final Set<Label> effectiveHandlers = new HashSet<>();
    private final Collection<Label> handlers = new ArrayList<>();
    private final Set<Label> visitedLabels = new HashSet<>();

    public StackTrackingMethodVisitor(MethodVisitor mv, String className, String desc, boolean isStatic) {
        super(Opcodes.ASM5, mv);
        Type[] args = Type.getArgumentTypes(desc);
        state = new State(isStatic ? null : new InstanceItem(Type.getObjectType(className)), args);
    }

    @Override
    public void visitMultiANewArrayInsn(String string, int i) {
        super.visitMultiANewArrayInsn(string, i);
    }

    @Override
    public void visitLookupSwitchInsn(Label label, int[] ints, Label[] labels) {
        state.pop();
        super.visitLookupSwitchInsn(label, ints, labels);
    }

    @Override
    public void visitTableSwitchInsn(int i, int i1, Label label, Label... labels) {
        state.pop();
        super.visitTableSwitchInsn(i, i1, label, labels);
    }

    @Override
    public void visitLdcInsn(Object o) {
        ConstantItem ci = new ConstantItem(o);
        state.push(ci);
        if (o instanceof Long ||
            o instanceof Double) {
            state.push(ci);
        }
        super.visitLdcInsn(o);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        super.visitJumpInsn(opcode, label);
        switch(opcode) {
            case Opcodes.IFEQ:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IFLT:
            case Opcodes.IFNE:
            case Opcodes.IFNONNULL:
            case Opcodes.IFNULL: {
                state.pop();
                state.branch(label);
                break;
            }
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPNE: {
                state.pop();
                state.pop();
                state.branch(label);
                break;
            }
            case Opcodes.GOTO:
            case Opcodes.JSR: {
                state.branch(label);
                state.reset();
                break;
            }
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String string, String string1, Handle handle, Object... os) {
        super.visitInvokeDynamicInsn(string, string1, handle, os);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itfc) {
        super.visitMethodInsn(opcode, owner, name, desc, itfc);

        List<StackItem> poppedArgs = new ArrayList<>();
        Type[] args = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);

        for(int i=args.length-1;i>=0;i--) {
            if (args[i] != Type.VOID_TYPE) {
                switch(args[i].getSort()) {
                    case Type.LONG:
                    case Type.DOUBLE: {
                        state.pop();
                    }
                    case Type.INT:
                    case Type.FLOAT:
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.SHORT:
                    case Type.BYTE:
                    case Type.ARRAY:
                    case Type.METHOD:
                    case Type.OBJECT: {
                        poppedArgs.add(state.pop());
                        break;
                    }
                }
            }
        }

        if (opcode != Opcodes.INVOKESTATIC) {
            poppedArgs.add(state.pop());
        }

        if (ret != Type.VOID_TYPE) {
            StackItem sl = new ResultItem(owner, name, desc, ResultItem.Origin.METHOD, poppedArgs.toArray(new StackItem[poppedArgs.size()]));
            switch (ret.getSort()) {
                case Type.LONG:
                case Type.DOUBLE: {
                    state.push(sl);
                }
                case Type.INT:
                case Type.FLOAT:
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.SHORT:
                case Type.BYTE:
                case Type.ARRAY:
                case Type.METHOD:
                case Type.OBJECT: {
                    state.push(sl);
                    break;
                }
            }
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        Type t = Type.getType(desc);
        super.visitFieldInsn(opcode, owner, name, desc);

        List<StackItem> parents = new ArrayList<>();
        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            switch (t.getSort()) {
                case Type.LONG:
                case Type.DOUBLE: {
                    state.pop();
                }
                case Type.INT:
                case Type.FLOAT:
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.SHORT:
                case Type.BYTE:
                case Type.ARRAY:
                case Type.METHOD:
                case Type.OBJECT: {
                    parents.add(state.pop());
                    break;
                }
            }
        }
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
            parents.add(state.pop()); // pop 'this'
        }
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            StackItem sl = new ResultItem(owner, name, desc, ResultItem.Origin.FIELD, parents.toArray(new StackItem[parents.size()]));
            switch (t.getSort()) {
                case Type.LONG:
                case Type.DOUBLE: {
                    state.push(sl);
                }
                case Type.INT:
                case Type.FLOAT:
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.SHORT:
                case Type.BYTE:
                case Type.ARRAY:
                case Type.METHOD:
                case Type.OBJECT: {
                    state.push(sl);
                    break;
                }
            }
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type);
        switch (opcode) {
            case Opcodes.NEW: {
                state.push(new InstanceItem(Type.getType(type)));
                break;
            }
            case Opcodes.ANEWARRAY: {
                state.push(new InstanceItem(Type.getType(type), state.pop()));
                break;
            }
            case Opcodes.INSTANCEOF: {
                state.push(new InstanceItem(Type.BOOLEAN_TYPE, state.pop()));
                break;
            }
        }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        super.visitVarInsn(opcode, var);
        switch (opcode) {
            case Opcodes.ILOAD:
            case Opcodes.FLOAD:
            case Opcodes.ALOAD: {
                state.push(state.load(var));
                break;
            }
            case Opcodes.LLOAD:
            case Opcodes.DLOAD: {
                StackItem sl = state.load(var);
                state.push(sl);
                state.push(sl);
                break;
            }
            case Opcodes.ISTORE:
            case Opcodes.FSTORE:
            case Opcodes.ASTORE: {
                StackItem sl = state.pop();
                state.store(sl, var);
                break;
            }
            case Opcodes.LSTORE:
            case Opcodes.DSTORE: {
                StackItem sl = state.pop();
                state.pop();
                state.store(sl, var);
                break;
            }
        }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        super.visitIntInsn(opcode, operand);
        switch (opcode) {
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH: {
                state.push(new ConstantItem(operand));
                break;
            }
            case Opcodes.NEWARRAY: {
                StackItem sl = state.pop(); // size
                state.push(new InstanceItem(OBJECT_TYPE, sl));
                break;
            }
        }
    }

    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode);
        switch (opcode) {
            case Opcodes.ACONST_NULL: {
                state.push(new ConstantItem(null));
                break;
            }
            case Opcodes.ICONST_0: {
                state.push(new ConstantItem(0));
                break;
            }
            case Opcodes.ICONST_1: {
                state.push(new ConstantItem(1));
                break;
            }
            case Opcodes.ICONST_2: {
                state.push(new ConstantItem(2));
                break;
            }
            case Opcodes.ICONST_3: {
                state.push(new ConstantItem(3));
                break;
            }
            case Opcodes.ICONST_4: {
                state.push(new ConstantItem(4));
                break;
            }
            case Opcodes.ICONST_5: {
                state.push(new ConstantItem(5));
                break;
            }
            case Opcodes.ICONST_M1: {
                state.push(new ConstantItem(-1));
                break;
            }
            case Opcodes.FCONST_0: {
                state.push(new ConstantItem(0f));
                break;
            }
            case Opcodes.FCONST_1: {
                state.push(new ConstantItem(1f));
                break;
            }
            case Opcodes.FCONST_2: {
                state.push(new ConstantItem(2f));
                break;
            }
            case Opcodes.LCONST_0: {
                StackItem si = new ConstantItem(0L);
                state.push(si);
                state.push(si);
                break;
            }
            case Opcodes.LCONST_1: {
                StackItem si = new ConstantItem(1L);
                state.push(si);
                state.push(si);
                break;
            }
            case Opcodes.DCONST_0: {
                StackItem si = new ConstantItem(0d);
                state.push(si);
                state.push(si);
                break;
            }
            case Opcodes.DCONST_1: {
                StackItem si = new ConstantItem(1d);
                state.push(si);
                state.push(si);
                break;
            }
            case Opcodes.AALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                Type t = null;
                if (ref.getKind() == StackItem.Kind.INSTANCE) {
                    t = ((InstanceItem)ref).getType();
                } else if (ref.getKind() == StackItem.Kind.RESULT) {
                    t = ((ResultItem)ref).getType();
                }

                state.push(new InstanceItem(t, ref, index));
                break;
            }
            case Opcodes.IALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                state.push(new InstanceItem(Type.INT_TYPE, index, ref));
                break;
            }
            case Opcodes.FALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                state.push(new InstanceItem(Type.FLOAT_TYPE, index, ref));
                break;
            }
            case Opcodes.BALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                state.push(new InstanceItem(Type.BYTE_TYPE, index, ref));
                break;
            }
            case Opcodes.CALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                state.push(new InstanceItem(Type.CHAR_TYPE, index, ref));
                break;
            }
            case Opcodes.SALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                state.push(new InstanceItem(Type.SHORT_TYPE, index, ref));
                break;
            }
            case Opcodes.LALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                StackItem sl = new InstanceItem(Type.LONG_TYPE, index, ref);
                state.push(sl);
                state.push(sl);
                break;
            }
            case Opcodes.DALOAD: {
                StackItem index = state.pop();
                StackItem ref = state.pop();

                StackItem sl = new InstanceItem(Type.DOUBLE_TYPE, index, ref);
                state.push(sl);
                state.push(sl);
                break;
            }
            case Opcodes.AASTORE:
            case Opcodes.IASTORE:
            case Opcodes.FASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE: {
                state.pop(); // val
                state.pop(); // index
                state.pop(); // arrayref

                break;
            }
            case Opcodes.LASTORE:
            case Opcodes.DASTORE: {
                state.pop();
                state.pop(); // var
                state.pop(); // index
                state.pop(); // arrayref

                break;
            }
            case Opcodes.POP: {
                state.pop();
                break;
            }
            case Opcodes.POP2: {
                state.pop();
                state.pop();
                break;
            }
            case Opcodes.DUP: {
                state.push(state.peek());
                break;
            }
            case Opcodes.DUP_X1: {
                StackItem x = state.pop();
                StackItem y = state.pop();
                state.push(x);
                state.push(y);
                state.push(x);
                break;
            }
            case Opcodes.DUP_X2: {
                StackItem x = state.pop();
                StackItem y = state.pop();
                StackItem z = state.pop();
                state.push(x);
                state.push(z);
                state.push(y);
                state.push(x);
                break;
            }
            case Opcodes.DUP2: {
                StackItem x = state.pop();
                StackItem y = state.peek();
                state.push(x);
                state.push(y);
                state.push(x);
                break;
            }
            case Opcodes.DUP2_X1: {
                StackItem x2 = state.pop();
                StackItem x1 = state.pop();
                StackItem y = state.pop();
                state.push(x1);
                state.push(x2);
                state.push(y);
                state.push(x1);
                state.push(x2);
                break;
            }
            case Opcodes.DUP2_X2: {
                StackItem x2 = state.pop();
                StackItem x1 = state.pop();
                StackItem y2 = state.pop();
                StackItem y1 = state.pop();
                state.push(x1);
                state.push(x2);
                state.push(y1);
                state.push(y2);
                state.push(x1);
                state.push(x2);
                break;
            }
            case Opcodes.SWAP: {
                StackItem x = state.pop();
                StackItem y = state.pop();
                state.push(x);
                state.push(y);
                break;
            }
            case Opcodes.IADD:
            case Opcodes.ISUB:
            case Opcodes.IMUL:
            case Opcodes.IDIV:
            case Opcodes.IREM:
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
            case Opcodes.ISHR:
            case Opcodes.ISHL:
            case Opcodes.IUSHR: {
                StackItem x = state.pop();
                StackItem y = state.pop();
                state.push(new InstanceItem(Type.INT_TYPE, x, y));
                break;
            }
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
            case Opcodes.FREM: {
                StackItem x = state.pop();
                StackItem y = state.pop();
                state.push(new InstanceItem(Type.FLOAT_TYPE, x, y));
                break;
            }
            case Opcodes.LADD:
            case Opcodes.LSUB:
            case Opcodes.LMUL:
            case Opcodes.LDIV:
            case Opcodes.LREM:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
            case Opcodes.LSHR:
            case Opcodes.LSHL:
            case Opcodes.LUSHR: {
                StackItem x = state.pop();
                state.pop();
                StackItem y = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.LONG_TYPE, x, y);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
            case Opcodes.DREM: {
                StackItem x = state.pop();
                state.pop();
                StackItem y = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.DOUBLE_TYPE, x, y);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.I2L: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.LONG_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.I2F: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.FLOAT_TYPE, x);
                state.push(rslt);
                break;
            }
            case Opcodes.I2B: {
                Set<StackItem> parents = new HashSet<>();
                StackItem x = state.pop();
                parents.addAll(x.getParents());
                StackItem rslt = new InstanceItem(Type.BYTE_TYPE, parents.toArray(new StackItem[parents.size()]));
                state.push(rslt);
                break;
            }
            case Opcodes.I2C: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.CHAR_TYPE, x);
                state.push(rslt);
                break;
            }
            case Opcodes.I2S: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.SHORT_TYPE, x);
                state.push(rslt);
                break;
            }
            case Opcodes.I2D: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.DOUBLE_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.L2I: {
                StackItem x = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.INT_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.L2F: {
                StackItem x = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.FLOAT_TYPE, x);
                state.push(rslt);
                break;
            }
            case Opcodes.L2D: {
                StackItem x = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.DOUBLE_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.F2I: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.INT_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.F2L: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.LONG_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.F2D: {
                StackItem x = state.pop();
                StackItem rslt = new InstanceItem(Type.DOUBLE_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.D2I: {
                StackItem x = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.INT_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.D2F: {
                StackItem x = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.FLOAT_TYPE, x);
                state.push(rslt);
                break;
            }
            case Opcodes.D2L: {
                StackItem x = state.pop();
                state.pop();
                StackItem rslt = new InstanceItem(Type.LONG_TYPE, x);
                state.push(rslt);
                state.push(rslt);
                break;
            }
            case Opcodes.LCMP: {
                StackItem a = state.pop();
                StackItem b = state.pop();
                StackItem c = state.pop();
                StackItem d = state.pop();

                state.push(new InstanceItem(Type.INT_TYPE, a, b, c, d));
                break;
            }
            case Opcodes.FCMPL:
            case Opcodes.FCMPG: {
                StackItem x = state.pop();
                StackItem y = state.pop();

                state.push(new InstanceItem(Type.INT_TYPE, x, y));
                break;
            }
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:{
                StackItem a = state.pop();
                StackItem b = state.pop();
                StackItem c = state.pop();
                StackItem d = state.pop();

                state.push(new InstanceItem(Type.INT_TYPE, a, b, c, d));
                break;
            }
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.RET: {
                state.reset();
                break;
            }
            case Opcodes.ATHROW: {
                for(Label l : effectiveHandlers) {
                    state.branch(l);
                }
                state.reset();
                break;
            }
            case Opcodes.ARRAYLENGTH: {
                Set<StackItem> parents = new HashSet<>();
                parents.addAll(state.pop().getParents());
                state.push(new InstanceItem(Type.INT_TYPE, parents.toArray(new StackItem[parents.size()])));
                break;
            }
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT: {
                state.pop();
                break;
            }
        }
    }

    @Override
    public void visitIincInsn(int var, int inc) {
        StackItem si = state.load(var);
        state.store(new InstanceItem(Type.INT_TYPE, si), var);
        super.visitIincInsn(var, inc);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        super.visitTryCatchBlock(start, end, handler, type);
        Collection<Label> labels = tryCatchStart.get(start);
        if (labels == null) {
            labels = new ArrayList<>();
            tryCatchStart.put(start, labels);
        }
        labels.add(handler);

        addToMappedCollection(tryCatchStart, start, handler);
        addToMappedCollection(tryCatchEnd, end, handler);

        handlers.add(handler);
    }

    private void addToMappedCollection(Map<Label, Collection<Label>> map, Label l, Label handler) {
        Collection<Label> labels = map.get(l);
        if (labels == null) {
            labels = new ArrayList<>();
            map.put(l, labels);
        }
        labels.add(handler);
    }

    @Override
    public void visitLabel(Label label) {
        super.visitLabel(label);
        Collection<Label> labels = tryCatchStart.get(label);
        if (labels != null) {
            effectiveHandlers.addAll(labels);
        } else {
            labels = tryCatchEnd.get(label);
            if (labels != null) {
                effectiveHandlers.removeAll(labels);
            } else {
                state.join(label);
                if (handlers.contains(label)) {
                    state.push(new InstanceItem(Type.getType(Throwable.class)));
                }
            }
        }
        visitedLabels.add(label);
    }

    protected List<StackItem> getMethodParams(String desc, boolean isStatic) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        int idx = argTypes.length - 1;

        List<StackItem> items = new ArrayList<>();
        Iterator<StackItem> it = state.fState.stack.iterator();
        while (it.hasNext() && idx >= 0) {
            Type t = argTypes[idx];
            items.add(0, it.next());
            if (t == Type.LONG_TYPE ||
                t == Type.DOUBLE_TYPE) {
                it.next();
            }
            idx--;
        }
        if (!isStatic && it.hasNext()) {
            items.add(0, it.next());
        }
        return items;
    }
}
