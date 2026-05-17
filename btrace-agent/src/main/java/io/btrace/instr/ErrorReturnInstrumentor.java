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

import static io.btrace.instr.Constants.THROWABLE_INTERNAL;
import static io.btrace.instr.Constants.THROWABLE_TYPE;
import static org.objectweb.asm.Opcodes.ATHROW;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * This visitor helps in inserting code whenever a method "returns" because of an exception (i.e.,
 * no exception handler in the method and so it's frame poped). The code to insert on method error
 * return may be decided by derived class. By default, this class inserts code to print message to
 * say "no handler here".
 *
 * @author A. Sundararajan
 */
public class ErrorReturnInstrumentor extends MethodReturnInstrumentor {
  private final Label start = new Label();
  private final Label end = new Label();

  public ErrorReturnInstrumentor(
      ClassLoader cl,
      MethodVisitor mv,
      MethodInstrumentorHelper mHelper,
      String parentClz,
      String superClz,
      int access,
      String name,
      String desc) {
    super(cl, mv, mHelper, parentClz, superClz, access, name, desc);
  }

  @Override
  protected void visitMethodPrologue() {
    addTryCatchHandler(start, end);
    visitLabel(start);
    super.visitMethodPrologue();
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    visitTryCatchBlock(start, end, end, THROWABLE_INTERNAL);
    visitLabel(end);
    insertFrameReplaceStack(end, THROWABLE_TYPE);
    onErrorReturn();
    visitInsn(ATHROW);
    super.visitMaxs(maxStack, maxLocals);
  }

  @Override
  protected void onMethodEntry() {}

  @Override
  protected void onMethodReturn(int opcode) {}

  protected void onErrorReturn() {
    asm.println("error return from " + getName() + getDescriptor());
  }
}
