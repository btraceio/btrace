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
