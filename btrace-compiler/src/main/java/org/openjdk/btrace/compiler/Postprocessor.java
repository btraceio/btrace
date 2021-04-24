/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace.compiler;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.extensions.ExtensionEntry;
import org.openjdk.btrace.core.extensions.ExtensionRepository;
import org.openjdk.btrace.instr.Constants;

/** @author Jaroslav Bachorik */
public class Postprocessor extends ClassVisitor {
  private final List<FieldDescriptor> fields = new ArrayList<>();
  private boolean shortSyntax = false;
  private String className = "";

  public Postprocessor(ClassVisitor cv) {
    super(Opcodes.ASM7, cv);
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    if (((access & Opcodes.ACC_PUBLIC)
            | (access & Opcodes.ACC_PROTECTED)
            | (access & Opcodes.ACC_PRIVATE))
        == 0) {
      shortSyntax =
          true; // specifying "class <MyClass>" rather than "public class <MyClass>" means using
      // short syntax
      access |= Opcodes.ACC_PUBLIC; // force the public modifier on the btrace class
    }
    className = name;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {

    if (!shortSyntax)
      return new ExtensionMethodConvertor(
          super.visitMethod(access, name, desc, signature, exceptions));

    if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) == 0) {
      access &= ~Opcodes.ACC_PROTECTED;
      access |= Opcodes.ACC_PUBLIC;
    }
    int localVarOffset = ((access & Opcodes.ACC_STATIC) == 0) ? -1 : 0;
    access |= Opcodes.ACC_STATIC;

    boolean isconstructor = false;
    if ("<init>".equals(name)) {
      name = "<clinit>";
      isconstructor = true;
    }

