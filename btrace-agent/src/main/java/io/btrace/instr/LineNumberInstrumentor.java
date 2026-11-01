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

import java.util.Arrays;
import org.objectweb.asm.Handle;
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
  private int[] pendingLines = new int[2];
  private int pendingLineCount = 0;
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
    // A line number comes before the stack map frame of its offset, and the frame has to stay in
    // front of any code added there, so that code waits for the first instruction of the line
    if (pendingLineCount == pendingLines.length) {
      pendingLines = Arrays.copyOf(pendingLines, pendingLineCount * 2);
    }
    pendingLines[pendingLineCount++] = line;
    super.visitLineNumber(line, start);
  }

  private void flushLines() {
    // The code added for a line comes back through the visit methods below, and must find no
    // line pending; a visit adds no line numbers, so the array stays as it is meanwhile
    int count = pendingLineCount;
    pendingLineCount = 0;
    for (int i = 0; i < count; i++) {
      int line = pendingLines[i];
      if (lastLine != 0) {
        // The line that just finished is the previously-seen line, not line-1: line tables are not
        // guaranteed to be consecutive (gaps, branches), so line-1 would report the wrong line (and
        // miss a Kind.LINE/Where.AFTER probe on the actual previous line).
        onAfterLine(lastLine);
      }
      if (!isConstructor() || isPrologueVisited()) {
        onBeforeLine(line);
      }
      lastLine = line;
    }
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean iface) {
    flushLines();
    boolean beforeConstructor = !isPrologueVisited();
    super.visitMethodInsn(opcode, owner, name, desc, iface);
    if (lastLine != -1 && beforeConstructor) {
      onBeforeLine(lastLine);
    }
  }

  @Override
  public void visitInsn(int opcode) {
    flushLines();
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    flushLines();
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    flushLines();
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    flushLines();
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String desc) {
    flushLines();
    super.visitFieldInsn(opcode, owner, name, desc);
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
    flushLines();
    super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    flushLines();
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLdcInsn(Object value) {
    flushLines();
    super.visitLdcInsn(value);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    flushLines();
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    flushLines();
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    flushLines();
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    flushLines();
    super.visitMultiANewArrayInsn(desc, dims);
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
