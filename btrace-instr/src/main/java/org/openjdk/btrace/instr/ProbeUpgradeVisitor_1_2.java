package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

final class ProbeUpgradeVisitor_1_2 extends ClassVisitor {

  private static final String ANNOTATIONS_PREFIX_OLD = "Lcom/sun/btrace/annotations/";
  private static final String ANNOTATIONS_PREFIX_NEW = "Lorg/openjdk/btrace/core/annotations/";

  ProbeUpgradeVisitor_1_2(ClassVisitor cv) {
    super(ASM7, cv);
  }

  private String cName = null;

  static byte[] upgrade(ClassReader cr) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    cr.accept(new ProbeUpgradeVisitor_1_2(cw), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    cName = name.replace('.', '/');
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    if (desc.startsWith(ANNOTATIONS_PREFIX_OLD)) {
      desc = ANNOTATIONS_PREFIX_NEW + desc.substring(ANNOTATIONS_PREFIX_OLD.length());
    }
    return super.visitAnnotation(desc, visible);
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String desc, String signature, Object value) {
    if (desc.contains("BTraceRuntime")) {
      desc = Constants.BTRACERTACCESS_DESC;
    }
    return new FieldVisitor(ASM7, super.visitField(access, name, desc, signature, value)) {
      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.startsWith(ANNOTATIONS_PREFIX_OLD)) {
          desc = ANNOTATIONS_PREFIX_NEW + desc.substring(ANNOTATIONS_PREFIX_OLD.length());
        }
        return super.visitAnnotation(desc, visible);
      }
    };
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    return new MethodVisitor(ASM7, super.visitMethod(access, name, desc, signature, exceptions)) {
      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.startsWith(ANNOTATIONS_PREFIX_OLD)) {
          desc = ANNOTATIONS_PREFIX_NEW + desc.substring(ANNOTATIONS_PREFIX_OLD.length());
        }
        return super.visitAnnotation(desc, visible);
      }

      @Override
      public AnnotationVisitor visitParameterAnnotation(
          int parameter, String desc, boolean visible) {
        return super.visitParameterAnnotation(
            parameter,
            desc.replace("com/sun/btrace/annotations/", "org/openjdk/btrace/core/annotations/"),
            visible);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        if (desc.equals("Lcom/sun/btrace/BTraceRuntime;")) {
          desc = Constants.BTRACERTACCESS_DESC;
        }
        super.visitFieldInsn(opcode, owner, name, desc);
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        if (owner.equals("com/sun/btrace/BTraceRuntime")) {
          if (name.equals("enter")) {
            if (desc.equals("(Lcom/sun/btrace/BTraceRuntime;)Z")) {
              visitMethodInsn(
                  INVOKESTATIC,
                  Constants.BTRACERTACCESS_INTERNAL,
                  name,
                  "(" + Constants.BTRACERTACCESS_DESC + ")Z",
                  itf);
            } else {
              visitFieldInsn(GETSTATIC, cName, "runtime", Constants.BTRACERTACCESS_DESC);
              visitMethodInsn(INVOKEVIRTUAL, Constants.BTRACERTACCESS_INTERNAL, name, "()Z", itf);
            }
          } else if (name.equals("forClass")) {
            desc = desc.replace("com/sun/btrace/shared/", "org/openjdk/btrace/core/handlers/");
            desc =
                desc.replace(
                    ")Lcom/sun/btrace/BTraceRuntime;", ")" + Constants.BTRACERTACCESS_DESC);
            super.visitMethodInsn(opcode, Constants.BTRACERTACCESS_INTERNAL, name, desc, itf);
          } else {
            super.visitMethodInsn(INVOKESTATIC, Constants.BTRACERT_INTERNAL, name, desc, itf);
          }
        } else if (owner.startsWith("com/sun/btrace/BTraceUtils")) {
          super.visitMethodInsn(INVOKESTATIC, Constants.BTRACE_UTILS, name, desc, itf);
        } else if (owner.startsWith("com/sun/btrace/services/")) {
          owner = owner.replace("com/sun/btrace/services/", "org/openjdk/btrace/services/");
          desc =
              desc.replace(
                  "com/sun/btrace/BTraceRuntime", "org/openjdk/btrace/runtime/BTraceRuntimeAccess");
          super.visitMethodInsn(opcode, owner, name, desc, itf);
        } else {
          owner = owner.replace("com/sun/btrace/shared/", "org/openjdk/btrace/core/handlers/");
          super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
      }

      @Override
      public void visitTypeInsn(int opcode, String type) {
        type = type.replace("com/sun/btrace/shared/", "org/openjdk/btrace/core/handlers/");
        type = type.replace("com/sun/btrace/services/", "org/openjdk/btrace/services/");
        super.visitTypeInsn(opcode, type);
      }
    };
  }
}
