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

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author Jaroslav Bachorik
 */
public class StackTrackingMethodVisitorTest extends InstrumentorTestBase {
  private ClassReader reader;

  @BeforeAll
  public static void setUpClass() {}

  @AfterAll
  public static void tearDownClass() {}

  @BeforeEach
  public void setUp() throws IOException {
    byte[] data = loadTargetClass("StackTrackerTest");
    System.err.println(asmify(data));
    reader = new ClassReader(data);
  }

  @AfterEach
  @Override
  public void tearDown() {}

  @Test
  public void sanityTrackerTest() throws Exception {
    // just make sure that a sufficiently complex methods won't cause
    // any problems for tracking the stack
    reader.accept(
        new ClassVisitor(Opcodes.ASM9) {
          private String clzName;

          @Override
          public void visit(
              int i, int i1, String className, String string1, String string2, String[] strings) {
            this.clzName = className;
            super.visit(i, i1, className, string1, string2, strings);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
            return new StackTrackingMethodVisitor(
                mv, clzName, desc, ((access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC));
          }
        },
        ClassReader.EXPAND_FRAMES);
  }
}
