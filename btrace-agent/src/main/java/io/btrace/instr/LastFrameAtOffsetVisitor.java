/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Holds a stack map frame back until the next instruction, so that of several frames at one offset
 * only the last gets written. A class file allows one frame per offset, and the code injected at
 * the end of a branch can end right where the method's own frame for the join point is due. That
 * frame has to win: it holds on every path, while the injected one also claims the locals added
 * inside the branch.
 */
final class LastFrameAtOffsetVisitor extends MethodVisitor {
  private boolean pending = false;
  private int type;
  private int nLocal;
  private Object[] local;
  private int nStack;
  private Object[] stack;

  LastFrameAtOffsetVisitor(MethodVisitor mv) {
    super(Opcodes.ASM9, mv);
  }

  @Override
  public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
    pending = true;
    this.type = type;
    this.nLocal = nLocal;
    this.local = local != null ? local.clone() : null;
    this.nStack = nStack;
    this.stack = stack != null ? stack.clone() : null;
  }

  private void flushFrame() {
    if (pending) {
      pending = false;
      super.visitFrame(type, nLocal, local, nStack, stack);
    }
  }

  @Override
  public void visitInsn(int opcode) {
    flushFrame();
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    flushFrame();
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int varIndex) {
    flushFrame();
    super.visitVarInsn(opcode, varIndex);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    flushFrame();
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    flushFrame();
    super.visitFieldInsn(opcode, owner, name, descriptor);
  }

  @Override
  public void visitMethodInsn(
      int opcode, String owner, String name, String descriptor, boolean isInterface) {
    flushFrame();
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
  }

  @Override
  public void visitInvokeDynamicInsn(
      String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArgs) {
    flushFrame();
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArgs);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    flushFrame();
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLdcInsn(Object value) {
    flushFrame();
    super.visitLdcInsn(value);
  }

  @Override
  public void visitIincInsn(int varIndex, int increment) {
    flushFrame();
    super.visitIincInsn(varIndex, increment);
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    flushFrame();
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    flushFrame();
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    flushFrame();
    super.visitMultiANewArrayInsn(descriptor, numDimensions);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    flushFrame();
    super.visitMaxs(maxStack, maxLocals);
  }
}
