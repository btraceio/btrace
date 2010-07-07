/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.compiler;

import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassAdapter;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodAdapter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.util.NullVisitor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Jaroslav Bachorik
 */
public class Postprocessor extends ClassAdapter {
    private List<FieldDescriptor> fields = new ArrayList<FieldDescriptor>();

    public Postprocessor(ClassVisitor cv) {
        super(cv);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) == 0) {
            access &= ~Opcodes.ACC_PROTECTED;
            access |= Opcodes.ACC_PUBLIC;
        }
        final int localVarOffset = ((access & Opcodes.ACC_STATIC) == 0) ? -1 : 0;
        access |= Opcodes.ACC_STATIC;

        MethodVisitor mv = new MethodConvertor(localVarOffset, "<init>".equals(name), super.visitMethod(access, name, desc, signature, exceptions));
        return mv;
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
        final List<Attribute> attrs = new ArrayList<Attribute>();
        return new FieldVisitor() {

            public AnnotationVisitor visitAnnotation(String string, boolean bln) {
                return new NullVisitor();
            }

            public void visitAttribute(Attribute atrbt) {
                attrs.add(atrbt);

            }

            public void visitEnd() {
                FieldDescriptor fd = new FieldDescriptor(access, name, desc,
                    signature, value, attrs);
                fields.add(fd);
            }
        };
    }

    @Override
    public void visitEnd() {
        addFields();
    }

    private void addFields() {
        for (FieldDescriptor fd : fields) {
            String fieldName = fd.name;
            int fieldAccess = fd.access;
            String fieldDesc = fd.desc;
            String fieldSignature = fd.signature;
            Object fieldValue = fd.value;

            fieldAccess &= ~Opcodes.ACC_PRIVATE;
            fieldAccess &= ~Opcodes.ACC_PROTECTED;
            fieldAccess |= Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

            FieldVisitor fv = super.visitField(fieldAccess,
                                 fieldName,
                                 fieldDesc, fieldSignature, fieldValue);

            for (Attribute attr : fd.attributes) {
                fv.visitAttribute(attr);
            }
            fv.visitEnd();
        }
    }

    private static class MethodConvertor extends MethodAdapter {
        private Deque<Boolean> simulatedStack = new ArrayDeque<Boolean>();
        private int localVarOffset = 0;
        private boolean isConstructor;
        private boolean copyEnabled = false;

        public MethodConvertor(int localVarOffset, boolean isConstructor, MethodVisitor mv) {
            super(mv);
            this.localVarOffset = localVarOffset;
            this.isConstructor = isConstructor;
            this.copyEnabled = !isConstructor; // copy is enabled by default for all methods except constructor
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            if (index + localVarOffset < 0 || !copyEnabled) {
                return;
            }
            super.visitLocalVariable(name, desc, signature, start, end, index + localVarOffset);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            boolean delegate = true;
            switch (opcode) {
                case Opcodes.ALOAD: {
                    delegate = (var + localVarOffset) >= 0;
                    simulatedStack.push(!delegate);
                    break;
                }
                case Opcodes.ILOAD:
                case Opcodes.LLOAD:
                case Opcodes.FLOAD:
                case Opcodes.DLOAD:
                {
                    simulatedStack.push(Boolean.FALSE);
                    break;
                }
                case Opcodes.ASTORE:
                case Opcodes.ISTORE:
                case Opcodes.LSTORE:
                case Opcodes.FSTORE:
                case Opcodes.DSTORE: {
                    simulatedStack.poll();
                    break;
                }
            }

            if (delegate && copyEnabled) super.visitVarInsn(opcode, var + localVarOffset);
        }

        @Override
        public void visitInsn(int opcode) {
            switch(opcode) {
                case Opcodes.POP: {
                    simulatedStack.poll();
                    break;
                }
                case Opcodes.POP2: {
                    simulatedStack.poll();
                    simulatedStack.poll();
                    break;
                }
                case Opcodes.DUP: {
                    Boolean val = simulatedStack.peek();
                    val = val != null ? val : Boolean.FALSE;
                    simulatedStack.push(val);
                    if (val) return;
                    break;
                }
                case Opcodes.DUP_X1: {
                    Boolean[] vals = new Boolean[]{Boolean.FALSE, Boolean.FALSE};
                    Iterator<Boolean> iter = simulatedStack.descendingIterator();
                    int cntr = 0;
                    while (cntr < vals.length && iter.hasNext()) {
                        vals[cntr++] = iter.next();
                    }
                    simulatedStack.push(vals[vals.length - 1]);
                    simulatedStack.addAll(Arrays.asList(vals));
                    break;
                }
                case Opcodes.DUP_X2: {
                    Boolean[] vals = new Boolean[]{Boolean.FALSE, Boolean.FALSE, Boolean.FALSE};
                    Iterator<Boolean> iter = simulatedStack.descendingIterator();
                    int cntr = 0;
                    while (cntr < vals.length && iter.hasNext()) {
                        vals[cntr++] = iter.next();
                    }
                    simulatedStack.push(vals[vals.length - 1]);
                    simulatedStack.addAll(Arrays.asList(vals));
                    break;
                }
                case Opcodes.DUP2: {
                    Boolean[] vals = new Boolean[]{Boolean.FALSE, Boolean.FALSE};
                    Iterator<Boolean> iter = simulatedStack.descendingIterator();
                    int cntr = 0;
                    while (cntr < vals.length && iter.hasNext()) {
                        vals[cntr++] = iter.next();
                    }
                    simulatedStack.addAll(Arrays.asList(vals));
                    break;
                }
                case Opcodes.DUP2_X1: {
                    Boolean[] vals = new Boolean[]{Boolean.FALSE, Boolean.FALSE, Boolean.FALSE};
                    Iterator<Boolean> iter = simulatedStack.descendingIterator();
                    int cntr = 0;
                    while (cntr < vals.length && iter.hasNext()) {
                        vals[cntr++] = iter.next();
                    }
                    simulatedStack.push(vals[vals.length - 2]);
                    simulatedStack.push(vals[vals.length - 1]);
                    simulatedStack.addAll(Arrays.asList(vals));
                    break;
                }
                case Opcodes.DUP2_X2: {
                    Boolean[] vals = new Boolean[]{Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE};
                    Iterator<Boolean> iter = simulatedStack.descendingIterator();
                    int cntr = 0;
                    while (cntr < vals.length && iter.hasNext()) {
                        vals[cntr++] = iter.next();
                    }
                    simulatedStack.push(vals[vals.length - 2]);
                    simulatedStack.push(vals[vals.length - 1]);
                    simulatedStack.addAll(Arrays.asList(vals));
                    break;
                }
                case Opcodes.IADD:
                case Opcodes.LADD:
                case Opcodes.FADD:
                case Opcodes.DADD:
                case Opcodes.ISUB:
                case Opcodes.LSUB:
                case Opcodes.FSUB:
                case Opcodes.DSUB:
                case Opcodes.IMUL:
                case Opcodes.LMUL:
                case Opcodes.DMUL:
                case Opcodes.IDIV:
                case Opcodes.LDIV:
                case Opcodes.FDIV:
                case Opcodes.DDIV:
                case Opcodes.IREM:
                case Opcodes.LREM:
                case Opcodes.FREM:
                case Opcodes.DREM:
                case Opcodes.ISHL:
                case Opcodes.LSHL:
                case Opcodes.ISHR:
                case Opcodes.LSHR:
                case Opcodes.IUSHR:
                case Opcodes.LUSHR:
                case Opcodes.IAND:
                case Opcodes.LAND:
                case Opcodes.IOR:
                case Opcodes.LOR:
                case Opcodes.LCMP:
                case Opcodes.FCMPL:
                case Opcodes.FCMPG:
                case Opcodes.DCMPL:
                case Opcodes.DCMPG:
                case Opcodes.BALOAD:
                case Opcodes.SALOAD:
                case Opcodes.CALOAD:
                case Opcodes.IALOAD:
                case Opcodes.LALOAD:
                case Opcodes.FALOAD:
                case Opcodes.DALOAD:
                {
                    simulatedStack.poll();
                    break;
                }
            }
            if (copyEnabled) {
                super.visitInsn(opcode);
            }
        }

        @Override
        public void visitIntInsn(int opcode, int index) {
            switch (opcode) {
                case Opcodes.BIPUSH:
                case Opcodes.SIPUSH: {
                    simulatedStack.push(Boolean.FALSE);
                    break;
                }
            }
            if (copyEnabled) {
                super.visitIntInsn(opcode, index);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            switch (opcode) {
                case Opcodes.IFEQ:
                case Opcodes.IFNE:
                case Opcodes.IFLE:
                case Opcodes.IFGE:
                case Opcodes.IFGT:
                case Opcodes.IFLT:
                case Opcodes.IFNULL:
                case Opcodes.IFNONNULL: {
                    simulatedStack.poll();
                    break;
                }
                case Opcodes.IF_ICMPEQ:
                case Opcodes.IF_ICMPGE:
                case Opcodes.IF_ICMPGT:
                case Opcodes.IF_ICMPLE:
                case Opcodes.IF_ICMPLT:
                case Opcodes.IF_ICMPNE: {
                    simulatedStack.poll();
                    simulatedStack.poll();
                    break;
                }
            }
            if (copyEnabled) {
                super.visitJumpInsn(opcode, label);
            }
        }

        @Override
        public void visitTableSwitchInsn(int i, int i1, Label label, Label[] labels) {
            simulatedStack.poll();
            if (copyEnabled) {
                super.visitTableSwitchInsn(i, i1, label, labels);
            }
        }

        @Override
        public void visitLookupSwitchInsn(Label label, int[] ints, Label[] labels) {
            simulatedStack.poll();
            if (copyEnabled) {
                super.visitLookupSwitchInsn(label, ints, labels);
            }
        }

        @Override
        public void visitLdcInsn(Object o) {
            simulatedStack.push(Boolean.FALSE);
            if (copyEnabled) {
                super.visitLdcInsn(o);
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack, (maxLocals + localVarOffset > 0 ? maxLocals + localVarOffset : 0));
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            if (copyEnabled) {
                super.visitIincInsn(var + localVarOffset, increment);
            }
        }

        @Override
        public void visitFieldInsn(int i, String clazz, String name, String desc) {
            if (i == Opcodes.GETFIELD) {
                Boolean opTarget = simulatedStack.poll();
                opTarget = opTarget != null ? opTarget : Boolean.FALSE;
                if (opTarget) {
                    i = Opcodes.GETSTATIC;
                }
            } else if (i == Opcodes.PUTFIELD) {
                simulatedStack.poll();
                Boolean opTarget = simulatedStack.poll();
                if (opTarget != null && opTarget) {
                    i = Opcodes.PUTSTATIC;
                }
            }
            switch (i) {
                case Opcodes.GETFIELD:
                case Opcodes.GETSTATIC: {
                    simulatedStack.push(Boolean.FALSE);
                    break;
                }
            }
            if (copyEnabled) {
                super.visitFieldInsn(i, clazz, name, desc);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String clazz, String method, String desc) {
            Type[] args = Type.getArgumentTypes(desc);
            for(Type t : args) {
                for(int i=0;i<t.getSize();i++) {
                    simulatedStack.poll();
                }
            }
            Boolean targetVal = simulatedStack.poll();
            if (targetVal != null && targetVal) {
                if (opcode == Opcodes.INVOKESPECIAL) {
                    opcode = Opcodes.INVOKESTATIC;
                }
            }
            if (!Type.getReturnType(desc).equals(Type.VOID_TYPE)) {
                simulatedStack.push(Boolean.FALSE);
            }
            if (!copyEnabled) {
                if (opcode == Opcodes.INVOKESPECIAL && isConstructor) {
                    copyEnabled = true;
                }
            } else {
                super.visitMethodInsn(opcode, clazz, method, desc);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String string, boolean bln) {
            return copyEnabled ? super.visitAnnotation(string, bln) : new NullVisitor();
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return copyEnabled ? super.visitAnnotationDefault() : new NullVisitor();
        }

        @Override
        public void visitAttribute(Attribute atrbt) {
            if (copyEnabled) {
                super.visitAttribute(atrbt);
            }
        }

        @Override
        public void visitMultiANewArrayInsn(String string, int i) {
            if (copyEnabled) {
                super.visitMultiANewArrayInsn(string, i);
            }
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int i, String string, boolean bln) {
            return copyEnabled ? super.visitParameterAnnotation(i, string, bln) : new NullVisitor();
        }

        @Override
        public void visitTypeInsn(int opcode, String typeName) {
            switch(opcode) {
                case Opcodes.NEW: {
                    simulatedStack.push(Boolean.FALSE);
                    break;
                }
            }
            if (copyEnabled) {
                super.visitTypeInsn(opcode, typeName);
            }
        }
    }

    private static class FieldDescriptor {
        int access;
        String name, desc, signature;
        Object value;
        List<Attribute> attributes;
        int var = -1;
        boolean initialized;

        FieldDescriptor(int acc, String n, String d,
                        String sig, Object val, List<Attribute> attrs) {
            access = acc;
            name = n;
            desc = d;
            signature = sig;
            value = val;
            attributes = attrs;
        }
    }
}
