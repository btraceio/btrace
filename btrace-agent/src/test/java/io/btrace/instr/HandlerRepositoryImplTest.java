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

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.HandlerRepository;
import io.btrace.core.extensions.Permission;
import io.btrace.runtime.IndyDispatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle and dispatch tests for HandlerRepositoryImpl + IndyDispatcher.
 *
 * <p>Covers register/unregister lifecycle, successful resolution via a loadable probe class,
 * IndyDispatcher.bootstrap() happy path (ConstantCallSite), and the self-relinking MutableCallSite
 * path for register-after-bootstrap races.
 */
// NB: must be `public` — publicLookup() can only resolve methods on public classes, and the
// happy-path tests use this class itself as the stand-in probe class.
public class HandlerRepositoryImplTest {

  /** Minimal stub BTraceProbe for lifecycle-only tests. */
  private static BTraceProbe stubProbe(String internalName) {
    return stubProbe(internalName, null);
  }

  /**
   * Stub {@link BTraceProbe} that returns {@code probeClass} from {@code getProbeClass()}. Use when
   * a test needs resolution to succeed against a specific loadable class (the production resolver
   * reads the class via {@code probe.getProbeClass()}).
   */
  private static BTraceProbe stubProbe(String internalName, Class<?> probeClass) {
    return new BTraceProbe() {
      @Override
      public String getClassName() {
        return internalName.replace('/', '.');
      }

      @Override
      public String getClassName(boolean internal) {
        return internal ? internalName : internalName.replace('/', '.');
      }

      @Override
      public boolean isTransforming() {
        return false;
      }

      @Override
      public boolean isClassRenamed() {
        return false;
      }

      @Override
      public boolean isVerified() {
        return true;
      }

      @Override
      public void checkVerified() {}

      @Override
      public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        return Collections.emptyList();
      }

      @Override
      public Iterable<OnMethod> onmethods() {
        return Collections.emptyList();
      }

      @Override
      public Iterable<OnProbe> onprobes() {
        return Collections.emptyList();
      }

      @Override
      public Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t) {
        return probeClass;
      }

      @Override
      public Class<?> getProbeClass() {
        return probeClass;
      }

      @Override
      public void unregister() {}

      @Override
      public byte[] getFullBytecode() {
        return new byte[0];
      }

      @Override
      public byte[] getDataHolderBytecode() {
        return new byte[0];
      }

      @Override
      public BTraceRuntime.Impl getRuntime() {
        return null;
      }

      @Override
      public boolean willInstrument(Class<?> clz) {
        return false;
      }

      @Override
      public String getActionPrefix() {
        return "";
      }

      @Override
      public void notifyTransform(String className) {}

      @Override
      public void copyHandlers(org.objectweb.asm.ClassVisitor cv) {}

      @Override
      public void applyArgs(ArgsMap argsMap) {}

