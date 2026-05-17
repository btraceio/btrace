/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.nio.charset.StandardCharsets;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Transparently remaps pre-compiled BTrace probe class files that still reference the legacy {@code
 * org/openjdk/btrace/} package to the current {@code io/btrace/} namespace. This shim is applied
 * during probe loading so that old persisted probe {@code .class} files continue to work without
 * recompilation.
 */
final class ProbePackageMigrator {

  private static final String OLD_PREFIX = "org/openjdk/btrace/";
  private static final String NEW_PREFIX = "io/btrace/";

  // Descriptor prefix variants for field/method descriptor remapping
  private static final String OLD_DESC_PREFIX = "Lorg/openjdk/btrace/";
  private static final String NEW_DESC_PREFIX = "Lio/btrace/";

  private ProbePackageMigrator() {}

  /**
   * Remaps {@code org/openjdk/btrace/} references in the supplied class bytes to {@code
   * io/btrace/}.
   *
   * <p>If the class bytes contain no old-namespace references the original array is returned
   * unchanged.
   *
   * @param bytes raw class file bytes
   * @return remapped class bytes, or the original array when no migration is needed
   */
  static byte[] migrate(byte[] bytes) {
    if (!needsMigration(bytes)) {
      return bytes;
    }
    ClassReader cr = new ClassReader(bytes);
    ClassWriter cw = new ClassWriter(cr, 0);
    cr.accept(new MigratingClassVisitor(cw), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  /**
   * Fast pre-check: scan the raw bytes for the old package prefix using ISO-8859-1 so that every
   * byte maps 1:1 to a character (no multi-byte encoding issues).
   */
  private static boolean needsMigration(byte[] bytes) {
    String raw = new String(bytes, StandardCharsets.ISO_8859_1);
    return raw.contains(OLD_PREFIX);
  }

  // --- helpers ---

  private static String remapInternalName(String name) {
    if (name != null && name.startsWith(OLD_PREFIX)) {
      return NEW_PREFIX + name.substring(OLD_PREFIX.length());
    }
    return name;
  }

  private static String remapDescriptor(String desc) {
    if (desc != null && desc.contains(OLD_DESC_PREFIX)) {
      return desc.replace(OLD_DESC_PREFIX, NEW_DESC_PREFIX);
    }
    return desc;
  }

  private static String remapSignature(String sig) {
    if (sig != null && sig.contains(OLD_PREFIX)) {
      return sig.replace(OLD_PREFIX, NEW_PREFIX);
    }
    return sig;
  }

  private static String[] remapExceptions(String[] exceptions) {
    if (exceptions == null) {
      return null;
    }
    String[] remapped = null;
    for (int i = 0; i < exceptions.length; i++) {
      String r = remapInternalName(exceptions[i]);
      if (r != exceptions[i]) {
        if (remapped == null) {
          remapped = exceptions.clone();
        }
        remapped[i] = r;
      }
    }
    return remapped != null ? remapped : exceptions;
  }

  private static Handle remapHandle(Handle h) {
    String owner = remapInternalName(h.getOwner());
    String desc = remapDescriptor(h.getDesc());
    if (owner != h.getOwner() || desc != h.getDesc()) {
      return new Handle(h.getTag(), owner, h.getName(), desc, h.isInterface());
    }
    return h;
  }

  private static Object[] remapBsmArgs(Object[] args) {
    if (args == null) {
      return null;
    }
    Object[] remapped = null;
    for (int i = 0; i < args.length; i++) {
      Object orig = args[i];
      Object r = orig;
      if (orig instanceof String) {
        r = remapDescriptor((String) orig);
        if (r.equals(orig)) {
          r = remapInternalName((String) orig);
        }
      } else if (orig instanceof Handle) {
        r = remapHandle((Handle) orig);
      }
      if (r != orig) {
        if (remapped == null) {
          remapped = args.clone();
        }
        remapped[i] = r;
      }
    }
    return remapped != null ? remapped : args;
  }

  // --- visitor hierarchy ---

  private static final class MigratingClassVisitor extends ClassVisitor {

    MigratingClassVisitor(ClassWriter cw) {
      super(ASM9, cw);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      super.visit(
          version,
          access,
          remapInternalName(name),
          remapSignature(signature),
          remapInternalName(superName),
          remapInterfaces(interfaces));
    }

    private static String[] remapInterfaces(String[] ifaces) {
      if (ifaces == null) {
        return null;
      }
      String[] out = null;
      for (int i = 0; i < ifaces.length; i++) {
        String r = remapInternalName(ifaces[i]);
        if (r != ifaces[i]) {
          if (out == null) {
            out = ifaces.clone();
          }
          out[i] = r;
        }
      }
      return out != null ? out : ifaces;
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
      super.visitOuterClass(remapInternalName(owner), name, remapDescriptor(descriptor));
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
      super.visitInnerClass(
          remapInternalName(name), remapInternalName(outerName), innerName, access);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return new MigratingAnnotationVisitor(
          super.visitAnnotation(remapDescriptor(descriptor), visible));
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      return new MigratingFieldVisitor(
          super.visitField(
              access, name, remapDescriptor(descriptor), remapSignature(signature), value));
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new MigratingMethodVisitor(
          super.visitMethod(
              access,
              name,
              remapDescriptor(descriptor),
              remapSignature(signature),
              remapExceptions(exceptions)));
    }
  }

  private static final class MigratingFieldVisitor extends FieldVisitor {

    MigratingFieldVisitor(FieldVisitor fv) {
      super(ASM9, fv);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return new MigratingAnnotationVisitor(
          super.visitAnnotation(remapDescriptor(descriptor), visible));
    }
  }

  private static final class MigratingMethodVisitor extends MethodVisitor {

    MigratingMethodVisitor(MethodVisitor mv) {
      super(ASM9, mv);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return new MigratingAnnotationVisitor(
          super.visitAnnotation(remapDescriptor(descriptor), visible));
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(
        int parameter, String descriptor, boolean visible) {
      return new MigratingAnnotationVisitor(
          super.visitParameterAnnotation(parameter, remapDescriptor(descriptor), visible));
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      super.visitTypeInsn(opcode, remapInternalName(type));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      super.visitFieldInsn(opcode, remapInternalName(owner), name, remapDescriptor(descriptor));
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      super.visitMethodInsn(
          opcode, remapInternalName(owner), name, remapDescriptor(descriptor), isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name,
        String descriptor,
        Handle bootstrapMethodHandle,
        Object... bootstrapMethodArguments) {
      super.visitInvokeDynamicInsn(
          name,
          remapDescriptor(descriptor),
          remapHandle(bootstrapMethodHandle),
          remapBsmArgs(bootstrapMethodArguments));
    }

    @Override
    public void visitLocalVariable(
        String name, String descriptor, String signature, Label start, Label end, int index) {
      super.visitLocalVariable(
          name, remapDescriptor(descriptor), remapSignature(signature), start, end, index);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      super.visitTryCatchBlock(start, end, handler, remapInternalName(type));
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      super.visitMultiANewArrayInsn(remapDescriptor(descriptor), numDimensions);
    }
  }

  private static final class MigratingAnnotationVisitor extends AnnotationVisitor {

    MigratingAnnotationVisitor(AnnotationVisitor av) {
      super(ASM9, av);
    }

    @Override
    public void visit(String name, Object value) {
      if (value instanceof String) {
        value = remapDescriptor((String) value);
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      super.visitEnum(name, remapDescriptor(descriptor), value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return new MigratingAnnotationVisitor(
          super.visitAnnotation(name, remapDescriptor(descriptor)));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new MigratingAnnotationVisitor(super.visitArray(name));
    }
  }
}