    return new MethodConvertor(
        localVarOffset,
        isconstructor,
        new ExtensionMethodConvertor(super.visitMethod(access, name, desc, signature, exceptions)));
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String desc, String signature, Object value) {
    Type fldType = Type.getType(desc);
    String fieldDesc =
        ExtensionRepository.getInstance().getExtensionForType(fldType.getClassName()) != null
            ? generalizeType(fldType).getDescriptor()
            : desc;

    if (!shortSyntax) return super.visitField(access, name, fieldDesc, signature, value);

    List<Attribute> attrs = new ArrayList<>();
    return new FieldVisitor(Opcodes.ASM7) {
      private final List<AnnotationDef> annotations = new ArrayList<>();

      @Override
      public AnnotationVisitor visitAnnotation(String type, boolean visible) {
        AnnotationDef ad = new AnnotationDef(type);
        annotations.add(ad);
        return new AnnotationVisitor(Opcodes.ASM7, super.visitAnnotation(type, visible)) {
          @Override
          public void visit(String name, Object val) {
            ad.addValue(name, val);
            super.visit(name, val);
          }
        };
      }

      @Override
      public void visitAttribute(Attribute atrbt) {
        super.visitAttribute(atrbt);
        attrs.add(atrbt);
      }

      @Override
      public void visitEnd() {
        FieldDescriptor fd =
            new FieldDescriptor(access, name, fieldDesc, signature, value, attrs, annotations);
        fields.add(fd);
        super.visitEnd();
      }
    };
  }

  @Override
  public void visitEnd() {
    if (shortSyntax) {
      addFields();
    }
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

      FieldVisitor fv =
          super.visitField(fieldAccess, fieldName, fieldDesc, fieldSignature, fieldValue);

      for (AnnotationDef ad : fd.annotations) {
        AnnotationVisitor av = fv.visitAnnotation(ad.getType(), true);
        for (Map.Entry<String, Object> attr : ad.getValues().entrySet()) {
          av.visit(attr.getKey(), attr.getValue());
        }
      }

      for (Attribute attr : fd.attributes) {
        fv.visitAttribute(attr);
      }
      fv.visitEnd();
    }
  }

  private static final class AnnotationDef {
    private final String type;
    private final Map<String, Object> values = new HashMap<>();

    public AnnotationDef(String type) {
      this.type = type;
    }

    public void addValue(String name, Object val) {
      values.put(name, val);
    }

    public String getType() {
      return type;
    }

    public Map<String, Object> getValues() {
      return values;
    }
  }

  private static class FieldDescriptor {
    final int access;
    final String name, desc, signature;
    final Object value;
    final List<Attribute> attributes;
    final List<AnnotationDef> annotations;
    int var = -1;
    boolean initialized;

    FieldDescriptor(
        int acc,
        String n,
        String d,
        String sig,
        Object val,
        List<Attribute> attrs,
        List<AnnotationDef> annots) {
      access = acc;
      name = n;
      desc = d;
      signature = sig;
      value = val;
      attributes = attrs;
      annotations = annots;
    }
  }

  private class MethodConvertor extends MethodVisitor {
    private final Deque<Boolean> simulatedStack = new ArrayDeque<>();
    private final boolean isConstructor;
    private int localVarOffset = 0;
    private boolean copyEnabled = false;

    public MethodConvertor(int localVarOffset, boolean isConstructor, MethodVisitor mv) {
      super(Opcodes.ASM7, mv);
      this.localVarOffset = localVarOffset;
      this.isConstructor = isConstructor;
      copyEnabled = !isConstructor; // copy is enabled by default for all methods except constructor
    }

    @Override
    public void visitLocalVariable(
        String name, String desc, String signature, Label start, Label end, int index) {
      if (index + localVarOffset < 0 || !copyEnabled) {
        return;
      }
      super.visitLocalVariable(name, desc, signature, start, end, index + localVarOffset);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
      boolean delegate = true;
      switch (opcode) {
        case Opcodes.ALOAD:
          {
            delegate = (var + localVarOffset) >= 0;
            simulatedStack.push(!delegate);
            break;
          }
        case Opcodes.LLOAD:
        case Opcodes.DLOAD:
          {
            simulatedStack.push(Boolean.FALSE);
            // long and double occoupy 2 stack slots; fall through
          }
        case Opcodes.ILOAD:
        case Opcodes.FLOAD:
          {
            simulatedStack.push(Boolean.FALSE);
            break;
          }
        case Opcodes.LSTORE:
        case Opcodes.DSTORE:
          {
            simulatedStack.poll();
            // long and double occoupy 2 stack slots; fall through
          }
        case Opcodes.ASTORE:
        case Opcodes.ISTORE:
        case Opcodes.FSTORE:
          {
            simulatedStack.poll();
            break;
          }
      }

      if (delegate && copyEnabled) super.visitVarInsn(opcode, var + localVarOffset);
    }

    @Override
    public void visitInsn(int opcode) {
      switch (opcode) {
        case Opcodes.POP:
          {
            if (simulatedStack.pop()) {
              return;
            }
            break;
          }
        case Opcodes.POP2:
          {
            Boolean[] vals = new Boolean[2];
            vals[0] = simulatedStack.poll();
            vals[1] = simulatedStack.poll();
            if (vals[0] && vals[1]) {
              return;
            } else if (vals[0] || vals[1]) {
              opcode = Opcodes.POP;
            }
            break;
          }
        case Opcodes.DUP:
          {
            Boolean val = simulatedStack.peek();
            val = val != null ? val : Boolean.FALSE;
            simulatedStack.push(val);
            if (val) return;
            break;
          }
        case Opcodes.DUP_X1:
          {
            if (simulatedStack.size() < 2) return;
            Boolean[] vals = new Boolean[2];
            int cntr = vals.length - 1;
            while (cntr >= 0) {
              vals[cntr--] = simulatedStack.pop();
            }
            simulatedStack.push(vals[vals.length - 1]);
            simulatedStack.addAll(Arrays.asList(vals));
            if (vals[1]) {
              return;
            } else if (vals[0]) {
              opcode = Opcodes.DUP;
            }
            break;
          }
        case Opcodes.DUP_X2:
          {
            if (simulatedStack.size() < 3) return;
            Boolean[] vals = new Boolean[3];
            int cntr = vals.length - 1;
            while (cntr >= 0) {
              vals[cntr--] = simulatedStack.pop();
            }
            simulatedStack.push(vals[vals.length - 1]);
            simulatedStack.addAll(Arrays.asList(vals));
            if (vals[2]) {
              return;
            } else if (vals[0] && vals[1]) {
              opcode = Opcodes.DUP;
            } else {
              opcode = Opcodes.DUP_X1;
            }
            break;
          }
        case Opcodes.DUP2:
          {
            if (simulatedStack.size() < 2) return;
            Boolean[] vals = new Boolean[2];
            int cntr = vals.length - 1;
            while (cntr >= 0) {
              vals[cntr--] = simulatedStack.pop();
            }
            simulatedStack.addAll(Arrays.asList(vals));
            if (vals[0] && vals[1]) {
              return;
            } else if (vals[0] || vals[1]) {
              opcode = Opcodes.DUP;
            }
            break;
          }
        case Opcodes.DUP2_X1:
          {
            if (simulatedStack.size() < 3) return;
            Boolean[] vals = new Boolean[3];
            int cntr = vals.length - 1;
            while (cntr >= 0) {
              vals[cntr--] = simulatedStack.pop();
            }
            simulatedStack.push(vals[vals.length - 2]);
            simulatedStack.push(vals[vals.length - 1]);
            simulatedStack.addAll(Arrays.asList(vals));
            if (vals[1] && vals[2]) {
              return;
            }
            if (vals[0]) {
              if (vals[1] || vals[2]) {
                opcode = Opcodes.DUP;
              } else {
                opcode = Opcodes.DUP2;
              }
            } else {
              if (vals[1] || vals[2]) {
                opcode = Opcodes.DUP_X1;
              }
            }
            break;
          }
        case Opcodes.DUP2_X2:
          {
            Boolean[] vals = {Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE};
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
        case Opcodes.SWAP:
          {
            if (simulatedStack.size() < 2) return;
            Boolean[] vals = new Boolean[2];

            int cntr = vals.length - 1;
            while (cntr >= 0) {
              vals[cntr--] = simulatedStack.pop();
            }
            if (vals[0] || vals[1]) {
              return;
            }
            simulatedStack.push(vals[1]);
            simulatedStack.push(vals[0]);
            break;
          }
          // zero operand instructions
        case Opcodes.LCONST_0:
        case Opcodes.LCONST_1:
        case Opcodes.DCONST_0:
        case Opcodes.DCONST_1:
          {
            simulatedStack.push(Boolean.FALSE);
            // the value occupies 2 slots on stack
            // fall through
          }
        case Opcodes.ICONST_0:
        case Opcodes.ICONST_1:
        case Opcodes.ICONST_2:
        case Opcodes.ICONST_3:
        case Opcodes.ICONST_4:
        case Opcodes.ICONST_5:
        case Opcodes.ICONST_M1:
        case Opcodes.FCONST_0:
        case Opcodes.FCONST_1:
        case Opcodes.FCONST_2:
        case Opcodes.ACONST_NULL:
        case Opcodes.MONITORENTER:
        case Opcodes.MONITOREXIT:
          {
            simulatedStack.push(Boolean.FALSE);
            break;
          }

          // one operand instructions
        case Opcodes.INEG:
        case Opcodes.FNEG:
        case Opcodes.DNEG:
        case Opcodes.LNEG:
        case Opcodes.I2B:
        case Opcodes.I2C:
        case Opcodes.I2F:
        case Opcodes.I2S:
        case Opcodes.L2D:
        case Opcodes.D2L:
        case Opcodes.F2I:
        case Opcodes.CHECKCAST:
        case Opcodes.ARRAYLENGTH:
          {
            // nothing changes in regard to the simulated stack
            break;
          }
        case Opcodes.I2L:
        case Opcodes.I2D:
          {
            simulatedStack.push(Boolean.FALSE); // extending the original value by one slot
            break;
          }

          // two operand instructions
        case Opcodes.LADD:
        case Opcodes.DADD:
        case Opcodes.LSUB:
        case Opcodes.DSUB:
        case Opcodes.LMUL:
        case Opcodes.DMUL:
        case Opcodes.LDIV:
        case Opcodes.DDIV:
        case Opcodes.LREM:
        case Opcodes.DREM:
        case Opcodes.LSHL:
        case Opcodes.LSHR:
        case Opcodes.LUSHR:
        case Opcodes.LAND:
        case Opcodes.LOR:
        case Opcodes.LXOR:
        case Opcodes.LALOAD:
        case Opcodes.DALOAD:
          {
            simulatedStack.pop();
            simulatedStack.pop();
            // remove 4 slots == 2 long/double operands and add 2 slots == 1 long/double result
            break;
          }
        case Opcodes.LCMP:
        case Opcodes.DCMPL:
        case Opcodes.DCMPG:
          {
            simulatedStack.pop();
            simulatedStack.pop();
            simulatedStack.pop();
            // remove 4 slots == 2 long/double operands and add 1 slot == 1 int result
            break;
          }
        case Opcodes.IADD:
        case Opcodes.FADD:
        case Opcodes.ISUB:
        case Opcodes.FSUB:
        case Opcodes.IMUL:
        case Opcodes.IDIV:
        case Opcodes.FDIV:
        case Opcodes.IREM:
        case Opcodes.FREM:
        case Opcodes.ISHL:
        case Opcodes.ISHR:
        case Opcodes.IUSHR:
        case Opcodes.IAND:
        case Opcodes.IOR:
        case Opcodes.IXOR:
        case Opcodes.FCMPL:
        case Opcodes.FCMPG:
        case Opcodes.BALOAD:
        case Opcodes.SALOAD:
        case Opcodes.CALOAD:
        case Opcodes.IALOAD:
        case Opcodes.FALOAD:
          {
            simulatedStack.poll();
            // remove 2 slots == 2 intoperands and add 1 slot == 1 int result
            break;
          }

          // three operand instructions
        case Opcodes.LASTORE:
        case Opcodes.DASTORE:
          {
            simulatedStack.pop();
            // LASTORE, DSTORE occupy one more slot compared to BASTORE etc.; falling through
            // fall through
          }
        case Opcodes.BASTORE: // fall through
        case Opcodes.CASTORE: // fall through
        case Opcodes.SASTORE: // fall through
        case Opcodes.IASTORE: // fall through
        case Opcodes.FASTORE:
          {
            simulatedStack.pop();
            simulatedStack.pop();
            simulatedStack.pop();
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
        case Opcodes.SIPUSH:
          {
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
        case Opcodes.IFNONNULL:
          {
            simulatedStack.poll();
            break;
          }
        case Opcodes.IF_ICMPEQ:
        case Opcodes.IF_ICMPGE:
        case Opcodes.IF_ICMPGT:
        case Opcodes.IF_ICMPLE:
        case Opcodes.IF_ICMPLT:
        case Opcodes.IF_ICMPNE:
          {
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
    public void visitTableSwitchInsn(int i, int i1, Label label, Label... labels) {
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
      if (o instanceof Long || o instanceof Double) {
        simulatedStack.push(Boolean.FALSE);
      }
      if (copyEnabled) {
        super.visitLdcInsn(o);
      }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      super.visitMaxs(maxStack, (Math.max(maxLocals + localVarOffset, 0)));
    }

    @Override
    public void visitIincInsn(int var, int increment) {
      if (copyEnabled) {
        super.visitIincInsn(var + localVarOffset, increment);
      }
    }

    @Override
    public void visitFieldInsn(int opcode, String clazz, String name, String desc) {
      if (opcode == Opcodes.GETFIELD) {
        Boolean opTarget = simulatedStack.poll();
        opTarget = opTarget != null ? opTarget : Boolean.FALSE;
        if (opTarget) {
          opcode = Opcodes.GETSTATIC;
        }
      } else if (opcode == Opcodes.PUTFIELD) {
        simulatedStack.pop();
        simulatedStack.pop();
        if (desc.equals("J") || desc.equals("D")) {
          simulatedStack.pop();
        }
        if (clazz.equals(className)) { // all local fields are static
          opcode = Opcodes.PUTSTATIC;
        }
      }
      switch (opcode) {
        case Opcodes.GETFIELD:
        case Opcodes.GETSTATIC:
          {
            simulatedStack.push(Boolean.FALSE);
            if (desc.equals("J") || desc.equals("D")) {
              simulatedStack.push(Boolean.FALSE);
            }
            break;
          }
      }
      if (copyEnabled) {
        super.visitFieldInsn(opcode, clazz, name, desc);
      }
    }

    @Override
    public void visitMethodInsn(
        int opcode, String clazz, String method, String desc, boolean iface) {
      int origOpcode = opcode;
      Type[] args = Type.getArgumentTypes(desc);
      for (Type t : args) {
        for (int i = 0; i < t.getSize(); i++) {
          simulatedStack.poll();
        }
      }
      if (opcode != Opcodes.INVOKESTATIC) {
        Boolean targetVal = simulatedStack.poll();
        if (targetVal != null
            && targetVal) { // "true" on stack means the original reference to "this"
          opcode = Opcodes.INVOKESTATIC;
        }
      }
      if (!Type.getReturnType(desc).equals(Type.VOID_TYPE)) {
        simulatedStack.push(Boolean.FALSE);
      }
      if (!copyEnabled) {
        if (origOpcode == Opcodes.INVOKESPECIAL && isConstructor) {
          copyEnabled = true;
        }
      } else {
        super.visitMethodInsn(opcode, clazz, method, desc, iface);
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String string, boolean bln) {
      return copyEnabled
          ? super.visitAnnotation(string, bln)
          : new AnnotationVisitor(Opcodes.ASM7) {};
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return copyEnabled ? super.visitAnnotationDefault() : new AnnotationVisitor(Opcodes.ASM7) {};
    }

    @Override
    public void visitAttribute(Attribute atrbt) {
      if (copyEnabled) {
        super.visitAttribute(atrbt);
      }
    }

    @Override
    public void visitMultiANewArrayInsn(String string, int i) {
      for (int ind = 0; ind < i; ind++) {
        simulatedStack.pop();
      }
      simulatedStack.push(Boolean.FALSE);
      if (copyEnabled) {
        super.visitMultiANewArrayInsn(string, i);
      }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int i, String string, boolean bln) {
      return copyEnabled
          ? super.visitParameterAnnotation(i, string, bln)
          : new AnnotationVisitor(Opcodes.ASM7) {};
    }

    @Override
    public void visitTypeInsn(int opcode, String typeName) {
      switch (opcode) {
        case Opcodes.NEW:
          {
            simulatedStack.push(Boolean.FALSE);
            break;
          }
      }
      if (copyEnabled) {
        super.visitTypeInsn(opcode, typeName);
      }
    }
  }

  private static class ExtensionMethodConvertor extends MethodVisitor {
    private final MethodType BOOTSTRAP_METHOD_TYPE =
        MethodType.methodType(
            CallSite.class,
            MethodHandles.Lookup.class,
            String.class,
            MethodType.class,
            String.class,
            String.class,
            String.class,
            int.class);

    public ExtensionMethodConvertor(MethodVisitor parent) {
      super(Opcodes.ASM9, parent);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      ExtensionEntry extension = ExtensionRepository.getInstance().getExtensionForType(owner);
      if (extension != null) {
        Type retType =
            opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD
                ? Type.getType(descriptor)
                : Type.VOID_TYPE;
        Type[] argTypes =
            opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD
                ? new Type[] {Type.getType(descriptor)}
                : new Type[0];

        // Do type erasure for return type and argument types
        retType = generalizeType(retType);
        for (int argIdx = 0; argIdx < argTypes.length; argIdx++) {
          argTypes[argIdx] = generalizeType(argTypes[argIdx]);
        }

        String dsc = Type.getMethodDescriptor(retType, argTypes);
        super.visitInvokeDynamicInsn(
            name,
            dsc,
            new Handle(
                Opcodes.H_INVOKESTATIC,
                Constants.EXTENSION_BOOTSTRAP_INTERNAL,
                "bootstrapInvoke",
                BOOTSTRAP_METHOD_TYPE.toMethodDescriptorString(),
                false),
            extension.getId(),
            owner,
            dsc,
            opcode);
      } else {
        Type fieldType = Type.getType(descriptor);
        if (ExtensionRepository.getInstance().getExtensionForType(fieldType.getClassName())
            != null) {
          fieldType = generalizeType(fieldType);
        }
        super.visitFieldInsn(opcode, owner, name, fieldType.getDescriptor());
      }
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (opcode != Opcodes.INVOKEDYNAMIC) {
        ExtensionEntry extension = ExtensionRepository.getInstance().getExtensionForType(owner);
        if (extension != null) {
          Type[] argTypes = Type.getArgumentTypes(descriptor);
          Type retType = Type.getReturnType(descriptor);

          if (opcode != Opcodes.INVOKESTATIC) {
            // Capture 'this' as 0-th argument
            Type[] newArgTypes = new Type[argTypes.length + 1];
            System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
            newArgTypes[0] = Constants.OBJECT_TYPE;
            argTypes = newArgTypes;
            descriptor = Type.getMethodDescriptor(retType, argTypes);
          }

          // Remove references to any potentially unresolvable types coming from extensions
          retType = generalizeType(retType);
          for (int i = 0; i < argTypes.length; i++) {
            argTypes[i] = generalizeType(argTypes[i]);
          }

          super.visitInvokeDynamicInsn(
              name,
              Type.getMethodDescriptor(retType, argTypes),
              new Handle(
                  Opcodes.H_INVOKESTATIC,
                  "org/openjdk/btrace/runtime/ExtensionBootstrap",
                  "bootstrapInvoke",
                  BOOTSTRAP_METHOD_TYPE.toMethodDescriptorString(),
                  false),
              extension.getId(),
              owner,
              descriptor,
              opcode);
          return;
        }
      }
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
  }

  private static Type generalizeType(Type type) {
    ExtensionRepository extensionRepository = ExtensionRepository.getInstance();
    if (type.getSort() == Type.OBJECT) {
      if (extensionRepository.getExtensionForType(type.getClassName()) != null) {
        type = Constants.OBJECT_TYPE;
      }
    }
    if (type.getSort() == Type.ARRAY) {
      if (type.getElementType().getSort() == Type.OBJECT) {
        if (extensionRepository.getExtensionForType(type.getElementType().getClassName()) != null) {
          StringBuilder typeDesc = new StringBuilder();
          for (int i = 0; i < type.getDimensions(); i++) {
            typeDesc.append('[');
          }
          typeDesc.append(type.getElementType().getDescriptor());
          type = Type.getType(typeDesc.toString());
        }
      }
    }
    return type;
  }
}
