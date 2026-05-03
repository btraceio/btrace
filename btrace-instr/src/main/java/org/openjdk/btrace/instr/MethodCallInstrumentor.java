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

import org.objectweb.asm.MethodVisitor;

/**
 * This visitor helps in inserting code whenever a method call is done. The code to insert on method
 * calls may be decided by derived class. By default, this class inserts code to print the called
 * method.
 *
 * @author A. Sundararajan
 */
public class MethodCallInstrumentor extends MethodInstrumentor {
  private int callId = 0;

  public MethodCallInstrumentor(
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
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean iface) {
    if (name.startsWith("$btrace")) {
      super.visitMethodInsn(opcode, owner, name, desc, iface);
      return;
    }

    callId++;

    onBeforeCallMethod(opcode, owner, name, desc);
    super.visitMethodInsn(opcode, owner, name, desc, iface);
    onAfterCallMethod(opcode, owner, name, desc);
  }

  protected void onBeforeCallMethod(int opcode, String owner, String name, String desc) {
    asm.println("before call: " + owner + "." + name + desc);
  }

  protected void onAfterCallMethod(int opcode, String owner, String name, String desc) {
    asm.println("after call: " + owner + "." + name + desc);
  }

  protected int getCallId() {
    return callId;
  }
}
