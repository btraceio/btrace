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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * A hacked version of <a
 * href="http://asm.ow2.org/asm50/javadoc/user/org/objectweb/asm/ClassWriter.html">ClassWriter</a>
 * allowing to plug-in instrumentation providers and instrument class in single invocation. Also, it
 * provides a smart and lightweight common supertype resolution method for computing frames.
 *
 * <p>The class is not thread-safe but since there is exactly one instance per instrumented class
 * there is no chance of parallel access ever happening.
 *
 * @author Jaroslav Bachorik
 */
final class BTraceClassWriter extends ClassWriter {
  private final Deque<Instrumentor> instrumentors = new ArrayDeque<>();
  private final ClassLoader targetCL;
  private final BTraceClassReader cr;

  BTraceClassWriter(ClassLoader cl, int flags) {
    super(flags);
    targetCL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    cr = null;
  }

  BTraceClassWriter(ClassLoader cl, BTraceClassReader reader, int flags) {
    super(reader, flags);
    targetCL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    cr = reader;
  }

  public void addInstrumentor(BTraceProbe bp) {
    addInstrumentor(bp, null);
  }

  public void addInstrumentor(BTraceProbe bp, ClassLoader cl) {
    if (cr != null && bp != null) {
      Instrumentor top = instrumentors.peekLast();
      ClassVisitor parent = top != null ? top : this;
      Instrumentor i = Instrumentor.create(cr, bp, parent, cl);
      if (i != null) {
        instrumentors.add(i);
      }
    }
  }

  public byte[] instrument() {
    boolean hit = false;
    if (instrumentors.isEmpty()) return null;

    Instrumentor top = instrumentors.peekLast();
    ClassVisitor cv = top != null ? top : this;
    InstrumentUtils.accept(cr, cv);
    for (Instrumentor i : instrumentors) {
      hit |= i.hasMatch();
    }
    return hit ? toByteArray() : null;
  }

  @Override
  protected String getCommonSuperClass(String type1, String type2) {
    // Using type closures resolved via the associate classloader
    LinkedHashSet<String> type1Closure = new LinkedHashSet<>();
    LinkedHashSet<String> type2Closure = new LinkedHashSet<>();
    InstrumentUtils.collectHierarchyClosure(targetCL, type1, type1Closure, true);
    InstrumentUtils.collectHierarchyClosure(targetCL, type2, type2Closure, true);
    // basically, do intersection
    type1Closure.retainAll(type2Closure);

    // if the intersection is not empty the first element is the closest common ancestor
    Iterator<String> iter = type1Closure.iterator();
    if (iter.hasNext()) {
      return iter.next();
    }
    return Constants.OBJECT_INTERNAL;
  }
}
