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

/**
 * This visitor helps in inserting code whenever a source line is reached. The code to insert on
 * line number may be decided by derived class. By default, this class inserts code to print the
 * line.
 *
 * @author A. Sundararajan
 */
public class LineNumberInstrumentor extends MethodInstrumentor {
  private int lastLine;

  public LineNumberInstrumentor(
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
  public void visitLineNumber(int line, Label start) {
    if (lastLine != 0) {
      onAfterLine(line - 1);
    }
    if (!isConstructor() || isPrologueVisited()) {
      onBeforeLine(line);
    }
    lastLine = line;
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean iface) {
    boolean beforeConstructor = !isPrologueVisited();
    super.visitMethodInsn(opcode, owner, name, desc, iface);
    if (lastLine != -1 && beforeConstructor) {
      onBeforeLine(lastLine);
    }
  }

  protected void onBeforeLine(int line) {
    asm.println("before line " + line);
  }

  protected void onAfterLine(int line) {
    asm.println("after line " + line);
  }

  @Override
  public void visitEnd() {
    if (lastLine != -1) {
      onAfterLine(lastLine);
    }
    super.visitEnd();
  }
}
