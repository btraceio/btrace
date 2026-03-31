package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.HandlerRepository;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.indy.IndyDispatcher;

/**
 * Tests for HandlerRepositoryImpl: resolveHandler, resolveRuntime, and unregisterProbe cache
 * invalidation.
 */
public class HandlerRepositoryImplTest {

  /**
   * Public static handler class so MethodHandles.publicLookup() can find its methods.
   */
  public static class TestHandlers {
    public static void onEntry() {}

    public static void onEntryWithArgs(String pcn, String pmn) {}

    public static boolean hit(int mid) {
      return true;
    }
  }

  @BeforeAll
  static void loadHandlerRepository() throws Exception {
    // Trigger HandlerRepositoryImpl static initializer, which sets IndyDispatcher.repository
    Class.forName("org.openjdk.btrace.instr.HandlerRepositoryImpl");
  }

  private static HandlerRepository repo() {
    return IndyDispatcher.repository;
  }

  // ---------------------------------------------------------------------------
  // resolveHandler
  // ---------------------------------------------------------------------------

  @Test
  void resolveHandler_returnsNull_whenProbeNotRegistered() {
    MethodHandle mh =
        repo()
            .resolveHandler(
                "test/nonexistent/Probe",
                "onEntry",
                MethodType.methodType(void.class));
    assertNull(mh);
  }

