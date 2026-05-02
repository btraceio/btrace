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

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.MethodVisitor;

/**
 * This visitor helps in inserting code whenever a method call returns. The code to insert on method
 * return may be decided by derived class. By default, this class inserts code to print name and
 * signature of the method returned.
 *
 * @author A. Sundararajan
 */
public class MethodReturnInstrumentor extends MethodEntryInstrumentor {
  public MethodReturnInstrumentor(
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
  public void visitInsn(int opcode) {
    switch (opcode) {
      case IRETURN:
      case ARETURN:
      case FRETURN:
      case LRETURN:
      case DRETURN:
      case RETURN:
        onMethodReturn(opcode);
        break;
      default:
        break;
    }
    super.visitInsn(opcode);
  }

  protected void onMethodReturn(int opcode) {
    asm.println("leaving " + getName() + getDescriptor());
  }
}
