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

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class BTraceMethodVisitor extends MethodVisitor {
  private final MethodInstrumentorHelper mHelper;

  public BTraceMethodVisitor(MethodVisitor mv, MethodInstrumentorHelper mHelper) {
    super(Opcodes.ASM9, mv);
    this.mHelper = mHelper;
  }

  public final int storeAsNew() {
    return mHelper.storeAsNew();
  }

  public final int storeNewLocal(Type t) {
    int index = mHelper.newVar(t);
    super.visitVarInsn(t.getOpcode(Opcodes.ISTORE), index);
    return index;
  }

  public final void addTryCatchHandler(Label start, Label handler) {
    mHelper.addTryCatchHandler(start, handler);
  }

  public void insertFrameReplaceStack(Label l, Type... stack) {
    mHelper.insertFrameReplaceStack(l, stack);
  }

  public void insertFrameAppendStack(Label l, Type... stack) {
    mHelper.insertFrameAppendStack(l, stack);
  }

  public void insertFrameSameStack(Label l) {
    mHelper.insertFrameSameStack(l);
  }
}
