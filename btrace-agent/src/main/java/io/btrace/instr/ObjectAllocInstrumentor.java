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

import static org.objectweb.asm.Opcodes.NEW;

import java.util.ArrayDeque;
import java.util.Deque;
import org.objectweb.asm.MethodVisitor;

/**
 * This visitor helps in inserting code whenever an object is allocated. The code to insert on
 * object alloc may be decided by derived class. By default, this class inserts code to print a
 * message.
 *
 * @author A. Sundararajan
 */
public class ObjectAllocInstrumentor extends MethodInstrumentor {
  private final boolean needsInitialization;
  // Stack of types allocated by NEW but not yet initialized by their <init>. A single boolean is
  // insufficient: for a nested allocation such as `new Outer(new Inner())` the inner constructor's
  // <init> would clear the flag and the outer <init> would be missed. Constructor arguments (and
  // thus nested news) are fully evaluated before the enclosing <init>, so pending NEWs are
  // initialized in LIFO order; a stack matches that discipline. A constructor's own super()/this()
  // delegation runs with an empty stack, so it correctly does not fire afterObjectNew.
  private final Deque<String> pendingNews = new ArrayDeque<>();

  public ObjectAllocInstrumentor(
      ClassLoader cl,
      MethodVisitor mv,
      MethodInstrumentorHelper mHelper,
      String parentClz,
      String superClz,
      int access,
      String name,
      String desc) {
    this(cl, mv, mHelper, parentClz, superClz, access, name, desc, false);
  }

  public ObjectAllocInstrumentor(
      ClassLoader cl,
      MethodVisitor mv,
      MethodInstrumentorHelper mHelper,
      String parentClz,
      String superClz,
      int access,
      String name,
      String desc,
      boolean needsInitialization) {
    super(cl, mv, mHelper, parentClz, superClz, access, name, desc);
    this.needsInitialization = needsInitialization;
  }

  @Override
  public void visitTypeInsn(int opcode, String desc) {
    if (opcode == NEW) {
      beforeObjectNew(desc);
    }
    super.visitTypeInsn(opcode, desc);
    if (opcode == NEW) {
      if (needsInitialization) {
        pendingNews.push(desc);
      } else {
        afterObjectNew(desc);
      }
    }
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean iface) {
    super.visitMethodInsn(opcode, owner, name, desc, iface);
    if (Constants.CONSTRUCTOR.equals(name) && !pendingNews.isEmpty()) {
      pendingNews.pop();
      afterObjectNew(owner);
    }
  }

  protected void beforeObjectNew(String desc) {}

  protected void afterObjectNew(String desc) {}
}
