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

import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.NEWARRAY;

import org.objectweb.asm.MethodVisitor;

/**
 * This visitor helps in inserting code whenever an array is allocated. The code to insert on method
 * entry may be decided by derived class. By default, this class inserts code to print allocated
 * array objects.
 *
 * @author A. Sundararajan
 */
public class ArrayAllocInstrumentor extends MethodInstrumentor {
  public ArrayAllocInstrumentor(
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
  public void visitIntInsn(int opcode, int operand) {
    String desc = null;
    if (opcode == NEWARRAY) {
      desc = InstrumentUtils.arrayDescriptorFor(operand);
      onBeforeArrayNew(getPlainType(desc), 1);
    }
    super.visitIntInsn(opcode, operand);
    if (opcode == NEWARRAY) {
      onAfterArrayNew(getPlainType(desc), 1);
    }
  }

  @Override
  public void visitTypeInsn(int opcode, String desc) {
    if (opcode == ANEWARRAY) {
      onBeforeArrayNew("L" + desc + ";", 1);
    }
    super.visitTypeInsn(opcode, desc);
    if (opcode == ANEWARRAY) {
      onAfterArrayNew("L" + desc + ";", 1);
    }
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    String type = getPlainType(desc);
    onBeforeArrayNew(type, dims);
    super.visitMultiANewArrayInsn(desc, dims);
    onAfterArrayNew(type, dims);
  }

  protected void onBeforeArrayNew(String desc, int dims) {
    asm.println("before allocating " + desc);
  }

  protected void onAfterArrayNew(String desc, int dims) {
    asm.dup().printObject();
  }

  private String getPlainType(String desc) {
    int index = desc.lastIndexOf('[') + 1;
    if (index > 0) {
      return desc.substring(index);
    }
    return desc;
  }
}
