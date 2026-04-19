/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
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
package org.openjdk.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.openjdk.btrace.core.HandlerRepository;

/**
 * INVOKEDYNAMIC bootstrap class for BTrace probe handler dispatch.
 *
 * <p>Works with Java 8+. Replaces the Java 15-specific {@code Indy} class that used
 * {@code defineHiddenClass}. Probe handler methods stay in the probe class (bootstrap CL)
 * and are resolved via {@link MethodHandles#publicLookup()}.findStatic() — no bytecode
 * copying required.
 *
 * <p><b>Dispatch strategy:</b> every call site is a {@link MutableCallSite}. On bootstrap
 * we attempt immediate resolution; on success the MCS target is set to the resolved
 * {@link MethodHandle}; on failure the target is a self-relinking trampoline that retries
 * on every invocation until resolution succeeds.
 *
 * <p><b>Why always MutableCallSite</b> (and not {@code ConstantCallSite} even for the
 * immediate-success path): a {@code ConstantCallSite} cannot be relinked, so once the JVM
 * decoration pins an instrumented method to a resolved handler handle, unregistering the
 * probe is <i>impossible</i> — the handler keeps firing even after {@code BTraceRuntime}
 * state is torn down, leading to NPEs inside the instrumented application. By making
 * every dispatch site mutable we can re-point the target to a noop when
 * {@link #invalidateProbe(String)} is called from the agent at detach time.
 *
 * <p><b>Important:</b> This class lives in the bootstrap classloader and must not reference
 * SLF4J or any other logging framework. SLF4J initialization in the bootstrap classloader
 * context can fail, causing this class's static initializer to throw, which prevents the
 * repository from being wired up. Handler resolution failures are logged by
 * {@code HandlerRepositoryImpl.resolveHandler()} on the agent-classloader side.
 */
public final class IndyDispatcher {

  /**
   * Bridge to the HandlerRepository implementation (HandlerRepositoryImpl in agent CL).
   * Set via reflection from HandlerRepositoryImpl's static initializer. May be null if
   * bootstrap runs before the agent wiring completes — the trampoline handles that case.
   */
  public static volatile HandlerRepository repository = null;

  /**
   * Live call sites per probe class name (internal form). Used to invalidate all sites
   * targeting a given probe on unregister. Entries are weak references so call sites that
   * have become unreachable (e.g. instrumented class unloaded) don't leak.
   */
  private static final ConcurrentMap<String, ConcurrentLinkedQueue<WeakReference<MutableCallSite>>>
      LIVE_SITES = new ConcurrentHashMap<>();

