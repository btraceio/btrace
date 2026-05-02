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
package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.ASM9;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class CopyingVisitor extends ClassVisitor {
  private final boolean renameParent;
  private final String targetClassName;

  private String origClassName;

  public CopyingVisitor(String targetClassName, boolean renameParent, ClassVisitor parent) {
    super(Opcodes.ASM8, parent);
    this.targetClassName = targetClassName;
    this.renameParent = renameParent;
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    if (renameParent) {
      super.visit(version, access, targetClassName, signature, superName, interfaces);
    }
    origClassName = name;
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String sig, String[] exceptions) {
    return new MethodVisitor(
        ASM9,
        super.visitMethod(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, getMethodName(name), desc, sig, exceptions)) {
      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String desc, boolean itfc) {
        if (owner.equals(origClassName)) {
          owner = targetClassName;
          name = getActionMethodName(name);
        }
        super.visitMethodInsn(opcode, owner, name, desc, itfc);
      }
    };
  }

  protected String getActionMethodName(String name) {
    return name;
  }

  protected String getMethodName(String name) {
    return name;
  }
}
