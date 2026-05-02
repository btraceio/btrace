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
import org.objectweb.asm.Type;

/**
 * This visitor helps in inserting code whenever an array access is done. Code to insert on array
 * access may be decided by derived class. By default, this class inserts code to print array
 * access.
 *
 * @author A. Sundararajan
 */
public class ArrayAccessInstrumentor extends MethodInstrumentor {
  public ArrayAccessInstrumentor(
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
    boolean arrayload = false;
    boolean arraystore = false;
    switch (opcode) {
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        arrayload = true;
        break;

      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case AASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        arraystore = true;
        break;
    }
    if (arrayload) {
      onBeforeArrayLoad(opcode);
    } else if (arraystore) {
      onBeforeArrayStore(opcode);
    }
    super.visitInsn(opcode);
    if (arrayload) {
      onAfterArrayLoad(opcode);
    } else if (arraystore) {
      onAfterArrayStore(opcode);
    }
  }

  protected final boolean locationTypeMismatch(Location loc, Type arrtype, Type itemType) {
    return !loc.getType().isEmpty()
        && (!loc.getType().equals(arrtype.getClassName())
            && !loc.getType().equals(itemType.getClassName()));
  }

  protected void onBeforeArrayLoad(int opcode) {}

  protected void onAfterArrayLoad(int opcode) {}

  protected void onBeforeArrayStore(int opcode) {}

  protected void onAfterArrayStore(int opcode) {}
}
