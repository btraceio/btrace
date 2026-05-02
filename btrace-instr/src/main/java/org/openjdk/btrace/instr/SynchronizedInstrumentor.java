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
 * This visitor helps in inserting code whenever a synchronized method or block is about to be
 * entered/exited. The code to insert on synchronized method or block entry/exit may be decided by
 * derived class. By default, this class inserts code to print a message.
 *
 * @author A. Sundararajan
 */
public class SynchronizedInstrumentor extends MethodEntryExitInstrumentor {

  protected final boolean isStatic;
  protected final boolean isSyncMethod;

  public SynchronizedInstrumentor(
      ClassLoader cl,
      MethodVisitor mv,
      MethodInstrumentorHelper mHelper,
      String parentClz,
      String superClz,
      int access,
      String name,
      String desc) {
    super(cl, mv, mHelper, parentClz, superClz, access, name, desc);

    isStatic = (access & ACC_STATIC) != 0;
    isSyncMethod = (access & ACC_SYNCHRONIZED) != 0;
  }

  @Override
  protected void onMethodEntry() {
    if (isSyncMethod) {
      onAfterSyncEntry();
    }
  }

  @Override
  protected void onMethodReturn(int opcode) {
    onErrorReturn();
  }

  @Override
  protected void onErrorReturn() {
    if (isSyncMethod) {
      onBeforeSyncExit();
    }
  }

  @Override
  public void visitInsn(int opcode) {
    if (opcode == MONITORENTER) {
      onBeforeSyncEntry();
    } else if (opcode == MONITOREXIT) {
      onBeforeSyncExit();
    }
    super.visitInsn(opcode);
    if (opcode == MONITORENTER) {
      onAfterSyncEntry();
    } else if (opcode == MONITOREXIT) {
      onAfterSyncExit();
    }
  }

  protected void onBeforeSyncEntry() {
    asm.println("before synchronized entry");
  }

  protected void onAfterSyncEntry() {
    asm.println("after synchronized entry");
  }

  protected void onBeforeSyncExit() {
    asm.println("before synchronized exit");
  }

  protected void onAfterSyncExit() {
    asm.println("after synchronized exit");
  }
}
