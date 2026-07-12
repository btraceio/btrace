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

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Finds every {@code invokedynamic} call site in a class file. In bytecode compiled at Java 8
 * source/target level (as this codebase's core modules are), {@code invokedynamic} is emitted
 * exclusively for lambda expressions and method references (bootstrapped via {@code
 * LambdaMetafactory}) -- string concatenation and other {@code invokedynamic} uses introduced by
 * later javac versions are not present at that bytecode level.
 *
 * <p>Used to enforce that BTrace's {@code -javaagent premain()} bootstrap path never triggers a
 * <em>first-time</em> {@code invokedynamic} linkage, which can race the JVM's own concurrent
 * initialization of shared {@code java.lang.invoke} bootstrap classes and throw {@code
 * ClassCircularityError}.
 */
public final class IndyScanner {

  private IndyScanner() {}

  /** One {@code invokedynamic} call site found in a scanned class. */
  public static final class IndySite {
    public final String className;
    public final String methodName;
    public final String methodDescriptor;
    public final String bootstrapMethodOwner;

    IndySite(String className, String methodName, String methodDescriptor, Handle bootstrap) {
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
      this.bootstrapMethodOwner = bootstrap.getOwner();
    }

    @Override
    public String toString() {
      return className
          + "#"
          + methodName
          + methodDescriptor
          + " (bootstrap: "
          + bootstrapMethodOwner
          + ")";
    }
  }

  /** Scans one class file's bytes and returns every {@code invokedynamic} call site found. */
  public static List<IndySite> scan(byte[] classBytes) {
    ClassNode cn = new ClassNode();
    new ClassReader(classBytes).accept(cn, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

    List<IndySite> sites = new ArrayList<>();
    for (MethodNode mn : cn.methods) {
      for (AbstractInsnNode insn : mn.instructions) {
        if (insn instanceof InvokeDynamicInsnNode) {
          InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
          sites.add(new IndySite(cn.name, mn.name, mn.desc, indy.bsm));
        }
      }
    }
    return sites;
  }
}
