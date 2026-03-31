package org.openjdk.btrace.indy;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.HandlerRepository;

class IndyDispatcherTest {

  private HandlerRepository savedRepository;

  @BeforeEach
  void saveRepository() {
    savedRepository = IndyDispatcher.repository;
  }

  @AfterEach
  void restoreRepository() {
    IndyDispatcher.repository = savedRepository;
  }

  // --- bootstrap() ---

  @Test
  void bootstrap_returnsConstantCallSite() throws Throwable {
    IndyDispatcher.repository = nullReturningRepo();
    CallSite cs =
        IndyDispatcher.bootstrap(
            MethodHandles.lookup(),
            "onEntry",
            MethodType.methodType(void.class),
            "some/Probe");
    assertInstanceOf(ConstantCallSite.class, cs);
  }

  @Test
  void bootstrap_noopWhenRepositoryIsNull() throws Throwable {
    IndyDispatcher.repository = null;
    MethodType type = MethodType.methodType(void.class, String.class);
    CallSite cs =
        IndyDispatcher.bootstrap(MethodHandles.lookup(), "onEntry", type, "some/Probe");
    assertNotNull(cs);
    cs.dynamicInvoker().invoke("arg"); // must not throw
  }

  @Test
  void bootstrap_noopWhenRepositoryReturnsNull() throws Throwable {
    IndyDispatcher.repository = nullReturningRepo();
    MethodType type = MethodType.methodType(void.class, int.class);
    CallSite cs =
        IndyDispatcher.bootstrap(MethodHandles.lookup(), "onEntry", type, "some/Probe");
    assertNotNull(cs);
    cs.dynamicInvoker().invoke(42); // must not throw
  }

  @Test
  void bootstrap_noopWhenRepositoryThrows() throws Throwable {
    IndyDispatcher.repository = throwingRepo();
    MethodType type = MethodType.methodType(void.class);
    CallSite cs =
        IndyDispatcher.bootstrap(MethodHandles.lookup(), "onEntry", type, "some/Probe");
    assertNotNull(cs);
    cs.dynamicInvoker().invoke(); // must not throw
  }

  @Test
  void bootstrap_usesResolvedHandle() throws Throwable {
    MethodHandle target =
        MethodHandles.lookup()
            .findStatic(
                IndyDispatcherTest.class, "noop", MethodType.methodType(void.class));
    IndyDispatcher.repository = repoReturning(target);
    CallSite cs =
        IndyDispatcher.bootstrap(
            MethodHandles.lookup(),
            "noop",
            MethodType.methodType(void.class),
            "some/Probe");
    assertSame(target, cs.getTarget());
  }

  // --- runtimeBootstrap() ---

  @Test
  void runtimeBootstrap_returnsConstantCallSite() throws Throwable {
    IndyDispatcher.repository = nullReturningRepo();
    CallSite cs =
        IndyDispatcher.runtimeBootstrap(
            MethodHandles.lookup(),
            "hit",
            MethodType.methodType(boolean.class, int.class),
            "some/Runtime");
    assertInstanceOf(ConstantCallSite.class, cs);
  }

  @Test
  void runtimeBootstrap_noopWhenRepositoryThrows() throws Throwable {
    IndyDispatcher.repository = throwingRepo();
    MethodType type = MethodType.methodType(void.class, int.class);
    CallSite cs =
        IndyDispatcher.runtimeBootstrap(
            MethodHandles.lookup(), "updateEndTs", type, "some/Runtime");
    assertNotNull(cs);
    cs.dynamicInvoker().invoke(1); // must not throw
  }

  @Test
  void runtimeBootstrap_noopWhenRepositoryIsNull() throws Throwable {
    IndyDispatcher.repository = null;
    MethodType type = MethodType.methodType(void.class, int.class);
    CallSite cs =
        IndyDispatcher.runtimeBootstrap(
            MethodHandles.lookup(), "updateEndTs", type, "some/Runtime");
    assertNotNull(cs);
    cs.dynamicInvoker().invoke(1); // must not throw
  }

  @Test
  void runtimeBootstrap_defaultReturnsFalseForBooleanType() throws Throwable {
    IndyDispatcher.repository = null;
    MethodType type = MethodType.methodType(boolean.class, int.class);
    CallSite cs =
        IndyDispatcher.runtimeBootstrap(MethodHandles.lookup(), "hit", type, "some/Runtime");
    Object result = cs.dynamicInvoker().invoke(1);
    assertEquals(Boolean.FALSE, result);
  }

  @Test
  void runtimeBootstrap_defaultReturnsZeroForLongType() throws Throwable {
    IndyDispatcher.repository = null;
    MethodType type = MethodType.methodType(long.class, int.class);
    CallSite cs =
        IndyDispatcher.runtimeBootstrap(
            MethodHandles.lookup(), "hitTimed", type, "some/Runtime");
    Object result = cs.dynamicInvoker().invoke(1);
    assertEquals(0L, result);
  }

  @Test
  void runtimeBootstrap_defaultReturnsZeroForIntType() throws Throwable {
    IndyDispatcher.repository = null;
    MethodType type = MethodType.methodType(int.class, int.class);
    CallSite cs =
        IndyDispatcher.runtimeBootstrap(
            MethodHandles.lookup(), "someInt", type, "some/Runtime");
    Object result = cs.dynamicInvoker().invoke(1);
    assertEquals(0, result);
  }

