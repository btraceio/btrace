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

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.CommandListener;
import io.btrace.runtime.BTraceRuntimes;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Verifies that a probe {@code Class<?>} defined through {@link
 * BTraceRuntime.Impl#defineClass(byte[])} becomes weakly reachable once all caller-held strong
 * references are dropped.
 *
 * <p>This test asserts <strong>weak reachability</strong> only — it does NOT assert that Metaspace
 * has actually unloaded the class, as JVM-level unloading under {@link System#gc()} is not
 * deterministic and is flaky on CI. Weak reachability is the reliable precondition for unloading
 * and is what this test guards.
 *
 * <p>The test exercises whichever {@code BTraceRuntimeImpl_*} the host JDK selects:
 *
 * <ul>
 *   <li>JDK 8 → {@code Unsafe.defineClass} into a fresh {@code new ClassLoader(null){}}.
 *   <li>JDK 9-10 → {@code privateLookupIn(anchor, ...).defineClass} where {@code anchor} is a
 *       per-probe class in a fresh unnamed loader.
 *   <li>JDK 11-14 → same anchor-based path as 9-10.
 *   <li>JDK 15+ path uses {@code defineHiddenClass(code, true)} with no {@code ClassOption}, so the
 *       hidden class is unloadable when its {@code Class<?>} mirror becomes unreachable.
 * </ul>
 *
 * <p>On JDK 15+ the probe's reported {@code ClassLoader} is the loader of {@code Auxiliary} —
 * shared with the agent — so the loader assertion is conditional: we only require the loader to be
 * collected when it is non-null, distinct from the test's own loader, and distinct from the loader
 * that already owns the BTrace runtime classes themselves.
 */
public class ProbeClassUnloadingTest {

  private static final CommandListener NOOP_LISTENER =
      new CommandListener() {
        @Override
        public void onCommand(Command cmd) throws IOException {}
      };

  @Test
  public void sameProbeNameCanBeDefinedTwice() throws Exception {
    // With bootstrap-CL residency this threw LinkageError on the second defineClass.
    // With per-probe ClassLoader / hidden class, each attach gets its own class
    // mirror, so the same internal name is legal.
    String probeName = "io.btrace.runtime.auxiliary.SameNameProbe$" + System.nanoTime();
    byte[] bytes = generateMinimalClass(probeName);
    Class<?> first = defineProbe(probeName, bytes);
    Class<?> second = defineProbe(probeName, bytes);
    Assertions.assertNotNull(first);
    Assertions.assertNotNull(second);
    Assertions.assertNotSame(
        first, second, "Re-define of same-named probe must yield a distinct Class mirror");
    // Clean up both runtimes (removeRuntime is idempotent; second call is a no-op).
    BTraceRuntimes.removeRuntime(probeName);
  }

  private static Class<?> defineProbe(String probeName, byte[] bytes) {
    BTraceRuntime.Impl rt =
        BTraceRuntimes.getRuntime(probeName, new ArgsMap(), NOOP_LISTENER, null);
    Assertions.assertNotNull(rt, "runtime must be created");
    Class<?> probeClass = rt.defineClass(bytes);
    Assertions.assertNotNull(probeClass, "defineClass must succeed");
    // Release the runtime GC root so a subsequent getRuntime() for the same name
    // can install a fresh Impl (and thus a fresh ClassLoader / hidden-class group).
    BTraceRuntimes.removeRuntime(probeName);
    return probeClass;
  }

  @Test
  public void probeClassWeaklyReachableAfterDefine() throws Exception {
    WeakReference<?>[] refs = defineAndDropProbe();
    WeakReference<?> weakClass = refs[0];
    WeakReference<?> weakLoader = refs[1];

    for (int i = 0; i < 30; i++) {
      System.gc();
      System.runFinalization();
      Thread.sleep(50);
      if (weakClass.get() == null && (weakLoader == null || weakLoader.get() == null)) {
        break;
      }
    }

    Assertions.assertNull(
        weakClass.get(),
        "probe Class<?> is still strongly reachable after defineClass() caller drops"
            + " its references");
    if (weakLoader != null) {
      Assertions.assertNull(
          weakLoader.get(),
          "per-probe ClassLoader is still strongly reachable after caller drops" + " references");
    }
  }

  /**
   * All strong references to the probe {@code Class<?>}, its {@code ClassLoader}, and the {@link
   * BTraceRuntime.Impl} live only in this helper method's frame. When it returns, those locals go
   * out of scope, enabling GC to collect the class.
   *
   * <p>Correctness depends on this helper not being inlined into the {@code @Test} method: inlining
   * would keep the Impl and probe-{@code Class<?>} locals alive in the test frame across the GC
   * loop, masking any retention regression.
   */
  // WARNING: do not inline — test depends on helper-frame locals going out of scope before GC
  private static WeakReference<?>[] defineAndDropProbe() {
    // Use the Auxiliary package so the bytes are in the same package as the Lookup
    // class on the JDK 15+ hidden-class path.
    String probeName = "io.btrace.runtime.auxiliary.ProbeX$" + System.nanoTime();
    BTraceRuntime.Impl rt =
        BTraceRuntimes.getRuntime(
            probeName,
            new ArgsMap(),
            new CommandListener() {
              @Override
              public void onCommand(Command cmd) throws IOException {}
            },
            null);
    Assertions.assertNotNull(rt, "runtime must be created");

    byte[] bytes = generateMinimalClass(probeName);
    Class<?> probeClass = rt.defineClass(bytes);
    Assertions.assertNotNull(probeClass, "defineClass must succeed");

    ClassLoader probeLoader = probeClass.getClassLoader();
    ClassLoader agentLoader = BTraceRuntime.class.getClassLoader();
    ClassLoader testLoader = ProbeClassUnloadingTest.class.getClassLoader();
    boolean loaderIsIsolated =
        probeLoader != null && probeLoader != agentLoader && probeLoader != testLoader;

    WeakReference<Class<?>> weakClass = new WeakReference<>(probeClass);
    WeakReference<ClassLoader> weakLoader =
        loaderIsIsolated ? new WeakReference<>(probeLoader) : null;

    // Release the runtime GC root in BTraceRuntimeAccessImpl.runtimes so the class can be
    // collected.
    BTraceRuntimes.removeRuntime(probeName);

    return new WeakReference<?>[] {weakClass, weakLoader};
  }

  /**
   * Generate a minimal valid {@code .class}: a {@code public} class with a public no-arg
   * constructor and a public static {@code handler()} that does nothing.
   */
  private static byte[] generateMinimalClass(String binaryName) {
    String internalName = binaryName.replace('.', '/');
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    MethodVisitor h =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handler", "()V", null, null);
    h.visitCode();
    h.visitInsn(Opcodes.RETURN);
    h.visitMaxs(0, 0);
    h.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