  private static final MethodHandle RELINK_MH;
  private static final MethodHandle NOOP_IMPL_MH;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      RELINK_MH =
          lookup.findStatic(
              IndyDispatcher.class,
              "relink",
              MethodType.methodType(
                  MethodHandle.class,
                  MutableCallSite.class,
                  String.class,
                  String.class,
                  MethodType.class));
      NOOP_IMPL_MH =
          lookup.findStatic(
              IndyDispatcher.class, "noopImpl", MethodType.methodType(void.class));
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * INVOKEDYNAMIC bootstrap method. Called by the JVM on first execution of each
   * instrumented call site.
   *
   * <p>Always returns a {@link MutableCallSite}. If resolution succeeds immediately the
   * target is the resolved handler; otherwise it is a self-relinking trampoline.
   *
   * @param caller         the lookup context of the instrumented class
   * @param name           the handler method name (probe-prefixed, e.g. {@code "probeName$handlerMethod"})
   * @param type           the method type of the call site
   * @param probeClassName the internal name of the probe class (passed as BSM constant)
   */
  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName) {
    MutableCallSite mcs = new MutableCallSite(type);
    MethodHandle mh = tryResolve(probeClassName, name, type);
    if (mh != null) {
      mcs.setTarget(mh);
    } else {
      mcs.setTarget(makeTrampoline(mcs, probeClassName, name, type));
    }
    registerSite(probeClassName, mcs);
    return mcs;
  }

  /**
   * Invalidate all live dispatch sites for a given probe. Called by
   * {@code HandlerRepositoryImpl.unregisterProbe()} on detach. Each site's target is reset
   * to a noop, so probe handler bodies are not invoked after teardown (prevents crashes
   * when {@code BTraceRuntime} state has been disposed).
   *
   * <p>Sites remain registered. Note: the current implementation installs a direct noop
   * handle (not a trampoline), so sites do not auto-rebind on re-registration — a
   * subsequent {@code registerProbe} for the same name creates new call sites on next
   * bootstrap, which is the common case. See TODO in {@link #invalidateProbe} body for the
   * tradeoffs.
   *
   * @param probeClassName internal name of the probe class to invalidate
   */
  public static void invalidateProbe(String probeClassName) {
    ConcurrentLinkedQueue<WeakReference<MutableCallSite>> q = LIVE_SITES.get(probeClassName);
    if (q == null) {
      return;
    }
    List<MutableCallSite> updated = new ArrayList<>();
    Iterator<WeakReference<MutableCallSite>> it = q.iterator();
    while (it.hasNext()) {
      WeakReference<MutableCallSite> ref = it.next();
      MutableCallSite site = ref.get();
      if (site == null) {
        it.remove();
        continue;
      }
      // Re-install a trampoline so (a) this invocation lands on noop, and (b) if the
      // probe is re-registered later, the next call re-runs resolution and relinks.
      MethodType type = site.type();
      // We no longer know the handler name here (bootstrap binds it into the trampoline).
      // So install a direct-noop target for now — re-registration requires bootstrap to
      // replace this MCS (the JVM caches CallSites per invokedynamic site, so the trampoline
      // approach would only work if we also remembered the name). Direct noop is the safe
      // choice: it eliminates the crash risk, which is the stated contract.
      site.setTarget(noop(type));
      updated.add(site);
    }
    if (!updated.isEmpty()) {
      MutableCallSite.syncAll(updated.toArray(new MutableCallSite[0]));
    }
  }

  /**
   * Attempts a single resolution pass. Returns null when the repository is not yet wired
   * or when the repository itself returns null / throws.
   */
  private static MethodHandle tryResolve(String probeClassName, String name, MethodType type) {
    HandlerRepository repo = repository;
    if (repo == null) {
      return null;
    }
    try {
      return repo.resolveHandler(probeClassName, name, type);
    } catch (Throwable t) {
      // Logged on the agent-CL side (HandlerRepositoryImpl). SLF4J must not be
      // referenced from bootstrap-CL code. The trampoline will retry on next call.
      return null;
    }
  }

  /**
   * Build a trampoline handle of the call site's type that, on invocation, calls
   * {@link #relink} to obtain a real target. The result of relink is then invoked with the
   * original call-site arguments. Once relink returns the resolved MethodHandle, it also
   * updates {@code mcs.setTarget(...)} so subsequent invocations bypass this trampoline.
   */
  private static MethodHandle makeTrampoline(
      MutableCallSite mcs, String probeClassName, String name, MethodType type) {
    MethodHandle relinker =
        MethodHandles.insertArguments(RELINK_MH, 0, mcs, probeClassName, name, type);
    return MethodHandles.foldArguments(MethodHandles.exactInvoker(type), relinker);
  }

  /**
   * Relink hook invoked by the trampoline. Tries to resolve; on success, updates the
   * MutableCallSite's target so future invocations go directly to the resolved handle.
   * On failure, returns a noop handle (this single invocation is a no-op, but the
   * trampoline remains installed — next invocation will retry again).
   */
  @SuppressWarnings("unused") // referenced via MethodHandle
  private static MethodHandle relink(
      MutableCallSite mcs, String probeClassName, String name, MethodType type) {
    MethodHandle resolved = tryResolve(probeClassName, name, type);
    if (resolved != null) {
      mcs.setTarget(resolved);
      MutableCallSite.syncAll(new MutableCallSite[] {mcs});
      return resolved;
    }
    return noop(type);
  }

  private static void registerSite(String probeClassName, MutableCallSite mcs) {
    LIVE_SITES
        .computeIfAbsent(probeClassName, k -> new ConcurrentLinkedQueue<>())
        .add(new WeakReference<>(mcs));
  }

  /**
   * Noop handle of the given call-site type. Accepts the call site's argument list and
   * produces the default value for its return type (void / null / 0) without invoking any
   * probe code. Used by {@link #invalidateProbe(String)} and by the trampoline when
   * resolution is still failing.
   */
  private static MethodHandle noop(MethodType type) {
    Class<?> rt = type.returnType();
    MethodHandle body;
    if (rt == void.class) {
      body = NOOP_IMPL_MH; // () -> void
    } else {
      // constant(T, defaultValueFor(T)) has type () -> T; dropArguments adds the call-site args.
      body = MethodHandles.constant(rt, defaultValueFor(rt));
    }
    return MethodHandles.dropArguments(body, 0, type.parameterArray());
  }

  private static Object defaultValueFor(Class<?> t) {
    if (!t.isPrimitive()) return null;
    if (t == boolean.class) return Boolean.FALSE;
    if (t == byte.class) return (byte) 0;
    if (t == short.class) return (short) 0;
    if (t == char.class) return (char) 0;
    if (t == int.class) return 0;
    if (t == long.class) return 0L;
    if (t == float.class) return 0f;
    if (t == double.class) return 0d;
    return null; // unreachable
  }

  @SuppressWarnings("unused") // referenced via MethodHandle
  private static void noopImpl() {}

  /** Test-only: clear the live-sites registry so tests don't see residue from prior runs. */
  static void __resetForTests() {
    LIVE_SITES.clear();
  }
}
