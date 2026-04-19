/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 */
package org.openjdk.btrace.instr;

import java.io.IOException;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.runtime.BTraceRuntimes;

/**
 * Verifies that a probe {@code Class<?>} defined through
 * {@link BTraceRuntime.Impl#defineClass(byte[])} becomes weakly reachable once
 * all caller-held strong references are dropped.
 *
 * <p>This test asserts <strong>weak reachability</strong> only — it does NOT assert that
 * Metaspace has actually unloaded the class, as JVM-level unloading under
 * {@link System#gc()} is not deterministic and is flaky on CI. Weak reachability is the
 * reliable precondition for unloading and is what this test guards.
 *
 * <p>The test exercises whichever {@code BTraceRuntimeImpl_*} the host JDK selects:
 * <ul>
 *   <li>JDK 8  → {@code Unsafe.defineClass} into a fresh {@code new ClassLoader(null){}}.</li>
 *   <li>JDK 9-10 → {@code privateLookupIn(anchor, ...).defineClass} where {@code anchor}
 *       is a per-probe class in a fresh unnamed loader.</li>
 *   <li>JDK 11-14 → same anchor-based path as 9-10.</li>
 *   <li>JDK 15+ path uses {@code defineHiddenClass(code, true)} with no
 *       {@code ClassOption}, so the hidden class is unloadable when its
 *       {@code Class<?>} mirror becomes unreachable.</li>
 * </ul>
 *
 * <p>On JDK 15+ the probe's reported {@code ClassLoader} is the loader of
 * {@code Auxiliary} — shared with the agent — so the loader assertion is conditional:
 * we only require the loader to be collected when it is non-null, distinct from the
 * test's own loader, and distinct from the loader that already owns the BTrace runtime
 * classes themselves.
 */
public class ProbeClassUnloadingTest {

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
          "per-probe ClassLoader is still strongly reachable after caller drops"
              + " references");
    }
  }

  /**
   * All strong references to the probe {@code Class<?>}, its {@code ClassLoader}, and
   * the {@link BTraceRuntime.Impl} live only in this helper method's frame. When it
   * returns, those locals go out of scope, enabling GC to collect the class.
   *
   * <p>Correctness depends on this helper not being inlined into the {@code @Test}
   * method: inlining would keep the Impl and probe-{@code Class<?>} locals alive in the
   * test frame across the GC loop, masking any retention regression.
   */
  // WARNING: do not inline — test depends on helper-frame locals going out of scope before GC
  private static WeakReference<?>[] defineAndDropProbe() {
    // Use the Auxiliary package so the bytes are in the same package as the Lookup
    // class on the JDK 15+ hidden-class path.
    String probeName = "org.openjdk.btrace.runtime.auxiliary.ProbeX$" + System.nanoTime();
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

    // Release the runtime GC root in BTraceRuntimeAccessImpl.runtimes so the class can be collected.
    BTraceRuntimes.removeRuntime(probeName);

    return new WeakReference<?>[] {weakClass, weakLoader};
  }

  /**
   * Generate a minimal valid {@code .class}: a {@code public} class with a public
   * no-arg constructor and a public static {@code handler()} that does nothing.
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
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    MethodVisitor h =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handler", "()V", null, null);
    h.visitCode();
    h.visitInsn(Opcodes.RETURN);
    h.visitMaxs(0, 0);
    h.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
