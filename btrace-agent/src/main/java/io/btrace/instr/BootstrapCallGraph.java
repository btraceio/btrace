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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Walks the static call graph reachable from a root method, collecting every {@link
 * IndyScanner.IndySite} found along the way. Used to audit that BTrace's {@code -javaagent
 * premain()} bootstrap path never reaches a lambda/method-reference expression, whose first
 * execution triggers {@code invokedynamic} linkage that can race the JVM's own concurrent {@code
 * java.lang.invoke} initialization (see the investigation doc referenced by {@link IndyScanner}).
 *
 * <p><b>Precision note:</b> this is a best-effort static analysis, not a precise interprocedural
 * call-graph builder. It follows {@code MethodInsnNode} targets by their <em>declared</em> owner
 * class in the constant pool -- for virtual/interface dispatch, it does not resolve every possible
 * runtime override, only the statically declared target. This is a known gap (a call through an
 * interface to a method with no body at the declared owner yields nothing to scan at that call
 * site), traded for tractability. Recursion is scoped by the caller-supplied {@code inScope}
 * predicate (typically {@code owner -> owner.startsWith("io/btrace/")}), so JDK and third-party
 * classes are never loaded or descended into. It does, however, follow every {@code invokedynamic}
 * site's bootstrap-method {@link Handle} arguments into the synthetic implementation method a
 * lambda/method-reference compiles to, so calls made only from inside a lambda's own body (not
 * visible as a direct {@code MethodInsnNode} edge) are still reached.
 */
public final class BootstrapCallGraph {

  private BootstrapCallGraph() {}

  /** One (owner, name, descriptor) triple identifying a method, used as a visited-set key. */
  private static final class MethodKey {
    final String owner;
    final String name;
    final String desc;

    MethodKey(String owner, String name, String desc) {
      this.owner = owner;
      this.name = name;
      this.desc = desc;
    }

    String label() {
      return owner + "#" + name + desc;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MethodKey)) return false;
      MethodKey k = (MethodKey) o;
      return owner.equals(k.owner) && name.equals(k.name) && desc.equals(k.desc);
    }

    @Override
    public int hashCode() {
      return owner.hashCode() * 31 * 31 + name.hashCode() * 31 + desc.hashCode();
    }
  }

  /** Result of a {@link #scan} call. */
  public static final class Result {
    /** Every invokedynamic call site found in any reachable, in-scope method. */
    public final List<IndyScanner.IndySite> indySites;

    /** Every method actually visited (owner#name+desc), for diagnostics. */
    public final Set<String> visitedMethods;

    Result(List<IndyScanner.IndySite> indySites, Set<String> visitedMethods) {
      this.indySites = indySites;
      this.visitedMethods = visitedMethods;
    }
  }

  /**
   * Scans the call graph reachable from {@code rootOwner#rootName(rootDesc)}, restricted to classes
   * for which {@code inScope} returns {@code true}.
   */
  public static Result scan(
      String rootOwner,
      String rootName,
      String rootDesc,
      Predicate<String> inScope,
      ClassLoader loader) {
    List<IndyScanner.IndySite> indySites = new ArrayList<>();
    Set<String> visited = new LinkedHashSet<>();
    Deque<MethodKey> worklist = new ArrayDeque<>();
    worklist.push(new MethodKey(rootOwner, rootName, rootDesc));
    Set<MethodKey> seen = new LinkedHashSet<>();

    while (!worklist.isEmpty()) {
      MethodKey key = worklist.pop();
      if (!inScope.test(key.owner) || !seen.add(key)) {
        continue;
      }

      byte[] classBytes;
      try {
        classBytes = readClassBytes(key.owner, loader);
      } catch (IOException e) {
        // Class not found/loadable (e.g. a synthetic or generated name) -- skip silently, this
        // is a best-effort scan, not a guarantee of exhaustive coverage.
        continue;
      }

      ClassNode cn = new ClassNode();
      new ClassReader(classBytes).accept(cn, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

      MethodNode method = null;
      for (MethodNode mn : cn.methods) {
        if (mn.name.equals(key.name) && mn.desc.equals(key.desc)) {
          method = mn;
          break;
        }
      }
      if (method == null) {
        // Declared-owner resolution miss (e.g. inherited method not redeclared here) -- skip.
        continue;
      }

      visited.add(key.label());

      for (AbstractInsnNode insn : method.instructions) {
        if (insn instanceof InvokeDynamicInsnNode) {
          InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
          indySites.add(new IndyScanner.IndySite(cn.name, method.name, method.desc, indy.bsm));
          // A lambda/method-ref's own body compiles to a synthetic implementation method
          // (e.g. lambda$main$0) that is only invoked indirectly through the CallSite's
          // MethodHandle at runtime -- there is no MethodInsnNode edge to it from this method.
          // The bootstrap args of a LambdaMetafactory indy site carry a Handle pointing at that
          // implementation method; follow it so calls made only from inside a lambda body (e.g.
          // Main#startServer(), reachable only via the agentThread Runnable's body) are not
          // silently missed.
          for (Object bsmArg : indy.bsmArgs) {
            if (bsmArg instanceof Handle) {
              Handle h = (Handle) bsmArg;
              worklist.push(new MethodKey(h.getOwner(), h.getName(), h.getDesc()));
            }
          }
        } else if (insn instanceof MethodInsnNode) {
          MethodInsnNode call = (MethodInsnNode) insn;
          worklist.push(new MethodKey(call.owner, call.name, call.desc));
        }
      }
    }

    return new Result(indySites, visited);
  }

  private static byte[] readClassBytes(String internalClassName, ClassLoader loader)
      throws IOException {
    String resource = internalClassName + ".class";
    try (InputStream in = loader.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException("resource not found: " + resource);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    }
  }
}
