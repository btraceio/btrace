/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
