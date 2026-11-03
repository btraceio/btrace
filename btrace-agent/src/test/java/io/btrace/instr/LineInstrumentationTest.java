/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** {@code Kind.LINE} locations on lines that start where the method has a stack map frame. */
class LineInstrumentationTest extends InstrumentorTestBase {
  @Test
  void probeOnEveryLinePassesVerification() throws Exception {
    loadTargetClass("OnMethodTest");
    // Lines in OnMethodTest start at catch handlers and at join points
    transform("onmethod/AllLines");

    assertNotSame(originalBC, transformedBC, "the probe was not applied");
    assertVerifies();
  }

  @Test
  void probeOnEveryLineOfOneMethodLeavesTheOtherMethodsAlone() throws Exception {
    loadTargetClass("OnMethodTest");
    transform("onmethod/LineInMethod");

    assertEquals(Collections.singleton("caught"), methodsCallingHandler("LineInMethod$args"));
    assertVerifies();
  }

  private Set<String> methodsCallingHandler(String handlerSuffix) {
    ClassNode cn = new ClassNode();
    new ClassReader(transformedBC).accept(cn, 0);
    Set<String> methods = new TreeSet<>();
    for (MethodNode mn : cn.methods) {
      for (AbstractInsnNode insn : mn.instructions) {
        if (insn instanceof InvokeDynamicInsnNode
            && ((InvokeDynamicInsnNode) insn).name.endsWith(handlerSuffix)) {
          methods.add(mn.name);
        }
      }
    }
    return methods;
  }
}
