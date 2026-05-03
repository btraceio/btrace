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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class ProbeRenameVisitor extends ClassVisitor {
  private String oldClassName = null;
  private final String newClassName;

  ProbeRenameVisitor(ClassVisitor classVisitor, String newClassName) {
    super(Opcodes.ASM9, classVisitor);
    this.newClassName = newClassName.replace('.', '/');
  }

  static byte[] rename(String newClassName, byte[] data) {
    ClassReader cr = new ClassReader(data);
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    cr.accept(new ProbeRenameVisitor(cw, newClassName), ClassReader.SKIP_DEBUG);
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
    oldClassName = name;
    super.visit(version, access, newClassName, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    return new MethodVisitor(
        Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
      @Override
      public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type.equals(oldClassName) ? newClassName : type);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        super.visitFieldInsn(
            opcode, owner.equals(oldClassName) ? newClassName : owner, name, descriptor);
      }

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        super.visitMethodInsn(
            opcode,
            owner.equals(oldClassName) ? newClassName : owner,
            name,
            descriptor,
            isInterface);
      }

      @Override
      public void visitLdcInsn(Object value) {
        // Rename class constants that reference the old class name
        if (value instanceof Type) {
          Type t = (Type) value;
          if (t.getSort() == Type.OBJECT && t.getInternalName().equals(oldClassName)) {
            super.visitLdcInsn(Type.getObjectType(newClassName));
            return;
          }
        }
        // Rename String constants that contain the old class name
        if (value instanceof String) {
          String s = (String) value;
          String oldDotName = oldClassName.replace('/', '.');
          String newDotName = newClassName.replace('/', '.');
          if (s.equals(oldDotName)) {
            super.visitLdcInsn(newDotName);
            return;
          }
          if (s.equals(oldClassName)) {
            super.visitLdcInsn(newClassName);
            return;
          }
        }
        super.visitLdcInsn(value);
      }
    };
  }
}