  @Test
  void resolveHandler_returnsNull_whenDefinedClassIsNull() {
    String probeName = "test/NullClassProbe";
    BTraceProbe probe = stubProbe(probeName, null);
    HandlerRepositoryImpl.registerProbe(probe);
    try {
      MethodHandle mh =
          repo()
              .resolveHandler(probeName, "onEntry", MethodType.methodType(void.class));
      assertNull(mh);
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  @Test
  void resolveHandler_returnsMethodHandle_forRegisteredProbe() throws Throwable {
    String probeName = "test/SimpleProbe";
    BTraceProbe probe = stubProbe(probeName, TestHandlers.class);
    HandlerRepositoryImpl.registerProbe(probe);
    try {
      MethodHandle mh =
          repo()
              .resolveHandler(probeName, "onEntry", MethodType.methodType(void.class));
      assertNotNull(mh);
      mh.invoke(); // must not throw
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  @Test
  void resolveHandler_stripsPrefix_fromDollarSignHandlerName() throws Throwable {
    String probeName = "test/PrefixedProbe";
    BTraceProbe probe = stubProbe(probeName, TestHandlers.class);
    HandlerRepositoryImpl.registerProbe(probe);
    try {
      // Instrumented call sites use prefixed names like "$btrace$test$PrefixedProbe$onEntry"
      MethodHandle mh =
          repo()
              .resolveHandler(
                  probeName,
                  "$btrace$test$PrefixedProbe$onEntry",
                  MethodType.methodType(void.class));
      assertNotNull(mh);
      mh.invoke();
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  @Test
  void resolveHandler_caches_secondCallReturnsSameHandle() {
    String probeName = "test/CacheProbe";
    BTraceProbe probe = stubProbe(probeName, TestHandlers.class);
    HandlerRepositoryImpl.registerProbe(probe);
    try {
      MethodType type = MethodType.methodType(void.class);
      MethodHandle mh1 = repo().resolveHandler(probeName, "onEntry", type);
      MethodHandle mh2 = repo().resolveHandler(probeName, "onEntry", type);
      assertNotNull(mh1);
      assertSame(mh1, mh2);
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }
  }

  @Test
  void resolveHandler_returnsNull_afterUnregister() {
    String probeName = "test/UnregisterProbe";
    BTraceProbe probe = stubProbe(probeName, TestHandlers.class);
    HandlerRepositoryImpl.registerProbe(probe);

    // Pre-populate cache
    MethodType type = MethodType.methodType(void.class);
    assertNotNull(repo().resolveHandler(probeName, "onEntry", type));

    // Unregister clears both probeMap and cache
    HandlerRepositoryImpl.unregisterProbe(probe);
    MethodHandle mh = repo().resolveHandler(probeName, "onEntry", type);
    assertNull(mh);
  }

  @Test
  void unregisterProbe_doesNotEvictOtherProbesCacheEntries() {
    // Regression: old implementation used a custom substring match that could
    // incorrectly match "test/Probe#..." when unregistering "test/P#..." if not
    // anchored with the '#' delimiter. The current implementation uses
    // key.startsWith(probeName + "#"), so "test/ProbeExtended#..." must survive
    // when "test/Probe" is unregistered.
    String probe1Name = "test/PrefixRegressionBase";
    String probe2Name = "test/PrefixRegressionBaseExtended";
    BTraceProbe probe1 = stubProbe(probe1Name, TestHandlers.class);
    BTraceProbe probe2 = stubProbe(probe2Name, TestHandlers.class);
    HandlerRepositoryImpl.registerProbe(probe1);
    HandlerRepositoryImpl.registerProbe(probe2);
    try {
      MethodType type = MethodType.methodType(void.class);
      // Pre-populate cache for both
      assertNotNull(repo().resolveHandler(probe1Name, "onEntry", type));
      assertNotNull(repo().resolveHandler(probe2Name, "onEntry", type));

      // Unregister probe1 – must not evict probe2's cache entry
      HandlerRepositoryImpl.unregisterProbe(probe1);

      MethodHandle mh2 = repo().resolveHandler(probe2Name, "onEntry", type);
      assertNotNull(mh2, "probe2 handler should not be evicted when probe1 is unregistered");
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe2);
    }
  }

  // ---------------------------------------------------------------------------
  // resolveRuntime
  // ---------------------------------------------------------------------------

  @Test
  void resolveRuntime_returnsMethodHandle_forKnownStaticMethod() throws Throwable {
    MethodHandle mh =
        repo()
            .resolveRuntime(
                "java/lang/Math", "abs", MethodType.methodType(int.class, int.class));
    assertNotNull(mh);
    assertEquals(42, (int) mh.invoke(-42));
  }

  @Test
  void resolveRuntime_returnsNull_forUnknownClass() {
    MethodHandle mh =
        repo()
            .resolveRuntime(
                "nonexistent/RuntimeClass", "method", MethodType.methodType(void.class));
    assertNull(mh);
  }

  @Test
  void resolveRuntime_returnsNull_forUnknownMethod() {
    MethodHandle mh =
        repo()
            .resolveRuntime(
                "java/lang/Math",
                "noSuchMethod",
                MethodType.methodType(void.class));
    assertNull(mh);
  }

  @Test
  void resolveRuntime_caches_secondCallReturnsSameHandle() {
    MethodType type = MethodType.methodType(int.class, int.class);
    MethodHandle mh1 = repo().resolveRuntime("java/lang/Math", "abs", type);
    MethodHandle mh2 = repo().resolveRuntime("java/lang/Math", "abs", type);
    assertNotNull(mh1);
    assertSame(mh1, mh2);
  }

  // ---------------------------------------------------------------------------
  // Stub factory
  // ---------------------------------------------------------------------------

  /** Creates a minimal BTraceProbe stub that only implements getClassName and getDefinedClass. */
  private static BTraceProbe stubProbe(String internalName, Class<?> definedClass) {
    return new BTraceProbe() {
      @Override
      public String getClassName(boolean internal) {
        return internalName;
      }

      @Override
      public String getClassName() {
        return internalName.replace('/', '.');
      }

      @Override
      public Class<?> getDefinedClass() {
        return definedClass;
      }

      @Override
      public String getActionPrefix() {
        return "";
      }

      @Override
      public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        return Collections.emptyList();
      }

      @Override
      public byte[] getFullBytecode() {
        return new byte[0];
      }

      @Override
      public byte[] getDataHolderBytecode() {
        return new byte[0];
      }

      @Override
      public boolean isClassRenamed() {
        return false;
      }

      @Override
      public boolean isTransforming() {
        return false;
      }

      @Override
      public boolean isVerified() {
        return true;
      }

      @Override
      public void notifyTransform(String className) {}

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
        return null;
      }

      @Override
      public void unregister() {}

      @Override
      public boolean willInstrument(Class<?> clz) {
        return false;
      }

      @Override
      public void checkVerified() {}

      @Override
      public void applyArgs(ArgsMap argsMap) {}

      @Override
      public BTraceRuntime.Impl getRuntime() {
        return null;
      }

      @Override
      public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
      }
    };
  }
}