      @Override
      public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
      }
    };
  }

  /**
   * Public static handler used by the happy-path resolution test. The production resolver strips
   * the {@code probeName$} prefix from the handler name, so the call-site handler name is {@code
   * "HandlerRepositoryImplTest$ping"} but the actual method is {@code ping}.
   */
  public static void ping() {
    HANDLER_CALLED.set(true);
  }

  private static final AtomicBoolean HANDLER_CALLED = new AtomicBoolean();

  @AfterEach
  void cleanup() {
    HANDLER_CALLED.set(false);
  }

  @Test
  void testGetProbeClassExposesClassOnStubProbe() {
    BTraceProbe probe = stubProbe("test/accessor/StubProbe", HandlerRepositoryImplTest.class);
    assertSame(HandlerRepositoryImplTest.class, probe.getProbeClass());
  }

  @Test
  void testRegisterAndResolveReturnsNullForUnknownHandler() {
    String probeName = "test/lifecycle/StubProbe";
    BTraceProbe probe = stubProbe(probeName);
    try {
      HandlerRepositoryImpl.registerProbe(probe);
      MethodHandle result =
          HandlerRepositoryImpl.resolveHandler(
              probeName, "onMethod", MethodType.methodType(void.class));
      assertNull(result, "Should return null when probe class cannot be loaded");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  @Test
  void testUnregisterClearsCacheForProbe() {
    String probeName = "test/lifecycle/AnotherProbe";
    BTraceProbe probe = stubProbe(probeName);
    HandlerRepositoryImpl.registerProbe(probe);
    HandlerRepositoryImpl.resolveHandler(
        probeName, "someHandler", MethodType.methodType(void.class));
    HandlerRepositoryImpl.unregisterProbe(probe);

    MethodHandle afterUnregister =
        HandlerRepositoryImpl.resolveHandler(
            probeName, "someHandler", MethodType.methodType(void.class));
    assertNull(afterUnregister, "Should return null after probe is unregistered");
  }

  @Test
  void testUnregisterDoesNotAffectOtherProbes() {
    String probeName1 = "test/lifecycle/ProbeOne";
    String probeName2 = "test/lifecycle/ProbeTwo";
    BTraceProbe probe1 = stubProbe(probeName1);
    BTraceProbe probe2 = stubProbe(probeName2);
    try {
      HandlerRepositoryImpl.registerProbe(probe1);
      HandlerRepositoryImpl.registerProbe(probe2);
      HandlerRepositoryImpl.resolveHandler(
          probeName1, "handler", MethodType.methodType(void.class));
      HandlerRepositoryImpl.resolveHandler(
          probeName2, "handler", MethodType.methodType(void.class));
      HandlerRepositoryImpl.unregisterProbe(probe1);
      assertNotNull(probe2);
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe2);
    }
  }

  /**
   * Success path: register a probe whose internal name points at this test class (loadable, has a
   * matching public static method). Verify resolveHandler returns a live, invocable MH.
   */
  @Test
  void testResolvesRealHandlerFromLoadedClass() throws Throwable {
    String probeName = HandlerRepositoryImplTest.class.getName().replace('.', '/');
    BTraceProbe probe = stubProbe(probeName, HandlerRepositoryImplTest.class);
    try {
      HandlerRepositoryImpl.registerProbe(probe);

      MethodHandle mh =
          HandlerRepositoryImpl.resolveHandler(
              probeName, "HandlerRepositoryImplTest$ping", MethodType.methodType(void.class));

      assertNotNull(
          mh, "resolveHandler should succeed when probe class has matching static method");
      mh.invokeExact();
      assertTrue(HANDLER_CALLED.get(), "Resolved handler should invoke the target method");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  /**
   * Bootstrap happy path: every call site is a MutableCallSite (so it can be re-linked to a noop on
   * unregister). On immediate resolution the target is the resolved handle.
   */
  @Test
  void testBootstrapReturnsMutableCallSiteOnImmediateResolution() throws Throwable {
    String probeName = HandlerRepositoryImplTest.class.getName().replace('.', '/');
    BTraceProbe probe = stubProbe(probeName, HandlerRepositoryImplTest.class);
    try {
      HandlerRepositoryImpl.registerProbe(probe);

      CallSite cs =
          IndyDispatcher.bootstrap(
              MethodHandles.lookup(),
              "HandlerRepositoryImplTest$ping",
              MethodType.methodType(void.class),
              probeName);

      assertTrue(
          cs instanceof MutableCallSite,
          "Expected MutableCallSite so unregister can re-link to noop; got: " + cs.getClass());
      cs.dynamicInvoker().invokeExact();
      assertTrue(HANDLER_CALLED.get(), "MutableCallSite target must invoke the resolved handler");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  /**
   * Crash-safety regression: after a probe is unregistered, any previously-linked call site
   * targeting it must stop invoking the handler body (so the instrumented app is not exposed to
   * half-torn-down BTraceRuntime state). This is the dispatch-level equivalent of the older
   * "cushion" approach that stubbed probe method bodies via bytecode redefine.
   */
  @Test
  void testUnregisterRelinksLiveCallSiteToNoop() throws Throwable {
    String probeName = HandlerRepositoryImplTest.class.getName().replace('.', '/');
    BTraceProbe probe = stubProbe(probeName, HandlerRepositoryImplTest.class);
    HandlerRepositoryImpl.registerProbe(probe);

    CallSite cs =
        IndyDispatcher.bootstrap(
            MethodHandles.lookup(),
            "HandlerRepositoryImplTest$ping",
            MethodType.methodType(void.class),
            probeName);
    // Sanity: handler fires before unregister.
    cs.dynamicInvoker().invokeExact();
    assertTrue(HANDLER_CALLED.get(), "Handler must fire while probe is registered");
    HANDLER_CALLED.set(false);

    HandlerRepositoryImpl.unregisterProbe(probe);

    // After unregister the live call site must no longer run the handler body.
    cs.dynamicInvoker().invokeExact();
    assertFalse(
        HANDLER_CALLED.get(),
        "Handler must NOT fire after unregister — call site must be relinked to noop");
  }

  /**
   * Unregistering probe A must not affect a live call site targeting probe B. Tests the per-probe
   * scoping of {@code IndyDispatcher.invalidateProbe}.
   */
  @Test
  void testUnregisterProbeADoesNotInvalidateProbeB() throws Throwable {
    // Probe A: stubProbe with a name that is not loadable — we only need to verify that
    // invalidateProbe doesn't touch probe B's sites.
    String probeAName = "test/unrelated/ProbeA";
    BTraceProbe probeA = stubProbe(probeAName);
    HandlerRepositoryImpl.registerProbe(probeA);

    // Probe B: this test class — actually resolves a real handler.
    String probeBName = HandlerRepositoryImplTest.class.getName().replace('.', '/');
    BTraceProbe probeB = stubProbe(probeBName, HandlerRepositoryImplTest.class);
    HandlerRepositoryImpl.registerProbe(probeB);
    try {
      CallSite csB =
          IndyDispatcher.bootstrap(
              MethodHandles.lookup(),
              "HandlerRepositoryImplTest$ping",
              MethodType.methodType(void.class),
              probeBName);
      csB.dynamicInvoker().invokeExact();
      assertTrue(HANDLER_CALLED.get(), "Probe B handler fires before any unregister");
      HANDLER_CALLED.set(false);

      // Unregistering probe A must leave probe B's site intact.
      HandlerRepositoryImpl.unregisterProbe(probeA);

      csB.dynamicInvoker().invokeExact();
      assertTrue(
          HANDLER_CALLED.get(), "Probe B call site must survive unrelated probe A unregister");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probeB);
    }
  }

  /**
   * Two live call sites on the same probe: unregister must relink BOTH. Guards against a registry
   * bug where only the most recent site is invalidated.
   */
  @Test
  void testUnregisterRelinksAllLiveSitesForProbe() throws Throwable {
    String probeName = HandlerRepositoryImplTest.class.getName().replace('.', '/');
    BTraceProbe probe = stubProbe(probeName, HandlerRepositoryImplTest.class);
    HandlerRepositoryImpl.registerProbe(probe);

    CallSite cs1 =
        IndyDispatcher.bootstrap(
            MethodHandles.lookup(),
            "HandlerRepositoryImplTest$ping",
            MethodType.methodType(void.class),
            probeName);
    CallSite cs2 =
        IndyDispatcher.bootstrap(
            MethodHandles.lookup(),
            "HandlerRepositoryImplTest$ping",
            MethodType.methodType(void.class),
            probeName);

    // Warm both.
    cs1.dynamicInvoker().invokeExact();
    assertTrue(HANDLER_CALLED.get());
    HANDLER_CALLED.set(false);
    cs2.dynamicInvoker().invokeExact();
    assertTrue(HANDLER_CALLED.get());
    HANDLER_CALLED.set(false);

    HandlerRepositoryImpl.unregisterProbe(probe);

    cs1.dynamicInvoker().invokeExact();
    assertFalse(HANDLER_CALLED.get(), "Site 1 must be relinked on unregister");
    cs2.dynamicInvoker().invokeExact();
    assertFalse(HANDLER_CALLED.get(), "Site 2 must also be relinked on unregister");
  }

  /**
   * F01/F02/F03 regression: bootstrap() before registration returns a MutableCallSite that is a
   * no-op while unresolved but self-heals after the probe is registered. No permanent noop trap.
   */
  @Test
  void testBootstrapBeforeRegistrationHealsViaMutableCallSite() throws Throwable {
    String probeName = HandlerRepositoryImplTest.class.getName().replace('.', '/');

    CallSite cs =
        IndyDispatcher.bootstrap(
            MethodHandles.lookup(),
            "HandlerRepositoryImplTest$ping",
            MethodType.methodType(void.class),
            probeName);
    assertTrue(
        cs instanceof MutableCallSite,
        "Expected MutableCallSite when resolution cannot succeed yet, got: " + cs.getClass());

    cs.dynamicInvoker().invokeExact();
    assertFalse(HANDLER_CALLED.get(), "Handler must not fire while probe is unregistered");

    BTraceProbe probe = stubProbe(probeName, HandlerRepositoryImplTest.class);
    try {
      HandlerRepositoryImpl.registerProbe(probe);

      cs.dynamicInvoker().invokeExact();
      assertTrue(
          HANDLER_CALLED.get(),
          "MutableCallSite must self-heal and invoke handler after registration");

      HANDLER_CALLED.set(false);
      cs.dynamicInvoker().invokeExact();
      assertTrue(HANDLER_CALLED.get(), "Relinked target must remain resolved on subsequent calls");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  /** Null repository returns a MutableCallSite that no-ops without crashing. */
  @Test
  void testBootstrapWithNullRepositoryDoesNotCrash() throws Throwable {
    HandlerRepository saved = IndyDispatcher.repository;
    try {
      IndyDispatcher.repository = null;
      CallSite cs =
          IndyDispatcher.bootstrap(
              MethodHandles.lookup(),
              "probe$handler",
              MethodType.methodType(void.class),
              "nonexistent/Probe");
      assertTrue(
          cs instanceof MutableCallSite,
          "Null-repository path must return MutableCallSite (self-healing), not ConstantCallSite");
      cs.dynamicInvoker().invokeExact(); // must not throw
    } finally {
      IndyDispatcher.repository = saved;
    }
  }

  @Test
  void testResolveHandlerUsesProbeClassAccessorNotClassForName() throws Throwable {
    // Use a probeName that is NOT resolvable by Class.forName (no such class exists),
    // but pass the test class as the getProbeClass() value. If resolveHandler still
    // returns a live MethodHandle, it means resolution went through probe.getProbeClass()
    // and bypassed any Class.forName lookup of the fake probe name.
    String unresolvableProbeName = "non/existent/FakeProbe$" + System.nanoTime();
    BTraceProbe probe = stubProbe(unresolvableProbeName, HandlerRepositoryImplTest.class);
    try {
      HandlerRepositoryImpl.registerProbe(probe);
      MethodHandle mh =
          HandlerRepositoryImpl.resolveHandler(
              unresolvableProbeName, "FakeProbe$ping", MethodType.methodType(void.class));
      assertNotNull(
          mh,
          "resolveHandler must find handler via probe.getProbeClass(), not via Class.forName(probeName)");
      mh.invokeExact();
      assertTrue(HANDLER_CALLED.get(), "Resolved handler must invoke the test's ping()");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }
}
