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

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.MethodVisitor;

/**
 * This visitor helps in inserting code whenever an field access is done. The code to insert on
 * field access may be decided by derived class. By default, this class inserts code to print the
 * field access.
 *
 * @author A. Sundararajan
 */
public class FieldAccessInstrumentor extends MethodInstrumentor {
  protected boolean isStaticAccess = false;

  public FieldAccessInstrumentor(
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
  public void visitFieldInsn(int opcode, String owner, String name, String desc) {
    boolean get;
    // ignore any internal BTrace fields
    if (name.contains("$btrace$")) {
      super.visitFieldInsn(opcode, owner, name, desc);
      return;
    }

    get = opcode == GETFIELD || opcode == GETSTATIC;
    isStaticAccess = (opcode == GETSTATIC || opcode == PUTSTATIC);

    if (get) {
      onBeforeGetField(opcode, owner, name, desc);
    } else {
      onBeforePutField(opcode, owner, name, desc);
    }
    super.visitFieldInsn(opcode, owner, name, desc);
    if (get) {
      onAfterGetField(opcode, owner, name, desc);
    } else {
      onAfterPutField(opcode, owner, name, desc);
    }
  }

  protected void onBeforeGetField(int opcode, String owner, String name, String desc) {}

  protected void onAfterGetField(int opcode, String owner, String name, String desc) {}

  protected void onBeforePutField(int opcode, String owner, String name, String desc) {}

  protected void onAfterPutField(int opcode, String owner, String name, String desc) {}
}