  // --- levelBootstrap() ---

  @Test
  void levelBootstrap_returnsZeroWhenRepositoryIsNull() throws Throwable {
    IndyDispatcher.repository = null;
    CallSite cs =
        IndyDispatcher.levelBootstrap(
            MethodHandles.lookup(), "levelCheck", MethodType.methodType(int.class), "p.Probe", 0, Integer.MAX_VALUE);
    assertEquals(0, (int) cs.dynamicInvoker().invoke());
  }

  @Test
  void levelBootstrap_returnsOneWhenLevelInRange() throws Throwable {
    IndyDispatcher.repository = repoWithLevel(5);
    CallSite cs =
        IndyDispatcher.levelBootstrap(
            MethodHandles.lookup(), "levelCheck", MethodType.methodType(int.class), "p.Probe", 1, 10);
    assertEquals(1, (int) cs.dynamicInvoker().invoke());
  }

  @Test
  void levelBootstrap_returnsZeroWhenLevelBelowRange() throws Throwable {
    IndyDispatcher.repository = repoWithLevel(0);
    CallSite cs =
        IndyDispatcher.levelBootstrap(
            MethodHandles.lookup(), "levelCheck", MethodType.methodType(int.class), "p.Probe", 1, 10);
    assertEquals(0, (int) cs.dynamicInvoker().invoke());
  }

  @Test
  void levelBootstrap_returnsZeroWhenLevelAboveRange() throws Throwable {
    IndyDispatcher.repository = repoWithLevel(11);
    CallSite cs =
        IndyDispatcher.levelBootstrap(
            MethodHandles.lookup(), "levelCheck", MethodType.methodType(int.class), "p.Probe", 1, 10);
    assertEquals(0, (int) cs.dynamicInvoker().invoke());
  }

  @Test
  void levelBootstrap_returnsZeroWhenProbeNotRegistered() throws Throwable {
    IndyDispatcher.repository = repoWithLevel(Integer.MIN_VALUE);
    CallSite cs =
        IndyDispatcher.levelBootstrap(
            MethodHandles.lookup(), "levelCheck", MethodType.methodType(int.class), "p.Probe", 0, Integer.MAX_VALUE);
    assertEquals(0, (int) cs.dynamicInvoker().invoke());
  }

  @Test
  void levelBootstrap_reflectsLevelChangeAfterLinking() throws Throwable {
    int[] levelHolder = {5};
    HandlerRepository repo = new HandlerRepository() {
      @Override public MethodHandle resolveHandler(String p, String h, MethodType t) { return null; }
      @Override public MethodHandle resolveRuntime(String o, String n, MethodType t) { return null; }
      @Override public int getLevel(String probeName) { return levelHolder[0]; }
    };
    IndyDispatcher.repository = repo;
    CallSite cs =
        IndyDispatcher.levelBootstrap(
            MethodHandles.lookup(), "levelCheck", MethodType.methodType(int.class), "p.Probe", 1, 10);
    assertEquals(1, (int) cs.dynamicInvoker().invoke());
    // Simulate setInstrumentationLevel pushing level out of range
    levelHolder[0] = 20;
    assertEquals(0, (int) cs.dynamicInvoker().invoke());
  }

  // --- helpers ---

  public static void noop() {}

  private static HandlerRepository nullReturningRepo() {
    return new HandlerRepository() {
      @Override
      public MethodHandle resolveHandler(String p, String h, MethodType t) {
        return null;
      }

      @Override
      public MethodHandle resolveRuntime(String o, String n, MethodType t) {
        return null;
      }

      @Override
      public int getLevel(String probeName) {
        return Integer.MAX_VALUE;
      }
    };
  }

  private static HandlerRepository throwingRepo() {
    return new HandlerRepository() {
      @Override
      public MethodHandle resolveHandler(String p, String h, MethodType t) {
        throw new RuntimeException("repository unavailable");
      }

      @Override
      public MethodHandle resolveRuntime(String o, String n, MethodType t) {
        throw new RuntimeException("repository unavailable");
      }

      @Override
      public int getLevel(String probeName) {
        return Integer.MAX_VALUE;
      }
    };
  }

  private static HandlerRepository repoWithLevel(int level) {
    return new HandlerRepository() {
      @Override
      public MethodHandle resolveHandler(String p, String h, MethodType t) {
        return null;
      }

      @Override
      public MethodHandle resolveRuntime(String o, String n, MethodType t) {
        return null;
      }

      @Override
      public int getLevel(String probeName) {
        return level;
      }
    };
  }

  private static HandlerRepository repoReturning(MethodHandle mh) {
    return new HandlerRepository() {
      @Override
      public MethodHandle resolveHandler(String p, String h, MethodType t) {
        return mh;
      }

      @Override
      public MethodHandle resolveRuntime(String o, String n, MethodType t) {
        return mh;
      }

      @Override
      public int getLevel(String probeName) {
        return Integer.MAX_VALUE;
      }
    };
  }
}
