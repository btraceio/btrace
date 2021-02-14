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

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * This visitor helps in inserting code whenever an exception is caught or finally block is reached.
 * The code to insert on exception catch or finally may be decided by derived class. By default,
 * this class inserts code to print a message.
 *
 * @author A. Sundararajan
 */
public class CatchInstrumentor extends MethodInstrumentor {
  private final Map<Label, String> handlers = new HashMap<>();

  public CatchInstrumentor(
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
  public void visitLabel(Label label) {
    super.visitLabel(label);
    String catchType = handlers.get(label);
    if (catchType != null) {
      insertFrameReplaceStack(label, Type.getObjectType(catchType));
      onCatch(catchType);
    }
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
    if (type != null) {
      handlers.put(handler, type);
    }
    super.visitTryCatchBlock(start, end, handler, type);
  }

  protected void onCatch(String type) {
    asm.println("catching " + type);
  }
}
